package com.icap.logicoti.report;

import com.icap.logicoti.exception.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

@Service
public class HistoryRetentionService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(HistoryRetentionService.class);
    private static final ZoneId DEFAULT_ZONE =
            ZoneId.of("America/Mazatlan");

    private static final String SETTINGS_QUERY = """
            SELECT
                enabled,
                retention_months,
                last_run_at,
                last_run_by,
                last_cutoff_at,
                last_deleted_events,
                last_deleted_commands,
                last_deleted_security,
                last_deleted_diagnostics,
                last_deleted_bypasses,
                last_deleted_notifications,
                updated_at,
                updated_by
            FROM history_retention_policy
            WHERE id = 1
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    public HistoryRetentionService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    HistoryRetentionService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public HistoryRetentionResponse getPolicy() {
        RetentionSettings settings = loadSettings(false);
        Instant cutoff = cutoff(settings.retentionMonths());

        return response(settings, cutoff, countCandidates(cutoff));
    }

    @Transactional
    public HistoryRetentionResponse updatePolicy(
            HistoryRetentionUpdateRequest request,
            String username
    ) {
        jdbcTemplate.update(
                """
                UPDATE history_retention_policy
                SET enabled = ?,
                    retention_months = ?,
                    updated_at = CURRENT_TIMESTAMP,
                    updated_by = ?
                WHERE id = 1
                """,
                request.enabled(),
                request.retentionMonths(),
                username
        );

        RetentionSettings settings = loadSettings(false);
        Instant cutoff = cutoff(settings.retentionMonths());

        return response(settings, cutoff, countCandidates(cutoff));
    }

    @Transactional
    public HistoryRetentionRunResponse runManually(String username) {
        RetentionSettings settings = loadSettings(true);

        if (!settings.enabled()) {
            throw new ConflictException(
                    "Habilita primero la política de retención antes de ejecutar la limpieza."
            );
        }

        return purge(settings, username);
    }

    @Scheduled(
            cron = "${history.retention.cron:0 30 3 * * *}",
            zone = "${history.retention.scheduler-zone:America/Mazatlan}"
    )
    @Transactional
    public void runScheduled() {
        RetentionSettings settings = loadSettings(true);

        if (!settings.enabled()) {
            return;
        }

        HistoryRetentionRunResponse result = purge(settings, "SYSTEM");

        LOGGER.info(
                "Retención histórica automática terminada: {} registros eliminados, corte {}.",
                result.deleted().total(),
                result.cutoffAt()
        );
    }

    private HistoryRetentionRunResponse purge(
            RetentionSettings settings,
            String username
    ) {
        Instant cutoff = cutoff(settings.retentionMonths());
        Timestamp cutoffTimestamp = Timestamp.from(cutoff);

        jdbcTemplate.update(
                """
                DELETE FROM alarm_comment
                WHERE event_id IN (
                    SELECT id
                    FROM device_event_history
                    WHERE detected_at < ?
                )
                """,
                cutoffTimestamp
        );
        jdbcTemplate.update(
                """
                DELETE FROM alarm_acknowledgement
                WHERE event_id IN (
                    SELECT id
                    FROM device_event_history
                    WHERE detected_at < ?
                )
                """,
                cutoffTimestamp
        );

        long events = jdbcTemplate.update(
                "DELETE FROM device_event_history WHERE detected_at < ?",
                cutoffTimestamp
        );
        long commands = jdbcTemplate.update(
                "DELETE FROM device_command_history WHERE requested_at < ?",
                cutoffTimestamp
        );
        long legacySecurity = jdbcTemplate.update(
                "DELETE FROM intrusion_alarm_history WHERE changed_at < ?",
                cutoffTimestamp
        );
        long zoneSecurity = jdbcTemplate.update(
                "DELETE FROM security_zone_history WHERE changed_at < ?",
                cutoffTimestamp
        );
        long security = legacySecurity + zoneSecurity;
        long diagnostics = jdbcTemplate.update(
                """
                DELETE FROM sensor_diagnostic_session
                WHERE started_at < ?
                  AND status <> 'RUNNING'
                """,
                cutoffTimestamp
        );
        long bypasses = jdbcTemplate.update(
                """
                DELETE FROM sensor_bypass_history
                WHERE created_at < ?
                  AND active = FALSE
                """,
                cutoffTimestamp
        );
        long notifications = jdbcTemplate.update(
                """
                DELETE FROM web_push_delivery
                WHERE created_at < ?
                  AND status IN ('ACCEPTED', 'FAILED', 'EXPIRED_SUBSCRIPTION')
                """,
                cutoffTimestamp
        );

        HistoryRetentionCounts deleted = HistoryRetentionCounts.of(
                events,
                commands,
                security,
                diagnostics,
                bypasses,
                notifications
        );
        Instant executedAt = clock.instant();

        jdbcTemplate.update(
                """
                UPDATE history_retention_policy
                SET last_run_at = ?,
                    last_run_by = ?,
                    last_cutoff_at = ?,
                    last_deleted_events = ?,
                    last_deleted_commands = ?,
                    last_deleted_security = ?,
                    last_deleted_diagnostics = ?,
                    last_deleted_bypasses = ?,
                    last_deleted_notifications = ?,
                    updated_at = CURRENT_TIMESTAMP,
                    updated_by = ?
                WHERE id = 1
                """,
                Timestamp.from(executedAt),
                username,
                cutoffTimestamp,
                deleted.events(),
                deleted.commands(),
                deleted.securityTransitions(),
                deleted.diagnostics(),
                deleted.revokedBypasses(),
                deleted.notifications(),
                username
        );

        return new HistoryRetentionRunResponse(
                cutoff,
                deleted,
                executedAt,
                username
        );
    }

    private HistoryRetentionCounts countCandidates(Instant cutoff) {
        Timestamp value = Timestamp.from(cutoff);

        return HistoryRetentionCounts.of(
                count("device_event_history", "detected_at < ?", value),
                count("device_command_history", "requested_at < ?", value),
                count("intrusion_alarm_history", "changed_at < ?", value)
                        + count(
                                "security_zone_history",
                                "changed_at < ?",
                                value
                        ),
                count(
                        "sensor_diagnostic_session",
                        "started_at < ? AND status <> 'RUNNING'",
                        value
                ),
                count(
                        "sensor_bypass_history",
                        "created_at < ? AND active = FALSE",
                        value
                ),
                count(
                        "web_push_delivery",
                        "created_at < ? "
                                + "AND status IN ('ACCEPTED', 'FAILED', 'EXPIRED_SUBSCRIPTION')",
                        value
                )
        );
    }

    private long count(
            String table,
            String condition,
            Timestamp cutoff
    ) {
        Long result = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + condition,
                Long.class,
                cutoff
        );

        return result == null ? 0 : result;
    }

    private HistoryRetentionResponse response(
            RetentionSettings settings,
            Instant cutoff,
            HistoryRetentionCounts candidates
    ) {
        return new HistoryRetentionResponse(
                settings.enabled(),
                settings.retentionMonths(),
                cutoff,
                candidates,
                settings.lastRunAt(),
                settings.lastRunBy(),
                settings.lastCutoffAt(),
                settings.lastDeleted(),
                settings.updatedAt(),
                settings.updatedBy(),
                clock.instant()
        );
    }

    private RetentionSettings loadSettings(boolean lock) {
        String query = lock
                ? SETTINGS_QUERY + " FOR UPDATE"
                : SETTINGS_QUERY;

        return jdbcTemplate.queryForObject(
                query,
                this::mapSettings
        );
    }

    private RetentionSettings mapSettings(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new RetentionSettings(
                resultSet.getBoolean("enabled"),
                resultSet.getInt("retention_months"),
                instant(resultSet, "last_run_at"),
                resultSet.getString("last_run_by"),
                instant(resultSet, "last_cutoff_at"),
                HistoryRetentionCounts.of(
                        resultSet.getLong("last_deleted_events"),
                        resultSet.getLong("last_deleted_commands"),
                        resultSet.getLong("last_deleted_security"),
                        resultSet.getLong("last_deleted_diagnostics"),
                        resultSet.getLong("last_deleted_bypasses"),
                        resultSet.getLong("last_deleted_notifications")
                ),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getString("updated_by")
        );
    }

    private Instant cutoff(int retentionMonths) {
        ZoneId zone = resolveTimezone();

        return clock.instant()
                .atZone(zone)
                .minusMonths(retentionMonths)
                .toInstant();
    }

    private ZoneId resolveTimezone() {
        try {
            String value = jdbcTemplate.queryForObject(
                    "SELECT timezone FROM security_settings WHERE id = 1",
                    String.class
            );

            return value == null || value.isBlank()
                    ? DEFAULT_ZONE
                    : ZoneId.of(value);
        } catch (RuntimeException exception) {
            return DEFAULT_ZONE;
        }
    }

    private Instant instant(ResultSet resultSet, String column)
            throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private record RetentionSettings(
            boolean enabled,
            int retentionMonths,
            Instant lastRunAt,
            String lastRunBy,
            Instant lastCutoffAt,
            HistoryRetentionCounts lastDeleted,
            Instant updatedAt,
            String updatedBy
    ) {
    }
}
