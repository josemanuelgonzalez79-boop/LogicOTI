package com.icap.logicoti.intrusion;

import com.icap.logicoti.camera.CameraListResponse;
import com.icap.logicoti.camera.CameraService;
import com.icap.logicoti.event.SensorEventHistoryResponse;
import com.icap.logicoti.notification.WebPushSubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class IntrusionMotionAlarmService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    IntrusionMotionAlarmService.class
            );

    private final JdbcTemplate jdbcTemplate;
    private final IntrusionAlarmService alarmService;
    private final CameraService cameraService;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebPushSubscriptionService webPushSubscriptionService;

    public IntrusionMotionAlarmService(
            JdbcTemplate jdbcTemplate,
            IntrusionAlarmService alarmService,
            CameraService cameraService,
            SimpMessagingTemplate messagingTemplate,
            WebPushSubscriptionService webPushSubscriptionService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.alarmService = alarmService;
        this.cameraService = cameraService;
        this.messagingTemplate = messagingTemplate;
        this.webPushSubscriptionService = webPushSubscriptionService;
    }

    public void process(SensorEventHistoryResponse event) {
        if (!isMotionActivation(event)) {
            return;
        }

        if (isBypassed(event.deviceCode())) {
            LOGGER.info(
                    "El movimiento de {} no activó la alarma porque el sensor está omitido.",
                    event.deviceCode()
            );
            return;
        }

        SecurityStatusResponse security =
                alarmService.activateFromMotion(event);

        if (security == null) {
            return;
        }

        CameraListResponse cameras = cameraService.findAll(
                null,
                event.areaCode()
        );

        String message = cameras.items().isEmpty()
                ? "Se detectó movimiento en "
                    + event.areaName()
                    + ", pero el área no tiene una cámara asociada."
                : "Se detectó movimiento en "
                    + event.areaName()
                    + ". Se abrió la cámara relacionada.";

        SecurityMotionAlertResponse response =
                new SecurityMotionAlertResponse(
                        event,
                        security,
                        cameras.items(),
                        message,
                        Instant.now()
                );

        messagingTemplate.convertAndSend(
                "/topic/security/motion-alerts",
                response
        );

        String targetUrl = cameras.items().isEmpty()
                ? "/security"
                : "/cameras?camera="
                        + cameras.items().getFirst().code();

        webPushSubscriptionService.sendToAll(
                "Movimiento con alarma armada",
                message,
                "motion-" + event.deviceCode(),
                targetUrl,
                true
        );

        LOGGER.warn(
                "Alarma de movimiento publicada para {} con {} cámaras asociadas.",
                event.deviceCode(),
                cameras.items().size()
        );
    }

    private boolean isMotionActivation(
            SensorEventHistoryResponse event
    ) {
        return "MOTION".equalsIgnoreCase(event.deviceType())
                && "ACTIVATED".equalsIgnoreCase(event.eventType())
                && event.currentState();
    }

    private boolean isBypassed(String sensorCode) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sensor_bypass_history
                WHERE UPPER(device_code) = UPPER(?)
                  AND active = TRUE
                """,
                Integer.class,
                sensorCode
        );

        return count != null && count > 0;
    }
}
