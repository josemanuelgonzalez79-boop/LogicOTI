package com.icap.logicoti.auth;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorCodeRequest(
        @NotBlank(message = "El código de verificación es obligatorio.")
        String code
) {
}
