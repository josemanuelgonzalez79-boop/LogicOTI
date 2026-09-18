package com.icap.logicoti.intrusion;

import com.icap.logicoti.device.AreaStateResponse;
import com.icap.logicoti.device.AreaStateService;
import com.icap.logicoti.device.DeviceCommandExecutionService;
import com.icap.logicoti.event.SensorEventHistoryResponse;
import com.icap.logicoti.signal.SignalQuality;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AreaEnergySavingServiceTests {

    private JdbcTemplate jdbc;
    private SecurityScheduleService schedules;
    private AreaStateService areas;
    private DeviceCommandExecutionService commands;
    private SecurityZoneLightingService emergencyLighting;
    private AreaInactivityService service;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:area_energy_" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        ));
        jdbc.execute("""
                CREATE TABLE building_floor (
                    id BIGINT PRIMARY KEY,
                    code VARCHAR,
                    name VARCHAR,
                    active BOOLEAN
                );
                CREATE TABLE building_area (
                    id BIGINT PRIMARY KEY,
                    code VARCHAR,
                    name VARCHAR,
                    floor_id BIGINT,
                    active BOOLEAN
                );
                CREATE TABLE building_device (
                    id BIGINT PRIMARY KEY,
                    area_id BIGINT,
                    code VARCHAR,
                    active BOOLEAN,
                    device_type VARCHAR
                );
                CREATE TABLE security_area_energy_saving (
                    area_id BIGINT PRIMARY KEY,
                    enabled BOOLEAN
                );
                CREATE TABLE security_zone_area (
                    area_id BIGINT PRIMARY KEY,
                    zone_code VARCHAR
                );
                CREATE TABLE security_zone_state (
                    zone_code VARCHAR PRIMARY KEY,
                    mode VARCHAR
                );
                CREATE TABLE sensor_bypass_history (
                    device_code VARCHAR,
                    active BOOLEAN
                );
                CREATE TABLE security_area_inactivity_runtime (
                    area_id BIGINT PRIMARY KEY,
                    last_motion_at TIMESTAMP WITH TIME ZONE,
                    light_turn_off_at TIMESTAMP WITH TIME ZONE,
                    minisplit_turn_off_at TIMESTAMP WITH TIME ZONE,
                    light_processed BOOLEAN,
                    minisplit_processed BOOLEAN,
                    lights_turned_off INT,
                    minisplits_turned_off INT,
                    updated_at TIMESTAMP WITH TIME ZONE
                );
                CREATE TABLE security_automatic_lighting_target (
                    device_id BIGINT PRIMARY KEY
                );

                INSERT INTO building_floor VALUES (1, 'PB', 'Planta Baja', TRUE);
                INSERT INTO building_area VALUES
                    (1, 'PB_A01', 'Recepción', 1, TRUE),
                    (2, 'PB_A02', 'Sala', 1, TRUE);
                INSERT INTO building_device VALUES
                    (10, 1, 'MOTION_PB_1', TRUE, 'MOTION'),
                    (11, 1, 'MOTION_PB_2', TRUE, 'MOTION'),
                    (20, 2, 'MOTION_SALA', TRUE, 'MOTION');
                INSERT INTO security_area_energy_saving VALUES
                    (1, TRUE),
                    (2, FALSE);
                INSERT INTO security_zone_area VALUES
                    (1, 'PB'),
                    (2, 'PB');
                INSERT INTO security_zone_state VALUES ('PB', 'DISARMED');
                """);

        schedules = mock(SecurityScheduleService.class);
        areas = mock(AreaStateService.class);
        commands = mock(DeviceCommandExecutionService.class);
        emergencyLighting = mock(SecurityZoneLightingService.class);
        when(schedules.getSettings()).thenReturn(settings());
        when(areas.getAreaState(anyString()))
                .thenAnswer(invocation -> areaState(
                        invocation.getArgument(0),
                        false,
                        true
                ));
        when(commands.executeAutomatic(anyString(), eq(true)))
                .thenAnswer(invocation -> commandResponse(
                        invocation.getArgument(0),
                        true
                ));
        when(emergencyLighting.isOwned(anyString())).thenReturn(false);

        service = new AreaInactivityService(
                jdbc,
                schedules,
                new AreaInactivityRuntimeService(jdbc),
                commands,
                areas,
                emergencyLighting,
                mock(SimpMessagingTemplate.class)
        );
    }

    @Test
    void disarmedMotionTurnsOnOnlyTheLightFromItsEnabledArea() {
        service.processMotion(motion("MOTION_PB_1", "PB_A01"));
        service.processMotion(motion("MOTION_SALA", "PB_A02"));

        verify(commands).executeAutomatic("LIGHT_PB_A01", true);
        verify(commands, never()).executeAutomatic("MINISPLIT_PB_A01", true);
        verify(commands, never()).executeAutomatic("LIGHT_PB_A02", true);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM security_area_inactivity_runtime",
                Integer.class
        )).isEqualTo(1);
    }

    @Test
    void armedAreaDoesNotRunEnergySavingAutomation() {
        jdbc.update("""
                UPDATE security_zone_state
                SET mode = 'ARMED'
                WHERE zone_code = 'PB'
                """);

        service.processMotion(motion("MOTION_PB_2", "PB_A01"));

        verify(commands, never()).executeAutomatic(anyString(), eq(true));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM security_area_inactivity_runtime",
                Integer.class
        )).isZero();
    }

    @Test
    void inactivityTurnsOffTheAreaLightAndMinisplit() {
        service.processMotion(motion("MOTION_PB_1", "PB_A01"));
        java.sql.Timestamp expiredAt = java.sql.Timestamp.from(
                Instant.now().minusSeconds(60)
        );
        jdbc.update("""
                UPDATE security_area_inactivity_runtime
                SET light_turn_off_at = ?,
                    minisplit_turn_off_at = ?
                """,
                expiredAt,
                expiredAt
        );

        reset(areas);
        when(areas.getAreaState("PB_A01"))
                .thenReturn(areaState("PB_A01", true, true));
        when(commands.executeAutomatic(anyString(), eq(false)))
                .thenAnswer(invocation -> commandResponse(
                        invocation.getArgument(0),
                        false
                ));

        service.processExpiredAreas();

        verify(commands).executeAutomatic("LIGHT_PB_A01", false);
        verify(commands).executeAutomatic("MINISPLIT_PB_A01", false);
    }

    private SecuritySettingsResponse settings() {
        return new SecuritySettingsResponse(
                true,
                true,
                LocalTime.of(18, 0),
                LocalTime.of(8, 0),
                true,
                "America/Mazatlan",
                60,
                10,
                30,
                120,
                4,
                List.of(),
                List.of(),
                List.of(),
                Instant.now(),
                "SYSTEM"
        );
    }

    private SensorEventHistoryResponse motion(
            String sensorCode,
            String areaCode
    ) {
        return new SensorEventHistoryResponse(
                42L,
                sensorCode,
                sensorCode,
                areaCode,
                areaCode,
                "MOTION",
                "TAG",
                false,
                true,
                "ACTIVATED",
                "INFO",
                "Movimiento",
                Instant.now(),
                false,
                null,
                null,
                0
        );
    }

    private AreaStateResponse areaState(
            String areaCode,
            boolean lightState,
            boolean minisplitState
    ) {
        return new AreaStateResponse(
                areaCode,
                areaCode,
                true,
                true,
                List.of(
                        device(
                                "LIGHT_" + areaCode,
                                "LIGHT",
                                lightState
                        ),
                        device(
                                "MINISPLIT_" + areaCode,
                                "MINISPLIT",
                                minisplitState
                        )
                ),
                "Disponible",
                Instant.now()
        );
    }

    private AreaStateResponse commandResponse(
            String deviceCode,
            boolean state
    ) {
        return new AreaStateResponse(
                "PB_A01",
                "PB_A01",
                true,
                true,
                List.of(new AreaStateResponse.DeviceStateResponse(
                        1L,
                        deviceCode,
                        deviceCode,
                        deviceCode.startsWith("LIGHT")
                                ? "LIGHT"
                                : "MINISPLIT",
                        1,
                        true,
                        state,
                        state,
                        false,
                        SignalQuality.GOOD,
                        Instant.now(),
                        "Disponible"
                )),
                "Disponible",
                Instant.now()
        );
    }

    private AreaStateResponse.DeviceStateResponse device(
            String code,
            String type,
            boolean state
    ) {
        return new AreaStateResponse.DeviceStateResponse(
                1L,
                code,
                code,
                type,
                1,
                true,
                state,
                state,
                false,
                SignalQuality.GOOD,
                Instant.now(),
                "Disponible"
        );
    }
}
