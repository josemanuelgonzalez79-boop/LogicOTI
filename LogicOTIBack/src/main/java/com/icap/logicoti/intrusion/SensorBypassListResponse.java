package com.icap.logicoti.intrusion;

import java.time.Instant;
import java.util.List;

public record SensorBypassListResponse(
        List<SensorBypassResponse> items,
        int total,
        Instant timestamp
) {
}
