package com.icap.logicoti.intrusion;

import java.time.Instant;
import java.util.List;

public record SecurityWarningListResponse(
        List<SecurityWarningResponse> items,
        int total,
        Instant timestamp
) {
}
