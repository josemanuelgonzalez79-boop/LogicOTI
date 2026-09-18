package com.icap.logicoti.alarm;

import java.time.Instant;

public record AlarmAcknowledgementResponse(
        Long id,
        Long eventId,
        String acknowledgedBy,
        Instant acknowledgedAt
) {
}
