package com.icap.logicoti.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WebPushSubscriptionRequest(
        @NotBlank String endpoint,
        @NotNull @Valid Keys keys,
        String userAgent
) {
    public record Keys(
            @NotBlank String p256dh,
            @NotBlank String auth
    ) {
    }
}
