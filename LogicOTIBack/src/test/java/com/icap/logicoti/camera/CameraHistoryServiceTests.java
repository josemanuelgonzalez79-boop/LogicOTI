package com.icap.logicoti.camera;

import com.icap.logicoti.config.CameraHistoryProperties;
import com.icap.logicoti.exception.BadRequestException;
import com.icap.logicoti.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CameraHistoryServiceTests {

    @Mock
    private CameraService cameraService;

    @Mock
    private NvrRecordingClient recordingClient;

    @Mock
    private HistoryPlaybackGateway playbackGateway;

    private CameraHistoryProperties properties;
    private CameraHistoryService service;

    @BeforeEach
    void setUp() {
        properties = new CameraHistoryProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(URI.create("http://192.0.2.18"));
        properties.setUsername("history-user");
        properties.setPassword("test-password");
        properties.setTimeZone("America/Mazatlan");
        properties.setLocalTimeAsUtc(true);
        properties.setMaxSearchHours(24);

        service = new CameraHistoryService(
                cameraService,
                recordingClient,
                playbackGateway,
                properties
        );
    }

    @Test
    void searchesTheMainTrackUsingLocalWallClockTime() {
        LocalDateTime start = LocalDateTime.of(
                2026,
                9,
                21,
                9,
                30
        );
        LocalDateTime end = start.plusMinutes(15);

        when(cameraService.findByCode("CAM-025"))
                .thenReturn(camera(25, true));
        when(recordingClient.search(2501, start, end))
                .thenReturn(List.of(new NvrRecordingSegment(
                        start.minusMinutes(5),
                        end.plusMinutes(5),
                        "H.264-BP",
                        "timing",
                        "rtsp://nvr/playback-without-credentials"
                )));

        CameraRecordingSearchResponse response = service.search(
                "CAM-025",
                new CameraRecordingSearchRequest(start, end)
        );

        verify(recordingClient).search(2501, start, end);
        assertThat(response.cameraCode()).isEqualTo("CAM-025");
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items().getFirst().startTime())
                .isEqualTo(start.minusMinutes(5));
        assertThat(response.items().getFirst().codecType())
                .isEqualTo("H.264-BP");
        assertThat(response.playbackConfigured()).isFalse();
    }

    @Test
    void rejectsSearchesLongerThanTheConfiguredLimit() {
        LocalDateTime start = LocalDateTime.of(
                2026,
                9,
                20,
                8,
                0
        );

        assertThrows(
                BadRequestException.class,
                () -> service.search(
                        "CAM-001",
                        new CameraRecordingSearchRequest(
                                start,
                                start.plusHours(25)
                        )
                )
        );
    }

    @Test
    void rejectsAnInactiveCamera() {
        LocalDateTime start = LocalDateTime.of(
                2026,
                9,
                21,
                9,
                0
        );
        when(cameraService.findByCode("CAM-029"))
                .thenReturn(camera(29, false));

        assertThrows(
                BadRequestException.class,
                () -> service.search(
                        "CAM-029",
                        new CameraRecordingSearchRequest(
                                start,
                                start.plusMinutes(15)
                        )
                )
        );
    }

    @Test
    void createsATemporaryPlaybackForTheSelectedSegment() {
        LocalDateTime start = LocalDateTime.of(
                2026,
                9,
                22,
                9,
                7,
                21
        );
        LocalDateTime end = start.plusMinutes(37);
        String playbackUri = "rtsp://192.0.2.18/Streaming/tracks/101/"
                + "?starttime=20260922T090721Z&endtime=20260922T094409Z";
        Instant expiresAt = Instant.parse("2026-09-22T19:00:00Z");

        properties.setPlaybackEnabled(true);
        properties.setMediaMtxControlUrl(
                URI.create("http://127.0.0.1:9997")
        );
        properties.setMediaMtxTimeout(Duration.ofSeconds(5));
        properties.setPlaybackSessionTtl(Duration.ofHours(2));

        when(cameraService.isPlaybackConfigured()).thenReturn(true);
        when(cameraService.findByCode("CAM-001"))
                .thenReturn(camera(1, true));
        when(recordingClient.search(101, start, end))
                .thenReturn(List.of(new NvrRecordingSegment(
                        start,
                        end,
                        "H.264-BP",
                        "timing",
                        playbackUri
                )));
        when(playbackGateway.open("CAM-001", 101, playbackUri, start))
                .thenReturn(new HistoryPlaybackSession(
                        "logicoti-history-session",
                        expiresAt
                ));
        when(cameraService.buildViewUrl("logicoti-history-session"))
                .thenReturn(
                        "https://video.local/camera/logicoti-history-session/"
                );

        CameraRecordingPlaybackResponse response = service.startPlayback(
                "CAM-001",
                new CameraRecordingPlaybackRequest(start, end)
        );

        verify(playbackGateway).open("CAM-001", 101, playbackUri, start);
        assertThat(response.viewUrl()).isEqualTo(
                "https://video.local/camera/logicoti-history-session/"
        );
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void seeksWithinTheVerifiedSegmentWithoutExposingTheNvrUri() {
        LocalDateTime segmentStart = LocalDateTime.of(2026, 9, 22, 9, 7, 21);
        LocalDateTime requestedStart = segmentStart.plusMinutes(12);
        LocalDateTime segmentEnd = segmentStart.plusMinutes(37);
        String playbackUri = "rtsp://192.0.2.18/Streaming/tracks/101/"
                + "?starttime=20260922T090721Z&endtime=20260922T094421Z";
        properties.setPlaybackEnabled(true);
        properties.setMediaMtxControlUrl(URI.create("http://127.0.0.1:9997"));

        when(cameraService.isPlaybackConfigured()).thenReturn(true);
        when(cameraService.findByCode("CAM-001")).thenReturn(camera(1, true));
        when(recordingClient.search(101, requestedStart, segmentEnd))
                .thenReturn(List.of(new NvrRecordingSegment(
                        segmentStart, segmentEnd, "H.264", "timing", playbackUri
                )));
        when(playbackGateway.open("CAM-001", 101, playbackUri, requestedStart))
                .thenReturn(new HistoryPlaybackSession(
                        "logicoti-history-seek", Instant.parse("2026-09-22T19:00:00Z")
                ));
        when(cameraService.buildViewUrl("logicoti-history-seek"))
                .thenReturn("https://video.local/camera/logicoti-history-seek/");

        service.startPlayback("CAM-001", new CameraRecordingPlaybackRequest(
                requestedStart, segmentEnd
        ));

        verify(playbackGateway).open("CAM-001", 101, playbackUri, requestedStart);
    }

    @Test
    void refusesToSeekIntoAnUnrecordedGap() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 22, 9, 20);
        LocalDateTime end = start.plusMinutes(20);
        properties.setPlaybackEnabled(true);
        properties.setMediaMtxControlUrl(URI.create("http://127.0.0.1:9997"));

        when(cameraService.isPlaybackConfigured()).thenReturn(true);
        when(cameraService.findByCode("CAM-001")).thenReturn(camera(1, true));
        when(recordingClient.search(101, start, end))
                .thenReturn(List.of(new NvrRecordingSegment(
                        start.plusMinutes(5), end, "H.264", "timing",
                        "rtsp://192.0.2.18/Streaming/tracks/101/"
                )));

        assertThrows(ResourceNotFoundException.class, () -> service.startPlayback(
                "CAM-001", new CameraRecordingPlaybackRequest(start, end)
        ));
    }

    private CameraResponse camera(
            int channelNumber,
            boolean active
    ) {
        return new CameraResponse(
                (long) channelNumber,
                "CAM-%03d".formatted(channelNumber),
                channelNumber,
                "Cámara " + channelNumber,
                "EXT",
                null,
                "NVR",
                "oti-cam-%02d".formatted(channelNumber),
                active,
                active,
                active
                        ? "https://video.local/camera/oti-cam-%02d/"
                                .formatted(channelNumber)
                        : null
        );
    }
}
