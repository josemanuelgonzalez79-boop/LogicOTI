package com.icap.logicoti.diagnostic;

import java.time.Instant;
import java.util.List;

public record SensorDiagnosticListResponse(
        List<SensorDiagnosticResponse> items,
        int total,
        Instant timestamp
) {
}
