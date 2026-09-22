package com.icap.logicoti.camera;

import java.time.LocalDateTime;

record NvrRecordingSegment(
        LocalDateTime startTime,
        LocalDateTime endTime,
        String codecType,
        String recordingType,
        String playbackUri
) {
}
