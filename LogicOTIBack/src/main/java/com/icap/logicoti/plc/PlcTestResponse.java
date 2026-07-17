package com.icap.logicoti.plc;

import java.time.Instant;

public record PlcTestResponse(
        boolean enabled,
        boolean connected,
        Boolean lightCommand,
        Boolean lightFeedback,
        Boolean motion,
        Boolean smoke,
        String message,
        Instant timestamp
) {
}

