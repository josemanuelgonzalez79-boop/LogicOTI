package com.icap.logicoti.alarm;

import java.time.Instant;

public record AlarmCommentResponse(
        Long id,
        Long eventId,
        String comment,
        String createdBy,
        Instant createdAt
) {
}
