package com.icap.logicoti.camera;

import java.time.Instant;

public record CameraAlertViewResponse(
        String cameraCode,
        String cameraName,
        String floorCode,
        String areaCode,
        String viewUrl,
        Instant expiresAt,
        Instant timestamp
) {
}
