package com.icap.logicoti.intrusion;

import com.icap.logicoti.camera.CameraResponse;
import com.icap.logicoti.event.SensorEventHistoryResponse;

import java.time.Instant;
import java.util.List;

public record SecurityMotionAlertResponse(
        SensorEventHistoryResponse event,
        SecurityStatusResponse security,
        List<CameraResponse> cameras,
        String message,
        Instant publishedAt
) {
}
