package com.icap.logicoti.auth;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorVerifyRequest(
        @NotBlank(message = "El desafío de autenticación es obligatorio.")
        String challengeToken,

        @NotBlank(message = "El código de verificación es obligatorio.")
        String code
) {
}
