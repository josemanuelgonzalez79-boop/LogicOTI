package com.icap.logicoti.intrusion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SensorBypassRequest(
        @NotBlank(message = "El código del sensor es obligatorio.")
        String sensorCode,

        @NotBlank(message = "Explique por qué se omitirá el sensor.")
        @Size(max = 300, message = "El motivo no puede exceder 300 caracteres.")
        String reason
) {
}
