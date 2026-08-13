package com.icap.logicoti.notification;

public record WebPushDeliveryTarget(
        Long deliveryId,
        Long subscriptionId,
        Long userId,
        String endpoint,
        String p256dh,
        String auth,
        String title,
        String body,
        String tag,
        String targetUrl,
        boolean requireInteraction,
        int attemptCount
) {
}
