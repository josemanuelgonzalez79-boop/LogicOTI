package com.icap.logicoti.diagnostic;

import java.time.Instant;
import java.util.List;

public record SensorDiagnosticResponse(
        long id,
        String status,
        String startedBy,
        String startedByRole,
        Instant startedAt,
        Instant expiresAt,
        Instant completedAt,
        int remainingSeconds,
        String message,
        List<Item> sensors
) {

    public record Item(
            long id,
            String deviceCode,
            String deviceName,
            String areaCode,
            String areaName,
            String deviceType,
            String plcStateTag,
            boolean initialState,
            boolean sawInactive,
            boolean sawActive,
            String status,
            Instant passedAt
    ) {
    }
}
