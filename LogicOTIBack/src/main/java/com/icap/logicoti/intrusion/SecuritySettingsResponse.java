package com.icap.logicoti.intrusion;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

public record SecuritySettingsResponse(
        boolean automaticScheduleEnabled,
        String timezone,
        int exitDelaySeconds,
        int lightInactivityMinutes,
        int minisplitInactivityMinutes,
        int diagnosticTimeoutSeconds,
        int diagnosticValidityMonths,
        List<ScheduleDay> days,
        Instant updatedAt,
        String updatedBy
) {

    public record ScheduleDay(
            int dayOfWeek,
            String dayName,
            boolean enabled,
            boolean allDayArmed,
            LocalTime armTime,
            LocalTime disarmTime
    ) {
    }
}
