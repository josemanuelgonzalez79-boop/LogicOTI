package com.icap.logicoti.event;

import java.time.Instant;

public record SensorEventHistoryQuery(
        Criteria criteria,
        Page page
) {

    public record Criteria(
            String areaCode,
            String deviceCode,
            String deviceType,
            String eventType,
            String severity,
            Instant from,
            Instant to
    ) {
    }

    public record Page(
            int limit,
            int offset
    ) {
    }
}