package com.icap.logicoti.intrusion;

import java.time.Instant;

public record SensorBypassResponse(
        long id,
        String sensorCode,
        String sensorName,
        String areaCode,
        String areaName,
        String reason,
        boolean active,
        String createdBy,
        Instant createdAt,
        String revokedBy,
        Instant revokedAt
) {
}
