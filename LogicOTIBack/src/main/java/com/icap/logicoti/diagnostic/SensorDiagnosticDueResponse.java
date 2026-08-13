package com.icap.logicoti.diagnostic;

import com.icap.logicoti.signal.SignalQuality;

import java.time.Instant;
import java.util.List;

public record SensorDiagnosticDueResponse(
        int validityMonths,
        int totalSensors,
        int dueSensors,
        int goodSignals,
        int badSignals,
        int staleSignals,
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
            Instant validUntil,
            SignalQuality quality,
            Instant lastUpdatedAt,
            String qualityDetail
    ) {
    }
}
