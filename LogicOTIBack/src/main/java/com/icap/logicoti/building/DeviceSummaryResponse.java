package com.icap.logicoti.building;

import java.time.Instant;

public record DeviceSummaryResponse(
        boolean plcEnabled,
        boolean connected,
        int totalControllable,
        Integer poweredOn,
        Integer lightsOn,
        Integer minisplitsOn,
        String message,
        Instant timestamp
) {
}
