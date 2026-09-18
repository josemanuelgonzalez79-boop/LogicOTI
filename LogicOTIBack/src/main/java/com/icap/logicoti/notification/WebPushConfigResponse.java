package com.icap.logicoti.notification;

public record WebPushConfigResponse(
        boolean supported,
        boolean enabled,
        String publicKey,
        boolean subscribed,
        long subscriptionCount,
        String message
) {
}
