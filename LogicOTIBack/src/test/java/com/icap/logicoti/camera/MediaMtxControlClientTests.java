package com.icap.logicoti.camera;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaMtxControlClientTests {

    @Test
    void addsCredentialsOnlyToTheExpectedNvrTrack() {
        URI source = MediaMtxControlClient.authenticatedSource(
                URI.create(
                        "rtsp://192.0.2.18/Streaming/tracks/101/"
                                + "?starttime=20260922T090721Z"
                                + "&endtime=20260922T094409Z"
                ),
                URI.create("http://192.0.2.18"),
                101,
                "history-user",
                "p@ss word",
                LocalDateTime.of(2026, 9, 22, 9, 19, 21)
        );

        assertThat(source.getHost()).isEqualTo("192.0.2.18");
        assertThat(source.getPort()).isEqualTo(554);
        assertThat(source.getUserInfo()).isEqualTo(
                "history-user:p@ss word"
        );
        assertThat(source.toASCIIString()).contains("p%40ss%20word");
        assertThat(source.getQuery())
                .contains("starttime=20260922T091921Z")
                .contains("endtime=20260922T094409Z");
    }

    @Test
    void rejectsAPlaybackUriThatTargetsAnotherHost() {
        assertThrows(
                CameraHistoryUnavailableException.class,
                () -> MediaMtxControlClient.authenticatedSource(
                        URI.create(
                                "rtsp://198.51.100.20/Streaming/tracks/101/"
                                        + "?starttime=a&endtime=b"
                        ),
                        URI.create("http://192.0.2.18"),
                        101,
                        "user",
                        "password",
                        LocalDateTime.of(2026, 9, 22, 9, 19, 21)
                )
        );
    }

    @Test
    void encodesTheExpirationInTheTemporaryPath() {
        Instant expiration = Instant.parse("2026-09-22T19:00:00Z");
        String path = MediaMtxControlClient.temporaryPathName(
                "CAM-021",
                expiration,
                UUID.fromString("01234567-89ab-cdef-0123-456789abcdef")
        );

        assertThat(path).startsWith(
                "logicoti-history-" + expiration.getEpochSecond()
        );
        assertThat(MediaMtxControlClient.isExpiredTemporaryPath(
                path,
                expiration.minusSeconds(1)
        )).isFalse();
        assertThat(MediaMtxControlClient.isExpiredTemporaryPath(
                path,
                expiration
        )).isTrue();
    }
}
