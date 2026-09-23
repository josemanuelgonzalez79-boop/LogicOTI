package com.icap.logicoti.camera;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record CameraRecordingSearchResponse(
        String cameraCode,
        String cameraName,
        int channelNumber,
        LocalDateTime requestedStartTime,
        LocalDateTime requestedEndTime,
        List<CameraRecordingSegmentResponse> items,
        int total,
        List<CameraRecordingSegmentResponse> motionItems,
        String motionStatus,
        boolean playbackConfigured,
        Instant timestamp
) {
}
