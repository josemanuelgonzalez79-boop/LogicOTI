package com.icap.logicoti.auth;

public record TwoFactorStatusResponse(
        boolean enabled,
        int unusedRecoveryCodes
) {
}
