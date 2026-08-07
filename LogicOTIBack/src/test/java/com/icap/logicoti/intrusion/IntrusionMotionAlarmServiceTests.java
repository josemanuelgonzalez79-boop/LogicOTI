package com.icap.logicoti.intrusion;

import com.icap.logicoti.camera.CameraListResponse;
import com.icap.logicoti.camera.CameraResponse;
import com.icap.logicoti.camera.CameraService;
import com.icap.logicoti.event.SensorEventHistoryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntrusionMotionAlarmServiceTests {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private IntrusionAlarmService alarmService;

    @Mock
    private CameraService cameraService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private IntrusionMotionAlarmService service;

    @Test
    void publishesTheRelatedCameraWhenAnIncludedMotionSensorActivates() {
        SensorEventHistoryResponse event = motionEvent();
        SecurityStatusResponse security = alarmStatus();
        CameraResponse camera = new CameraResponse(
                8L,
                "CAM-008",
                8,
                "Recepción",
                "PB",
                "PB_A01",
                "NVR-6",
                "oti-cam-08",
                true,
                true,
                "http://video.local:8889/oti-cam-08/"
        );

        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Integer.class),
                eq("PB_A01_MOV01")
        )).thenReturn(0);
        when(alarmService.activateFromMotion(event))
                .thenReturn(security);
        when(cameraService.findAll(null, "PB_A01"))
                .thenReturn(new CameraListResponse(
                        List.of(camera),
                        1,
                        true,
                        Instant.now()
                ));

        service.process(event);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/security/motion-alerts"),
                any(SecurityMotionAlertResponse.class)
        );
    }

    @Test
    void doesNotActivateTheAlarmWhenTheMotionSensorIsBypassed() {
        SensorEventHistoryResponse event = motionEvent();

        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Integer.class),
                eq("PB_A01_MOV01")
        )).thenReturn(1);

        service.process(event);

        verify(alarmService, never()).activateFromMotion(any());
        verify(messagingTemplate, never()).convertAndSend(
                anyString(),
                any(Object.class)
        );
    }

    @Test
    void ignoresMotionRestorationEvents() {
        SensorEventHistoryResponse cleared =
                new SensorEventHistoryResponse(
                        32L,
                        "PB_A01_MOV01",
                        "Sensor de movimiento 1",
                        "PB_A01",
                        "Recepción",
                        "MOTION",
                        "OTI_PB_A01_MOV01_ST",
                        true,
                        false,
                        "CLEARED",
                        "INFO",
                        "Movimiento finalizado en Recepción.",
                        Instant.now()
                );

        service.process(cleared);

        verify(alarmService, never()).activateFromMotion(any());
        verify(messagingTemplate, never()).convertAndSend(
                anyString(),
                any(Object.class)
        );
    }

    private SensorEventHistoryResponse motionEvent() {
        return new SensorEventHistoryResponse(
                31L,
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
                Instant.now()
        );
    }

    private SecurityStatusResponse alarmStatus() {
        return new SecurityStatusResponse(
                "ALARM",
                true,
                true,
                "Movimiento detectado por Sensor de movimiento 1 en Recepción.",
                "SYSTEM",
                "SYSTEM",
                Instant.now(),
                null,
                true,
                "America/Mazatlan",
                60,
                10,
                30,
                Instant.now()
        );
    }
}
