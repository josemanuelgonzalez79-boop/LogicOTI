package com.icap.logicoti.camera;

import java.time.Instant;
import java.util.List;

public record CameraListResponse(
        List<CameraResponse> items,
        int total,
        boolean playbackConfigured,
        Instant timestamp
) {
}
