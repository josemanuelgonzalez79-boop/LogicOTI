package com.icap.logicoti.diagnostic;

import java.time.Instant;
import java.util.List;

public record SensorDiagnosticDueResponse(
        int validityMonths,
        int totalSensors,
        int dueSensors,
        List<Sensor> sensors,
        Instant timestamp
) {

    public record Sensor(
            String deviceCode,
            String deviceName,
            String areaCode,
            String areaName,
            String deviceType,
            String status,
            Instant lastPassedAt,
            Instant validUntil
    ) {
    }
}
