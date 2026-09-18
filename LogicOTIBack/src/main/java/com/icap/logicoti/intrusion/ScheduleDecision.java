package com.icap.logicoti.intrusion;

import java.time.Instant;

record ScheduleDecision(
        boolean automaticEnabled,
        boolean shouldBeArmed,
        String transitionKey,
        Instant evaluatedAt
) {
}
