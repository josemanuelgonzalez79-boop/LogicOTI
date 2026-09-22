package com.icap.logicoti.camera;

import java.time.LocalDateTime;
import java.util.List;

interface NvrRecordingClient {

    List<NvrRecordingSegment> search(
            int trackId,
            LocalDateTime startTime,
            LocalDateTime endTime
    );
}
