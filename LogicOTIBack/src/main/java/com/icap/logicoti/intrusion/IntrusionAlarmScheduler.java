package com.icap.logicoti.intrusion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IntrusionAlarmScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(IntrusionAlarmScheduler.class);

    private final IntrusionAlarmService alarmService;

    public IntrusionAlarmScheduler(
            IntrusionAlarmService alarmService
    ) {
        this.alarmService = alarmService;
    }

    @Scheduled(
            initialDelay = 5000,
            fixedDelayString = "${security.alarm.schedule-poll-ms:15000}"
    )
    public void evaluateSchedule() {
        try {
            alarmService.evaluateAutomaticSchedule();
        } catch (Exception exception) {
            LOGGER.warn(
                    "No se pudo evaluar el horario de alarma: {}",
                    exception.getMessage()
            );
        }
    }

    @Scheduled(
            initialDelay = 1000,
            fixedDelayString = "${security.alarm.arming-poll-ms:1000}"
    )
    public void completeArming() {
        try {
            alarmService.completeArmingIfDue();
        } catch (Exception exception) {
            LOGGER.warn(
                    "No se pudo completar el armado: {}",
                    exception.getMessage()
            );
        }
    }
}
