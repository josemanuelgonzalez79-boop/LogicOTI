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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AutomaticLightingService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AutomaticLightingService.class);

    private static final String TOPIC =
            "/topic/security/automatic-lighting";

    private static final Duration RETRY_DELAY =
            Duration.ofSeconds(30);

    private static final Duration ACTIVE_MOTION_REFRESH =
            Duration.ofSeconds(30);

    private final JdbcTemplate jdbcTemplate;
    private final SecurityScheduleService scheduleService;
    private final AutomaticLightingRuntimeService runtimeService;
    private final DeviceCommandExecutionService commandService;
    private final AreaStateService areaStateService;
    private final SimpMessagingTemplate messagingTemplate;

    private final Map<String, Instant> lastProcessedMotion =
            new HashMap<>();

    public AutomaticLightingService(
            JdbcTemplate jdbcTemplate,
            SecurityScheduleService scheduleService,
            AutomaticLightingRuntimeService runtimeService,
            DeviceCommandExecutionService commandService,
            AreaStateService areaStateService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.scheduleService = scheduleService;
        this.runtimeService = runtimeService;
        this.commandService = commandService;
        this.areaStateService = areaStateService;
        this.messagingTemplate = messagingTemplate;
    }

    public void processMotion(SensorEventHistoryResponse event) {
        if (!"MOTION".equalsIgnoreCase(event.deviceType())
                || !"ACTIVATED".equalsIgnoreCase(event.eventType())
                || !event.currentState()) {
            return;
        }

        processMotionActivity(event.deviceCode());
    }

    public void refreshActiveMotion(String sensorCode) {
        processMotionActivity(sensorCode);
    }

    private void processMotionActivity(String sensorCode) {
        Instant now = Instant.now();

        if (!reserveMotionWindow(sensorCode, now)
                || isSensorBypassed(sensorCode)) {
            return;
        }

        try {
            SecuritySettingsResponse settings =
                    scheduleService.getSettings();

            LightingWindow window = evaluateWindow(settings, now);

            if (!settings.automaticLightingEnabled()
                    || !window.active()) {
                return;
            }

            List<AutomaticLightingRuntimeService.LightingDevice> targets =
                    runtimeService.findTargets();

            if (targets.isEmpty()) {
                return;
            }

            Instant inactivityEnd = now.plus(
                    Duration.ofMinutes(
                            settings.lightInactivityMinutes()
                    )
            );

            Instant turnOffAt = inactivityEnd.isBefore(window.endAt())
                    ? inactivityEnd
                    : window.endAt();

            runtimeService.extendAll(now, turnOffAt);

            Set<String> ownedCodes = new HashSet<>();
            runtimeService.findOwnedLights().forEach(light ->
                    ownedCodes.add(
                            light.code().toUpperCase(Locale.ROOT)
                    )
            );

            Map<String, AreaStateResponse> areaStates =
                    new HashMap<>();

            for (AutomaticLightingRuntimeService.LightingDevice target
                    : targets) {

                AreaStateResponse areaState = areaStates.computeIfAbsent(
                        target.areaCode(),
                        areaStateService::getAreaState
                );

                AreaStateResponse.DeviceStateResponse deviceState =
                        areaState.devices()
                                .stream()
                                .filter(device ->
                                        device.code().equalsIgnoreCase(
                                                target.code()
                                        )
                                )
                                .findFirst()
                                .orElse(null);

                if (deviceState == null || !areaState.connected()) {
                    continue;
                }

                boolean alreadyOwned = ownedCodes.contains(
                        target.code().toUpperCase(Locale.ROOT)
                );

                if (Boolean.TRUE.equals(deviceState.state())) {
                    if (alreadyOwned) {
                        runtimeService.claim(
                                target.id(),
                                now,
                                turnOffAt
                        );
                    }

                    // Una luz encendida manualmente nunca se toma como
                    // propiedad de la automatización.
                    continue;
                }

                AreaStateResponse response =
                        commandService.executeAutomatic(
                                target.code(),
                                true
                        );

                AreaStateResponse.DeviceStateResponse confirmation =
                        response.devices()
                                .stream()
                                .filter(device ->
                                        device.code().equalsIgnoreCase(
                                                target.code()
                                        )
                                )
                                .findFirst()
                                .orElse(null);

                if (confirmation != null
                        && Boolean.TRUE.equals(confirmation.command())
                        && Boolean.TRUE.equals(confirmation.state())) {

                    runtimeService.claim(
                            target.id(),
                            now,
                            turnOffAt
                    );
                }
            }

            publishStatus();

        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "No se pudo procesar la iluminación automática por {}: {}",
                    sensorCode,
                    exception.getMessage()
            );
        }
    }

    @Scheduled(
            initialDelayString =
                    "${automatic.lighting.initial-delay-ms:10000}",
            fixedDelayString =
                    "${automatic.lighting.poll-ms:5000}"
    )
    public void turnOffExpiredLights() {
        int removed = runtimeService.removeInactiveEntries();

        if (removed > 0) {
            LOGGER.info(
                    "Se descartaron {} ejecuciones de iluminación "
                            + "asociadas a dispositivos o áreas inactivas.",
                    removed
            );
        }

        Instant now = Instant.now();
        List<AutomaticLightingRuntimeService.RuntimeLight> expired =
                runtimeService.findExpiredLights(now);

        if (expired.isEmpty()) {
            return;
        }

        for (AutomaticLightingRuntimeService.RuntimeLight light
                : expired) {
            try {
                AreaStateResponse response =
                        commandService.executeAutomatic(
                                light.code(),
                                false
                        );

                AreaStateResponse.DeviceStateResponse confirmation =
                        response.devices()
                                .stream()
                                .filter(device ->
                                        device.code().equalsIgnoreCase(
                                                light.code()
                                        )
                                )
                                .findFirst()
                                .orElse(null);

                if (confirmation != null
                        && Boolean.FALSE.equals(confirmation.command())
                        && Boolean.FALSE.equals(confirmation.state())) {
                    runtimeService.release(light.code());
                } else {
                    runtimeService.postpone(
                            light.id(),
                            now.plus(RETRY_DELAY)
                    );
                }

            } catch (RuntimeException exception) {
                runtimeService.postpone(
                        light.id(),
                        now.plus(RETRY_DELAY)
                );

                LOGGER.warn(
                        "No se pudo apagar automáticamente {}: {}",
                        light.code(),
                        exception.getMessage()
                );
            }
        }

        publishStatus();
    }

    public AutomaticLightingStatusResponse getStatus() {
        SecuritySettingsResponse settings =
                scheduleService.getSettings();

        Instant now = Instant.now();
        LightingWindow window = evaluateWindow(settings, now);

        List<AutomaticLightingRuntimeService.LightingDevice> targets =
                runtimeService.findTargets();

        List<AutomaticLightingRuntimeService.RuntimeLight> owned =
                runtimeService.findOwnedLights();

        Instant lastMotionAt = owned.stream()
                .map(AutomaticLightingRuntimeService.RuntimeLight::lastMotionAt)
                .max(Instant::compareTo)
                .orElse(null);

        Instant nextTurnOffAt = owned.stream()
                .map(AutomaticLightingRuntimeService.RuntimeLight::turnOffAt)
                .min(Instant::compareTo)
                .orElse(null);

        List<AutomaticLightingStatusResponse.ControlledLight> lights =
                owned.stream()
                        .map(light ->
                                new AutomaticLightingStatusResponse.ControlledLight(
                                        light.code(),
                                        light.name(),
                                        light.areaCode(),
                                        light.areaName(),
                                        light.activatedAt(),
                                        light.turnOffAt()
                                )
                        )
                        .toList();

        String message;
        if (!settings.automaticLightingEnabled()) {
            message = "La iluminación automática está deshabilitada.";
        } else if (!window.active()) {
            message = "Fuera del horario de iluminación automática.";
        } else if (owned.isEmpty()) {
            message = "Horario activo, sin luces encendidas automáticamente.";
        } else {
            message = "Iluminación automática activa.";
        }

        return new AutomaticLightingStatusResponse(
                settings.automaticLightingEnabled(),
                window.active(),
                settings.automaticLightingStartTime(),
                settings.automaticLightingEndTime(),
                settings.lightInactivityMinutes(),
                targets.size(),
                owned.size(),
                lastMotionAt,
                nextTurnOffAt,
                lights,
                message,
                now
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

    private void publishStatus() {
        try {
            messagingTemplate.convertAndSend(TOPIC, getStatus());
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "No se pudo publicar el estado de iluminación automática: {}",
                    exception.getMessage()
            );
        }
    }

    private LightingWindow evaluateWindow(
            SecuritySettingsResponse settings,
            Instant instant
    ) {
        ZoneId zoneId = ZoneId.of(settings.timezone());
        ZonedDateTime now = instant.atZone(zoneId);
        LocalDate date = now.toLocalDate();
        LocalTime time = now.toLocalTime();
        LocalTime start = settings.automaticLightingStartTime();
        LocalTime end = settings.automaticLightingEndTime();

        LocalDateTime startAt;
        LocalDateTime endAt;
        boolean active;

        if (start.isBefore(end)) {
            startAt = date.atTime(start);
            endAt = date.atTime(end);
            active = !time.isBefore(start) && time.isBefore(end);
        } else {
            active = !time.isBefore(start) || time.isBefore(end);

            if (!time.isBefore(start)) {
                startAt = date.atTime(start);
                endAt = date.plusDays(1).atTime(end);
            } else {
                startAt = date.minusDays(1).atTime(start);
                endAt = date.atTime(end);
            }
        }

        return new LightingWindow(
                active,
                startAt.atZone(zoneId).toInstant(),
                endAt.atZone(zoneId).toInstant()
        );
    }

    private record LightingWindow(
            boolean active,
            Instant startAt,
            Instant endAt
    ) {
    }
}
