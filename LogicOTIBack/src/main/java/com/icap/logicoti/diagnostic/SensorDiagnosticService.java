package com.icap.logicoti.diagnostic;

import com.icap.logicoti.config.PlcProperties;
import com.icap.logicoti.exception.BadRequestException;
import com.icap.logicoti.exception.ConflictException;
import com.icap.logicoti.exception.ResourceNotFoundException;
import com.icap.logicoti.intrusion.SecurityScheduleService;
import com.icap.logicoti.intrusion.SecuritySettingsResponse;
import com.icap.logicoti.plc.PlcCommunicationService;
import com.icap.logicoti.signal.SignalQuality;
import com.icap.logicoti.signal.SignalQualityRegistry;
import com.icap.logicoti.signal.SignalQualitySnapshot;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SensorDiagnosticService {

    private static final Set<String> SESSION_STATUSES = Set.of(
            "RUNNING",
            "PASSED",
            "REJECTED",
            "CANCELLED"
    );

    private static final String INSERT_SESSION = """
            INSERT INTO sensor_diagnostic_session (
                status,
                started_by,
                started_by_role,
                expires_at,
                message
            )
            VALUES ('RUNNING', ?, ?, ?, ?)
            RETURNING id
            """;

    private static final String INSERT_ITEM = """
            INSERT INTO sensor_diagnostic_item (
                session_id,
                device_id,
                device_code,
                device_name,
                area_code,
                area_name,
                device_type,
                plc_state_tag,
                initial_state,
                saw_inactive,
                saw_active,
                status
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'RUNNING')
            """;

    private static final String SESSION_QUERY = """
            SELECT
                id,
                status,
                started_by,
                started_by_role,
                started_at,
                expires_at,
                completed_at,
                message
            FROM sensor_diagnostic_session
            WHERE id = ?
            """;

    private static final String ITEMS_QUERY = """
            SELECT
                id,
                device_code,
                device_name,
                area_code,
                area_name,
                device_type,
                plc_state_tag,
                initial_state,
                saw_inactive,
                saw_active,
                status,
                passed_at
            FROM sensor_diagnostic_item
            WHERE session_id = ?
            ORDER BY id
            """;

    private static final String RUNNING_ITEMS_QUERY = """
            SELECT
                item.id,
                item.session_id,
                item.device_id,
                item.device_code,
                item.device_name,
                item.area_code,
                item.area_name,
                item.device_type,
                item.plc_state_tag,
                item.saw_inactive,
                item.saw_active,
                session.expires_at
            FROM sensor_diagnostic_item item
            INNER JOIN sensor_diagnostic_session session
                ON session.id = item.session_id
            WHERE item.status = 'RUNNING'
              AND session.status = 'RUNNING'
              AND session.expires_at > CURRENT_TIMESTAMP
            ORDER BY item.id
            """;

    private static final String DUE_SENSORS_QUERY = """
            SELECT
                device.id AS device_id,
                device.code AS device_code,
                device.name AS device_name,
                area.code AS area_code,
                area.name AS area_name,
                device.device_type,
                latest.completed_at
            FROM building_device device
            INNER JOIN building_area area
                ON area.id = device.area_id
            LEFT JOIN LATERAL (
                SELECT session.completed_at
                FROM sensor_diagnostic_item item
                INNER JOIN sensor_diagnostic_session session
                    ON session.id = item.session_id
                WHERE item.device_id = device.id
                  AND item.status = 'PASSED'
                  AND session.status = 'PASSED'
                ORDER BY session.completed_at DESC, session.id DESC
                LIMIT 1
            ) latest ON TRUE
            WHERE device.active = TRUE
              AND area.active = TRUE
              AND device.device_type IN ('MOTION', 'SMOKE')
            ORDER BY area.display_order, device.display_order
            """;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final PlcCommunicationService plcCommunicationService;
    private final PlcProperties plcProperties;
    private final SecurityScheduleService scheduleService;
    private final SimpMessagingTemplate messagingTemplate;
    private final SignalQualityRegistry signalQualityRegistry;

    public SensorDiagnosticService(
            JdbcTemplate jdbcTemplate,
            NamedParameterJdbcTemplate namedJdbcTemplate,
            PlcCommunicationService plcCommunicationService,
            PlcProperties plcProperties,
            SecurityScheduleService scheduleService,
            SimpMessagingTemplate messagingTemplate,
            SignalQualityRegistry signalQualityRegistry
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.plcCommunicationService = plcCommunicationService;
        this.plcProperties = plcProperties;
        this.scheduleService = scheduleService;
        this.messagingTemplate = messagingTemplate;
        this.signalQualityRegistry = signalQualityRegistry;
    }

    @Transactional
    public SensorDiagnosticResponse start(
            SensorDiagnosticStartRequest request,
            String username,
            String role
    ) {
        List<String> codes = normalizeCodes(request.sensorCodes());
        List<DiagnosticSensor> sensors = findSensors(codes);

        validateRequestedSensors(codes, sensors);
        validateNoRunningDiagnostics(sensors);

        Map<Long, Boolean> initialStates = readStates(sensors);
        SecuritySettingsResponse settings = scheduleService.getSettings();
        Instant expiresAt = Instant.now()
                .plusSeconds(settings.diagnosticTimeoutSeconds());

        Long sessionId = jdbcTemplate.queryForObject(
                INSERT_SESSION,
                Long.class,
                username,
                role,
                Timestamp.from(expiresAt),
                "Diagnóstico iniciado. Active y restablezca cada sensor antes de que termine el tiempo."
        );

        if (sessionId == null) {
            throw new IllegalStateException(
                    "No se pudo crear la sesión de diagnóstico."
            );
        }

        for (DiagnosticSensor sensor : sensors) {
            boolean initialState = initialStates.get(sensor.id());

            jdbcTemplate.update(
                    INSERT_ITEM,
                    sessionId,
                    sensor.id(),
                    sensor.code(),
                    sensor.name(),
                    sensor.areaCode(),
                    sensor.areaName(),
                    sensor.type(),
                    sensor.stateTag(),
                    initialState,
                    !initialState,
                    initialState
            );
        }

        SensorDiagnosticResponse response = get(sessionId);
        publish(response);
        return response;
    }

    @Transactional(readOnly = true)
    public SensorDiagnosticResponse get(long id) {
        List<SessionRow> sessions = jdbcTemplate.query(
                SESSION_QUERY,
                this::mapSession,
                id
        );

        if (sessions.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No existe el diagnóstico con id " + id + "."
            );
        }

        SessionRow session = sessions.getFirst();
        List<SensorDiagnosticResponse.Item> items = jdbcTemplate.query(
                ITEMS_QUERY,
                this::mapItem,
                id
        );

        return toResponse(session, items);
    }

    @Transactional(readOnly = true)
    public SensorDiagnosticListResponse find(
            String requestedStatus,
            int requestedLimit
    ) {
        String status = normalizeStatus(requestedStatus);
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        StringBuilder query = new StringBuilder("""
                SELECT id
                FROM sensor_diagnostic_session
                WHERE 1 = 1
                """);
        List<Object> parameters = new ArrayList<>();

        if (status != null) {
            query.append(" AND status = ?");
            parameters.add(status);
        }

        query.append(" ORDER BY started_at DESC, id DESC LIMIT ?");
        parameters.add(limit);

        List<Long> ids = jdbcTemplate.query(
                query.toString(),
                (resultSet, rowNumber) -> resultSet.getLong("id"),
                parameters.toArray()
        );

        List<SensorDiagnosticResponse> items = ids.stream()
                .map(this::get)
                .toList();

        return new SensorDiagnosticListResponse(
                items,
                items.size(),
                Instant.now()
        );
    }

    @Transactional
    public SensorDiagnosticResponse cancel(
            long id,
            String username
    ) {
        SensorDiagnosticResponse current = get(id);

        if (!"RUNNING".equals(current.status())) {
            throw new ConflictException(
                    "El diagnóstico " + id + " ya terminó."
            );
        }

        jdbcTemplate.update("""
                UPDATE sensor_diagnostic_item
                SET status = 'CANCELLED'
                WHERE session_id = ?
                  AND status = 'RUNNING'
                """, id);

        jdbcTemplate.update("""
                UPDATE sensor_diagnostic_session
                SET status = 'CANCELLED',
                    completed_at = CURRENT_TIMESTAMP,
                    message = ?
                WHERE id = ?
                  AND status = 'RUNNING'
                """,
                "Diagnóstico cancelado por " + username + ".",
                id
        );

        SensorDiagnosticResponse response = get(id);
        publish(response);
        return response;
    }

    @Transactional(readOnly = true)
    public SensorDiagnosticDueResponse findDueSensors() {
        SecuritySettingsResponse settings = scheduleService.getSettings();
        ZoneId zoneId = ZoneId.of(settings.timezone());
        Instant now = Instant.now();

        List<SensorDiagnosticDueResponse.Sensor> sensors =
                jdbcTemplate.query(
                        DUE_SENSORS_QUERY,
                        (resultSet, rowNumber) -> {
                            SignalQualitySnapshot quality =
                                    signalQualityRegistry.snapshot(
                                            resultSet.getLong("device_id")
                                    );
                            Instant lastPassedAt = toInstant(
                                    resultSet.getTimestamp("completed_at")
                            );
                            Instant validUntil = lastPassedAt == null
                                    ? null
                                    : ZonedDateTime.ofInstant(
                                            lastPassedAt,
                                            zoneId
                                    ).plusMonths(
                                            settings.diagnosticValidityMonths()
                                    ).toInstant();

                            String status;

                            if (lastPassedAt == null) {
                                status = "DUE";
                            } else if (!validUntil.isAfter(now)) {
                                status = "EXPIRED";
                            } else {
                                status = "VALID";
                            }

                            return new SensorDiagnosticDueResponse.Sensor(
                                    resultSet.getString("device_code"),
                                    resultSet.getString("device_name"),
                                    resultSet.getString("area_code"),
                                    resultSet.getString("area_name"),
                                    resultSet.getString("device_type"),
                                    status,
                                    lastPassedAt,
                                    validUntil,
                                    quality.quality(),
                                    quality.lastUpdatedAt(),
                                    quality.detail()
                            );
                        }
                );

        int due = (int) sensors.stream()
                .filter(sensor -> !"VALID".equals(sensor.status()))
                .count();
        int goodSignals = countQuality(
                sensors,
                SignalQuality.GOOD
        );
        int badSignals = countQuality(
                sensors,
                SignalQuality.BAD
        );
        int staleSignals = countQuality(
                sensors,
                SignalQuality.STALE
        );

        return new SensorDiagnosticDueResponse(
                settings.diagnosticValidityMonths(),
                sensors.size(),
                due,
                goodSignals,
                badSignals,
                staleSignals,
                sensors,
                now
        );
    }

    private int countQuality(
            List<SensorDiagnosticDueResponse.Sensor> sensors,
            SignalQuality quality
    ) {
        return (int) sensors.stream()
                .filter(sensor -> sensor.quality() == quality)
                .count();
    }

    @Transactional(readOnly = true)
    List<RunningItem> findRunningItems() {
        return jdbcTemplate.query(
                RUNNING_ITEMS_QUERY,
                (resultSet, rowNumber) -> new RunningItem(
                        resultSet.getLong("id"),
                        resultSet.getLong("session_id"),
                        resultSet.getLong("device_id"),
                        resultSet.getString("device_code"),
                        resultSet.getString("device_name"),
                        resultSet.getString("area_code"),
                        resultSet.getString("area_name"),
                        resultSet.getString("device_type"),
                        resultSet.getString("plc_state_tag"),
                        resultSet.getBoolean("saw_inactive"),
                        resultSet.getBoolean("saw_active"),
                        resultSet.getTimestamp("expires_at").toInstant()
                )
        );
    }

    Map<Long, Boolean> readRunningStates(
            List<RunningItem> items
    ) {
        List<DiagnosticSensor> sensors = items.stream()
                .map(item -> new DiagnosticSensor(
                        item.deviceId(),
                        item.deviceCode(),
                        item.deviceName(),
                        item.areaCode(),
                        item.areaName(),
                        item.deviceType(),
                        item.stateTag()
                ))
                .collect(Collectors.toMap(
                        DiagnosticSensor::id,
                        Function.identity(),
                        (first, second) -> first
                ))
                .values()
                .stream()
                .toList();

        return readStates(sensors);
    }

    @Transactional
    Set<Long> recordStates(
            List<RunningItem> items,
            Map<Long, Boolean> states
    ) {
        Set<Long> changedSessions = new LinkedHashSet<>();

        for (RunningItem item : items) {
            Boolean currentState = states.get(item.deviceId());

            if (currentState == null) {
                continue;
            }

            boolean sawInactive = item.sawInactive() || !currentState;
            boolean sawActive = item.sawActive() || currentState;
            boolean passed = sawInactive && sawActive;

            if (sawInactive == item.sawInactive()
                    && sawActive == item.sawActive()) {
                continue;
            }

            jdbcTemplate.update("""
                    UPDATE sensor_diagnostic_item
                    SET saw_inactive = ?,
                        saw_active = ?,
                        status = ?,
                        passed_at = CASE
                            WHEN ? THEN CURRENT_TIMESTAMP
                            ELSE passed_at
                        END
                    WHERE id = ?
                      AND status = 'RUNNING'
                    """,
                    sawInactive,
                    sawActive,
                    passed ? "PASSED" : "RUNNING",
                    passed,
                    item.id()
            );

            changedSessions.add(item.sessionId());
        }

        for (Long sessionId : changedSessions) {
            jdbcTemplate.update("""
                    UPDATE sensor_diagnostic_session session
                    SET status = 'PASSED',
                        completed_at = CURRENT_TIMESTAMP,
                        message = 'Todos los sensores respondieron correctamente.'
                    WHERE session.id = ?
                      AND session.status = 'RUNNING'
                      AND NOT EXISTS (
                          SELECT 1
                          FROM sensor_diagnostic_item item
                          WHERE item.session_id = session.id
                            AND item.status = 'RUNNING'
                      )
                    """, sessionId);
        }

        return changedSessions;
    }

    @Transactional
    Set<Long> rejectExpired() {
        List<Long> expiredIds = jdbcTemplate.query("""
                SELECT id
                FROM sensor_diagnostic_session
                WHERE status = 'RUNNING'
                  AND expires_at <= CURRENT_TIMESTAMP
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> resultSet.getLong("id")
        );

        for (Long sessionId : expiredIds) {
            jdbcTemplate.update("""
                    UPDATE sensor_diagnostic_item
                    SET status = 'REJECTED'
                    WHERE session_id = ?
                      AND status = 'RUNNING'
                    """, sessionId);

            jdbcTemplate.update("""
                    UPDATE sensor_diagnostic_session
                    SET status = 'REJECTED',
                        completed_at = CURRENT_TIMESTAMP,
                        message = 'El tiempo terminó y uno o más sensores no cambiaron de estado.'
                    WHERE id = ?
                      AND status = 'RUNNING'
                    """, sessionId);
        }

        return new LinkedHashSet<>(expiredIds);
    }

    void publishSessions(Set<Long> sessionIds) {
        sessionIds.forEach(id -> publish(get(id)));
    }

    private void publish(SensorDiagnosticResponse response) {
        messagingTemplate.convertAndSend(
                "/topic/diagnostics/sensors/" + response.id(),
                response
        );
    }

    private List<String> normalizeCodes(List<String> requestedCodes) {
        return requestedCodes.stream()
                .map(String::trim)
                .map(code -> code.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private List<DiagnosticSensor> findSensors(List<String> codes) {
        return namedJdbcTemplate.query("""
                SELECT
                    device.id,
                    device.code,
                    device.name,
                    area.code AS area_code,
                    area.name AS area_name,
                    device.device_type,
                    device.plc_state_tag
                FROM building_device device
                INNER JOIN building_area area
                    ON area.id = device.area_id
                WHERE UPPER(device.code) IN (:codes)
                  AND device.active = TRUE
                  AND area.active = TRUE
                ORDER BY area.display_order, device.display_order
                """,
                new MapSqlParameterSource("codes", codes),
                (resultSet, rowNumber) -> new DiagnosticSensor(
                        resultSet.getLong("id"),
                        resultSet.getString("code"),
                        resultSet.getString("name"),
                        resultSet.getString("area_code"),
                        resultSet.getString("area_name"),
                        resultSet.getString("device_type"),
                        resultSet.getString("plc_state_tag")
                )
        );
    }

    private void validateRequestedSensors(
            List<String> codes,
            List<DiagnosticSensor> sensors
    ) {
        Set<String> foundCodes = sensors.stream()
                .map(sensor -> sensor.code().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());

        List<String> missingCodes = codes.stream()
                .filter(code -> !foundCodes.contains(code))
                .toList();

        if (!missingCodes.isEmpty()) {
            throw new BadRequestException(
                    "No existen o están inactivos estos sensores: "
                            + String.join(", ", missingCodes)
                            + "."
            );
        }

        for (DiagnosticSensor sensor : sensors) {
            if (!Set.of("MOTION", "SMOKE").contains(sensor.type())) {
                throw new BadRequestException(
                        sensor.code()
                                + " no es un sensor de movimiento o humo."
                );
            }

            if (sensor.stateTag() == null
                    || sensor.stateTag().isBlank()) {
                throw new ConflictException(
                        sensor.code()
                                + " no tiene un tag de estado configurado."
                );
            }
        }
    }

    private void validateNoRunningDiagnostics(
            List<DiagnosticSensor> sensors
    ) {
        List<Long> deviceIds = sensors.stream()
                .map(DiagnosticSensor::id)
                .toList();

        List<String> busyCodes = namedJdbcTemplate.query("""
                SELECT item.device_code
                FROM sensor_diagnostic_item item
                INNER JOIN sensor_diagnostic_session session
                    ON session.id = item.session_id
                WHERE item.device_id IN (:deviceIds)
                  AND item.status = 'RUNNING'
                  AND session.status = 'RUNNING'
                """,
                new MapSqlParameterSource("deviceIds", deviceIds),
                (resultSet, rowNumber) ->
                        resultSet.getString("device_code")
        );

        if (!busyCodes.isEmpty()) {
            throw new ConflictException(
                    "Ya existe un diagnóstico activo para: "
                            + String.join(", ", busyCodes)
                            + "."
            );
        }
    }

    private Map<Long, Boolean> readStates(
            List<DiagnosticSensor> sensors
    ) {
        return plcCommunicationService.read(connection -> {
            if (!connection.getMetadata().isReadSupported()) {
                throw new IllegalStateException(
                        "La conexión no permite leer tags."
                );
            }

            PlcReadRequest.Builder builder =
                    connection.readRequestBuilder();

            sensors.forEach(sensor -> builder.addTagAddress(
                    alias(sensor.id()),
                    sensor.stateTag()
            ));

            PlcReadResponse response = builder
                    .build()
                    .execute()
                    .get(
                            plcProperties.getTimeout().toMillis(),
                            TimeUnit.MILLISECONDS
                    );

            return sensors.stream().collect(Collectors.toMap(
                    DiagnosticSensor::id,
                    sensor -> readBoolean(response, sensor)
            ));
        });
    }

    private boolean readBoolean(
            PlcReadResponse response,
            DiagnosticSensor sensor
    ) {
        String alias = alias(sensor.id());
        PlcResponseCode code = response.getResponseCode(alias);

        if (code != PlcResponseCode.OK) {
            throw new IllegalStateException(
                    "El PLC respondió "
                            + code
                            + " al leer "
                            + sensor.stateTag()
                            + "."
            );
        }

        if (!response.isValidBoolean(alias)) {
            throw new IllegalStateException(
                    sensor.stateTag() + " no devolvió un BOOL."
            );
        }

        return response.getBoolean(alias);
    }

    private String alias(long deviceId) {
        return "diagnostic_" + deviceId;
    }

    private String normalizeStatus(String requestedStatus) {
        if (requestedStatus == null || requestedStatus.isBlank()) {
            return null;
        }

        String status = requestedStatus.trim().toUpperCase(Locale.ROOT);

        if (!SESSION_STATUSES.contains(status)) {
            throw new BadRequestException(
                    "Estado de diagnóstico inválido: "
                            + requestedStatus
                            + "."
            );
        }

        return status;
    }

    private SessionRow mapSession(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new SessionRow(
                resultSet.getLong("id"),
                resultSet.getString("status"),
                resultSet.getString("started_by"),
                resultSet.getString("started_by_role"),
                resultSet.getTimestamp("started_at").toInstant(),
                resultSet.getTimestamp("expires_at").toInstant(),
                toInstant(resultSet.getTimestamp("completed_at")),
                resultSet.getString("message")
        );
    }

    private SensorDiagnosticResponse.Item mapItem(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new SensorDiagnosticResponse.Item(
                resultSet.getLong("id"),
                resultSet.getString("device_code"),
                resultSet.getString("device_name"),
                resultSet.getString("area_code"),
                resultSet.getString("area_name"),
                resultSet.getString("device_type"),
                resultSet.getString("plc_state_tag"),
                resultSet.getBoolean("initial_state"),
                resultSet.getBoolean("saw_inactive"),
                resultSet.getBoolean("saw_active"),
                resultSet.getString("status"),
                toInstant(resultSet.getTimestamp("passed_at"))
        );
    }

    private SensorDiagnosticResponse toResponse(
            SessionRow session,
            List<SensorDiagnosticResponse.Item> items
    ) {
        int remainingSeconds = "RUNNING".equals(session.status())
                ? (int) Math.max(
                        0,
                        Duration.between(
                                Instant.now(),
                                session.expiresAt()
                        ).toSeconds()
                )
                : 0;

        return new SensorDiagnosticResponse(
                session.id(),
                session.status(),
                session.startedBy(),
                session.startedByRole(),
                session.startedAt(),
                session.expiresAt(),
                session.completedAt(),
                remainingSeconds,
                session.message(),
                items
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record DiagnosticSensor(
            long id,
            String code,
            String name,
            String areaCode,
            String areaName,
            String type,
            String stateTag
    ) {
    }

    record RunningItem(
            long id,
            long sessionId,
            long deviceId,
            String deviceCode,
            String deviceName,
            String areaCode,
            String areaName,
            String deviceType,
            String stateTag,
            boolean sawInactive,
            boolean sawActive,
            Instant expiresAt
    ) {
    }

    private record SessionRow(
            long id,
            String status,
            String startedBy,
            String startedByRole,
            Instant startedAt,
            Instant expiresAt,
            Instant completedAt,
            String message
    ) {
    }
}
