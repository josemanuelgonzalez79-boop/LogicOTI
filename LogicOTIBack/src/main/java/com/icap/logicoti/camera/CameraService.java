package com.icap.logicoti.camera;

import com.icap.logicoti.config.CameraProperties;
import com.icap.logicoti.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class CameraService {

    private static final String CAMERA_COLUMNS = """
            SELECT
                camera.id,
                camera.code,
                camera.channel_number,
                camera.name,
                camera.floor_code,
                camera.area_code,
                camera.source_name,
                camera.stream_key,
                camera.active
            FROM camera
            """;

    private final JdbcTemplate jdbcTemplate;
    private final CameraProperties cameraProperties;

    public CameraService(
            JdbcTemplate jdbcTemplate,
            CameraProperties cameraProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.cameraProperties = cameraProperties;
    }

    public CameraListResponse findAll(String requestedFloorCode) {
        String floorCode = normalize(requestedFloorCode);

        String query = CAMERA_COLUMNS
                + (floorCode == null
                ? ""
                : " WHERE UPPER(camera.floor_code) = ?")
                + " ORDER BY camera.display_order, camera.id";

        List<CameraResponse> items = floorCode == null
                ? jdbcTemplate.query(query, this::mapCamera)
                : jdbcTemplate.query(
                        query,
                        this::mapCamera,
                        floorCode.toUpperCase(Locale.ROOT)
                );

        return new CameraListResponse(
                items,
                items.size(),
                isPlaybackConfigured(),
                Instant.now()
        );
    }

    public CameraResponse findByCode(String requestedCameraCode) {
        String cameraCode = normalize(requestedCameraCode);

        if (cameraCode == null) {
            throw new ResourceNotFoundException(
                    "No se encontró la cámara solicitada."
            );
        }

        List<CameraResponse> items = jdbcTemplate.query(
                CAMERA_COLUMNS
                        + " WHERE UPPER(camera.code) = ?",
                this::mapCamera,
                cameraCode.toUpperCase(Locale.ROOT)
        );

        return items.stream()
                .findFirst()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "No se encontró la cámara "
                                        + cameraCode
                                        + "."
                        )
                );
    }

    private CameraResponse mapCamera(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        String streamKey = resultSet.getString("stream_key");
        boolean active = resultSet.getBoolean("active");
        boolean videoAvailable = active && isPlaybackConfigured();

        return new CameraResponse(
                resultSet.getLong("id"),
                resultSet.getString("code"),
                resultSet.getInt("channel_number"),
                resultSet.getString("name"),
                resultSet.getString("floor_code"),
                resultSet.getString("area_code"),
                resultSet.getString("source_name"),
                streamKey,
                active,
                videoAvailable,
                videoAvailable ? buildViewUrl(streamKey) : null
        );
    }

    private boolean isPlaybackConfigured() {
        return cameraProperties.isEnabled()
                && cameraProperties.getPlaybackBaseUrl() != null
                && !cameraProperties.getPlaybackBaseUrl().isBlank();
    }

    private String buildViewUrl(String streamKey) {
        String baseUrl = cameraProperties
                .getPlaybackBaseUrl()
                .trim();

        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(
                    0,
                    baseUrl.length() - 1
            );
        }

        return baseUrl + "/" + streamKey;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
