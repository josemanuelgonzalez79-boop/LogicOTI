package com.icap.logicoti.intrusion;

import java.time.Instant;

public record SecurityStatusResponse(
        String mode,
        boolean armed,
        boolean alarmActive,
        String message,
        String changedBy,
        String changeSource,
        Instant changedAt,
        Instant armingCompletesAt,
        boolean automaticScheduleEnabled,
        String timezone,
        int exitDelaySeconds,
        int lightInactivityMinutes,
        int minisplitInactivityMinutes,
        Instant timestamp
) {
}
