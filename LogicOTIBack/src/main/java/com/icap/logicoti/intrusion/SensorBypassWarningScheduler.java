package com.icap.logicoti.intrusion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SensorBypassWarningScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SensorBypassWarningScheduler.class);

    private final SensorBypassService bypassService;

    public SensorBypassWarningScheduler(
            SensorBypassService bypassService
    ) {
        this.bypassService = bypassService;
    }

    @Scheduled(
            initialDelay = 10000,
            fixedDelayString = "${security.bypass.warning-poll-ms:60000}"
    )
    public void createDailyWarnings() {
        try {
            bypassService.createDailyWarnings();
        } catch (Exception exception) {
            LOGGER.warn(
                    "No se pudieron generar las advertencias de sensores omitidos: {}",
                    exception.getMessage()
            );
        }
    }
}
