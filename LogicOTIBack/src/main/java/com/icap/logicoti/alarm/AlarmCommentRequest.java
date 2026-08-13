package com.icap.logicoti.alarm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlarmCommentRequest(
        @NotBlank(message = "El comentario es obligatorio.")
        @Size(
                max = 500,
                message = "El comentario no puede exceder 500 caracteres."
        )
        String comment
) {
}
