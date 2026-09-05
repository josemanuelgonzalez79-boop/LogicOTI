package com.icap.logicoti.audit;

import com.icap.logicoti.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DeviceCommandHistoryService {

    private static final String DEVICE_QUERY = """
            SELECT
                device.id,
                device.code,
                area.code AS area_code,
                device.plc_command_tag
            FROM building_device device
            INNER JOIN building_area area
                ON area.id = device.area_id
            WHERE UPPER(device.code) = ?
              AND device.active = TRUE
              AND area.active = TRUE
            """;

    private static final String INSERT_QUERY = """
            INSERT INTO device_command_history (
                device_id,
                device_code,
                area_code,
                plc_command_tag,
                requested_value,
                status,
                message,
                requested_by,
                requested_by_role,
                source_ip
            )
            VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?)
            RETURNING id
            """;

    private static final String COMPLETE_QUERY = """
            UPDATE device_command_history
            SET command_value = ?,
                feedback_value = ?,
                status = ?,
                message = ?,
                duration_ms = ?,
                completed_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;

    private static final String HISTORY_COLUMNS = """
            SELECT
                history.id,
                history.device_code,
                device.name AS device_name,
                history.area_code,
                area.name AS area_name,
                history.plc_command_tag,
                history.requested_value,
                history.command_value,
                history.feedback_value,
                history.status,
                history.message,
                history.requested_by,
                history.requested_by_role,
                history.source_ip,
                history.duration_ms,
                history.requested_at,
                history.completed_at
            """;

    private static final String HISTORY_FROM = """
            FROM device_command_history history
            INNER JOIN building_device device
                ON device.id = history.device_id
            INNER JOIN building_area area
                ON area.id = device.area_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public DeviceCommandHistoryService(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long start(
            String requestedDeviceCode,
            boolean requestedValue,
            String requestedBy,
            String requestedByRole,
            String sourceIp
    ) {
        AuditDevice device = findDevice(
                requestedDeviceCode
        );

        Long historyId = jdbcTemplate.queryForObject(
                INSERT_QUERY,
                Long.class,
                device.id(),
                device.code(),
                device.areaCode(),
                device.commandTag(),
                requestedValue,
                "Comando recibido por la API.",
                requestedBy,
                requestedByRole,
                sourceIp
        );

        if (historyId == null) {
            throw new IllegalStateException(
                    "No se pudo crear el histórico del comando."
            );
        }

        return historyId;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(
            long historyId,
            Boolean commandValue,
            Boolean feedbackValue,
            String status,
            String message,
            long durationMs
    ) {
        int updatedRows = jdbcTemplate.update(
                COMPLETE_QUERY,
                commandValue,
                feedbackValue,
                status,
                limitMessage(message),
                durationMs,
                historyId
        );

        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "No se encontró el histórico "
                            + historyId
                            + "."
            );
        }
    }

    @Transactional(readOnly = true)
    public DeviceCommandHistoryPageResponse find(
            DeviceCommandHistoryQuery query
    ) {
        int limit = Math.clamp(
                query.limit(),
                1,
                500
        );

        int offset = Math.max(
                0,
                query.offset()
        );

        HistoryFilter filter = buildFilter(
                query.areaCode(),
                query.deviceCode(),
                query.status(),
                query.requestedBy(),
                query.from(),
                query.to()
        );

        String dataQuery = HISTORY_COLUMNS
                + HISTORY_FROM
                + filter.whereClause()
                + "\nORDER BY history.requested_at DESC, history.id DESC"
                + "\nLIMIT ? OFFSET ?";

        List<Object> dataParameters =
                new ArrayList<>(
                        filter.parameters()
                );

        dataParameters.add(limit);
        dataParameters.add(offset);

        List<DeviceCommandHistoryResponse> items =
                jdbcTemplate.query(
                        dataQuery,
                        this::mapHistory,
                        dataParameters.toArray()
                );

        String countQuery = """
                SELECT COUNT(*)
                """
                + HISTORY_FROM
                + filter.whereClause();

        Long total = jdbcTemplate.queryForObject(
                countQuery,
                Long.class,
                filter.parameters().toArray()
        );

        return new DeviceCommandHistoryPageResponse(
                items,
                total == null ? 0 : total,
                limit,
                offset
        );
    }

    private HistoryFilter buildFilter(
            String requestedAreaCode,
            String requestedDeviceCode,
            String requestedStatus,
            String requestedBy,
            java.time.Instant from,
            java.time.Instant to
    ) {
        StringBuilder where =
                new StringBuilder(
                        " WHERE 1 = 1"
                );

        List<Object> parameters =
                new ArrayList<>();

        String areaCode =
                normalize(requestedAreaCode);

        if (areaCode != null) {
            where.append(
                    " AND UPPER(history.area_code) = ?"
            );

            parameters.add(
                    areaCode.toUpperCase(
                            Locale.ROOT
                    )
            );
        }

        String deviceCode =
                normalize(requestedDeviceCode);

        if (deviceCode != null) {
            where.append(
                    " AND UPPER(history.device_code) = ?"
            );

            parameters.add(
                    deviceCode.toUpperCase(
                            Locale.ROOT
                    )
            );
        }

        String status =
                normalize(requestedStatus);

        if (status != null) {
            where.append(
                    " AND UPPER(history.status) = ?"
            );

            parameters.add(
                    status.toUpperCase(
                            Locale.ROOT
                    )
            );
        }

        String username =
                normalize(requestedBy);

        if (username != null) {
            where.append(
                    " AND LOWER(history.requested_by) = LOWER(?)"
            );

            parameters.add(username);
        }

        if (from != null) {
            where.append(
                    " AND history.requested_at >= ?"
            );

            parameters.add(
                    Timestamp.from(from)
            );
        }

        if (to != null) {
            where.append(
                    " AND history.requested_at <= ?"
            );

            parameters.add(
                    Timestamp.from(to)
            );
        }

        return new HistoryFilter(
                where.toString(),
                parameters
        );
    }

    private DeviceCommandHistoryResponse mapHistory(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {

        Timestamp completedAt =
                resultSet.getTimestamp(
                        "completed_at"
                );

        Number duration =
                (Number) resultSet.getObject(
                        "duration_ms"
                );

        return new DeviceCommandHistoryResponse(
                resultSet.getLong("id"),
                resultSet.getString(
                        "device_code"
                ),
                resultSet.getString(
                        "device_name"
                ),
                resultSet.getString(
                        "area_code"
                ),
                resultSet.getString(
                        "area_name"
                ),
                resultSet.getString(
                        "plc_command_tag"
                ),
                resultSet.getBoolean(
                        "requested_value"
                ),
                (Boolean) resultSet.getObject(
                        "command_value"
                ),
                (Boolean) resultSet.getObject(
                        "feedback_value"
                ),
                resultSet.getString("status"),
                resultSet.getString("message"),
                resultSet.getString(
                        "requested_by"
                ),
                resultSet.getString(
                        "requested_by_role"
                ),
                resultSet.getString(
                        "source_ip"
                ),
                duration == null
                        ? null
                        : duration.longValue(),
                resultSet
                        .getTimestamp(
                                "requested_at"
                        )
                        .toInstant(),
                completedAt == null
                        ? null
                        : completedAt.toInstant()
        );
    }

    private AuditDevice findDevice(
            String requestedDeviceCode
    ) {
        String deviceCode =
                requestedDeviceCode
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        List<AuditDevice> devices =
                jdbcTemplate.query(
                        DEVICE_QUERY,
                        (resultSet, rowNumber) ->
                                new AuditDevice(
                                        resultSet.getLong(
                                                "id"
                                        ),
                                        resultSet.getString(
                                                "code"
                                        ),
                                        resultSet.getString(
                                                "area_code"
                                        ),
                                        resultSet.getString(
                                                "plc_command_tag"
                                        )
                                ),
                        deviceCode
                );

        if (devices.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No se encontró el dispositivo "
                            + deviceCode
                            + "."
            );
        }

        return devices.getFirst();
    }

    private String normalize(
            String value
    ) {
        if (value == null
                || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String limitMessage(
            String message
    ) {
        if (message == null
                || message.length() <= 500) {
            return message;
        }

        return message.substring(
                0,
                500
        );
    }

    private record AuditDevice(
            Long id,
            String code,
            String areaCode,
            String commandTag
    ) {
    }

    private record HistoryFilter(
            String whereClause,
            List<Object> parameters
    ) {
    }
}