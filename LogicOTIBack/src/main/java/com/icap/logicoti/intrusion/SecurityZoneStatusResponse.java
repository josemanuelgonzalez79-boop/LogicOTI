package com.icap.logicoti.intrusion;

import java.time.Instant;

public record SecurityZoneStatusResponse(
        String code,
        String name,
        int displayOrder,
        boolean motionDetectionEnabled,
        int motionSensorCount,
        int lightCircuitCount,
        int availableLightCircuitCount,
        int controlledLightCount,
        String mode,
        boolean armed,
        boolean alarmActive,
        String message,
        String changedBy,
        String changeSource,
        Instant changedAt,
        Instant armingCompletesAt,
        Long alarmEventId
) {
}
