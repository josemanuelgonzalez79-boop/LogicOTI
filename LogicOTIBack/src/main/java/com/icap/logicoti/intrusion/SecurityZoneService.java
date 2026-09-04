package com.icap.logicoti.intrusion;

import com.icap.logicoti.event.SensorEventHistoryResponse;
import com.icap.logicoti.exception.BadRequestException;
import com.icap.logicoti.exception.ConflictException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class SecurityZoneService {

    public static final String ZONES_TOPIC = "/topic/security/zones";
    private static final String STATUS_TOPIC = "/topic/security/status";
    private static final Duration AUTOMATIC_RETRY_DELAY =
            Duration.ofMinutes(1);

    private static final String STATE_QUERY = """
            SELECT
                zone.code,
                zone.name,
                zone.display_order,
                zone.motion_detection_enabled,
                state.mode,
                state.mode_before_alarm,
                state.message,
                state.changed_by,
                state.change_source,
                state.changed_at,
                state.arming_completes_at,
                state.automatic_transition_key,
                state.alarm_event_id,
                (
                    SELECT COUNT(*)
                    FROM security_zone_area sensor_zone_area
                    INNER JOIN building_area sensor_area
                        ON sensor_area.id = sensor_zone_area.area_id
                    INNER JOIN building_device sensor
                        ON sensor.area_id = sensor_area.id
                    WHERE sensor_zone_area.zone_code = zone.code
                      AND sensor_area.active = TRUE
                      AND sensor.active = TRUE
                      AND sensor.device_type = 'MOTION'
                ) AS motion_sensor_count,
                (
                    SELECT COUNT(*)
                    FROM security_zone_area light_zone_area
                    INNER JOIN building_area light_area
                        ON light_area.id = light_zone_area.area_id
                    INNER JOIN building_device light
                        ON light.area_id = light_area.id
                    WHERE light_zone_area.zone_code = zone.code
                      AND light_area.active = TRUE
                      AND light.device_type = 'LIGHT'
                ) AS light_circuit_count,
                (
                    SELECT COUNT(*)
                    FROM security_zone_area ready_zone_area
                    INNER JOIN building_area ready_area
                        ON ready_area.id = ready_zone_area.area_id
                    INNER JOIN building_device ready_light
                        ON ready_light.area_id = ready_area.id
                    WHERE ready_zone_area.zone_code = zone.code
                      AND ready_area.active = TRUE
                      AND ready_light.active = TRUE
                      AND ready_light.device_type = 'LIGHT'
                      AND ready_light.controllable = TRUE
                      AND ready_light.plc_command_tag IS NOT NULL
                      AND TRIM(ready_light.plc_command_tag) <> ''
                      AND ready_light.plc_state_tag IS NOT NULL
                      AND TRIM(ready_light.plc_state_tag) <> ''
                ) AS available_light_circuit_count,
                (
                    SELECT COUNT(*)
                    FROM security_zone_light_runtime runtime
                    WHERE runtime.zone_code = zone.code
                ) AS controlled_light_count
            FROM security_zone zone
            INNER JOIN security_zone_state state
                ON state.zone_code = zone.code
            WHERE zone.active = TRUE
            ORDER BY zone.display_order
            """;

    private final JdbcTemplate jdbcTemplate;
    private final SecurityScheduleService scheduleService;
    private final SecurityPrecheckService precheckService;
    private final SecurityZoneLightingService lightingService;
    private final SimpMessagingTemplate messagingTemplate;

    public SecurityZoneService(
            JdbcTemplate jdbcTemplate,
            SecurityScheduleService scheduleService,
            SecurityPrecheckService precheckService,
            SecurityZoneLightingService lightingService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.scheduleService = scheduleService;
        this.precheckService = precheckService;
        this.lightingService = lightingService;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional(readOnly = true)
    public SecurityZoneListResponse getStatus() {
        List<ZoneState> states = findStates();
        return createZoneList(states);
    }

    @Transactional(readOnly = true)
    public SecurityStatusResponse getAggregateStatus() {
        return createAggregateStatus(findStates());
    }

    @Transactional(readOnly = true)
    public SecurityPrecheckResponse precheck(
            Collection<String> requestedZoneCodes
    ) {
        List<String> zoneCodes = normalizeAndValidate(
                requestedZoneCodes
        );
        return precheckService.check(zoneCodes);
    }

    @Transactional
    public SecurityZoneActionResponse armManually(
            Collection<String> requestedZoneCodes,
            String username
    ) {
        ScheduleDecision decision =
                scheduleService.evaluate(Instant.now());

        return arm(
                requestedZoneCodes,
                username,
                "MANUAL",
                decision.transitionKey()
        );
    }

    @Transactional
    public SecurityZoneActionResponse disarmManually(
            Collection<String> requestedZoneCodes,
            String username
    ) {
        ScheduleDecision decision =
                scheduleService.evaluate(Instant.now());

        return disarm(
                requestedZoneCodes,
                username,
                "MANUAL",
                decision.transitionKey()
        );
    }

    @Transactional
    public SecurityZoneListResponse acknowledge(
            String requestedZoneCode,
            String username
    ) {
        String zoneCode = normalizeAndValidate(
                List.of(requestedZoneCode)
        ).getFirst();
        ZoneState current = findState(zoneCode);

        if (current.mode() != AlarmMode.ALARM) {
            throw new ConflictException(
                    "La zona " + current.name()
                            + " no tiene una alarma activa."
            );
        }

        if (current.alarmEventId() != null) {
            try {
                jdbcTemplate.update(
                        """
                        INSERT INTO alarm_acknowledgement (
                            event_id,
                            acknowledged_by
                        )
                        VALUES (?, ?)
                        """,
                        current.alarmEventId(),
                        username
                );
            } catch (DuplicateKeyException ignored) {
                // El evento pudo reconocerse desde la bitácora antes
                // de usar el botón de la zona; el estado debe restaurarse.
            }
        }

        AlarmMode restoredMode = current.modeBeforeAlarm() == null
                ? AlarmMode.ARMED
                : current.modeBeforeAlarm();

        transition(
                current,
                restoredMode,
                "La alarma de " + current.name()
                        + " fue reconocida; la zona permanece armada.",
                username,
                "MANUAL",
                null,
                current.automaticTransitionKey(),
                null,
                null
        );

        if (findStates().stream()
                .noneMatch(state -> state.mode() == AlarmMode.ALARM)) {
            List<String> armedZoneCodes = findStates().stream()
                    .filter(state -> state.mode().isArmed())
                    .map(ZoneState::code)
                    .toList();

            if (!armedZoneCodes.isEmpty()) {
                SecurityZoneLightingService.LightingActionResult result =
                        lightingService.turnOffOwnedZones(armedZoneCodes);

                if (!result.successful()) {
                    updateMessage(
                            zoneCode,
                            "La alarma fue reconocida y la zona permanece armada, "
                                    + "pero una o más luces no confirmaron el apagado."
                    );
                }
            }
        }

        publish();
        return getStatus();
    }

    @Transactional
    public SecurityStatusResponse activateFromMotion(
            SensorEventHistoryResponse event
    ) {
        ZoneState current = findStateByArea(event.areaCode());

        if (current == null || !current.mode().isArmed()) {
            return null;
        }

        if (current.mode() != AlarmMode.ALARM) {
            transition(
                    current,
                    AlarmMode.ALARM,
                    "Movimiento detectado por "
                            + event.deviceName()
                            + " en " + event.areaName() + ".",
                    "SYSTEM",
                    "SYSTEM",
                    null,
                    current.automaticTransitionKey(),
                    event.id(),
                    current.mode()
            );
        }

        List<String> armedZoneCodes = findStates().stream()
                .filter(state -> state.mode().isArmed())
                .map(ZoneState::code)
                .toList();

        SecurityZoneLightingService.LightingActionResult lights =
                lightingService.turnOnZones(
                        armedZoneCodes,
                        "ALARM",
                        event.id()
                );

        if (!lights.successful()) {
            updateMessage(
                    current.code(),
                    "Movimiento detectado en " + event.areaName()
                            + "; una o más luces no confirmaron el encendido."
            );
        }

        publish();
        return getAggregateStatus();
    }

    @Transactional
    public void evaluateAutomaticSchedule() {
        Instant now = Instant.now();
        ScheduleDecision decision = scheduleService.evaluate(now);

        if (!decision.automaticEnabled()) {
            return;
        }

        List<ZoneState> states = findStates();
        List<String> targetCodes;

        if (decision.shouldBeArmed()) {
            targetCodes = states.stream()
                    .filter(state -> shouldArmAutomatically(
                            state,
                            decision,
                            now
                    ))
                    .map(ZoneState::code)
                    .toList();

            if (!targetCodes.isEmpty()) {
                arm(
                        targetCodes,
                        "SYSTEM",
                        "SCHEDULE",
                        decision.transitionKey()
                );
            }
            return;
        }

        targetCodes = states.stream()
                .filter(state -> state.mode() != AlarmMode.DISARMED)
                .filter(state -> !Objects.equals(
                        state.automaticTransitionKey(),
                        decision.transitionKey()
                ))
                .map(ZoneState::code)
                .toList();

        if (!targetCodes.isEmpty()) {
            disarm(
                    targetCodes,
                    "SYSTEM",
                    "SCHEDULE",
                    decision.transitionKey()
            );
        }
    }

    @Transactional
    public void completeArmingIfDue() {
        Instant now = Instant.now();
        List<ZoneState> dueZones = findStates().stream()
                .filter(state -> state.mode() == AlarmMode.ARMING)
                .filter(state -> state.armingCompletesAt() != null)
                .filter(state -> !state.armingCompletesAt().isAfter(now))
                .toList();

        for (ZoneState zone : dueZones) {
            SecurityPrecheckResponse precheck =
                    precheckService.check(List.of(zone.code()));

            if (!precheck.ready()) {
                transition(
                        zone,
                        AlarmMode.REJECTED,
                        "El armado de " + zone.name()
                                + " fue rechazado por la revisión final.",
                        zone.changedBy(),
                        zone.changeSource(),
                        null,
                        zone.automaticTransitionKey(),
                        null,
                        null
                );
                continue;
            }

            SecurityZoneLightingService.LightingActionResult lights =
                    lightingService.turnOnZones(
                            List.of(zone.code()),
                            "ARMING",
                            null
                    );

            if (!lights.successful()) {
                lightingService.turnOffOwnedZones(
                        List.of(zone.code())
                );
                transition(
                        zone,
                        AlarmMode.REJECTED,
                        "El armado de " + zone.name()
                                + " fue rechazado porque la iluminación no confirmó el encendido; "
                                + "se intentó revertir las luces activadas.",
                        zone.changedBy(),
                        zone.changeSource(),
                        null,
                        zone.automaticTransitionKey(),
                        null,
                        null
                );
                continue;
            }

            AlarmMode armedMode = determineArmedMode(zone.code());
            transition(
                    zone,
                    armedMode,
                    armedMessage(zone.name(), armedMode),
                    zone.changedBy(),
                    zone.changeSource(),
                    null,
                    zone.automaticTransitionKey(),
                    null,
                    null
            );
        }

        if (!dueZones.isEmpty()) {
            publish();
        }
    }

    public List<String> allZoneCodes() {
        return jdbcTemplate.queryForList(
                """
                SELECT code
                FROM security_zone
                WHERE active = TRUE
                ORDER BY display_order
                """,
                String.class
        );
    }

    private SecurityZoneActionResponse arm(
            Collection<String> requestedZoneCodes,
            String username,
            String source,
            String transitionKey
    ) {
        List<String> zoneCodes = normalizeAndValidate(
                requestedZoneCodes
        );
        SecurityPrecheckResponse precheck =
                precheckService.check(zoneCodes);

        if (!precheck.ready()) {
            for (String zoneCode : zoneCodes) {
                ZoneState state = findState(zoneCode);
                if (!state.mode().isArmed()
                        && state.mode() != AlarmMode.ARMING) {
                    transition(
                            state,
                            AlarmMode.REJECTED,
                            "No se pudo armar " + state.name()
                                    + ". Revise los puntos del precheck.",
                            username,
                            source,
                            null,
                            transitionKey,
                            null,
                            null
                    );
                }
            }

            publish();
            return new SecurityZoneActionResponse(
                    getStatus(),
                    precheck
            );
        }

        int exitDelay = scheduleService.getSettings()
                .exitDelaySeconds();
        Instant completesAt = exitDelay == 0
                ? null
                : Instant.now().plusSeconds(exitDelay);

        for (String zoneCode : zoneCodes) {
            ZoneState state = findState(zoneCode);

            if (state.mode().isArmed()
                    || state.mode() == AlarmMode.ARMING) {
                continue;
            }

            if (exitDelay == 0) {
                SecurityZoneLightingService.LightingActionResult lights =
                        lightingService.turnOnZones(
                                List.of(zoneCode),
                                "ARMING",
                                null
                        );

                if (!lights.successful()) {
                    lightingService.turnOffOwnedZones(
                            List.of(zoneCode)
                    );
                    transition(
                            state,
                            AlarmMode.REJECTED,
                            "No se pudo armar " + state.name()
                                    + " porque la iluminación no confirmó el encendido; "
                                    + "se intentó revertir las luces activadas.",
                            username,
                            source,
                            null,
                            transitionKey,
                            null,
                            null
                    );
                    continue;
                }

                AlarmMode armedMode = determineArmedMode(zoneCode);
                transition(
                        state,
                        armedMode,
                        armedMessage(state.name(), armedMode),
                        username,
                        source,
                        null,
                        transitionKey,
                        null,
                        null
                );
                continue;
            }

            transition(
                    state,
                    AlarmMode.ARMING,
                    state.name() + " se está armando. Tiempo de salida: "
                            + exitDelay + " segundos.",
                    username,
                    source,
                    completesAt,
                    transitionKey,
                    null,
                    null
            );
        }

        publish();
        return new SecurityZoneActionResponse(
                getStatus(),
                precheck
        );
    }

    private SecurityZoneActionResponse disarm(
            Collection<String> requestedZoneCodes,
            String username,
            String source,
            String transitionKey
    ) {
        List<String> zoneCodes = normalizeAndValidate(
                requestedZoneCodes
        );
        SecurityZoneLightingService.LightingActionResult lights =
                lightingService.turnOffOwnedZones(zoneCodes);

        for (String zoneCode : zoneCodes) {
            ZoneState state = findState(zoneCode);
            String message = lights.successful()
                    ? state.name() + " fue desarmada correctamente."
                    : state.name() + " fue desarmada, pero una o más luces "
                            + "no confirmaron el apagado.";

            transition(
                    state,
                    AlarmMode.DISARMED,
                    message,
                    username,
                    source,
                    null,
                    transitionKey,
                    null,
                    null
            );
        }

        publish();
        return new SecurityZoneActionResponse(
                getStatus(),
                null
        );
    }

    private boolean shouldArmAutomatically(
            ZoneState state,
            ScheduleDecision decision,
            Instant now
    ) {
        if (state.mode().isArmed()
                || state.mode() == AlarmMode.ARMING) {
            return false;
        }

        if (!Objects.equals(
                state.automaticTransitionKey(),
                decision.transitionKey()
        )) {
            return true;
        }

        return state.mode() == AlarmMode.REJECTED
                && state.changedAt()
                .plus(AUTOMATIC_RETRY_DELAY)
                .isBefore(now);
    }

    private void transition(
            ZoneState previous,
            AlarmMode newMode,
            String message,
            String username,
            String source,
            Instant armingCompletesAt,
            String transitionKey,
            Long alarmEventId,
            AlarmMode modeBeforeAlarm
    ) {
        Instant changedAt = Instant.now();

        jdbcTemplate.update(
                """
                UPDATE security_zone_state
                SET mode = ?,
                    mode_before_alarm = ?,
                    message = ?,
                    changed_by = ?,
                    change_source = ?,
                    changed_at = ?,
                    arming_completes_at = ?,
                    automatic_transition_key = ?,
                    alarm_event_id = ?
                WHERE zone_code = ?
                """,
                newMode.name(),
                modeBeforeAlarm == null
                        ? null
                        : modeBeforeAlarm.name(),
                message,
                username,
                source,
                Timestamp.from(changedAt),
                armingCompletesAt == null
                        ? null
                        : Timestamp.from(armingCompletesAt),
                transitionKey,
                alarmEventId,
                previous.code()
        );

        if (previous.mode() != newMode) {
            jdbcTemplate.update(
                    """
                    INSERT INTO security_zone_history (
                        zone_code,
                        previous_mode,
                        current_mode,
                        message,
                        changed_by,
                        change_source,
                        alarm_event_id,
                        changed_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    previous.code(),
                    previous.mode().name(),
                    newMode.name(),
                    message,
                    username,
                    source,
                    alarmEventId,
                    Timestamp.from(changedAt)
            );
        }
    }

    private void updateMessage(String zoneCode, String message) {
        jdbcTemplate.update(
                """
                UPDATE security_zone_state
                SET message = ?, changed_at = CURRENT_TIMESTAMP
                WHERE zone_code = ?
                """,
                message,
                zoneCode
        );
    }

    private List<ZoneState> findStates() {
        return jdbcTemplate.query(
                STATE_QUERY,
                (resultSet, rowNumber) -> new ZoneState(
                        resultSet.getString("code"),
                        resultSet.getString("name"),
                        resultSet.getInt("display_order"),
                        resultSet.getBoolean(
                                "motion_detection_enabled"
                        ),
                        AlarmMode.valueOf(resultSet.getString("mode")),
                        toMode(resultSet.getString("mode_before_alarm")),
                        resultSet.getString("message"),
                        resultSet.getString("changed_by"),
                        resultSet.getString("change_source"),
                        toInstant(resultSet.getTimestamp("changed_at")),
                        toInstant(resultSet.getTimestamp(
                                "arming_completes_at"
                        )),
                        resultSet.getString(
                                "automatic_transition_key"
                        ),
                        resultSet.getObject("alarm_event_id") == null
                                ? null
                                : resultSet.getLong("alarm_event_id"),
                        resultSet.getInt("motion_sensor_count"),
                        resultSet.getInt("light_circuit_count"),
                        resultSet.getInt(
                                "available_light_circuit_count"
                        ),
                        resultSet.getInt("controlled_light_count")
                )
        );
    }

    private ZoneState findState(String zoneCode) {
        return findStates().stream()
                .filter(state -> state.code().equals(zoneCode))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "No existe la zona " + zoneCode + "."
                ));
    }

    private ZoneState findStateByArea(String areaCode) {
        List<String> zoneCodes = jdbcTemplate.queryForList(
                """
                SELECT zone_area.zone_code
                FROM security_zone_area zone_area
                INNER JOIN building_area area
                    ON area.id = zone_area.area_id
                INNER JOIN security_zone zone
                    ON zone.code = zone_area.zone_code
                WHERE UPPER(area.code) = UPPER(?)
                  AND area.active = TRUE
                  AND zone.active = TRUE
                """,
                String.class,
                areaCode
        );

        return zoneCodes.isEmpty()
                ? null
                : findState(zoneCodes.getFirst());
    }

    private SecurityZoneListResponse createZoneList(
            List<ZoneState> states
    ) {
        long alarmZones = states.stream()
                .filter(state -> state.mode() == AlarmMode.ALARM)
                .count();
        long armedZones = states.stream()
                .filter(state -> state.mode().isArmed())
                .count();

        String aggregateMode = aggregateMode(states);
        String message = aggregateMessage(
                aggregateMode,
                states.size(),
                (int) armedZones,
                (int) alarmZones
        );

        List<SecurityZoneStatusResponse> zones = states.stream()
                .map(this::toResponse)
                .toList();

        return new SecurityZoneListResponse(
                aggregateMode,
                message,
                states.size(),
                (int) armedZones,
                (int) alarmZones,
                zones,
                Instant.now()
        );
    }

    private SecurityStatusResponse createAggregateStatus(
            List<ZoneState> states
    ) {
        SecurityZoneListResponse zoneList = createZoneList(states);
        ZoneState lastChanged = states.stream()
                .max(Comparator.comparing(ZoneState::changedAt))
                .orElseThrow(() -> new IllegalStateException(
                        "No existen zonas de seguridad activas."
                ));
        SecuritySettingsResponse settings =
                scheduleService.getSettings();

        String legacyMode = "PARTIALLY_ARMED".equals(
                zoneList.aggregateMode()
        ) ? AlarmMode.ARMED.name() : zoneList.aggregateMode();

        Instant nextArming = states.stream()
                .map(ZoneState::armingCompletesAt)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);

        return new SecurityStatusResponse(
                legacyMode,
                zoneList.armedZones() > 0,
                zoneList.alarmZones() > 0,
                zoneList.message(),
                lastChanged.changedBy(),
                lastChanged.changeSource(),
                lastChanged.changedAt(),
                nextArming,
                settings.automaticScheduleEnabled(),
                settings.timezone(),
                settings.exitDelaySeconds(),
                settings.lightInactivityMinutes(),
                settings.minisplitInactivityMinutes(),
                Instant.now()
        );
    }

    private SecurityZoneStatusResponse toResponse(ZoneState state) {
        return new SecurityZoneStatusResponse(
                state.code(),
                state.name(),
                state.displayOrder(),
                state.motionDetectionEnabled(),
                state.motionSensorCount(),
                state.lightCircuitCount(),
                state.availableLightCircuitCount(),
                state.controlledLightCount(),
                state.mode().name(),
                state.mode().isArmed(),
                state.mode().isAlarmActive(),
                state.message(),
                state.changedBy(),
                state.changeSource(),
                state.changedAt(),
                state.armingCompletesAt(),
                state.alarmEventId()
        );
    }

    private String aggregateMode(List<ZoneState> states) {
        if (states.stream().anyMatch(
                state -> state.mode() == AlarmMode.ALARM
        )) {
            return AlarmMode.ALARM.name();
        }

        if (states.stream().anyMatch(
                state -> state.mode() == AlarmMode.ARMING
        )) {
            return AlarmMode.ARMING.name();
        }

        long armed = states.stream()
                .filter(state -> state.mode().isArmed())
                .count();

        if (armed == states.size() && armed > 0) {
            boolean withBypass = states.stream().anyMatch(
                    state -> state.mode() == AlarmMode.ARMED_WITH_BYPASS
            );
            return withBypass
                    ? AlarmMode.ARMED_WITH_BYPASS.name()
                    : AlarmMode.ARMED.name();
        }

        if (armed > 0) {
            return "PARTIALLY_ARMED";
        }

        if (states.stream().anyMatch(
                state -> state.mode() == AlarmMode.REJECTED
        )) {
            return AlarmMode.REJECTED.name();
        }

        return AlarmMode.DISARMED.name();
    }

    private String aggregateMessage(
            String mode,
            int totalZones,
            int armedZones,
            int alarmZones
    ) {
        return switch (mode) {
            case "ALARM" -> alarmZones
                    + (alarmZones == 1
                    ? " zona tiene una alarma activa."
                    : " zonas tienen alarmas activas.");
            case "ARMING" -> "Una o más zonas están completando el tiempo de salida.";
            case "ARMED", "ARMED_WITH_BYPASS" ->
                    "Las " + totalZones + " zonas están armadas.";
            case "PARTIALLY_ARMED" -> armedZones + " de "
                    + totalZones + " zonas están armadas.";
            case "REJECTED" -> "Una o más zonas no pudieron armarse.";
            default -> "Todas las zonas se encuentran desarmadas.";
        };
    }

    private List<String> normalizeAndValidate(
            Collection<String> requestedZoneCodes
    ) {
        if (requestedZoneCodes == null
                || requestedZoneCodes.isEmpty()) {
            throw new BadRequestException(
                    "Selecciona al menos una zona."
            );
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String requestedCode : requestedZoneCodes) {
            if (requestedCode == null || requestedCode.isBlank()) {
                throw new BadRequestException(
                        "El código de zona es obligatorio."
                );
            }

            normalized.add(requestedCode.trim().toUpperCase(Locale.ROOT));
        }

        List<String> available = allZoneCodes();
        if (!available.containsAll(normalized)) {
            throw new BadRequestException(
                    "Se seleccionó una zona inexistente o inactiva."
            );
        }

        return available.stream()
                .filter(normalized::contains)
                .toList();
    }

    private void publish() {
        SecurityZoneListResponse zones = getStatus();
        messagingTemplate.convertAndSend(ZONES_TOPIC, zones);
        messagingTemplate.convertAndSend(
                STATUS_TOPIC,
                getAggregateStatus()
        );
    }

    private AlarmMode determineArmedMode(String zoneCode) {
        Integer bypassedSensors = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM sensor_bypass_history bypass
                INNER JOIN building_device device
                    ON device.id = bypass.device_id
                INNER JOIN security_zone_area zone_area
                    ON zone_area.area_id = device.area_id
                WHERE zone_area.zone_code = ?
                  AND bypass.active = TRUE
                  AND device.active = TRUE
                  AND device.device_type = 'MOTION'
                """,
                Integer.class,
                zoneCode
        );

        return bypassedSensors != null && bypassedSensors > 0
                ? AlarmMode.ARMED_WITH_BYPASS
                : AlarmMode.ARMED;
    }

    private String armedMessage(String zoneName, AlarmMode mode) {
        if (mode == AlarmMode.ARMED_WITH_BYPASS) {
            return zoneName + " quedó armada con sensores omitidos.";
        }

        return zoneName + " quedó armada correctamente.";
    }

    private AlarmMode toMode(String mode) {
        return mode == null ? null : AlarmMode.valueOf(mode);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record ZoneState(
            String code,
            String name,
            int displayOrder,
            boolean motionDetectionEnabled,
            AlarmMode mode,
            AlarmMode modeBeforeAlarm,
            String message,
            String changedBy,
            String changeSource,
            Instant changedAt,
            Instant armingCompletesAt,
            String automaticTransitionKey,
            Long alarmEventId,
            int motionSensorCount,
            int lightCircuitCount,
            int availableLightCircuitCount,
            int controlledLightCount
    ) {
    }
}
