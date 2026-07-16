package com.icap.logicoti.system;

import java.time.Instant;

public record SystemStatusResponse(
        String application,
        String status,
        String database,
        boolean plcEnabled,
        Instant timestamp
) {
}
