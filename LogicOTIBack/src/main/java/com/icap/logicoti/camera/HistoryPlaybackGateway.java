package com.icap.logicoti.camera;

interface HistoryPlaybackGateway {

    HistoryPlaybackSession open(
            String cameraCode,
            int trackId,
            String playbackUri
    );

    void removeExpired();
}
