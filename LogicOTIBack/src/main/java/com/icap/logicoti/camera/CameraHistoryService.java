package com.icap.logicoti.camera;

import com.icap.logicoti.config.CameraHistoryProperties;
import com.icap.logicoti.exception.BadRequestException;
import com.icap.logicoti.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class CameraHistoryService {

    private static final int FIRST_STREAM_SUFFIX = 1;
    private static final int TRACK_MULTIPLIER = 100;
    private static final int MAX_CHANNEL_NUMBER = 32;

    private final CameraService cameraService;
    private final NvrRecordingClient recordingClient;
    private final NvrMotionClient motionClient;
    private final HistoryPlaybackGateway playbackGateway;
    private final CameraHistoryProperties properties;

    public CameraHistoryService(
            CameraService cameraService,
            NvrRecordingClient recordingClient,
            NvrMotionClient motionClient,
            HistoryPlaybackGateway playbackGateway,
            CameraHistoryProperties properties
    ) {
        this.cameraService = cameraService;
        this.recordingClient = recordingClient;
        this.motionClient = motionClient;
        this.playbackGateway = playbackGateway;
        this.properties = properties;
    }

    public CameraRecordingSearchResponse search(
            String cameraCode,
            CameraRecordingSearchRequest request
    ) {
        if (!properties.isConfigured()) {
            throw new CameraHistoryUnavailableException(
                    "El histórico de cámaras no está configurado."
            );
        }

        validateRange(request.startTime(), request.endTime());
        CameraResponse camera = cameraService.findByCode(cameraCode);

        if (!camera.active()) {
            throw new BadRequestException(
                    "La cámara seleccionada está inactiva."
            );
        }

        int trackId = trackId(camera.channelNumber());
        LocalDateTime nvrStart = toNvrTime(request.startTime());
        LocalDateTime nvrEnd = toNvrTime(request.endTime());
        List<NvrRecordingSegment> matches = recordingClient.search(
                trackId,
                nvrStart,
                nvrEnd
        );
        List<CameraRecordingSegmentResponse> items = new ArrayList<>();

        for (int index = 0; index < matches.size(); index++) {
            NvrRecordingSegment segment = matches.get(index);
            items.add(new CameraRecordingSegmentResponse(
                    index + 1,
                    fromNvrTime(segment.startTime()),
                    fromNvrTime(segment.endTime()),
                    segment.codecType(),
                    segment.recordingType()
            ));
        }

        List<CameraRecordingSegmentResponse> motionItems = new ArrayList<>();
        String motionStatus = "disabled";
        if (motionClient.enabled() && !matches.isEmpty()) {
            try {
                // SDK event timestamps are the NVR's local wall clock, regardless of
                // the ISAPI search workaround NVR_LOCAL_TIME_AS_UTC.
                List<NvrMotionInterval> events = motionClient.search(
                        camera.channelNumber(), request.startTime(), request.endTime());
                for (NvrMotionInterval event : events) {
                    for (CameraRecordingSegmentResponse recording : items) {
                        LocalDateTime from = max(event.startTime(), recording.startTime(), request.startTime());
                        LocalDateTime until = min(event.endTime(), recording.endTime(), request.endTime());
                        if (until.isAfter(from)) {
                            motionItems.add(new CameraRecordingSegmentResponse(
                                    motionItems.size() + 1, from, until, "", "MOTION"));
                        }
                    }
                }
                motionStatus = "available";
            } catch (CameraHistoryUnavailableException exception) {
                motionStatus = "unavailable";
            }
        }

        return new CameraRecordingSearchResponse(
                camera.code(),
                camera.name(),
                camera.channelNumber(),
                request.startTime(),
                request.endTime(),
                List.copyOf(items),
                items.size(),
                List.copyOf(motionItems),
                motionStatus,
                isPlaybackConfigured(),
                Instant.now()
        );
    }

    public CameraRecordingPlaybackResponse startPlayback(
            String cameraCode,
            CameraRecordingPlaybackRequest request
    ) {
        if (!isPlaybackConfigured()) {
            throw new CameraHistoryUnavailableException(
                    "La reproducción histórica no está configurada."
            );
        }

        validateRange(request.startTime(), request.endTime());
        CameraResponse camera = cameraService.findByCode(cameraCode);

        if (!camera.active()) {
            throw new BadRequestException(
                    "La cámara seleccionada está inactiva."
            );
        }

        int trackId = trackId(camera.channelNumber());
        LocalDateTime nvrStart = toNvrTime(request.startTime());
        LocalDateTime nvrEnd = toNvrTime(request.endTime());
        NvrRecordingSegment segment = recordingClient.search(
                        trackId,
                        nvrStart,
                        nvrEnd
                ).stream()
                .filter(item -> !item.startTime().isAfter(nvrStart)
                        && item.endTime().isAfter(nvrStart))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "La grabación seleccionada ya no está disponible."
                ));

        HistoryPlaybackSession session = playbackGateway.open(
                camera.code(),
                trackId,
                segment.playbackUri(),
                nvrStart
        );

        return new CameraRecordingPlaybackResponse(
                camera.code(),
                camera.name(),
                cameraService.buildViewUrl(session.pathName()),
                session.expiresAt(),
                Instant.now()
        );
    }

    private void validateRange(
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        if (startTime == null || endTime == null) {
            throw new BadRequestException(
                    "El periodo de búsqueda es obligatorio."
            );
        }

        if (!endTime.isAfter(startTime)) {
            throw new BadRequestException(
                    "La hora final debe ser posterior a la inicial."
            );
        }

        long maximumHours = Math.max(1, properties.getMaxSearchHours());
        if (Duration.between(startTime, endTime)
                .compareTo(Duration.ofHours(maximumHours)) > 0) {
            throw new BadRequestException(
                    "El periodo máximo de búsqueda es de "
                            + maximumHours
                            + " horas."
            );
        }

        LocalDateTime maximumEnd = LocalDateTime.now(
                configuredZoneId()
        ).plusMinutes(5);
        if (endTime.isAfter(maximumEnd)) {
            throw new BadRequestException(
                    "El periodo de búsqueda no puede estar en el futuro."
            );
        }
    }

    private boolean isPlaybackConfigured() {
        return properties.isPlaybackConfigured()
                && cameraService.isPlaybackConfigured();
    }

    private int trackId(int channelNumber) {
        if (channelNumber < 1 || channelNumber > MAX_CHANNEL_NUMBER) {
            throw new BadRequestException(
                    "El canal de la cámara no es válido para el NVR."
            );
        }

        return channelNumber * TRACK_MULTIPLIER
                + FIRST_STREAM_SUFFIX;
    }

    private LocalDateTime toNvrTime(LocalDateTime localTime) {
        if (properties.isLocalTimeAsUtc()) {
            return localTime;
        }

        return localTime.atZone(configuredZoneId())
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    private LocalDateTime fromNvrTime(LocalDateTime nvrTime) {
        if (properties.isLocalTimeAsUtc()) {
            return nvrTime;
        }

        return nvrTime.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(configuredZoneId())
                .toLocalDateTime();
    }

    private ZoneId configuredZoneId() {
        try {
            return ZoneId.of(properties.getTimeZone());
        } catch (RuntimeException exception) {
            throw new CameraHistoryUnavailableException(
                    "La zona horaria configurada para el NVR no es válida.",
                    exception
            );
        }
    }

    private LocalDateTime max(LocalDateTime a, LocalDateTime b, LocalDateTime c) {
        return a.isAfter(b) ? (a.isAfter(c) ? a : c) : (b.isAfter(c) ? b : c);
    }

    private LocalDateTime min(LocalDateTime a, LocalDateTime b, LocalDateTime c) {
        return a.isBefore(b) ? (a.isBefore(c) ? a : c) : (b.isBefore(c) ? b : c);
    }
}
