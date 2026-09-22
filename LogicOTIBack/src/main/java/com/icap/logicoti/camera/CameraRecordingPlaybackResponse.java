package com.icap.logicoti.camera;

import java.time.Instant;

public record CameraRecordingPlaybackResponse(
        String cameraCode,
        String cameraName,
        String viewUrl,
        Instant expiresAt,
        Instant timestamp
) {
}
