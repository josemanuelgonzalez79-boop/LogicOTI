package com.icap.logicoti.intrusion;

import com.icap.logicoti.device.AreaStateResponse;
import com.icap.logicoti.device.AreaStateService;
import com.icap.logicoti.device.DeviceCommandExecutionService;
import com.icap.logicoti.signal.SignalQuality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SecurityLightingSelectionTests {

    private JdbcTemplate jdbc;
    private AreaStateService areas;
    private DeviceCommandExecutionService commands;
    private SecurityZoneLightingService service;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:emergency_lighting_" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        ));
        jdbc.execute("""
                CREATE TABLE security_settings (
                    id INT PRIMARY KEY,
                    automatic_lighting_enabled BOOLEAN
                );
                CREATE TABLE security_zone_state (
                    zone_code VARCHAR PRIMARY KEY,
                    mode VARCHAR
                );
                CREATE TABLE building_floor (
                    id BIGINT PRIMARY KEY,
                    active BOOLEAN,
                    display_order INT
                );
                CREATE TABLE building_area (
                    id BIGINT PRIMARY KEY,
                    code VARCHAR,
                    name VARCHAR,
                    floor_id BIGINT,
                    active BOOLEAN,
                    display_order INT
                );
                CREATE TABLE building_device (
                    id BIGINT PRIMARY KEY,
                    code VARCHAR,
                    name VARCHAR,
                    area_id BIGINT,
                    active BOOLEAN,
                    device_type VARCHAR,
                    controllable BOOLEAN,
                    plc_command_tag VARCHAR,
                    plc_state_tag VARCHAR,
                    display_order INT
                );
                CREATE TABLE security_automatic_lighting_target (
                    device_id BIGINT PRIMARY KEY
                );
                CREATE TABLE security_zone_light_runtime (
                    device_id BIGINT PRIMARY KEY,
                    zone_code VARCHAR,
                    activation_reason VARCHAR,
                    trigger_event_id BIGINT,
                    activated_at TIMESTAMP WITH TIME ZONE
                );

                INSERT INTO security_settings VALUES (1, TRUE);
                INSERT INTO security_zone_state VALUES
                    ('PB', 'ALARM'),
                    ('P1', 'ARMED');
                INSERT INTO building_floor VALUES (1, TRUE, 1);
                INSERT INTO building_area VALUES
                    (1, 'PB_A01', 'Recepción', 1, TRUE, 1),
                    (2, 'P1_A01', 'Sala', 1, TRUE, 2);
                INSERT INTO building_device VALUES
                    (10, 'LIGHT_10', 'Recepción', 1, TRUE, 'LIGHT', TRUE, 'CMD10', 'FB10', 1),
                    (11, 'LIGHT_11', 'No seleccionada', 1, TRUE, 'LIGHT', TRUE, 'CMD11', 'FB11', 2),
                    (20, 'LIGHT_20', 'Sala', 2, TRUE, 'LIGHT', TRUE, 'CMD20', 'FB20', 1);
                INSERT INTO security_automatic_lighting_target VALUES (10), (20);
                """);

        areas = mock(AreaStateService.class);
        commands = mock(DeviceCommandExecutionService.class);
        service = new SecurityZoneLightingService(jdbc, areas, commands);

        when(areas.getAreaState(anyString()))
                .thenAnswer(invocation -> areaState(
                        invocation.getArgument(0),
                        false
                ));
        when(commands.executeAutomatic(anyString(), eq(true)))
                .thenAnswer(invocation -> commandResponse(
                        invocation.getArgument(0),
                        true
                ));
        when(commands.executeAutomatic(anyString(), eq(false)))
                .thenAnswer(invocation -> commandResponse(
                        invocation.getArgument(0),
                        false
                ));
    }

    @Test
    void armedZoneMotionTurnsOnOnlyConfiguredEmergencySelection() {
        SecurityZoneLightingService.LightingActionResult result =
                service.turnOnEmergencySelection("PB", 42L);

        assertThat(result.successful()).isTrue();
        assertThat(result.changedLights()).isEqualTo(2);
        verify(commands).executeAutomatic("LIGHT_10", true);
        verify(commands).executeAutomatic("LIGHT_20", true);
        verify(commands, never()).executeAutomatic("LIGHT_11", true);
        verifyNoMoreInteractions(commands);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM security_zone_light_runtime",
                Integer.class
        )).isEqualTo(2);
    }

    @Test
    void disabledEmergencyLightingDoesNotCommandSelectedLights() {
        jdbc.update("""
                UPDATE security_settings
                SET automatic_lighting_enabled = FALSE
                WHERE id = 1
                """);

        SecurityZoneLightingService.LightingActionResult result =
                service.turnOnEmergencySelection("PB", 42L);

        assertThat(result.changedLights()).isZero();
        verifyNoMoreInteractions(commands);
    }

    @Test
    void recognitionTurnsOffOwnedLightsAfterLastAlarm() {
        service.turnOnEmergencySelection("PB", 42L);
        jdbc.update("UPDATE security_zone_state SET mode = 'ARMED'");

        SecurityZoneLightingService.LightingActionResult result =
                service.turnOffOwnedIfNoActiveAlarm();

        assertThat(result.successful()).isTrue();
        verify(commands).executeAutomatic("LIGHT_10", false);
        verify(commands).executeAutomatic("LIGHT_20", false);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM security_zone_light_runtime",
                Integer.class
        )).isZero();
    }

    private AreaStateResponse areaState(String areaCode, boolean state) {
        String lightCode = "PB_A01".equals(areaCode)
                ? "LIGHT_10"
                : "LIGHT_20";
        return response(areaCode, lightCode, state, state);
    }

    private AreaStateResponse commandResponse(
            String deviceCode,
            boolean state
    ) {
        String areaCode = "LIGHT_10".equals(deviceCode)
                ? "PB_A01"
                : "P1_A01";
        return response(areaCode, deviceCode, state, state);
    }

    private AreaStateResponse response(
            String areaCode,
            String deviceCode,
            boolean state,
            boolean command
    ) {
        AreaStateResponse.DeviceStateResponse device =
                new AreaStateResponse.DeviceStateResponse(
                        1L,
                        deviceCode,
                        deviceCode,
                        "LIGHT",
                        1,
                        true,
                        command,
                        state,
                        false,
                        SignalQuality.GOOD,
                        Instant.now(),
                        "Disponible"
                );
        return new AreaStateResponse(
                areaCode,
                areaCode,
                true,
                true,
                List.of(device),
                "Disponible",
                Instant.now()
        );
    }
}
