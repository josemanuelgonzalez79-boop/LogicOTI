package com.icap.logicoti.notification;

import java.time.Instant;

public record WebPushTestResponse(
        int attempted,
        int accepted,
        int failed,
        String message,
        Instant timestamp
) {
}
