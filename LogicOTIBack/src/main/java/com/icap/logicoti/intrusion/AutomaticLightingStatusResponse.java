package com.icap.logicoti.intrusion;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

public record AutomaticLightingStatusResponse(
        boolean enabled,
        boolean withinSchedule,
        LocalTime startTime,
        LocalTime endTime,
        int inactivityMinutes,
        int configuredLights,
        int automaticLightsOn,
        Instant lastMotionAt,
        Instant nextTurnOffAt,
        List<ControlledLight> lights,
        String message,
        Instant timestamp
) {

    public record ControlledLight(
            String deviceCode,
            String deviceName,
            String areaCode,
            String areaName,
            Instant activatedAt,
            Instant turnOffAt
    ) {
    }
}
