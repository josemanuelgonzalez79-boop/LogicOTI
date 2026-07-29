package com.icap.logicoti.audit;

import java.time.Instant;

public record DeviceCommandHistoryResponse(
        Long id,
        String deviceCode,
        String deviceName,
        String areaCode,
        String areaName,
        String plcCommandTag,
        boolean requestedValue,
        Boolean commandValue,
        Boolean feedbackValue,
        String status,
        String message,
        String requestedBy,
        String requestedByRole,
        String sourceIp,
        Long durationMs,
        Instant requestedAt,
        Instant completedAt
) {
}