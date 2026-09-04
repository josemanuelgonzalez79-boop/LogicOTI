package com.icap.logicoti.intrusion;

import com.icap.logicoti.event.SensorEventHistoryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityZoneServiceTests {

    private JdbcTemplate jdbcTemplate;
    private SecurityZoneLightingService lightingService;
    private SecurityZoneService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl(
                "jdbc:h2:mem:security_zones_"
                        + UUID.randomUUID()
                        + ";MODE=PostgreSQL"
                        + ";DB_CLOSE_DELAY=-1"
                        + ";DATABASE_TO_LOWER=TRUE"
        );
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        jdbcTemplate = new JdbcTemplate(dataSource);
        SecurityScheduleService scheduleService = mock(
                SecurityScheduleService.class
        );
        SecurityPrecheckService precheckService = mock(
                SecurityPrecheckService.class
        );
        lightingService = mock(SecurityZoneLightingService.class);
        SimpMessagingTemplate messagingTemplate = mock(
                SimpMessagingTemplate.class
        );

        when(scheduleService.getSettings()).thenReturn(settings());

        service = new SecurityZoneService(
                jdbcTemplate,
                scheduleService,
                precheckService,
                lightingService,
                messagingTemplate
        );

        createSchema();
        seedZones();
    }

    @Test
    void reportsPartialArmingWithoutChangingOtherZones() {
        setMode("PB", "ARMED", null, null);

        SecurityZoneListResponse response = service.getStatus();

        assertThat(response.aggregateMode()).isEqualTo("PARTIALLY_ARMED");
        assertThat(response.armedZones()).isEqualTo(1);
        assertThat(response.totalZones()).isEqualTo(4);
        assertThat(response.zones())
                .filteredOn(zone -> zone.code().equals("PB"))
                .extracting(SecurityZoneStatusResponse::armed)
                .containsExactly(true);
    }

    @Test
    void motionAlarmsItsZoneAndTurnsOnEveryArmedZone() {
        setMode("PB", "ARMED", null, null);
        setMode("P1", "ARMED", null, null);
        when(lightingService.turnOnZones(
                List.of("PB", "P1"),
                "ALARM",
                42L
        )).thenReturn(successfulLighting());

        SecurityStatusResponse response = service.activateFromMotion(
                motionEvent()
        );

        assertThat(response.mode()).isEqualTo("ALARM");
        assertThat(stateValue("PB", "mode")).isEqualTo("ALARM");
        assertThat(stateValue("P1", "mode")).isEqualTo("ARMED");
        assertThat(stateLong("PB", "alarm_event_id")).isEqualTo(42L);
        verify(lightingService).turnOnZones(
                List.of("PB", "P1"),
                "ALARM",
                42L
        );
    }

    @Test
    void acknowledgementRestoresArmedModeAndTurnsOffOwnedLights() {
        setMode("PB", "ALARM", "ARMED", 42L);
        setMode("P1", "ARMED", null, null);
        when(lightingService.turnOffOwnedZones(
                List.of("PB", "P1")
        )).thenReturn(successfulLighting());

        SecurityZoneListResponse response = service.acknowledge(
                "PB",
                "operador"
        );

        assertThat(response.alarmZones()).isZero();
        assertThat(stateValue("PB", "mode")).isEqualTo("ARMED");
        assertThat(stateLong("PB", "alarm_event_id")).isNull();
        assertThat(countAcknowledgements(42L)).isEqualTo(1);
        verify(lightingService).turnOffOwnedZones(
                List.of("PB", "P1")
        );
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE security_zone (
                    code VARCHAR(20) PRIMARY KEY,
                    name VARCHAR(80) NOT NULL,
                    display_order SMALLINT NOT NULL,
                    motion_detection_enabled BOOLEAN NOT NULL,
                    active BOOLEAN NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE security_zone_state (
                    zone_code VARCHAR(20) PRIMARY KEY,
                    mode VARCHAR(30) NOT NULL,
                    mode_before_alarm VARCHAR(30),
                    message VARCHAR(400) NOT NULL,
                    changed_by VARCHAR(50) NOT NULL,
                    change_source VARCHAR(20) NOT NULL,
                    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    arming_completes_at TIMESTAMP WITH TIME ZONE,
                    automatic_transition_key VARCHAR(40),
                    alarm_event_id BIGINT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE security_zone_history (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    zone_code VARCHAR(20) NOT NULL,
                    previous_mode VARCHAR(30) NOT NULL,
                    current_mode VARCHAR(30) NOT NULL,
                    message VARCHAR(400) NOT NULL,
                    changed_by VARCHAR(50) NOT NULL,
                    change_source VARCHAR(20) NOT NULL,
                    alarm_event_id BIGINT,
                    changed_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE building_area (
                    id BIGINT PRIMARY KEY,
                    code VARCHAR(40) NOT NULL,
                    active BOOLEAN NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE security_zone_area (
                    area_id BIGINT PRIMARY KEY,
                    zone_code VARCHAR(20) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE building_device (
                    id BIGINT PRIMARY KEY,
                    area_id BIGINT NOT NULL,
                    active BOOLEAN NOT NULL,
                    device_type VARCHAR(30) NOT NULL,
                    controllable BOOLEAN NOT NULL,
                    plc_command_tag VARCHAR(80),
                    plc_state_tag VARCHAR(80)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE security_zone_light_runtime (
                    device_id BIGINT PRIMARY KEY,
                    zone_code VARCHAR(20) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE sensor_bypass_history (
                    id BIGINT PRIMARY KEY,
                    device_id BIGINT NOT NULL,
                    active BOOLEAN NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE alarm_acknowledgement (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    event_id BIGINT NOT NULL UNIQUE,
                    acknowledged_by VARCHAR(50) NOT NULL
                )
                """);
    }

    private void seedZones() {
        jdbcTemplate.update("""
                INSERT INTO security_zone (
                    code,
                    name,
                    display_order,
                    motion_detection_enabled,
                    active
                )
                VALUES
                    ('PB', 'Planta Baja', 1, TRUE, TRUE),
                    ('P1', 'Piso 1', 2, TRUE, TRUE),
                    ('P2', 'Piso 2', 3, TRUE, TRUE),
                    ('PATIO', 'Patio y exterior', 4, FALSE, TRUE)
                """);
        jdbcTemplate.update("""
                INSERT INTO security_zone_state (
                    zone_code,
                    mode,
                    message,
                    changed_by,
                    change_source,
                    changed_at
                )
                SELECT
                    code,
                    'DISARMED',
                    'La zona se encuentra desarmada.',
                    'SYSTEM',
                    'SYSTEM',
                    CURRENT_TIMESTAMP
                FROM security_zone
                """);
        jdbcTemplate.update("""
                INSERT INTO building_area (id, code, active)
                VALUES (1, 'PB_A01', TRUE)
                """);
        jdbcTemplate.update("""
                INSERT INTO security_zone_area (area_id, zone_code)
                VALUES (1, 'PB')
                """);
    }

    private void setMode(
            String zoneCode,
            String mode,
            String modeBeforeAlarm,
            Long alarmEventId
    ) {
        jdbcTemplate.update("""
                UPDATE security_zone_state
                SET mode = ?,
                    mode_before_alarm = ?,
                    alarm_event_id = ?,
                    changed_at = CURRENT_TIMESTAMP
                WHERE zone_code = ?
                """,
                mode,
                modeBeforeAlarm,
                alarmEventId,
                zoneCode
        );
    }

    private String stateValue(String zoneCode, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column
                        + " FROM security_zone_state WHERE zone_code = ?",
                String.class,
                zoneCode
        );
    }

    private Long stateLong(String zoneCode, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column
                        + " FROM security_zone_state WHERE zone_code = ?",
                Long.class,
                zoneCode
        );
    }

    private int countAcknowledgements(long eventId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM alarm_acknowledgement WHERE event_id = ?",
                Integer.class,
                eventId
        );
        return count == null ? 0 : count;
    }

    private SensorEventHistoryResponse motionEvent() {
        return new SensorEventHistoryResponse(
                42L,
                "PB_A01_MOV01",
                "Sensor de movimiento 1",
                "PB_A01",
                "Recepción",
                "MOTION",
                "OTI_PB_A01_MOV01_ST",
                false,
                true,
                "ACTIVATED",
                "INFO",
                "Movimiento detectado en Recepción.",
                Instant.now(),
                false,
                null,
                null,
                0
        );
    }

    private SecuritySettingsResponse settings() {
        return new SecuritySettingsResponse(
                true,
                false,
                LocalTime.of(18, 0),
                LocalTime.of(8, 0),
                false,
                "America/Mazatlan",
                60,
                10,
                30,
                120,
                4,
                List.of(),
                List.of(),
                Instant.now(),
                "SYSTEM"
        );
    }

    private SecurityZoneLightingService.LightingActionResult successfulLighting() {
        return new SecurityZoneLightingService.LightingActionResult(
                true,
                0,
                List.of()
        );
    }
}
