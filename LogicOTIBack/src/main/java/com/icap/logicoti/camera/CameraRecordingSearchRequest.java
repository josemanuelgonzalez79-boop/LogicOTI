package com.icap.logicoti.camera;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CameraRecordingSearchRequest(
        @NotNull(message = "La fecha inicial es obligatoria.")
        LocalDateTime startTime,

        @NotNull(message = "La fecha final es obligatoria.")
        LocalDateTime endTime
) {
}
