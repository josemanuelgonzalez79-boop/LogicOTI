package com.icap.logicoti.camera;

import java.time.LocalDateTime;

interface HistoryPlaybackGateway {

    HistoryPlaybackSession open(
            String cameraCode,
            int trackId,
            String playbackUri,
            LocalDateTime startTime
    );

    void removeExpired();
}
