package com.icap.logicoti.intrusion;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

public record SecuritySettingsResponse(
        boolean automaticScheduleEnabled,
        boolean automaticLightingEnabled,
        LocalTime automaticLightingStartTime,
        LocalTime automaticLightingEndTime,
        String timezone,
        int exitDelaySeconds,
        int lightInactivityMinutes,
        int minisplitInactivityMinutes,
        int diagnosticTimeoutSeconds,
        int diagnosticValidityMonths,
        List<ScheduleDay> days,
        List<LightingTarget> lightingTargets,
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

    public record LightingTarget(
            Long deviceId,
            String deviceCode,
            String deviceName,
            String areaCode,
            String areaName,
            String floorCode,
            String floorName,
            boolean selected
    ) {
    }
}
