package com.icap.logicoti.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SensorEventHistoryQueryServiceTests {

    private JdbcTemplate jdbcTemplate;
    private SensorEventHistoryQueryService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource();

        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl(
                "jdbc:h2:mem:event_history_"
                        + UUID.randomUUID()
                        + ";MODE=PostgreSQL"
                        + ";DB_CLOSE_DELAY=-1"
                        + ";DATABASE_TO_LOWER=TRUE"
        );
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        jdbcTemplate = new JdbcTemplate(dataSource);
        service = new SensorEventHistoryQueryService(jdbcTemplate);

        createSchema();
        seedAlarm();
    }

    @Test
    void includesAcknowledgementAndCommentCountInHistory() {
        SensorEventHistoryResponse event = service.find(
                        null,
                        null,
                        "SMOKE",
                        null,
                        null,
                        null,
                        null,
                        50,
                        0
                )
                .items()
                .getFirst();

        assertThat(event.id()).isEqualTo(70L);
        assertThat(event.acknowledged()).isTrue();
        assertThat(event.acknowledgedBy()).isEqualTo("operador");
        assertThat(event.acknowledgedAt()).isNotNull();
        assertThat(event.commentCount()).isEqualTo(2);
    }

    private void createSchema() {
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
                    plc_state_tag VARCHAR(80) NOT NULL,
                    previous_state BOOLEAN,
                    current_state BOOLEAN NOT NULL,
                    event_type VARCHAR(30) NOT NULL,
                    severity VARCHAR(20) NOT NULL,
                    message VARCHAR(500) NOT NULL,
                    detected_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE alarm_acknowledgement (
                    id BIGINT PRIMARY KEY,
                    event_id BIGINT NOT NULL UNIQUE,
                    acknowledged_by VARCHAR(50) NOT NULL,
                    acknowledged_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE alarm_comment (
                    id BIGINT PRIMARY KEY,
                    event_id BIGINT NOT NULL,
                    comment_text VARCHAR(500) NOT NULL,
                    created_by VARCHAR(50) NOT NULL,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
    }

    private void seedAlarm() {
        jdbcTemplate.update(
                "INSERT INTO building_area VALUES "
                        + "(1, 'PB_A01', 'Recepción')"
        );
        jdbcTemplate.update(
                "INSERT INTO building_device VALUES "
                        + "(7, 1, 'PB_A01_HUM01', 'Sensor de humo 1')"
        );
        jdbcTemplate.update("""
                INSERT INTO device_event_history VALUES (
                    70,
                    7,
                    'PB_A01_HUM01',
                    'PB_A01',
                    'SMOKE',
                    'OTI_PB_A01_HUM01_ALM',
                    FALSE,
                    TRUE,
                    'ACTIVATED',
                    'CRITICAL',
                    'Humo detectado en Recepción.',
                    CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.update(
                "INSERT INTO alarm_acknowledgement VALUES "
                        + "(1, 70, 'operador', CURRENT_TIMESTAMP)"
        );
        jdbcTemplate.update(
                "INSERT INTO alarm_comment VALUES "
                        + "(1, 70, 'Se notificó a seguridad.', "
                        + "'operador', CURRENT_TIMESTAMP), "
                        + "(2, 70, 'Área revisada.', "
                        + "'admin', CURRENT_TIMESTAMP)"
        );
    }
}
