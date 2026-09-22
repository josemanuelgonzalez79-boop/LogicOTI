package com.icap.logicoti.camera;

import java.time.Instant;

record HistoryPlaybackSession(
        String pathName,
        Instant expiresAt
) {
}
