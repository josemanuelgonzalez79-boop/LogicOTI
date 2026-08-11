package com.icap.logicoti.notification;

import jakarta.validation.constraints.NotBlank;

public record WebPushUnsubscribeRequest(
        @NotBlank String endpoint
) {
}
