package com.icap.logicoti.signal;

import java.time.Instant;

public record SignalQualitySnapshot(
        SignalQuality quality,
        Boolean value,
        Instant lastUpdatedAt,
        String detail
) {
}
