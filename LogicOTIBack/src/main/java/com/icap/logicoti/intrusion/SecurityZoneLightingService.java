package com.icap.logicoti.intrusion;

import com.icap.logicoti.device.AreaStateResponse;
import com.icap.logicoti.device.AreaStateService;
import com.icap.logicoti.device.DeviceCommandExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SecurityZoneLightingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            SecurityZoneLightingService.class
    );

    private static final String TARGETS_QUERY = """
            SELECT
                zone.code AS zone_code,
                device.id,
                device.code,
                device.name,
                area.code AS area_code
            FROM security_zone zone
            INNER JOIN security_zone_area zone_area
                ON zone_area.zone_code = zone.code
            INNER JOIN building_area area
                ON area.id = zone_area.area_id
            INNER JOIN building_device device
                ON device.area_id = area.id
            WHERE zone.active = TRUE
              AND area.active = TRUE
              AND device.active = TRUE
              AND device.device_type = 'LIGHT'
              AND device.controllable = TRUE
              AND device.plc_command_tag IS NOT NULL
              AND TRIM(device.plc_command_tag) <> ''
              AND device.plc_state_tag IS NOT NULL
              AND TRIM(device.plc_state_tag) <> ''
              AND zone.code IN (%s)
            ORDER BY zone.display_order, area.display_order, device.display_order
            """;

    private final JdbcTemplate jdbcTemplate;
    private final AreaStateService areaStateService;
    private final DeviceCommandExecutionService commandService;
    private final AutomaticLightingRuntimeService automaticLightingRuntimeService;

    public SecurityZoneLightingService(
            JdbcTemplate jdbcTemplate,
            AreaStateService areaStateService,
            DeviceCommandExecutionService commandService,
            AutomaticLightingRuntimeService automaticLightingRuntimeService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.areaStateService = areaStateService;
        this.commandService = commandService;
        this.automaticLightingRuntimeService = automaticLightingRuntimeService;
    }

    @Transactional
    public LightingActionResult turnOnZones(
            Collection<String> requestedZoneCodes,
            String reason,
            Long triggerEventId
    ) {
        List<String> zoneCodes = normalize(requestedZoneCodes);
        List<ZoneLight> lights = findTargets(zoneCodes);
        List<String> failures = new ArrayList<>();
        Map<String, AreaStateResponse> areaStates = new HashMap<>();
        int changedLights = 0;

        for (ZoneLight light : lights) {
            try {
                AreaStateResponse areaState = areaStates.computeIfAbsent(
                        light.areaCode(),
                        areaStateService::getAreaState
                );

                AreaStateResponse.DeviceStateResponse current =
                        findDeviceState(areaState, light.code());

                if (!areaState.connected() || current.state() == null) {
                    failures.add(light.name()
                            + ": no fue posible confirmar su estado.");
                    continue;
                }

                if (Boolean.TRUE.equals(current.state())) {
                    if (isOwned(light.id())) {
                        updateOwnership(
                                light.id(),
                                reason,
                                triggerEventId
                        );
                        continue;
                    }

                    if (isAutomaticallyOwned(light.id())) {
                        // Una luz encendida por V16 se transfiere a seguridad
                        // para impedir que venza mientras la zona está armada.
                        claim(light, reason, triggerEventId);
                        automaticLightingRuntimeService.release(
                                light.code()
                        );
                        continue;
                    }

                    // Si estaba encendida sin pertenecer a ninguna
                    // automatización, se considera manual y no se toma.
                    continue;
                }

                AreaStateResponse response =
                        commandService.executeAutomatic(
                                light.code(),
                                true
                        );

                AreaStateResponse.DeviceStateResponse confirmation =
                        findDeviceState(response, light.code());

                if (!Boolean.TRUE.equals(confirmation.command())
                        || !Boolean.TRUE.equals(confirmation.state())) {
                    failures.add(light.name()
                            + ": el PLC no confirmó el encendido.");
                    continue;
                }

                claim(light, reason, triggerEventId);
                changedLights++;

                areaStates.put(light.areaCode(), response);
            } catch (RuntimeException exception) {
                failures.add(light.name() + ": " + safeMessage(exception));
                LOGGER.warn(
                        "No se pudo encender {} para seguridad por zonas: {}",
                        light.code(),
                        exception.getMessage()
                );
            }
        }

        return new LightingActionResult(
                failures.isEmpty(),
                changedLights,
                List.copyOf(failures)
        );
    }

    @Transactional
    public LightingActionResult turnOffOwnedZones(
            Collection<String> requestedZoneCodes
    ) {
        List<String> zoneCodes = normalize(requestedZoneCodes);
        String query = """
                SELECT
                    runtime.zone_code,
                    device.id,
                    device.code,
                    device.name,
                    area.code AS area_code
                FROM security_zone_light_runtime runtime
                INNER JOIN building_device device
                    ON device.id = runtime.device_id
                INNER JOIN building_area area
                    ON area.id = device.area_id
                WHERE runtime.zone_code IN (%s)
                ORDER BY runtime.activated_at, device.id
                """.formatted(placeholders(zoneCodes.size()));

        List<ZoneLight> lights = jdbcTemplate.query(
                query,
                this::mapLight,
                zoneCodes.toArray()
        );

        List<String> failures = new ArrayList<>();
        int changedLights = 0;

        for (ZoneLight light : lights) {
            try {
                AreaStateResponse response =
                        commandService.executeAutomatic(
                                light.code(),
                                false
                        );

                AreaStateResponse.DeviceStateResponse confirmation =
                        findDeviceState(response, light.code());

                if (!Boolean.FALSE.equals(confirmation.command())
                        || !Boolean.FALSE.equals(confirmation.state())) {
                    failures.add(light.name()
                            + ": el PLC no confirmó el apagado.");
                    continue;
                }

                release(light.id());
                changedLights++;
            } catch (RuntimeException exception) {
                failures.add(light.name() + ": " + safeMessage(exception));
                LOGGER.warn(
                        "No se pudo apagar {} después de la alarma: {}",
                        light.code(),
                        exception.getMessage()
                );
            }
        }

        return new LightingActionResult(
                failures.isEmpty(),
                changedLights,
                List.copyOf(failures)
        );
    }

    @Transactional(readOnly = true)
    public int countOwnedLights(String zoneCode) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM security_zone_light_runtime
                WHERE zone_code = ?
                """,
                Integer.class,
                zoneCode
        );

        return count == null ? 0 : count;
    }

    @Transactional(readOnly = true)
    public boolean isOwned(String deviceCode) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM security_zone_light_runtime runtime
                INNER JOIN building_device device
                    ON device.id = runtime.device_id
                WHERE UPPER(device.code) = UPPER(?)
                """,
                Integer.class,
                deviceCode
        );

        return count != null && count > 0;
    }

    private List<ZoneLight> findTargets(List<String> zoneCodes) {
        String query = TARGETS_QUERY.formatted(
                placeholders(zoneCodes.size())
        );

        return jdbcTemplate.query(
                query,
                this::mapLight,
                zoneCodes.toArray()
        );
    }

    private ZoneLight mapLight(
            java.sql.ResultSet resultSet,
            int rowNumber
    ) throws java.sql.SQLException {
        return new ZoneLight(
                resultSet.getString("zone_code"),
                resultSet.getLong("id"),
                resultSet.getString("code"),
                resultSet.getString("name"),
                resultSet.getString("area_code")
        );
    }

    private AreaStateResponse.DeviceStateResponse findDeviceState(
            AreaStateResponse response,
            String deviceCode
    ) {
        return response.devices()
                .stream()
                .filter(device -> device.code().equalsIgnoreCase(deviceCode))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró el retorno de " + deviceCode + "."
                ));
    }

    private boolean isOwned(long deviceId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM security_zone_light_runtime
                WHERE device_id = ?
                """,
                Integer.class,
                deviceId
        );

        return count != null && count > 0;
    }

    private boolean isAutomaticallyOwned(long deviceId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM security_automatic_lighting_runtime
                WHERE device_id = ?
                """,
                Integer.class,
                deviceId
        );

        return count != null && count > 0;
    }

    private void claim(
            ZoneLight light,
            String reason,
            Long triggerEventId
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO security_zone_light_runtime (
                    device_id,
                    zone_code,
                    activation_reason,
                    trigger_event_id,
                    activated_at
                )
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (device_id) DO UPDATE
                SET activation_reason = EXCLUDED.activation_reason,
                    trigger_event_id = EXCLUDED.trigger_event_id
                """,
                light.id(),
                light.zoneCode(),
                reason,
                triggerEventId,
                java.sql.Timestamp.from(Instant.now())
        );
    }

    private void updateOwnership(
            long deviceId,
            String reason,
            Long triggerEventId
    ) {
        jdbcTemplate.update(
                """
                UPDATE security_zone_light_runtime
                SET activation_reason = ?,
                    trigger_event_id = ?
                WHERE device_id = ?
                """,
                reason,
                triggerEventId,
                deviceId
        );
    }

    private void release(long deviceId) {
        jdbcTemplate.update(
                "DELETE FROM security_zone_light_runtime WHERE device_id = ?",
                deviceId
        );
    }

    private List<String> normalize(Collection<String> requestedZoneCodes) {
        return requestedZoneCodes.stream()
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private String placeholders(int size) {
        return String.join(", ", java.util.Collections.nCopies(size, "?"));
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null
                || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private record ZoneLight(
            String zoneCode,
            long id,
            String code,
            String name,
            String areaCode
    ) {
    }

    public record LightingActionResult(
            boolean successful,
            int changedLights,
            List<String> failures
    ) {
    }
}
