package com.icap.logicoti.intrusion;

import com.icap.logicoti.device.AreaStateService;
import com.icap.logicoti.device.AreaStateResponse;
import com.icap.logicoti.device.DeviceCommandExecutionService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SecurityLightingSelectionTests {
    private JdbcTemplate jdbc;
    private AutomaticLightingRuntimeService runtime;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:light_selection_" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        ));
        jdbc.execute("""
                CREATE TABLE security_zone (code VARCHAR PRIMARY KEY, active BOOLEAN, display_order INT);
                CREATE TABLE security_zone_state (zone_code VARCHAR PRIMARY KEY, mode VARCHAR);
                CREATE TABLE security_zone_area (zone_code VARCHAR, area_id BIGINT);
                CREATE TABLE building_floor (id BIGINT PRIMARY KEY, active BOOLEAN);
                CREATE TABLE building_area (id BIGINT PRIMARY KEY, code VARCHAR, name VARCHAR,
                    floor_id BIGINT, active BOOLEAN, display_order INT);
                CREATE TABLE building_device (id BIGINT PRIMARY KEY, code VARCHAR, name VARCHAR,
                    area_id BIGINT, active BOOLEAN, device_type VARCHAR, controllable BOOLEAN,
                    plc_command_tag VARCHAR, plc_state_tag VARCHAR, display_order INT);
                CREATE TABLE security_automatic_lighting_target (device_id BIGINT PRIMARY KEY);
                CREATE TABLE security_automatic_lighting_runtime (
                    device_id BIGINT PRIMARY KEY, activated_at TIMESTAMP WITH TIME ZONE,
                    last_motion_at TIMESTAMP WITH TIME ZONE, turn_off_at TIMESTAMP WITH TIME ZONE,
                    updated_at TIMESTAMP WITH TIME ZONE);
                CREATE TABLE sensor_bypass_history (device_code VARCHAR, active BOOLEAN);
                INSERT INTO security_zone VALUES ('PB', TRUE, 1), ('PATIO', TRUE, 2), ('P1', TRUE, 3);
                INSERT INTO security_zone_state VALUES ('PB', 'ARMED'), ('PATIO', 'ARMED'), ('P1', 'DISARMED');
                INSERT INTO security_zone_area VALUES ('PB', 1), ('PATIO', 2), ('P1', 3);
                INSERT INTO building_floor VALUES (1, TRUE);
                INSERT INTO building_area VALUES (1, 'PB_A01', 'Recepción', 1, TRUE, 1),
                    (2, 'EXT_A01', 'Patio', 1, TRUE, 2), (3, 'P1_A01', 'Piso 1', 1, TRUE, 3);
                INSERT INTO building_device VALUES
                    (10, 'LIGHT_10', 'PB seleccionada', 1, TRUE, 'LIGHT', TRUE, 'CMD10', 'FB10', 1),
                    (11, 'LIGHT_11', 'PB no seleccionada', 1, TRUE, 'LIGHT', TRUE, 'CMD11', 'FB11', 2),
                    (12, 'LIGHT_12', 'PB inactiva', 1, FALSE, 'LIGHT', TRUE, 'CMD12', 'FB12', 3),
                    (20, 'LIGHT_20', 'Patio seleccionada', 2, TRUE, 'LIGHT', TRUE, 'CMD20', 'FB20', 1),
                    (30, 'LIGHT_30', 'P1 seleccionada', 3, TRUE, 'LIGHT', TRUE, 'CMD30', 'FB30', 1),
                    (50, 'MOTION_50', 'Sensor PB', 1, TRUE, 'MOTION', FALSE, NULL, 'ST50', 4),
                    (51, 'MOTION_51', 'Sensor P1', 3, TRUE, 'MOTION', FALSE, NULL, 'ST51', 2);
                INSERT INTO security_automatic_lighting_target VALUES (10), (12), (20), (30);
                """);
        runtime = new AutomaticLightingRuntimeService(jdbc);
    }

    @Test
    void commonAreaSelectionIsGlobalRegardlessOfArmedZones() {
        assertThat(runtime.findTargets())
                .extracting(AutomaticLightingRuntimeService.LightingDevice::code)
                .containsExactly("LIGHT_10", "LIGHT_20", "LIGHT_30");

        jdbc.update("UPDATE security_zone_state SET mode = 'DISARMED'");

        assertThat(runtime.findTargets())
                .extracting(AutomaticLightingRuntimeService.LightingDevice::code)
                .containsExactly("LIGHT_10", "LIGHT_20", "LIGHT_30");
    }

    @Test
    void motionFromDisarmedZoneCommandsGlobalSelectionWhileOtherZonesAreArmed() {
        SecurityScheduleService schedule = mock(SecurityScheduleService.class);
        DeviceCommandExecutionService commands = mock(DeviceCommandExecutionService.class);
        AreaStateService areas = mock(AreaStateService.class);
        when(schedule.getSettings()).thenReturn(settings(true));
        when(areas.getAreaState(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> areaState(invocation.getArgument(0), false));
        when(commands.executeAutomatic(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(true)))
                .thenAnswer(invocation -> commandResponse(invocation.getArgument(0)));

        AutomaticLightingService service = new AutomaticLightingService(
                jdbc,
                schedule,
                runtime,
                commands,
                areas,
                mock(SimpMessagingTemplate.class)
        );

        service.refreshActiveMotion("MOTION_51");

        verify(commands).executeAutomatic("LIGHT_10", true);
        verify(commands).executeAutomatic("LIGHT_20", true);
        verify(commands).executeAutomatic("LIGHT_30", true);
        verifyNoMoreInteractions(commands);
    }

    private AreaStateResponse areaState(String areaCode, boolean state) {
        String deviceCode = switch (areaCode) {
            case "PB_A01" -> "LIGHT_10";
            case "EXT_A01" -> "LIGHT_20";
            default -> "LIGHT_30";
        };
        return response(areaCode, deviceCode, state, false);
    }

    private AreaStateResponse commandResponse(String deviceCode) {
        String areaCode = switch (deviceCode) {
            case "LIGHT_10" -> "PB_A01";
            case "LIGHT_20" -> "EXT_A01";
            default -> "P1_A01";
        };
        return response(areaCode, deviceCode, false, true);
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

    private SecuritySettingsResponse settings(boolean automaticLightingEnabled) {
        return new SecuritySettingsResponse(
                true,
                automaticLightingEnabled,
                LocalTime.MIDNIGHT,
                LocalTime.MIDNIGHT,
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
}
