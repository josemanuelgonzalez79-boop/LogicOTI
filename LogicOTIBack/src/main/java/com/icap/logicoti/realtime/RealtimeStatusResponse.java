package com.icap.logicoti.realtime;

import java.time.Instant;
import java.util.Set;

public record RealtimeStatusResponse(
        int sessions,
        int subscriptions,
        Set<String> activeAreas,
        Instant timestamp
) {
}