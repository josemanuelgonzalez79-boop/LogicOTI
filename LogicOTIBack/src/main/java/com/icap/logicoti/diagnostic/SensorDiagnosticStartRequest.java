package com.icap.logicoti.diagnostic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SensorDiagnosticStartRequest(
        @NotEmpty(message = "Seleccione al menos un sensor.")
        @Size(max = 100, message = "No puede diagnosticar más de 100 sensores a la vez.")
        List<@NotBlank(message = "El código del sensor es obligatorio.") String> sensorCodes
) {
}
