package com.icap.logicoti.report;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutiveMonthlyReportServiceTests {

    private JdbcTemplate jdbcTemplate;
    private ExecutiveMonthlyReportService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource();

        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl(
                "jdbc:h2:mem:executive_report_"
                        + UUID.randomUUID()
                        + ";MODE=PostgreSQL"
                        + ";DB_CLOSE_DELAY=-1"
                        + ";DATABASE_TO_LOWER=TRUE"
        );
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        jdbcTemplate = new JdbcTemplate(dataSource);
        service = new ExecutiveMonthlyReportService(
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
    void generatesOperationalIndicatorsAndBreakdowns() {
        ExecutiveMonthlyReportResponse report = service.generate(
                YearMonth.of(2026, 8)
        );

        assertThat(report.month()).isEqualTo("2026-08");
        assertThat(report.timezone()).isEqualTo("America/Mazatlan");
        assertThat(report.alarms().total()).isEqualTo(2);
        assertThat(report.alarms().smoke()).isEqualTo(1);
        assertThat(report.alarms().motion()).isEqualTo(1);
        assertThat(report.alarms().acknowledgementRate()).isEqualTo(50.0);
        assertThat(report.alarms().averageRestoreMinutes()).isEqualTo(15.0);

        assertThat(report.commands().total()).isEqualTo(2);
        assertThat(report.commands().confirmed()).isEqualTo(1);
        assertThat(report.commands().failed()).isEqualTo(1);
        assertThat(report.commands().confirmationRate()).isEqualTo(50.0);
        assertThat(report.commands().averageLatencyMs()).isEqualTo(120.0);

        assertThat(report.maintenance().diagnosticsPassed()).isEqualTo(1);
        assertThat(report.maintenance().diagnosticsRejected()).isEqualTo(1);
        assertThat(report.maintenance().bypassesCreated()).isEqualTo(1);
        assertThat(report.security().rejectedArmings()).isEqualTo(3);

        assertThat(report.alarmsByArea())
                .extracting(ExecutiveMonthlyReportResponse.CountMetric::code)
                .containsExactly("PB_A01");
        assertThat(report.alarmsBySensor()).hasSize(2);
        assertThat(report.commandFailures()).hasSize(1);
        assertThat(report.armRejectionReasons().getFirst().count())
                .isEqualTo(3);
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE security_settings (
                    id SMALLINT PRIMARY KEY,
                    timezone VARCHAR(60) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE building_area (
                    id BIGINT PRIMARY KEY,
                    code VARCHAR(20) NOT NULL,
                    name VARCHAR(150) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE building_device (
                    id BIGINT PRIMARY KEY,
                    area_id BIGINT NOT NULL,
                    code VARCHAR(40) NOT NULL,
                    name VARCHAR(150) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE device_event_history (
                    id BIGINT PRIMARY KEY,
                    device_id BIGINT NOT NULL,
                    device_code VARCHAR(40) NOT NULL,
                    area_code VARCHAR(20) NOT NULL,
                    device_type VARCHAR(30) NOT NULL,
                    event_type VARCHAR(30) NOT NULL,
                    severity VARCHAR(20) NOT NULL,
                    detected_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE alarm_acknowledgement (
                    id BIGINT PRIMARY KEY,
                    event_id BIGINT NOT NULL UNIQUE
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE device_command_history (
                    id BIGINT PRIMARY KEY,
                    device_id BIGINT NOT NULL,
                    device_code VARCHAR(40) NOT NULL,
                    area_code VARCHAR(20) NOT NULL,
                    requested_value BOOLEAN NOT NULL,
                    feedback_value BOOLEAN,
                    status VARCHAR(30) NOT NULL,
                    duration_ms BIGINT,
                    requested_at TIMESTAMP WITH TIME ZONE NOT NULL
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
                CREATE TABLE sensor_bypass_history (
                    id BIGINT PRIMARY KEY,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE intrusion_alarm_history (
                    id BIGINT PRIMARY KEY,
                    current_mode VARCHAR(30) NOT NULL,
                    message VARCHAR(300) NOT NULL,
                    changed_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE security_zone_history (
                    id BIGINT PRIMARY KEY,
                    current_mode VARCHAR(30) NOT NULL,
                    message VARCHAR(400) NOT NULL,
                    changed_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
    }

    private void seedData() {
        jdbcTemplate.update(
                "INSERT INTO security_settings VALUES "
                        + "(1, 'America/Mazatlan')"
        );
        jdbcTemplate.update(
                "INSERT INTO building_area VALUES "
                        + "(1, 'PB_A01', 'Recepción')"
        );
        jdbcTemplate.update("""
                INSERT INTO building_device VALUES
                    (1, 1, 'PB_A01_HUM01', 'Sensor de humo 1'),
                    (2, 1, 'PB_A01_MOV01', 'Sensor de movimiento 1'),
                    (3, 1, 'PB_A01_LUZ01', 'Iluminación general')
                """);
        jdbcTemplate.update("""
                INSERT INTO device_event_history VALUES
                    (1, 1, 'PB_A01_HUM01', 'PB_A01', 'SMOKE',
                     'ACTIVATED', 'CRITICAL', '2026-08-10T18:00:00Z'),
                    (2, 1, 'PB_A01_HUM01', 'PB_A01', 'SMOKE',
                     'CLEARED', 'INFO', '2026-08-10T18:10:00Z'),
                    (3, 2, 'PB_A01_MOV01', 'PB_A01', 'MOTION',
                     'ACTIVATED', 'WARNING', '2026-08-11T19:00:00Z'),
                    (4, 2, 'PB_A01_MOV01', 'PB_A01', 'MOTION',
                     'CLEARED', 'INFO', '2026-08-11T19:20:00Z')
                """);
        jdbcTemplate.update(
                "INSERT INTO alarm_acknowledgement VALUES (1, 1)"
        );
        jdbcTemplate.update("""
                INSERT INTO device_command_history VALUES
                    (1, 3, 'PB_A01_LUZ01', 'PB_A01', TRUE, TRUE,
                     'CONFIRMED', 120, '2026-08-12T16:00:00Z'),
                    (2, 3, 'PB_A01_LUZ01', 'PB_A01', FALSE, TRUE,
                     'NOT_CONFIRMED', 5000, '2026-08-12T17:00:00Z')
                """);
        jdbcTemplate.update("""
                INSERT INTO sensor_diagnostic_session VALUES
                    (1, 'PASSED', '2026-08-05T16:00:00Z'),
                    (2, 'REJECTED', '2026-08-06T16:00:00Z')
                """);
        jdbcTemplate.update(
                "INSERT INTO sensor_bypass_history VALUES "
                        + "(1, '2026-08-07T16:00:00Z')"
        );
        jdbcTemplate.update("""
                INSERT INTO intrusion_alarm_history VALUES
                    (1, 'REJECTED', 'Hay sensores sin diagnóstico vigente.',
                     '2026-08-08T16:00:00Z'),
                    (2, 'REJECTED', 'Hay sensores sin diagnóstico vigente.',
                     '2026-08-09T16:00:00Z')
                """);
        jdbcTemplate.update("""
                INSERT INTO security_zone_history VALUES
                    (1, 'REJECTED', 'Hay sensores sin diagnóstico vigente.',
                     '2026-08-10T16:00:00Z')
                """);
    }
}
