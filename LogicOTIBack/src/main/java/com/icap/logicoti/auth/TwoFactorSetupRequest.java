package com.icap.logicoti.auth;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorSetupRequest(
        @NotBlank(message = "La contraseña actual es obligatoria.")
        String currentPassword
) {
}
