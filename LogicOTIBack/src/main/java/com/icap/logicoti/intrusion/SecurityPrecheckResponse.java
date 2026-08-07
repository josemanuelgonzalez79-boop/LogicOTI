package com.icap.logicoti.intrusion;

import java.time.Instant;
import java.util.List;

public record SecurityPrecheckResponse(
        boolean ready,
        boolean plcEnabled,
        boolean plcConnected,
        int totalMotionSensors,
        int readableMotionSensors,
        List<Issue> issues,
        Instant timestamp
) {

    public record Issue(
            String code,
            String severity,
            boolean blocking,
            String sensorCode,
            String sensorName,
            String areaCode,
            String areaName,
            String message
    ) {
    }
}
