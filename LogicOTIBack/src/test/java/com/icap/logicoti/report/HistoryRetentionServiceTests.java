package com.icap.logicoti.report;

import com.icap.logicoti.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoryRetentionServiceTests {

    private JdbcTemplate jdbcTemplate;
    private HistoryRetentionService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource();

        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl(
                "jdbc:h2:mem:history_retention_"
                        + UUID.randomUUID()
                        + ";MODE=PostgreSQL"
                        + ";DB_CLOSE_DELAY=-1"
                        + ";DATABASE_TO_LOWER=TRUE"
        );
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        jdbcTemplate = new JdbcTemplate(dataSource);
        service = new HistoryRetentionService(
                jdbcTemplate,
                Clock.fixed(
                        Instant.parse("2026-08-14T18:00:00Z"),
                        ZoneOffset.UTC
                )
        );

        createSchema();
        seedData();
    }

    @Test
    void previewsAndPurgesOnlyExpiredTerminalHistory() {
        HistoryRetentionResponse initial = service.getPolicy();

        assertThat(initial.enabled()).isFalse();
        assertThat(initial.retentionMonths()).isEqualTo(24);
        assertThat(initial.candidates().total()).isZero();
        assertThatThrownBy(() -> service.runManually("admin"))
                .isInstanceOf(ConflictException.class);

        HistoryRetentionResponse enabled = service.updatePolicy(
                new HistoryRetentionUpdateRequest(true, 6),
                "admin"
        );

        assertThat(enabled.enabled()).isTrue();
        assertThat(enabled.candidates().events()).isEqualTo(1);
        assertThat(enabled.candidates().commands()).isEqualTo(1);
        assertThat(enabled.candidates().securityTransitions()).isEqualTo(1);
        assertThat(enabled.candidates().diagnostics()).isEqualTo(1);
        assertThat(enabled.candidates().revokedBypasses()).isEqualTo(1);
        assertThat(enabled.candidates().notifications()).isEqualTo(1);
        assertThat(enabled.candidates().total()).isEqualTo(6);

        HistoryRetentionRunResponse result = service.runManually("admin");

        assertThat(result.deleted().total()).isEqualTo(6);
        assertThat(result.executedBy()).isEqualTo("admin");
        assertThat(count("device_event_history")).isEqualTo(1);
        assertThat(count("alarm_acknowledgement")).isZero();
        assertThat(count("alarm_comment")).isZero();
        assertThat(count("device_command_history")).isEqualTo(1);
        assertThat(count("intrusion_alarm_history")).isEqualTo(1);
        assertThat(count("sensor_diagnostic_session")).isEqualTo(2);
        assertThat(count("sensor_diagnostic_item")).isEqualTo(2);
        assertThat(count("sensor_bypass_history")).isEqualTo(2);
        assertThat(count("sensor_bypass_warning")).isEqualTo(2);
        assertThat(count("web_push_delivery")).isEqualTo(2);
        assertThat(count("web_push_delivery_attempt")).isEqualTo(2);

        HistoryRetentionResponse after = service.getPolicy();
        assertThat(after.candidates().total()).isZero();
        assertThat(after.lastDeleted().total()).isEqualTo(6);
        assertThat(after.lastRunAt())
                .isEqualTo(Instant.parse("2026-08-14T18:00:00Z"));
        assertThat(after.lastRunBy()).isEqualTo("admin");
        assertThat(after.updatedBy()).isEqualTo("admin");
    }

    private long count(String table) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table,
                Long.class
        );
        return value == null ? 0 : value;
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE security_settings (
                    id SMALLINT PRIMARY KEY,
                    timezone VARCHAR(60) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE history_retention_policy (
                    id SMALLINT PRIMARY KEY,
                    enabled BOOLEAN NOT NULL,
                    retention_months SMALLINT NOT NULL,
                    last_run_at TIMESTAMP WITH TIME ZONE,
                    last_run_by VARCHAR(50),
                    last_cutoff_at TIMESTAMP WITH TIME ZONE,
                    last_deleted_events BIGINT NOT NULL,
                    last_deleted_commands BIGINT NOT NULL,
                    last_deleted_security BIGINT NOT NULL,
                    last_deleted_diagnostics BIGINT NOT NULL,
                    last_deleted_bypasses BIGINT NOT NULL,
                    last_deleted_notifications BIGINT NOT NULL,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    updated_by VARCHAR(50) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE device_event_history (
                    id BIGINT PRIMARY KEY,
                    detected_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE alarm_acknowledgement (
                    id BIGINT PRIMARY KEY,
                    event_id BIGINT NOT NULL,
                    FOREIGN KEY (event_id)
                        REFERENCES device_event_history(id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE alarm_comment (
                    id BIGINT PRIMARY KEY,
                    event_id BIGINT NOT NULL,
                    FOREIGN KEY (event_id)
                        REFERENCES device_event_history(id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE device_command_history (
                    id BIGINT PRIMARY KEY,
                    requested_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE intrusion_alarm_history (
                    id BIGINT PRIMARY KEY,
                    changed_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE sensor_diagnostic_session (
                    id BIGINT PRIMARY KEY,
                    status VARCHAR(20) NOT NULL,
                    started_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE sensor_diagnostic_item (
                    id BIGINT PRIMARY KEY,
                    session_id BIGINT NOT NULL,
                    FOREIGN KEY (session_id)
                        REFERENCES sensor_diagnostic_session(id)
                        ON DELETE CASCADE
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE sensor_bypass_history (
                    id BIGINT PRIMARY KEY,
                    active BOOLEAN NOT NULL,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE sensor_bypass_warning (
                    id BIGINT PRIMARY KEY,
                    bypass_id BIGINT NOT NULL,
                    FOREIGN KEY (bypass_id)
                        REFERENCES sensor_bypass_history(id)
                        ON DELETE CASCADE
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE web_push_delivery (
                    id BIGINT PRIMARY KEY,
                    status VARCHAR(30) NOT NULL,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE web_push_delivery_attempt (
                    id BIGINT PRIMARY KEY,
                    delivery_id BIGINT NOT NULL,
                    FOREIGN KEY (delivery_id)
                        REFERENCES web_push_delivery(id)
                        ON DELETE CASCADE
                )
                """);
    }

    private void seedData() {
        jdbcTemplate.update(
                "INSERT INTO security_settings VALUES (1, 'America/Mazatlan')"
        );
        jdbcTemplate.update("""
                INSERT INTO history_retention_policy VALUES (
                    1, FALSE, 24, NULL, NULL, NULL,
                    0, 0, 0, 0, 0, 0,
                    '2026-08-14T18:00:00Z', 'SYSTEM'
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO device_event_history VALUES
                    (1, '2025-01-01T00:00:00Z'),
                    (2, '2026-08-01T00:00:00Z')
                """);
        jdbcTemplate.update(
                "INSERT INTO alarm_acknowledgement VALUES (1, 1)"
        );
        jdbcTemplate.update(
                "INSERT INTO alarm_comment VALUES (1, 1)"
        );
        jdbcTemplate.update("""
                INSERT INTO device_command_history VALUES
                    (1, '2025-01-01T00:00:00Z'),
                    (2, '2026-08-01T00:00:00Z')
                """);
        jdbcTemplate.update("""
                INSERT INTO intrusion_alarm_history VALUES
                    (1, '2025-01-01T00:00:00Z'),
                    (2, '2026-08-01T00:00:00Z')
                """);
        jdbcTemplate.update("""
                INSERT INTO sensor_diagnostic_session VALUES
                    (1, 'PASSED', '2025-01-01T00:00:00Z'),
                    (2, 'RUNNING', '2025-01-01T00:00:00Z'),
                    (3, 'PASSED', '2026-08-01T00:00:00Z')
                """);
        jdbcTemplate.update("""
                INSERT INTO sensor_diagnostic_item VALUES
                    (1, 1), (2, 2), (3, 3)
                """);
        jdbcTemplate.update("""
                INSERT INTO sensor_bypass_history VALUES
                    (1, FALSE, '2025-01-01T00:00:00Z'),
                    (2, TRUE, '2025-01-01T00:00:00Z'),
                    (3, FALSE, '2026-08-01T00:00:00Z')
                """);
        jdbcTemplate.update("""
                INSERT INTO sensor_bypass_warning VALUES
                    (1, 1), (2, 2), (3, 3)
                """);
        jdbcTemplate.update("""
                INSERT INTO web_push_delivery VALUES
                    (1, 'ACCEPTED', '2025-01-01T00:00:00Z'),
                    (2, 'RETRY_PENDING', '2025-01-01T00:00:00Z'),
                    (3, 'ACCEPTED', '2026-08-01T00:00:00Z')
                """);
        jdbcTemplate.update("""
                INSERT INTO web_push_delivery_attempt VALUES
                    (1, 1), (2, 2), (3, 3)
                """);
    }
}
