package com.icap.logicoti.intrusion;

import java.time.Instant;
import java.time.LocalDate;

public record SecurityWarningResponse(
        long id,
        long bypassId,
        String type,
        String sensorCode,
        String sensorName,
        String areaCode,
        String areaName,
        String reason,
        String message,
        LocalDate warningDate,
        Instant createdAt
) {
}
