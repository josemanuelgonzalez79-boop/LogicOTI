package com.icap.logicoti.camera;

import com.icap.logicoti.config.CameraHistoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
class MediaMtxControlClient implements HistoryPlaybackGateway {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MediaMtxControlClient.class);

    private static final String TEMPORARY_PATH_PREFIX =
            "logicoti-history-";
    private static final Duration MINIMUM_SESSION_TTL =
            Duration.ofMinutes(5);
    private static final Duration MAXIMUM_SESSION_TTL =
            Duration.ofHours(24);
    private static final DateTimeFormatter NVR_RTSP_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT);

    private final CameraHistoryProperties properties;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;

    MediaMtxControlClient(
            CameraHistoryProperties properties,
            JsonMapper jsonMapper
    ) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(normalizedTimeout(properties))
                .build();
    }

    @Override
    public HistoryPlaybackSession open(
            String cameraCode,
            int trackId,
            String playbackUri,
            LocalDateTime startTime
    ) {
        ensureConfigured();
        Instant expiresAt = Instant.now().plus(normalizedSessionTtl());
        String pathName = temporaryPathName(
                cameraCode,
                expiresAt,
                UUID.randomUUID()
        );
        URI parsedPlaybackUri;
        try {
            parsedPlaybackUri = URI.create(playbackUri);
        } catch (RuntimeException exception) {
            throw invalidPlaybackUri();
        }
        URI source = authenticatedSource(
                parsedPlaybackUri,
                properties.getBaseUrl(),
                trackId,
                properties.getUsername(),
                properties.getPassword(),
                startTime
        );
        String body = jsonMapper.writeValueAsString(Map.of(
                "source", source.toASCIIString(),
                "sourceOnDemand", true,
                "sourceOnDemandStartTimeout", "20s",
                "sourceOnDemandCloseAfter", "15s",
                "rtspTransport", "tcp"
        ));

        HttpResponse<String> response = send(
                "POST",
                controlEndpoint("/v3/config/paths/add/" + pathName),
                body
        );
        requireSuccess(response, "crear la reproducción temporal");

        LOGGER.info(
                "Se creó la ruta histórica temporal {} hasta {}.",
                pathName,
                expiresAt
        );

        return new HistoryPlaybackSession(pathName, expiresAt);
    }

    @Override
    public void removeExpired() {
        if (!properties.isPlaybackConfigured()) {
            return;
        }

        try {
            HttpResponse<String> response = send(
                    "GET",
                    controlEndpoint(
                            "/v3/config/paths/list?page=0&itemsPerPage=1000"
                    ),
                    null
            );
            requireSuccess(response, "consultar las rutas temporales");
            Map<?, ?> payload = jsonMapper.readValue(
                    response.body(),
                    Map.class
            );
            Object rawItems = payload.get("items");

            if (!(rawItems instanceof List<?> items)) {
                return;
            }

            Instant now = Instant.now();
            for (Object rawItem : items) {
                if (rawItem instanceof Map<?, ?> item) {
                    Object rawName = item.get("name");
                    if (rawName instanceof String name
                            && isExpiredTemporaryPath(name, now)) {
                        deletePath(name);
                    }
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "No fue posible limpiar las rutas históricas temporales "
                            + "({}).",
                    exception.getClass().getSimpleName()
            );
        }
    }

    static URI authenticatedSource(
            URI playbackUri,
            URI nvrBaseUrl,
            int trackId,
            String username,
            String password,
            LocalDateTime startTime
    ) {
        if (playbackUri == null
                || playbackUri.getScheme() == null
                || playbackUri.getHost() == null
                || playbackUri.getUserInfo() != null
                || nvrBaseUrl == null
                || nvrBaseUrl.getHost() == null
                || !playbackUri.getHost().equalsIgnoreCase(
                        nvrBaseUrl.getHost()
                )) {
            throw invalidPlaybackUri();
        }

        String scheme = playbackUri.getScheme();
        if (!"rtsp".equalsIgnoreCase(scheme)
                && !"rtsps".equalsIgnoreCase(scheme)) {
            throw invalidPlaybackUri();
        }

        String expectedPath = "/Streaming/tracks/" + trackId;
        String path = playbackUri.getPath();
        String query = playbackUri.getQuery();
        if (path == null
                || !(path.equals(expectedPath)
                || path.equals(expectedPath + "/"))
                || query == null
                || startTime == null) {
            throw invalidPlaybackUri();
        }

        String[] parameters = query.split("&", -1);
        int startCount = 0;
        int endCount = 0;
        for (int index = 0; index < parameters.length; index++) {
            if (parameters[index].startsWith("starttime=")) {
                startCount++;
                parameters[index] = "starttime=" + NVR_RTSP_TIME.format(startTime);
            } else if (parameters[index].startsWith("endtime=")) {
                endCount++;
            }
        }
        if (startCount != 1 || endCount != 1) {
            throw invalidPlaybackUri();
        }

        try {
            return new URI(
                    scheme.toLowerCase(Locale.ROOT),
                    username + ":" + password,
                    playbackUri.getHost(),
                    effectiveRtspPort(playbackUri),
                    path,
                    String.join("&", parameters),
                    null
            );
        } catch (URISyntaxException exception) {
            throw new CameraHistoryUnavailableException(
                    "No fue posible preparar la reproducción del NVR.",
                    exception
            );
        }
    }

    static String temporaryPathName(
            String cameraCode,
            Instant expiresAt,
            UUID identifier
    ) {
        String normalizedCameraCode = cameraCode == null
                ? "camera"
                : cameraCode.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("(^-+|-+$)", "");

        return TEMPORARY_PATH_PREFIX
                + expiresAt.getEpochSecond()
                + "-"
                + (normalizedCameraCode.isBlank()
                ? "camera"
                : normalizedCameraCode)
                + "-"
                + identifier.toString().replace("-", "");
    }

    static boolean isExpiredTemporaryPath(
            String pathName,
            Instant now
    ) {
        if (pathName == null
                || !pathName.startsWith(TEMPORARY_PATH_PREFIX)) {
            return false;
        }

        String suffix = pathName.substring(TEMPORARY_PATH_PREFIX.length());
        int separator = suffix.indexOf('-');
        if (separator <= 0) {
            return false;
        }

        try {
            long expiresAt = Long.parseLong(suffix.substring(0, separator));
            return !Instant.ofEpochSecond(expiresAt).isAfter(now);
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private void deletePath(String pathName) {
        HttpResponse<String> response = send(
                "DELETE",
                controlEndpoint("/v3/config/paths/delete/" + pathName),
                null
        );

        if (response.statusCode() != 404) {
            requireSuccess(response, "eliminar una reproducción temporal");
        }

        LOGGER.info("Se eliminó la ruta histórica temporal {}.", pathName);
    }

    private HttpResponse<String> send(
            String method,
            URI endpoint,
            String body
    ) {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(normalizedTimeout(properties))
                .method(method, publisher);

        if (body != null) {
            request.header("Content-Type", "application/json");
        }

        try {
            return httpClient.send(
                    request.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CameraHistoryUnavailableException(
                    "La comunicación con MediaMTX fue interrumpida.",
                    exception
            );
        } catch (IOException exception) {
            throw new CameraHistoryUnavailableException(
                    "No fue posible comunicarse con la API local de MediaMTX.",
                    exception
            );
        }
    }

    private URI controlEndpoint(String path) {
        String baseUrl = properties.getMediaMtxControlUrl().toString();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return URI.create(baseUrl + path);
    }

    private void ensureConfigured() {
        if (!properties.isPlaybackConfigured()) {
            throw new CameraHistoryUnavailableException(
                    "La reproducción histórica no está configurada."
            );
        }
    }

    private void requireSuccess(
            HttpResponse<String> response,
            String operation
    ) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new CameraHistoryUnavailableException(
                    "MediaMTX no pudo " + operation
                            + " (HTTP " + response.statusCode() + ")."
            );
        }
    }

    private Duration normalizedSessionTtl() {
        Duration configured = properties.getPlaybackSessionTtl();
        if (configured.compareTo(MINIMUM_SESSION_TTL) < 0) {
            return MINIMUM_SESSION_TTL;
        }
        if (configured.compareTo(MAXIMUM_SESSION_TTL) > 0) {
            return MAXIMUM_SESSION_TTL;
        }
        return configured;
    }

    private static Duration normalizedTimeout(
            CameraHistoryProperties properties
    ) {
        Duration configured = properties.getMediaMtxTimeout();
        return configured == null
                || configured.isZero()
                || configured.isNegative()
                ? Duration.ofSeconds(5)
                : configured;
    }

    private static int effectiveRtspPort(URI playbackUri) {
        if (playbackUri.getPort() > 0) {
            return playbackUri.getPort();
        }
        return "rtsps".equalsIgnoreCase(playbackUri.getScheme()) ? 322 : 554;
    }

    private static CameraHistoryUnavailableException invalidPlaybackUri() {
        return new CameraHistoryUnavailableException(
                "El NVR devolvió una dirección de reproducción no permitida."
        );
    }
}
