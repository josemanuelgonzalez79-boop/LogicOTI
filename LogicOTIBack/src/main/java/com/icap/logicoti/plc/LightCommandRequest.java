package com.icap.logicoti.plc;

import jakarta.validation.constraints.NotNull;

public record LightCommandRequest(

        @NotNull(message = "El campo on es obligatorio.")
        Boolean on

) {
}