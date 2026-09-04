package com.icap.logicoti.intrusion;

import com.icap.logicoti.event.SensorEventHistoryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Service
public class IntrusionAlarmService {

    private static final String SYSTEM = "SYSTEM";
    private static final String SCHEDULE = "SCHEDULE";


    private static final String STATE_QUERY = """
            SELECT
                mode,
                message,
                changed_by,
                change_source,
                changed_at,
                arming_completes_at,
                automatic_transition_key
            FROM intrusion_alarm_state
            WHERE id = 1
            """;

    private static final String UPDATE_STATE = """
            UPDATE intrusion_alarm_state
            SET mode = ?,
                message = ?,
                changed_by = ?,
                change_source = ?,
                changed_at = ?,
                arming_completes_at = ?,
                automatic_transition_key = ?
            WHERE id = 1
            """;

    private static final String UPDATE_TRANSITION_KEY = """
            UPDATE intrusion_alarm_state
            SET automatic_transition_key = ?
            WHERE id = 1
            """;

    private static final String INSERT_HISTORY = """
            INSERT INTO intrusion_alarm_history (
                previous_mode,
                current_mode,
                message,
                changed_by,
                change_source,
                changed_at
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final Duration AUTOMATIC_RETRY_DELAY =
            Duration.ofMinutes(1);

    private final JdbcTemplate jdbcTemplate;
    private final SecurityScheduleService scheduleService;
    private final SecurityPrecheckService precheckService;
    private final SimpMessagingTemplate messagingTemplate;

    public IntrusionAlarmService(
            JdbcTemplate jdbcTemplate,
            SecurityScheduleService scheduleService,
            SecurityPrecheckService precheckService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.scheduleService = scheduleService;
        this.precheckService = precheckService;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional(readOnly = true)
    public SecurityStatusResponse getStatus() {
        return createStatus(findState());
    }

    public SecurityPrecheckResponse precheck() {
        return precheckService.check();
    }

    @Transactional
    public SecurityStatusResponse activateFromMotion(
            SensorEventHistoryResponse event
    ) {
        StateRow current = findState();

        if (!current.mode().isArmed()) {
            return null;
        }

        if (current.mode() == AlarmMode.ALARM) {
            return createStatus(current);
        }

        return transition(
                AlarmMode.ALARM,
                "Movimiento detectado por "
                        + event.deviceName()
                        + " en "
                        + event.areaName()
                        + ".",
                SYSTEM,
                SYSTEM,
                null,
                current.automaticTransitionKey()
        );
    }

    @Transactional
    public SecurityActionResponse armManually(String username) {
        ScheduleDecision decision =
                scheduleService.evaluate(Instant.now());

        return arm(
                username,
                "MANUAL",
                decision.transitionKey()
        );
    }

    @Transactional
    public SecurityActionResponse disarmManually(String username) {
        ScheduleDecision decision =
                scheduleService.evaluate(Instant.now());

        SecurityStatusResponse status = transition(
                AlarmMode.DISARMED,
                "La alarma fue desarmada manualmente.",
                username,
                "MANUAL",
                null,
                decision.transitionKey()
        );

        return new SecurityActionResponse(status, null);
    }

    @Transactional
    public void evaluateAutomaticSchedule() {
        Instant now = Instant.now();
        ScheduleDecision decision =
                scheduleService.evaluate(now);

        if (!decision.automaticEnabled()) {
            return;
        }

        StateRow current = findState();
        boolean sameTransition = Objects.equals(
                current.automaticTransitionKey(),
                decision.transitionKey()
        );

        if (sameTransition) {
            if (decision.shouldBeArmed()
                    && current.mode() == AlarmMode.REJECTED
                    && current.changedAt()
                            .plus(AUTOMATIC_RETRY_DELAY)
                            .isBefore(now)) {

                arm(
                        SYSTEM,
                        SCHEDULE,
                        decision.transitionKey()
                );
            }

            return;
        }

        if (decision.shouldBeArmed()) {
            if (current.mode().isArmed()
                    || current.mode() == AlarmMode.ARMING) {

                markTransitionApplied(
                        decision.transitionKey()
                );
                return;
            }

            arm(
                    SYSTEM,
                    SCHEDULE,
                    decision.transitionKey()
            );
            return;
        }

        if (current.mode() == AlarmMode.DISARMED) {
            markTransitionApplied(decision.transitionKey());
            return;
        }

        transition(
                AlarmMode.DISARMED,
                "La alarma fue desarmada por el horario configurado.",
                SYSTEM,
                SCHEDULE,
                null,
                decision.transitionKey()
        );
    }

    @Transactional
    public void completeArmingIfDue() {
        StateRow current = findState();

        if (current.mode() != AlarmMode.ARMING
                || current.armingCompletesAt() == null
                || current.armingCompletesAt().isAfter(Instant.now())) {
            return;
        }

        SecurityPrecheckResponse precheck =
                precheckService.check();

        if (!precheck.ready()) {
            transition(
                    AlarmMode.REJECTED,
                    "El armado fue rechazado porque la revisión final "
                            + "de sensores no fue satisfactoria.",
                    current.changedBy(),
                    current.changeSource(),
                    null,
                    current.automaticTransitionKey()
            );
            return;
        }

        AlarmMode armedMode = determineArmedMode(precheck);

        transition(
                armedMode,
                armedMessage(armedMode),
                current.changedBy(),
                current.changeSource(),
                null,
                current.automaticTransitionKey()
        );
    }

    private SecurityActionResponse arm(
            String username,
            String source,
            String transitionKey
    ) {
        StateRow current = findState();

        if (current.mode().isArmed()
                || current.mode() == AlarmMode.ARMING) {

            return new SecurityActionResponse(
                    createStatus(current),
                    null
            );
        }

        SecurityPrecheckResponse precheck =
                precheckService.check();

        if (!precheck.ready()) {
            SecurityStatusResponse rejected = transition(
                    AlarmMode.REJECTED,
                    "No se pudo armar la alarma. "
                            + "Revise los puntos indicados en la revisión previa.",
                    username,
                    source,
                    null,
                    transitionKey
            );

            return new SecurityActionResponse(
                    rejected,
                    precheck
            );
        }

        SecuritySettingsResponse settings =
                scheduleService.getSettings();

        if (settings.exitDelaySeconds() == 0) {
            AlarmMode armedMode = determineArmedMode(precheck);
            SecurityStatusResponse armed = transition(
                    armedMode,
                    armedMessage(armedMode),
                    username,
                    source,
                    null,
                    transitionKey
            );

            return new SecurityActionResponse(armed, precheck);
        }

        Instant completesAt = Instant.now()
                .plusSeconds(settings.exitDelaySeconds());

        SecurityStatusResponse arming = transition(
                AlarmMode.ARMING,
                "La alarma se está armando. "
                        + "El tiempo de salida es de "
                        + settings.exitDelaySeconds()
                        + " segundos.",
                username,
                source,
                completesAt,
                transitionKey
        );

        return new SecurityActionResponse(arming, precheck);
    }

    private AlarmMode determineArmedMode(
            SecurityPrecheckResponse precheck
    ) {
        boolean hasBypassedSensors = precheck.issues()
                .stream()
                .anyMatch(issue ->
                        "SENSOR_BYPASSED".equals(issue.code())
                );

        return hasBypassedSensors
                ? AlarmMode.ARMED_WITH_BYPASS
                : AlarmMode.ARMED;
    }

    private String armedMessage(AlarmMode mode) {
        if (mode == AlarmMode.ARMED_WITH_BYPASS) {
            return "La alarma quedó armada con uno o más sensores "
                    + "de movimiento omitidos temporalmente.";
        }

        return "La alarma quedó armada correctamente.";
    }

        private SecurityStatusResponse transition(
                AlarmMode newMode,
                String message,
                String username,
                String source,
                Instant armingCompletesAt,
                String transitionKey
        ) {
        StateRow previous = findState();
        Instant changedAt = Instant.now();

        jdbcTemplate.update(
                UPDATE_STATE,
                newMode.name(),
                message,
                username,
                source,
                Timestamp.from(changedAt),
                armingCompletesAt == null
                        ? null
                        : Timestamp.from(armingCompletesAt),
                transitionKey
        );

        if (previous.mode() != newMode) {
                jdbcTemplate.update(
                        INSERT_HISTORY,
                        previous.mode().name(),
                        newMode.name(),
                        message,
                        username,
                        source,
                        Timestamp.from(changedAt)
                );
        }

        SecurityStatusResponse response =
                createStatus(findState());

        messagingTemplate.convertAndSend(
                "/topic/security/status",
                response
        );

        return response;
        }

    private void markTransitionApplied(String transitionKey) {
        jdbcTemplate.update(
                UPDATE_TRANSITION_KEY,
                transitionKey
        );
    }

        private StateRow findState() {
                return jdbcTemplate.queryForObject(
                        STATE_QUERY,
                        (resultSet, rowNumber) -> new StateRow(
                                AlarmMode.valueOf(
                                        resultSet.getString("mode")
                                ),
                                resultSet.getString("message"),
                                resultSet.getString("changed_by"),
                                resultSet.getString("change_source"),
                                toInstant(
                                        resultSet.getTimestamp("changed_at")
                                ),
                                toInstant(
                                        resultSet.getTimestamp(
                                                "arming_completes_at"
                                        )
                                ),
                                resultSet.getString(
                                        "automatic_transition_key"
                                )
                        )
                );
        }

    private SecurityStatusResponse createStatus(StateRow state) {
        SecuritySettingsResponse settings =
                scheduleService.getSettings();

        return new SecurityStatusResponse(
                state.mode().name(),
                state.mode().isArmed(),
                state.mode().isAlarmActive(),
                state.message(),
                state.changedBy(),
                state.changeSource(),
                state.changedAt(),
                state.armingCompletesAt(),
                settings.automaticScheduleEnabled(),
                settings.timezone(),
                settings.exitDelaySeconds(),
                settings.lightInactivityMinutes(),
                settings.minisplitInactivityMinutes(),
                Instant.now()
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record StateRow(
            AlarmMode mode,
            String message,
            String changedBy,
            String changeSource,
            Instant changedAt,
            Instant armingCompletesAt,
            String automaticTransitionKey
    ) {
    }
}
