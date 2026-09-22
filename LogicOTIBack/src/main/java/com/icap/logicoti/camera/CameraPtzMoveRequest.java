package com.icap.logicoti.camera;

import jakarta.validation.constraints.NotNull;

public record CameraPtzMoveRequest(
        @NotNull(message = "Selecciona una dirección PTZ.")
        CameraPtzDirection direction
) {
}
