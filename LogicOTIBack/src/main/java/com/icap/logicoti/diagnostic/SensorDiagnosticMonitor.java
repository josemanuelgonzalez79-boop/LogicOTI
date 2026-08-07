package com.icap.logicoti.diagnostic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SensorDiagnosticMonitor {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SensorDiagnosticMonitor.class);

    private final SensorDiagnosticService diagnosticService;

    public SensorDiagnosticMonitor(
            SensorDiagnosticService diagnosticService
    ) {
        this.diagnosticService = diagnosticService;
    }

    @Scheduled(
            initialDelay = 3000,
            fixedDelayString = "${security.diagnostic.poll-ms:1000}"
    )
    public void monitor() {
        Set<Long> sessionsToPublish = new LinkedHashSet<>();

        try {
            List<SensorDiagnosticService.RunningItem> items =
                    diagnosticService.findRunningItems();

            if (!items.isEmpty()) {
                Map<Long, Boolean> states =
                        diagnosticService.readRunningStates(items);

                sessionsToPublish.addAll(
                        diagnosticService.recordStates(items, states)
                );
            }
        } catch (Exception exception) {
            LOGGER.warn(
                    "No se pudieron leer los sensores en diagnóstico: {}",
                    safeMessage(exception)
            );
        }

        try {
            sessionsToPublish.addAll(
                    diagnosticService.rejectExpired()
            );

            diagnosticService.publishSessions(sessionsToPublish);
        } catch (Exception exception) {
            LOGGER.warn(
                    "No se pudieron cerrar los diagnósticos vencidos: {}",
                    safeMessage(exception)
            );
        }
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
