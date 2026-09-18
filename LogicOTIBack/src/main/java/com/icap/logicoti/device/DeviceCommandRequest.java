package com.icap.logicoti.device;

import jakarta.validation.constraints.NotNull;

public record DeviceCommandRequest(

    @NotNull(message = "El campo on es obligatorio.")
    Boolean on

) {
}