package com.icap.logicoti.intrusion;

import com.icap.logicoti.device.AreaStateResponse;
import com.icap.logicoti.device.AreaStateService;
import com.icap.logicoti.device.DeviceCommandExecutionService;
import com.icap.logicoti.signal.SignalQuality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SecurityLightingSelectionTests {
    private JdbcTemplate jdbc;
    private AutomaticLightingRuntimeService runtime;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:light_selection_" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""));
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
                    (50, 'MOTION_50', 'Sensor PB', 1, TRUE, 'MOTION', FALSE, NULL, 'ST50', 4);
                INSERT INTO security_automatic_lighting_target VALUES (10), (12), (20), (30);
                """);
        runtime = new AutomaticLightingRuntimeService(jdbc);
    }

    @Test
    void alarmOnlyCommandsSelectedActiveLightsInArmedZones() {
        AreaStateService areas = mock(AreaStateService.class);
        DeviceCommandExecutionService commands = mock(DeviceCommandExecutionService.class);
        SecurityZoneLightingService service = new SecurityZoneLightingService(jdbc, areas, commands, runtime);
        when(areas.getAreaState(anyString())).thenAnswer(call -> {
            String area = call.getArgument(0);
            long id = area.equals("PB_A01") ? 10L : 20L;
            var state = new AreaStateResponse.DeviceStateResponse(id, "LIGHT_" + id, "Luz", "LIGHT", 1,
                    true, true, false, false, SignalQuality.GOOD, Instant.now(), "Disponible");
            return new AreaStateResponse(area, area, true, true, List.of(state), "Disponible", Instant.now());
        });
        // El PLC se simula sin escrituras físicas; se comprueban los destinos de cada comando.
        when(commands.executeAutomatic(anyString(), eq(true))).thenThrow(new IllegalStateException("PLC de prueba"));

        service.turnOnZones(List.of("PB", "PATIO"), "ALARM", 42L);

        verify(commands).executeAutomatic("LIGHT_10", true);
        verify(commands).executeAutomatic("LIGHT_20", true);
        verifyNoMoreInteractions(commands);
    }

    @Test
    void emptySelectionDoesNotCommandAnyLight() {
        jdbc.update("DELETE FROM security_automatic_lighting_target");
        var commands = mock(DeviceCommandExecutionService.class);
        var areas = mock(AreaStateService.class);
        var service = new SecurityZoneLightingService(jdbc, areas, commands, runtime);

        assertThat(service.turnOnZones(List.of("PB", "PATIO"), "ALARM", 42L).successful()).isTrue();
        verifyNoInteractions(commands, areas);
    }

    @Test
    void regularAutomationExcludesArmedZonesButStatusKeepsTheFullSelection() {
        assertThat(runtime.findUnarmedTargets()).extracting(AutomaticLightingRuntimeService.LightingDevice::code)
                .containsExactly("LIGHT_30");
        assertThat(runtime.findTargets()).extracting(AutomaticLightingRuntimeService.LightingDevice::code)
                .containsExactly("LIGHT_10", "LIGHT_20", "LIGHT_30");
        assertThat(runtime.isSecurityManagedSensor("MOTION_50")).isTrue();
        jdbc.update("UPDATE security_zone_state SET mode = 'DISARMED' WHERE zone_code = 'PB'");
        assertThat(runtime.isSecurityManagedSensor("MOTION_50")).isFalse();
    }

    @Test
    void ongoingMotionAfterAcknowledgementDoesNotRelightAnArmedZone() {
        var schedule = mock(SecurityScheduleService.class);
        var commands = mock(DeviceCommandExecutionService.class);
        var service = new AutomaticLightingService(jdbc, schedule, runtime, commands,
                mock(AreaStateService.class), mock(SecurityZoneLightingService.class),
                mock(SimpMessagingTemplate.class));
        jdbc.execute("CREATE TABLE sensor_bypass_history (device_code VARCHAR, active BOOLEAN)");

        service.refreshActiveMotion("MOTION_50");

        verifyNoInteractions(commands, schedule);
    }
}
