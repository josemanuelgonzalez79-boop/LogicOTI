package com.icap.logicoti.event;

import java.time.Instant;

public record SensorEventHistoryResponse(
        Long id,
        String deviceCode,
        String deviceName,
        String areaCode,
        String areaName,
        String deviceType,
        String plcStateTag,
        Boolean previousState,
        boolean currentState,
        String eventType,
        String severity,
        String message,
        Instant detectedAt
) {
}