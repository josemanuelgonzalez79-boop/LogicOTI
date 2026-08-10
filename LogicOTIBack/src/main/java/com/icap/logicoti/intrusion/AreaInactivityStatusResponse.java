package com.icap.logicoti.intrusion;

import java.time.Instant;
import java.util.List;

public record AreaInactivityStatusResponse(
        boolean enabled,
        int lightInactivityMinutes,
        int minisplitInactivityMinutes,
        int trackedAreas,
        int pendingAreas,
        int lightsTurnedOff,
        int minisplitsTurnedOff,
        Instant lastMotionAt,
        Instant nextActionAt,
        List<AreaStatus> areas,
        String message,
        Instant timestamp
) {

    public record AreaStatus(
            String areaCode,
            String areaName,
            String floorCode,
            String floorName,
            Instant lastMotionAt,
            Instant lightTurnOffAt,
            Instant minisplitTurnOffAt,
            boolean lightProcessed,
            boolean minisplitProcessed,
            int lightsTurnedOff,
            int minisplitsTurnedOff
    ) {
    }
}
