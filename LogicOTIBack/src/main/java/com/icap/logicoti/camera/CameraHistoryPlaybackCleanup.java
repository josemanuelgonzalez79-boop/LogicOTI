package com.icap.logicoti.camera;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class CameraHistoryPlaybackCleanup {

    private final HistoryPlaybackGateway playbackGateway;

    CameraHistoryPlaybackCleanup(
            HistoryPlaybackGateway playbackGateway
    ) {
        this.playbackGateway = playbackGateway;
    }

    @Scheduled(
            initialDelayString = "${CAMERA_HISTORY_PLAYBACK_CLEANUP_MS:60000}",
            fixedDelayString = "${CAMERA_HISTORY_PLAYBACK_CLEANUP_MS:60000}"
    )
    void removeExpiredRoutes() {
        playbackGateway.removeExpired();
    }
}
