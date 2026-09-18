package com.icap.logicoti.event;

import com.icap.logicoti.config.PlcProperties;
import com.icap.logicoti.diagnostic.SensorDiagnosticService;
import com.icap.logicoti.intrusion.AreaInactivityService;
import com.icap.logicoti.intrusion.IntrusionMotionAlarmService;
import com.icap.logicoti.notification.WebPushSubscriptionService;
import com.icap.logicoti.plc.PlcCommunicationService;
import com.icap.logicoti.signal.SignalQualityRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SensorEventMonitorTests {
    private final SensorEventHistoryService history = mock(SensorEventHistoryService.class);
    private final PlcCommunicationService plc = mock(PlcCommunicationService.class);
    private final SensorDiagnosticService diagnostics = mock(SensorDiagnosticService.class);
    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final IntrusionMotionAlarmService intrusion = mock(IntrusionMotionAlarmService.class);
    private final AreaInactivityService inactivity = mock(AreaInactivityService.class);
    private final WebPushSubscriptionService push = mock(WebPushSubscriptionService.class);
    private final SensorDefinition smoke = sensor(7, "SMOKE");
    private SensorEventMonitor monitor;

    @BeforeEach
    void setUp() {
        PlcProperties properties = new PlcProperties();
        properties.setEnabled(true);
        monitor = new SensorEventMonitor(history, plc, properties, messaging, intrusion,
                inactivity, push, mock(SignalQualityRegistry.class), diagnostics, true);
        when(history.saveChange(any(), nullable(Boolean.class), anyBoolean())).thenAnswer(call -> {
            SensorDefinition sensor = call.getArgument(0);
            boolean active = call.getArgument(2);
            return new SensorEventHistoryResponse(1L, sensor.code(), sensor.name(), sensor.areaCode(),
                    sensor.areaName(), sensor.type(), sensor.stateTag(), call.getArgument(1), active,
                    active ? "ACTIVATED" : "CLEARED", "INFO", "Evento", Instant.now(), false, null, null, 0);
        });
    }

    @Test
    void smokeTestRecordsBothEdgesWithoutRealAlerts() {
        monitor.processState(smoke, false, true);
        monitor.processState(smoke, true, true);
        monitor.processState(smoke, true, true);
        // El monitor de diagnóstico puede haber aprobado ya al ver el reposo.
        monitor.processState(smoke, false, false);

        verify(history).saveDiagnosticChange(smoke, false, true);
        verify(history).saveDiagnosticChange(smoke, true, false);
        verify(history, never()).saveChange(any(), nullable(Boolean.class), anyBoolean());
        verifyNoInteractions(messaging, push, intrusion, inactivity);
    }

    @Test
    void otherSmokeSensorsContinueToAlarmDuringASelectedTest() {
        monitor.processState(smoke, true, true);
        SensorDefinition other = sensor(8, "SMOKE");
        monitor.processState(other, true, false);

        verify(history).saveChange(other, null, true);
        verify(push).sendToAll(eq("Alarma de humo"), anyString(), anyString(), eq("/alarms"), eq(true));
    }

    @Test
    void stillActiveSensorBecomesARealAlarmAfterTestEnds() {
        monitor.processState(smoke, true, true);
        // Cancelación o vencimiento: la señal sigue TRUE sin nuevo flanco.
        monitor.processState(smoke, true, false);
        monitor.processState(smoke, true, false);

        verify(history, times(1)).saveChange(smoke, null, true);
        verify(push, times(1)).sendToAll(anyString(), anyString(), anyString(), anyString(), eq(true));
    }

    @Test
    void nextActivationAfterPassedTestIsARealAlarm() {
        monitor.processState(smoke, true, true);
        monitor.processState(smoke, false, false);
        monitor.processState(smoke, true, false);

        verify(history).saveChange(smoke, false, true);
        verify(push).sendToAll(anyString(), anyString(), anyString(), anyString(), eq(true));
    }

    @Test
    void motionTestDoesNotTriggerIntrusionOrRefreshAutomation() {
        SensorDefinition motion = sensor(8, "MOTION");
        monitor.processState(motion, false, true);
        monitor.processState(motion, true, true);
        monitor.processState(motion, true, true);
        monitor.processState(motion, false, false);

        verifyNoInteractions(intrusion, inactivity, push, messaging);
    }

    @Test
    void previousRealAlarmStillPublishesItsRestoration() {
        monitor.processState(smoke, true, false);
        clearInvocations(messaging, push);
        monitor.processState(smoke, false, true);

        verify(history).saveChange(smoke, true, false);
        verify(messaging).convertAndSend(eq("/topic/alerts/smoke"), any(SensorEventHistoryResponse.class));
        verifyNoInteractions(push);
    }

    @Test
    void restartDoesNotHideAnActiveSensorFromAnExpiredTest() {
        when(history.findActiveSensors()).thenReturn(List.of(smoke));
        when(history.findLatestStates()).thenReturn(Map.of(smoke.id(), true));
        when(history.findLatestDiagnosticDeviceIds()).thenReturn(Set.of(smoke.id()));
        when(plc.read(any())).thenReturn(Map.of(smoke.id(), true));
        when(diagnostics.findTestingSensorIds(any())).thenReturn(Set.of());

        monitor.monitorSensors();

        verify(history).saveChange(smoke, null, true);
        verify(push).sendToAll(anyString(), anyString(), anyString(), anyString(), eq(true));
    }

    @Test
    void diagnosticLookupFailureKeepsNormalAlarmSurveillance() {
        when(history.findActiveSensors()).thenReturn(List.of(smoke));
        when(plc.read(any())).thenReturn(Map.of(smoke.id(), true));
        when(diagnostics.findTestingSensorIds(any())).thenThrow(new IllegalStateException("database"));

        monitor.monitorSensors();

        verify(history).saveChange(smoke, null, true);
        verify(push).sendToAll(anyString(), anyString(), anyString(), anyString(), eq(true));
    }

    private SensorDefinition sensor(long id, String type) {
        return new SensorDefinition(id, "SENSOR_" + id, "Sensor " + id, "PB_A01", "Recepción", type,
                "OTI_SENSOR_" + id);
    }
}
