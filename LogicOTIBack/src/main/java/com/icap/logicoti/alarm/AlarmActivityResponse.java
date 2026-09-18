package com.icap.logicoti.alarm;

import java.time.Instant;
import java.util.List;

public record AlarmActivityResponse(
        Long eventId,
        AlarmAcknowledgementResponse acknowledgement,
        List<AlarmCommentResponse> comments,
        long commentCount,
        Instant timestamp
) {
}
