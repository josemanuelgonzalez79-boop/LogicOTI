package com.icap.logicoti.auth;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorDisableRequest(
        @NotBlank(message = "La contraseña actual es obligatoria.")
        String currentPassword,

        @NotBlank(message = "El código de verificación es obligatorio.")
        String code
) {
}
