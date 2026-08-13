package com.icap.logicoti.notification;

import java.time.Instant;
import java.util.List;

public record WebPushDeliveryPageResponse(
        List<WebPushDeliveryResponse> items,
        long total,
        Instant timestamp
) {
}
