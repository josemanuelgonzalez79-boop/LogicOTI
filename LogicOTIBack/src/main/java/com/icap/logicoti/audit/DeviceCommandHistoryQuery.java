package com.icap.logicoti.audit;

import java.time.Instant;

public record DeviceCommandHistoryQuery(
        String areaCode,
        String deviceCode,
        String status,
        String requestedBy,
        Instant from,
        Instant to,
        int limit,
        int offset
) {
}