package com.icap.logicoti.device;

import com.icap.logicoti.audit.DeviceCommandHistoryService;
import com.icap.logicoti.device.AreaStateResponse.DeviceStateResponse;
import com.icap.logicoti.exception.ConflictException;
import com.icap.logicoti.intrusion.AutomaticLightingRuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class DeviceCommandExecutionService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    DeviceCommandExecutionService.class
            );

    private final AreaStateService areaStateService;
    private final DeviceCommandHistoryService historyService;
    private final AutomaticLightingRuntimeService automaticLightingRuntimeService;

    public DeviceCommandExecutionService(
            AreaStateService areaStateService,
            DeviceCommandHistoryService historyService,
            AutomaticLightingRuntimeService automaticLightingRuntimeService
    ) {
        this.areaStateService = areaStateService;
        this.historyService = historyService;
        this.automaticLightingRuntimeService =
                automaticLightingRuntimeService;
    }

    public AreaStateResponse execute(
            String deviceCode,
            boolean requestedValue,
            String requestedBy,
            String requestedByRole,
            String sourceIp
    ) {
        return executeInternal(
                deviceCode,
                requestedValue,
                requestedBy,
                requestedByRole,
                sourceIp,
                true
        );
    }

    public AreaStateResponse executeAutomatic(
            String deviceCode,
            boolean requestedValue
    ) {
        return executeInternal(
                deviceCode,
                requestedValue,
                "SYSTEM",
                "AUTOMATION",
                "INTERNAL",
                false
        );
    }

    private AreaStateResponse executeInternal(
            String deviceCode,
            boolean requestedValue,
            String requestedBy,
            String requestedByRole,
            String sourceIp,
            boolean releaseAutomaticOwnership
    ) {
        long startedAt = System.nanoTime();

        long historyId = historyService.start(
                deviceCode,
                requestedValue,
                requestedBy,
                requestedByRole,
                sourceIp
        );

        try {
            AreaStateResponse response =
                    areaStateService.commandDevice(
                            deviceCode,
                            requestedValue
                    );

            DeviceStateResponse deviceState = response.devices()
                    .stream()
                    .filter(device ->
                            device.code().equalsIgnoreCase(deviceCode)
                    )
                    .findFirst()
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "No se encontró el estado de "
                                            + deviceCode
                            )
                    );

            boolean confirmed =
                    Objects.equals(
                            deviceState.command(),
                            requestedValue
                    )
                    && Objects.equals(
                            deviceState.state(),
                            requestedValue
                    );

            String status = confirmed
                    ? "CONFIRMED"
                    : "NOT_CONFIRMED";

            String message = confirmed
                    ? "El PLC confirmó el comando."
                    : "El comando fue escrito, pero el estado de retorno no coincide.";

            completeHistorySafely(
                    historyId,
                    deviceState.command(),
                    deviceState.state(),
                    status,
                    message,
                    elapsedMilliseconds(startedAt)
            );

            if (releaseAutomaticOwnership) {
                releaseAutomaticControlSafely(deviceCode);
            }

            return response;

        } catch (ConflictException exception) {
            completeHistorySafely(
                    historyId,
                    null,
                    null,
                    "REJECTED",
                    exception.getMessage(),
                    elapsedMilliseconds(startedAt)
            );

            throw exception;

        } catch (RuntimeException exception) {
            completeHistorySafely(
                    historyId,
                    null,
                    null,
                    "FAILED",
                    exception.getMessage(),
                    elapsedMilliseconds(startedAt)
            );

            throw exception;
        }
    }

    private void releaseAutomaticControlSafely(String deviceCode) {
        try {
            automaticLightingRuntimeService.release(deviceCode);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "No se pudo liberar el control automático de {}.",
                    deviceCode,
                    exception
            );
        }
    }

    private long elapsedMilliseconds(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt
        );
    }

    private void completeHistorySafely(
            long historyId,
            Boolean commandValue,
            Boolean feedbackValue,
            String status,
            String message,
            long durationMs
    ) {
        try {
            historyService.complete(
                    historyId,
                    commandValue,
                    feedbackValue,
                    status,
                    message,
                    durationMs
            );
        } catch (RuntimeException auditException) {

            LOGGER.error(
                    "No se pudo actualizar el histórico {}.",
                    historyId,
                    auditException
            );
        }
    }
}
