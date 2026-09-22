package com.icap.logicoti.intrusion;

import com.icap.logicoti.device.AreaStateResponse;
import com.icap.logicoti.device.AreaStateService;
import com.icap.logicoti.device.DeviceCommandExecutionService;
import com.icap.logicoti.event.SensorEventHistoryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AreaInactivityService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AreaInactivityService.class);

    private static final String TOPIC =
            "/topic/security/inactivity";

    private static final Duration ACTIVE_MOTION_REFRESH =
            Duration.ofSeconds(30);

    private static final Duration RETRY_DELAY =
            Duration.ofSeconds(30);

    private final JdbcTemplate jdbcTemplate;
    private final SecurityScheduleService scheduleService;
    private final AreaInactivityRuntimeService runtimeService;
    private final DeviceCommandExecutionService commandService;
    private final AreaStateService areaStateService;
    private final SecurityZoneLightingService securityLightingService;
    private final SimpMessagingTemplate messagingTemplate;

    private final Map<String, Instant> lastProcessedMotion =
            new HashMap<>();

    public AreaInactivityService(
            JdbcTemplate jdbcTemplate,
            SecurityScheduleService scheduleService,
            AreaInactivityRuntimeService runtimeService,
            DeviceCommandExecutionService commandService,
            AreaStateService areaStateService,
            SecurityZoneLightingService securityLightingService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.scheduleService = scheduleService;
        this.runtimeService = runtimeService;
        this.commandService = commandService;
        this.areaStateService = areaStateService;
        this.securityLightingService = securityLightingService;
        this.messagingTemplate = messagingTemplate;
    }

    public void processMotion(SensorEventHistoryResponse event) {
        if (!"MOTION".equalsIgnoreCase(event.deviceType())
                || !"ACTIVATED".equalsIgnoreCase(event.eventType())
                || !event.currentState()) {
            return;
        }

        recordMotionActivity(event.deviceCode());
    }

    public void refreshActiveMotion(String sensorCode) {
        recordMotionActivity(sensorCode);
    }

    private void recordMotionActivity(String sensorCode) {
        Instant now = Instant.now();

        if (!reserveMotionWindow(sensorCode, now)
                || isSensorBypassed(sensorCode)) {
            return;
        }

        try {
            SecuritySettingsResponse settings =
                    scheduleService.getSettings();

            if (!settings.areaInactivityEnabled()) {
                return;
            }

            AreaInactivityRuntimeService.MotionArea area =
                    runtimeService.findMotionArea(sensorCode);

            if (area == null
                    || !area.energySavingEnabled()
                    || area.zoneArmed()) {
                return;
            }

            turnOnAreaLights(area.code());

            runtimeService.recordActivity(
                    area.id(),
                    now,
                    now.plus(Duration.ofMinutes(
                            settings.lightInactivityMinutes()
                    )),
                    now.plus(Duration.ofMinutes(
                            settings.minisplitInactivityMinutes()
                    ))
            );

            publishStatus();

        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "No se pudo registrar actividad para {}: {}",
                    sensorCode,
                    exception.getMessage()
            );
        }
    }

    @Scheduled(
            initialDelayString =
                    "${area.inactivity.initial-delay-ms:12000}",
            fixedDelayString =
                    "${area.inactivity.poll-ms:5000}"
    )
    public void processExpiredAreas() {
        int removed = runtimeService.removeInactiveEntries();

        if (removed > 0) {
            LOGGER.info(
                    "Se descartaron {} ejecuciones de inactividad "
                            + "asociadas a áreas inactivas.",
                    removed
            );
        }

        SecuritySettingsResponse settings;

        try {
            settings = scheduleService.getSettings();
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "No se pudo consultar la configuración de inactividad: {}",
                    exception.getMessage()
            );
            return;
        }

        if (!settings.areaInactivityEnabled()) {
            return;
        }

        Instant now = Instant.now();
        List<AreaInactivityRuntimeService.RuntimeArea> dueAreas =
                runtimeService.findDueAreas(now);

        if (dueAreas.isEmpty()) {
            return;
        }

        boolean changed = false;

        for (AreaInactivityRuntimeService.RuntimeArea area : dueAreas) {
            changed |= processArea(area, now);
        }

        if (changed) {
            publishStatus();
        }
    }

    /**
     * Apaga luces y minisplits al completar el armado de una o varias zonas.
     * El armado no se rechaza si un equipo no responde: la alarma conserva
     * prioridad y el fallo queda disponible para registro y diagnóstico.
     */
    public EquipmentShutdownResult turnOffZoneEquipment(
            Collection<String> requestedZoneCodes
    ) {
        Set<String> zoneCodes = new LinkedHashSet<>();

        for (String requestedCode : requestedZoneCodes) {
            if (requestedCode != null && !requestedCode.isBlank()) {
                zoneCodes.add(
                        requestedCode.trim().toUpperCase(Locale.ROOT)
                );
            }
        }

        if (zoneCodes.isEmpty()) {
            return new EquipmentShutdownResult(
                    true,
                    0,
                    0,
                    List.of()
            );
        }

        String placeholders = String.join(
                ", ",
                java.util.Collections.nCopies(zoneCodes.size(), "?")
        );
        List<ZoneArea> areas = jdbcTemplate.query(
                """
                SELECT DISTINCT
                    area.id,
                    area.code,
                    area.name
                FROM security_zone_area zone_area
                INNER JOIN building_area area
                    ON area.id = zone_area.area_id
                INNER JOIN building_floor floor
                    ON floor.id = area.floor_id
                WHERE zone_area.zone_code IN (%s)
                  AND area.active = TRUE
                  AND floor.active = TRUE
                ORDER BY area.id
                """.formatted(placeholders),
                (resultSet, rowNumber) -> new ZoneArea(
                        resultSet.getLong("id"),
                        resultSet.getString("code"),
                        resultSet.getString("name")
                ),
                zoneCodes.toArray()
        );

        List<String> failures = new ArrayList<>();
        int lightsTurnedOff = 0;
        int minisplitsTurnedOff = 0;

        for (ZoneArea area : areas) {
            try {
                AreaStateResponse areaState =
                        areaStateService.getAreaState(area.code());

                if (!areaState.connected()) {
                    failures.add(
                            area.name()
                                    + ": no fue posible leer el PLC."
                    );
                    continue;
                }

                GroupResult lights = turnOffDevices(
                        areaState,
                        "LIGHT"
                );
                GroupResult minisplits = turnOffDevices(
                        areaState,
                        "MINISPLIT"
                );

                lightsTurnedOff += lights.turnedOff();
                minisplitsTurnedOff += minisplits.turnedOff();

                if (!lights.success() || !minisplits.success()) {
                    failures.add(
                            area.name()
                                    + ": el PLC no confirmó todos los apagados."
                    );
                }
            } catch (RuntimeException exception) {
                failures.add(
                        area.name() + ": " + safeMessage(exception)
                );
                LOGGER.warn(
                        "No se pudo apagar el equipo de {} al armar: {}",
                        area.code(),
                        exception.getMessage()
                );
            } finally {
                runtimeService.release(area.id());
            }
        }

        publishStatus();
        return new EquipmentShutdownResult(
                failures.isEmpty(),
                lightsTurnedOff,
                minisplitsTurnedOff,
                List.copyOf(failures)
        );
    }

    private boolean processArea(
            AreaInactivityRuntimeService.RuntimeArea area,
            Instant now
    ) {
        if (!runtimeService.canManageArea(area.code())) {
            runtimeService.release(area.id());
            return true;
        }

        AreaStateResponse areaState;

        try {
            areaState = areaStateService.getAreaState(area.code());
        } catch (RuntimeException exception) {
            postponePendingActions(area, now);
            LOGGER.warn(
                    "No se pudo leer el área {} para apagar equipos: {}",
                    area.code(),
                    exception.getMessage()
            );
            return false;
        }

        if (!areaState.connected()) {
            postponePendingActions(area, now);
            return false;
        }

        boolean changed = false;

        if (!area.lightProcessed()
                && !now.isBefore(area.lightTurnOffAt())) {
            GroupResult result = turnOffDevices(
                    areaState,
                    "LIGHT"
            );

            if (result.success()) {
                runtimeService.markLightProcessed(
                        area.id(),
                        result.turnedOff()
                );
                changed = true;
            } else {
                runtimeService.postponeLight(
                        area.id(),
                        now.plus(RETRY_DELAY)
                );
            }
        }

        if (!area.minisplitProcessed()
                && !now.isBefore(area.minisplitTurnOffAt())) {
            GroupResult result = turnOffDevices(
                    areaState,
                    "MINISPLIT"
            );

            if (result.success()) {
                runtimeService.markMinisplitProcessed(
                        area.id(),
                        result.turnedOff()
                );
                changed = true;
            } else {
                runtimeService.postponeMinisplit(
                        area.id(),
                        now.plus(RETRY_DELAY)
                );
            }
        }

        return changed;
    }

    private GroupResult turnOffDevices(
            AreaStateResponse areaState,
            String deviceType
    ) {
        int turnedOff = 0;
        boolean success = true;

        for (AreaStateResponse.DeviceStateResponse device
                : areaState.devices()) {

            if (!deviceType.equalsIgnoreCase(device.type())
                    || !device.controllable()
                    || !Boolean.TRUE.equals(device.state())) {
                continue;
            }

            if ("LIGHT".equalsIgnoreCase(deviceType)
                    && securityLightingService.isOwned(device.code())) {
                // Una alarma activa tiene prioridad sobre el ahorro.
                continue;
            }

            try {
                AreaStateResponse confirmation =
                        commandService.executeAutomatic(
                                device.code(),
                                false
                        );

                AreaStateResponse.DeviceStateResponse confirmedDevice =
                        confirmation.devices()
                                .stream()
                                .filter(item ->
                                        item.code().equalsIgnoreCase(
                                                device.code()
                                        )
                                )
                                .findFirst()
                                .orElse(null);

                if (confirmedDevice != null
                        && Boolean.FALSE.equals(
                                confirmedDevice.command()
                        )
                        && Boolean.FALSE.equals(
                                confirmedDevice.state()
                        )) {
                    turnedOff++;
                } else {
                    success = false;
                }

            } catch (RuntimeException exception) {
                success = false;
                LOGGER.warn(
                        "No se pudo apagar {} por inactividad: {}",
                        device.code(),
                        exception.getMessage()
                );
            }
        }

        return new GroupResult(success, turnedOff);
    }

    private void turnOnAreaLights(String areaCode) {
        AreaStateResponse areaState = areaStateService.getAreaState(areaCode);

        if (!areaState.connected()) {
            return;
        }

        for (AreaStateResponse.DeviceStateResponse device
                : areaState.devices()) {
            if (!"LIGHT".equalsIgnoreCase(device.type())
                    || !device.controllable()
                    || device.state() == null
                    || Boolean.TRUE.equals(device.state())) {
                continue;
            }

            try {
                commandService.executeAutomatic(device.code(), true);
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "No se pudo encender {} por movimiento en {}: {}",
                        device.code(),
                        areaCode,
                        exception.getMessage()
                );
            }
        }
    }

    private void postponePendingActions(
            AreaInactivityRuntimeService.RuntimeArea area,
            Instant now
    ) {
        Instant retryAt = now.plus(RETRY_DELAY);

        if (!area.lightProcessed()
                && !now.isBefore(area.lightTurnOffAt())) {
            runtimeService.postponeLight(area.id(), retryAt);
        }

        if (!area.minisplitProcessed()
                && !now.isBefore(area.minisplitTurnOffAt())) {
            runtimeService.postponeMinisplit(area.id(), retryAt);
        }
    }

    public AreaInactivityStatusResponse getStatus() {
        SecuritySettingsResponse settings = scheduleService.getSettings();
        List<AreaInactivityRuntimeService.RuntimeArea> runtimeAreas =
                runtimeService.findAll();

        int pendingAreas = (int) runtimeAreas.stream()
                .filter(area ->
                        !area.lightProcessed()
                                || !area.minisplitProcessed()
                )
                .count();

        int lightsTurnedOff = runtimeAreas.stream()
                .mapToInt(
                        AreaInactivityRuntimeService.RuntimeArea
                                ::lightsTurnedOff
                )
                .sum();

        int minisplitsTurnedOff = runtimeAreas.stream()
                .mapToInt(
                        AreaInactivityRuntimeService.RuntimeArea
                                ::minisplitsTurnedOff
                )
                .sum();

        Instant lastMotionAt = runtimeAreas.stream()
                .map(AreaInactivityRuntimeService.RuntimeArea::lastMotionAt)
                .max(Instant::compareTo)
                .orElse(null);

        Instant nextActionAt = runtimeAreas.stream()
                .flatMap(area -> java.util.stream.Stream.of(
                        area.lightProcessed()
                                ? null
                                : area.lightTurnOffAt(),
                        area.minisplitProcessed()
                                ? null
                                : area.minisplitTurnOffAt()
                ))
                .filter(java.util.Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);

        List<AreaInactivityStatusResponse.AreaStatus> areas =
                runtimeAreas.stream()
                        .map(area ->
                                new AreaInactivityStatusResponse.AreaStatus(
                                        area.code(),
                                        area.name(),
                                        area.floorCode(),
                                        area.floorName(),
                                        area.lastMotionAt(),
                                        area.lightTurnOffAt(),
                                        area.minisplitTurnOffAt(),
                                        area.lightProcessed(),
                                        area.minisplitProcessed(),
                                        area.lightsTurnedOff(),
                                        area.minisplitsTurnedOff()
                                )
                        )
                        .toList();

        String message;
        if (!settings.areaInactivityEnabled()) {
            message = "El apagado por inactividad está deshabilitado.";
        } else if (runtimeAreas.isEmpty()) {
            message = "Ahorro habilitado, esperando movimiento en las áreas seleccionadas.";
        } else if (pendingAreas > 0) {
            message = "Vigilando la actividad de las áreas.";
        } else {
            message = "Las áreas registradas ya completaron su apagado.";
        }

        return new AreaInactivityStatusResponse(
                settings.areaInactivityEnabled(),
                settings.lightInactivityMinutes(),
                settings.minisplitInactivityMinutes(),
                runtimeAreas.size(),
                pendingAreas,
                lightsTurnedOff,
                minisplitsTurnedOff,
                lastMotionAt,
                nextActionAt,
                areas,
                message,
                Instant.now()
        );
    }

    private synchronized boolean reserveMotionWindow(
            String requestedSensorCode,
            Instant now
    ) {
        String sensorCode = requestedSensorCode
                .trim()
                .toUpperCase(Locale.ROOT);

        Instant previous = lastProcessedMotion.get(sensorCode);

        if (previous != null
                && now.isBefore(previous.plus(ACTIVE_MOTION_REFRESH))) {
            return false;
        }

        lastProcessedMotion.put(sensorCode, now);
        return true;
    }

    private boolean isSensorBypassed(String sensorCode) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sensor_bypass_history
                WHERE UPPER(device_code) = UPPER(?)
                  AND active = TRUE
                """,
                Integer.class,
                sensorCode
        );

        return count != null && count > 0;
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null
                || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private void publishStatus() {
        try {
            messagingTemplate.convertAndSend(TOPIC, getStatus());
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "No se pudo publicar el estado de inactividad: {}",
                    exception.getMessage()
            );
        }
    }

    private record GroupResult(boolean success, int turnedOff) {
    }

    private record ZoneArea(long id, String code, String name) {
    }

    public record EquipmentShutdownResult(
            boolean success,
            int lightsTurnedOff,
            int minisplitsTurnedOff,
            List<String> failures
    ) {
    }
}
