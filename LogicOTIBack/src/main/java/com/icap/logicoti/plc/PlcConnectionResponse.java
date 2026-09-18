package com.icap.logicoti.plc;

import java.time.Instant;

public record PlcConnectionResponse(
        boolean enabled,
        boolean connected,
        String message,
        Instant timestamp
) {
}