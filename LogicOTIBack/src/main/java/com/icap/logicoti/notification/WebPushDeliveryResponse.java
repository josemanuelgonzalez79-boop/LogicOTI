package com.icap.logicoti.notification;

import java.time.Instant;

public record WebPushDeliveryResponse(
        Long id,
        String batchId,
        Long subscriptionId,
        Long userId,
        String title,
        String tag,
        String status,
        int attemptCount,
        Integer lastHttpStatus,
        String lastError,
        Instant nextAttemptAt,
        Instant createdAt,
        Instant lastAttemptAt,
        Instant acceptedAt,
        Instant completedAt
) {
}
