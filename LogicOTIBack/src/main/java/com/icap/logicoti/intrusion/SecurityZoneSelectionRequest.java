package com.icap.logicoti.intrusion;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SecurityZoneSelectionRequest(
        @NotEmpty(message = "Selecciona al menos una zona.")
        @Size(max = 4, message = "No se pueden seleccionar más de cuatro zonas.")
        List<
                @Pattern(
                        regexp = "[A-Za-z0-9_-]{1,20}",
                        message = "El código de zona no es válido."
                ) String
                > zoneCodes
) {
}
