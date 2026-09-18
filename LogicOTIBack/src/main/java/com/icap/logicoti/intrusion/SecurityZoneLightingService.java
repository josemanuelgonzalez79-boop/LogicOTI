package com.icap.logicoti.intrusion;

import com.icap.logicoti.device.AreaStateResponse;
import com.icap.logicoti.device.AreaStateService;
import com.icap.logicoti.device.DeviceCommandExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SecurityZoneLightingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            SecurityZoneLightingService.class
    );

    private static final String TARGETS_QUERY = """
            SELECT
                device.id,
                device.code,
                device.name,
                area.code AS area_code
            FROM security_automatic_lighting_target target
            INNER JOIN building_device device
                ON device.id = target.device_id
            INNER JOIN building_area area
                ON area.id = device.area_id
            INNER JOIN building_floor floor
                ON floor.id = area.floor_id
            CROSS JOIN security_settings settings
            WHERE settings.id = 1
              AND settings.automatic_lighting_enabled = TRUE
              AND device.active = TRUE
              AND area.active = TRUE
              AND floor.active = TRUE
              AND device.device_type = 'LIGHT'
              AND device.controllable = TRUE
              AND device.plc_command_tag IS NOT NULL
              AND TRIM(device.plc_command_tag) <> ''
              AND device.plc_state_tag IS NOT NULL
              AND TRIM(device.plc_state_tag) <> ''
            ORDER BY floor.display_order,
                     area.display_order,
                     device.display_order,
                     device.id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final AreaStateService areaStateService;
    private final DeviceCommandExecutionService commandService;

    public SecurityZoneLightingService(
            JdbcTemplate jdbcTemplate,
            AreaStateService areaStateService,
            DeviceCommandExecutionService commandService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.areaStateService = areaStateService;
        this.commandService = commandService;
    }

    @Transactional
    public LightingActionResult turnOnEmergencySelection(
            String triggerZoneCode,
            Long triggerEventId
    ) {
        List<EmergencyLight> lights = jdbcTemplate.query(
                TARGETS_QUERY,
                (resultSet, rowNumber) -> new EmergencyLight(
                        resultSet.getLong("id"),
                        resultSet.getString("code"),
                        resultSet.getString("name"),
                        resultSet.getString("area_code")
                )
        );

        List<String> failures = new ArrayList<>();
        Map<String, AreaStateResponse> areaStates = new HashMap<>();
        int changedLights = 0;

        for (EmergencyLight light : lights) {
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

                if (!Boolean.TRUE.equals(current.state())) {
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

                    changedLights++;
                    areaStates.put(light.areaCode(), response);
                }

                claim(
                        light.id(),
                        triggerZoneCode,
                        triggerEventId
                );
            } catch (RuntimeException exception) {
                failures.add(light.name() + ": " + safeMessage(exception));
                LOGGER.warn(
                        "No se pudo encender {} como luz de emergencia: {}",
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
    public LightingActionResult turnOffOwnedIfNoActiveAlarm() {
        if (hasActiveAlarm()) {
            return new LightingActionResult(true, 0, List.of());
        }

        List<EmergencyLight> lights = jdbcTemplate.query("""
                SELECT
                    device.id,
                    device.code,
                    device.name,
                    area.code AS area_code
                FROM security_zone_light_runtime runtime
                INNER JOIN building_device device
                    ON device.id = runtime.device_id
                INNER JOIN building_area area
                    ON area.id = device.area_id
                ORDER BY runtime.activated_at, device.id
                """,
                (resultSet, rowNumber) -> new EmergencyLight(
                        resultSet.getLong("id"),
                        resultSet.getString("code"),
                        resultSet.getString("name"),
                        resultSet.getString("area_code")
                )
        );

        List<String> failures = new ArrayList<>();
        int changedLights = 0;

        for (EmergencyLight light : lights) {
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
                        "No se pudo apagar {} al reconocer la alarma: {}",
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
    public boolean isOwned(String deviceCode) {
        Integer count = jdbcTemplate.queryForObject("""
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

    private boolean hasActiveAlarm() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM security_zone_state
                WHERE mode = 'ALARM'
                """,
                Integer.class
        );

        return count != null && count > 0;
    }

    private AreaStateResponse.DeviceStateResponse findDeviceState(
            AreaStateResponse response,
            String deviceCode
    ) {
        return response.devices()
                .stream()
                .filter(device ->
                        device.code().equalsIgnoreCase(deviceCode)
                )
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró el retorno de " + deviceCode + "."
                ));
    }

    private void claim(
            long deviceId,
            String triggerZoneCode,
            Long triggerEventId
    ) {
        int updated = jdbcTemplate.update("""
                UPDATE security_zone_light_runtime
                SET zone_code = ?,
                    activation_reason = 'ALARM',
                    trigger_event_id = ?
                WHERE device_id = ?
                """,
                triggerZoneCode,
                triggerEventId,
                deviceId
        );

        if (updated > 0) {
            return;
        }

        try {
            jdbcTemplate.update("""
                INSERT INTO security_zone_light_runtime (
                    device_id,
                    zone_code,
                    activation_reason,
                    trigger_event_id,
                    activated_at
                )
                VALUES (?, ?, 'ALARM', ?, ?)
                """,
                deviceId,
                triggerZoneCode,
                triggerEventId,
                java.sql.Timestamp.from(Instant.now())
            );
        } catch (DuplicateKeyException exception) {
            claim(deviceId, triggerZoneCode, triggerEventId);
        }
    }

    private void release(long deviceId) {
        jdbcTemplate.update(
                "DELETE FROM security_zone_light_runtime WHERE device_id = ?",
                deviceId
        );
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null
                || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private record EmergencyLight(
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
