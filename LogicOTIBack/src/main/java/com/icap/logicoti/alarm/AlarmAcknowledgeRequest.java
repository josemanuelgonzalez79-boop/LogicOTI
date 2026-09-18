package com.icap.logicoti.alarm;

import jakarta.validation.constraints.Size;

public record AlarmAcknowledgeRequest(
        @Size(
                max = 500,
                message = "El comentario no puede exceder 500 caracteres."
        )
        String comment
) {
}
