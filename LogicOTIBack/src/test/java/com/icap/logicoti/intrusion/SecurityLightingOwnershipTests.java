package com.icap.logicoti.intrusion;

import com.icap.logicoti.device.AreaStateResponse;
import com.icap.logicoti.device.AreaStateService;
import com.icap.logicoti.device.DeviceCommandExecutionService;
import com.icap.logicoti.signal.SignalQuality;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityLightingOwnershipTests {

    @Test
    void automaticLightingDoesNotTurnOffASecurityOwnedLight() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SecurityScheduleService scheduleService = mock(
                SecurityScheduleService.class
        );
        AutomaticLightingRuntimeService runtimeService = mock(
                AutomaticLightingRuntimeService.class
        );
        DeviceCommandExecutionService commandService = mock(
                DeviceCommandExecutionService.class
        );
        AreaStateService areaStateService = mock(AreaStateService.class);
        SecurityZoneLightingService zoneLightingService = mock(
                SecurityZoneLightingService.class
        );
        SimpMessagingTemplate messagingTemplate = mock(
                SimpMessagingTemplate.class
        );
        AutomaticLightingService service = new AutomaticLightingService(
                jdbcTemplate,
                scheduleService,
                runtimeService,
                commandService,
                areaStateService,
                zoneLightingService,
                messagingTemplate
        );
        AutomaticLightingRuntimeService.RuntimeLight light =
                new AutomaticLightingRuntimeService.RuntimeLight(
                        10L,
                        "PB_A01_LUZ01",
                        "Iluminación general",
                        "PB_A01",
                        "Recepción",
                        Instant.now().minusSeconds(120),
                        Instant.now().minusSeconds(120),
                        Instant.now().minusSeconds(60)
                );

        when(runtimeService.findExpiredLights(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(light));
        when(runtimeService.findTargets()).thenReturn(List.of());
        when(runtimeService.findOwnedLights()).thenReturn(List.of());
        when(scheduleService.getSettings()).thenReturn(settings(false));
        when(zoneLightingService.isOwned(light.code())).thenReturn(true);

        service.turnOffExpiredLights();

        verify(runtimeService).release(light.code());
        verify(commandService, never()).executeAutomatic(
                light.code(),
                false
        );
    }

    @Test
    void areaInactivityDoesNotTurnOffASecurityOwnedLight() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SecurityScheduleService scheduleService = mock(
                SecurityScheduleService.class
        );
        AreaInactivityRuntimeService runtimeService = mock(
                AreaInactivityRuntimeService.class
        );
        DeviceCommandExecutionService commandService = mock(
                DeviceCommandExecutionService.class
        );
        AreaStateService areaStateService = mock(AreaStateService.class);
        SecurityZoneLightingService zoneLightingService = mock(
                SecurityZoneLightingService.class
        );
        SimpMessagingTemplate messagingTemplate = mock(
                SimpMessagingTemplate.class
        );
        AreaInactivityService service = new AreaInactivityService(
                jdbcTemplate,
                scheduleService,
                runtimeService,
                commandService,
                areaStateService,
                zoneLightingService,
                messagingTemplate
        );
        Instant now = Instant.now();
        AreaInactivityRuntimeService.RuntimeArea area =
                new AreaInactivityRuntimeService.RuntimeArea(
                        1L,
                        "PB_A01",
                        "Recepción",
                        "PB",
                        "Planta Baja",
                        now.minusSeconds(120),
                        now.minusSeconds(60),
                        now.minusSeconds(60),
                        false,
                        true,
                        0,
                        0
                );
        AreaStateResponse.DeviceStateResponse light =
                new AreaStateResponse.DeviceStateResponse(
                        10L,
                        "PB_A01_LUZ01",
                        "Iluminación general",
                        "LIGHT",
                        1,
                        true,
                        true,
                        true,
                        false,
                        SignalQuality.GOOD,
                        now,
                        "Señal disponible."
                );

        when(scheduleService.getSettings()).thenReturn(settings(true));
        when(runtimeService.findDueAreas(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(area));
        when(runtimeService.findAll()).thenReturn(List.of());
        when(areaStateService.getAreaState(area.code())).thenReturn(
                new AreaStateResponse(
                        area.code(),
                        area.name(),
                        true,
                        true,
                        List.of(light),
                        "Área disponible.",
                        now
                )
        );
        when(zoneLightingService.isOwned(light.code())).thenReturn(true);

        service.processExpiredAreas();

        verify(runtimeService).markLightProcessed(area.id(), 0);
        verify(commandService, never()).executeAutomatic(
                light.code(),
                false
        );
    }

    private SecuritySettingsResponse settings(
            boolean areaInactivityEnabled
    ) {
        return new SecuritySettingsResponse(
                true,
                true,
                LocalTime.of(18, 0),
                LocalTime.of(8, 0),
                areaInactivityEnabled,
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
