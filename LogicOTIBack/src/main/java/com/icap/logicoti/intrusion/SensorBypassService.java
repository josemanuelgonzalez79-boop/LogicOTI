package com.icap.logicoti.intrusion;

import com.icap.logicoti.exception.BadRequestException;
import com.icap.logicoti.exception.ConflictException;
import com.icap.logicoti.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
public class SensorBypassService {

    private static final String BYPASS_COLUMNS = """
            SELECT
                bypass.id,
                bypass.device_code,
                bypass.device_name,
                bypass.area_code,
                bypass.area_name,
                bypass.reason,
                bypass.active,
                bypass.created_by,
                bypass.created_at,
                bypass.revoked_by,
                bypass.revoked_at
            FROM sensor_bypass_history bypass
            """;

    private static final String ACTIVE_BYPASSES_QUERY = BYPASS_COLUMNS + """
            WHERE bypass.active = TRUE
            ORDER BY bypass.created_at DESC, bypass.id DESC
            """;

    private static final String BYPASS_BY_ID_QUERY = BYPASS_COLUMNS + """
            WHERE bypass.id = ?
            """;

    private static final String WARNING_COLUMNS = """
            SELECT
                warning.id,
                bypass.id AS bypass_id,
                bypass.device_code,
                bypass.device_name,
                bypass.area_code,
                bypass.area_name,
                bypass.reason,
                warning.message,
                warning.warning_date,
                warning.created_at
            FROM sensor_bypass_warning warning
            INNER JOIN sensor_bypass_history bypass
                ON bypass.id = warning.bypass_id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final SecurityScheduleService scheduleService;
    private final SimpMessagingTemplate messagingTemplate;

    public SensorBypassService(
            JdbcTemplate jdbcTemplate,
            SecurityScheduleService scheduleService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.scheduleService = scheduleService;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public SensorBypassResponse create(
            SensorBypassRequest request,
            String username
    ) {
        MotionSensor sensor = findMotionSensor(
                request.sensorCode()
        );

        Integer existing = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sensor_bypass_history
                WHERE device_id = ?
                  AND active = TRUE
                """,
                Integer.class,
                sensor.id()
        );

        if (existing != null && existing > 0) {
            throw new ConflictException(
                    sensor.code() + " ya está omitido temporalmente."
            );
        }

        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO sensor_bypass_history (
                    device_id,
                    device_code,
                    device_name,
                    area_code,
                    area_name,
                    reason,
                    active,
                    created_by
                )
                VALUES (?, ?, ?, ?, ?, ?, TRUE, ?)
                RETURNING id
                """,
                Long.class,
                sensor.id(),
                sensor.code(),
                sensor.name(),
                sensor.areaCode(),
                sensor.areaName(),
                request.reason().trim(),
                username
        );

        if (id == null) {
            throw new IllegalStateException(
                    "No se pudo registrar la omisión del sensor."
            );
        }

        SensorBypassResponse response = findById(id);
        createWarningForBypass(response);
        return response;
    }

    @Transactional(readOnly = true)
    public SensorBypassListResponse findActive() {
        List<SensorBypassResponse> items = jdbcTemplate.query(
                ACTIVE_BYPASSES_QUERY,
                this::mapBypass
        );

        return new SensorBypassListResponse(
                items,
                items.size(),
                Instant.now()
        );
    }

    @Transactional
    public SensorBypassResponse revoke(
            String requestedSensorCode,
            String username
    ) {
        String sensorCode = normalizeCode(requestedSensorCode);
        List<Long> ids = jdbcTemplate.query("""
                SELECT id
                FROM sensor_bypass_history
                WHERE UPPER(device_code) = ?
                  AND active = TRUE
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> resultSet.getLong("id"),
                sensorCode
        );

        if (ids.isEmpty()) {
            throw new ResourceNotFoundException(
                    sensorCode + " no tiene una omisión activa."
            );
        }

        long id = ids.getFirst();

        jdbcTemplate.update("""
                UPDATE sensor_bypass_history
                SET active = FALSE,
                    revoked_by = ?,
                    revoked_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                username,
                id
        );

        return findById(id);
    }

    @Transactional
    public void createDailyWarnings() {
        List<SensorBypassResponse> bypasses = jdbcTemplate.query(
                ACTIVE_BYPASSES_QUERY,
                this::mapBypass
        );

        bypasses.forEach(this::createWarningForBypass);
    }

    @Transactional(readOnly = true)
    public SecurityWarningListResponse findWarnings(
            int requestedLimit
    ) {
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        List<SecurityWarningResponse> items = jdbcTemplate.query(
                WARNING_COLUMNS + """
                 ORDER BY warning.created_at DESC, warning.id DESC
                 LIMIT ?
                """,
                this::mapWarning,
                limit
        );

        return new SecurityWarningListResponse(
                items,
                items.size(),
                Instant.now()
        );
    }

    private void createWarningForBypass(
            SensorBypassResponse bypass
    ) {
        ZoneId zoneId = ZoneId.of(
                scheduleService.getSettings().timezone()
        );
        LocalDate warningDate = LocalDate.now(zoneId);
        String message = "El sensor "
                + bypass.sensorName()
                + " de "
                + bypass.areaName()
                + " continúa omitido. Motivo: "
                + bypass.reason();

        List<WarningInsert> inserted = jdbcTemplate.query("""
                INSERT INTO sensor_bypass_warning (
                    bypass_id,
                    warning_date,
                    message
                )
                VALUES (?, ?, ?)
                ON CONFLICT (bypass_id, warning_date) DO NOTHING
                RETURNING id, created_at
                """,
                (resultSet, rowNumber) -> new WarningInsert(
                        resultSet.getLong("id"),
                        resultSet.getTimestamp("created_at").toInstant()
                ),
                bypass.id(),
                warningDate,
                message
        );

        if (inserted.isEmpty()) {
            return;
        }

        WarningInsert warning = inserted.getFirst();
        SecurityWarningResponse response = new SecurityWarningResponse(
                warning.id(),
                bypass.id(),
                "SENSOR_BYPASSED",
                bypass.sensorCode(),
                bypass.sensorName(),
                bypass.areaCode(),
                bypass.areaName(),
                bypass.reason(),
                message,
                warningDate,
                warning.createdAt()
        );

        messagingTemplate.convertAndSend(
                "/topic/security/warnings",
                response
        );
    }

    private SensorBypassResponse findById(long id) {
        List<SensorBypassResponse> items = jdbcTemplate.query(
                BYPASS_BY_ID_QUERY,
                this::mapBypass,
                id
        );

        if (items.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No existe la omisión con id " + id + "."
            );
        }

        return items.getFirst();
    }

    private MotionSensor findMotionSensor(
            String requestedSensorCode
    ) {
        String sensorCode = normalizeCode(requestedSensorCode);
        List<MotionSensor> sensors = jdbcTemplate.query("""
                SELECT
                    device.id,
                    device.code,
                    device.name,
                    area.code AS area_code,
                    area.name AS area_name,
                    device.device_type
                FROM building_device device
                INNER JOIN building_area area
                    ON area.id = device.area_id
                WHERE UPPER(device.code) = ?
                  AND device.active = TRUE
                  AND area.active = TRUE
                """,
                (resultSet, rowNumber) -> new MotionSensor(
                        resultSet.getLong("id"),
                        resultSet.getString("code"),
                        resultSet.getString("name"),
                        resultSet.getString("area_code"),
                        resultSet.getString("area_name"),
                        resultSet.getString("device_type")
                ),
                sensorCode
        );

        if (sensors.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No existe el sensor " + sensorCode + "."
            );
        }

        MotionSensor sensor = sensors.getFirst();

        if (!"MOTION".equalsIgnoreCase(sensor.type())) {
            throw new BadRequestException(
                    "Sólo se pueden omitir sensores de movimiento. "
                            + "Los sensores de humo nunca se omiten."
            );
        }

        return sensor;
    }

    private SensorBypassResponse mapBypass(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new SensorBypassResponse(
                resultSet.getLong("id"),
                resultSet.getString("device_code"),
                resultSet.getString("device_name"),
                resultSet.getString("area_code"),
                resultSet.getString("area_name"),
                resultSet.getString("reason"),
                resultSet.getBoolean("active"),
                resultSet.getString("created_by"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getString("revoked_by"),
                toInstant(resultSet.getTimestamp("revoked_at"))
        );
    }

    private SecurityWarningResponse mapWarning(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new SecurityWarningResponse(
                resultSet.getLong("id"),
                resultSet.getLong("bypass_id"),
                "SENSOR_BYPASSED",
                resultSet.getString("device_code"),
                resultSet.getString("device_name"),
                resultSet.getString("area_code"),
                resultSet.getString("area_name"),
                resultSet.getString("reason"),
                resultSet.getString("message"),
                resultSet.getObject("warning_date", LocalDate.class),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }

    private String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(
                    "El código del sensor es obligatorio."
            );
        }

        return value.trim().toUpperCase(Locale.ROOT);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record MotionSensor(
            long id,
            String code,
            String name,
            String areaCode,
            String areaName,
            String type
    ) {
    }

    private record WarningInsert(
            long id,
            Instant createdAt
    ) {
    }
}
