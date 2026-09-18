package com.icap.logicoti.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(

        @NotBlank
        @Size(max = 50)
        String username,

        @Size(min = 8, max = 100)
        String password,

        @NotBlank
        @Size(max = 150)
        String fullName,

        @NotBlank
        @Size(max = 30)
        @Pattern(
                regexp = "(?i)ADMIN|OPERATOR|MONITORING",
                message = "El rol debe ser ADMIN, OPERATOR o MONITORING"
        )
        String role,

        @NotNull
        Boolean active

) {
}
