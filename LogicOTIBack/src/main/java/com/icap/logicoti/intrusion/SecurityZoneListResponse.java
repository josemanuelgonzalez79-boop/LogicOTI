package com.icap.logicoti.intrusion;

import java.time.Instant;
import java.util.List;

public record SecurityZoneListResponse(
        String aggregateMode,
        String message,
        int totalZones,
        int armedZones,
        int alarmZones,
        List<SecurityZoneStatusResponse> zones,
        Instant timestamp
) {
}
