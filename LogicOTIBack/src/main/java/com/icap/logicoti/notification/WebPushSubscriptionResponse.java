package com.icap.logicoti.notification;

import java.time.Instant;

public record WebPushSubscriptionResponse(
        boolean subscribed,
        String message,
        Instant timestamp
) {
}
