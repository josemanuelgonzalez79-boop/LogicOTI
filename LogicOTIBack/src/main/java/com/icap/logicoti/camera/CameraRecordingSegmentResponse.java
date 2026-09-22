package com.icap.logicoti.camera;

import java.time.LocalDateTime;

public record CameraRecordingSegmentResponse(
        int sequence,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String codecType,
        String recordingType
) {
}
