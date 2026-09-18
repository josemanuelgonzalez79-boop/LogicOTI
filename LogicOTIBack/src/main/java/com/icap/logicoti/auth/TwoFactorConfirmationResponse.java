package com.icap.logicoti.auth;

import java.util.List;

public record TwoFactorConfirmationResponse(
        boolean enabled,
        List<String> recoveryCodes
) {
}
