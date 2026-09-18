package com.icap.logicoti.auth;

import java.time.Instant;

public record TwoFactorSetupResponse(
        String manualKey,
        String qrCodeDataUrl,
        String accountName,
        String issuer,
        Instant expiresAt
) {
}
