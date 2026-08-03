package com.icap.logicoti.event;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class SensorEventHistoryQueryService {

    private static final String HISTORY_COLUMNS = """
            SELECT
                history.id,
                history.device_code,
                device.name AS device_name,
                history.area_code,
                area.name AS area_name,
                history.device_type,
                history.plc_state_tag,
                history.previous_state,
                history.current_state,
                history.event_type,
                history.severity,
                history.message,
                history.detected_at
            """;

    private static final String HISTORY_FROM = """
            FROM device_event_history history
            INNER JOIN building_device device
                ON device.id = history.device_id
            INNER JOIN building_area area
                ON area.id = device.area_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public SensorEventHistoryQueryService(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public SensorEventHistoryPageResponse find(
            String requestedAreaCode,
            String requestedDeviceCode,
            String requestedDeviceType,
            String requestedEventType,
            String requestedSeverity,
            Instant from,
            Instant to,
            int requestedLimit,
            int requestedOffset
    ) {
        int limit = Math.max(1, Math.min(requestedLimit, 500));
        int offset = Math.max(0, requestedOffset);

        EventFilter filter = buildFilter(
                requestedAreaCode,
                requestedDeviceCode,
                requestedDeviceType,
                requestedEventType,
                requestedSeverity,
                from,
                to
        );

        String dataQuery = HISTORY_COLUMNS
                + HISTORY_FROM
                + filter.whereClause()
                + """
                 ORDER BY history.detected_at DESC, history.id DESC
                 LIMIT ? OFFSET ?
                """;

        List<Object> dataParameters =
                new ArrayList<>(filter.parameters());

        dataParameters.add(limit);
        dataParameters.add(offset);

        List<SensorEventHistoryResponse> items =
                jdbcTemplate.query(
                        dataQuery,
                        this::mapEvent,
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

        return new SensorEventHistoryPageResponse(
                items,
                total == null ? 0 : total,
                limit,
                offset
        );
    }

    @Transactional(readOnly = true)
    public List<SensorEventHistoryResponse> findActiveSmokeAlarms() {
        String query = HISTORY_COLUMNS
                + """
                FROM (
                    SELECT DISTINCT ON (history.device_id)
                        history.*
                    FROM device_event_history history
                    WHERE history.device_type = 'SMOKE'
                    ORDER BY
                        history.device_id,
                        history.detected_at DESC,
                        history.id DESC
                ) history
                INNER JOIN building_device device
                    ON device.id = history.device_id
                INNER JOIN building_area area
                    ON area.id = device.area_id
                WHERE history.current_state = TRUE
                ORDER BY history.detected_at DESC, history.id DESC
                """;

        return jdbcTemplate.query(
                query,
                this::mapEvent
        );
    }

    private EventFilter buildFilter(
            String requestedAreaCode,
            String requestedDeviceCode,
            String requestedDeviceType,
            String requestedEventType,
            String requestedSeverity,
            Instant from,
            Instant to
    ) {
        StringBuilder where =
                new StringBuilder(" WHERE 1 = 1");

        List<Object> parameters =
                new ArrayList<>();

        addUppercaseFilter(
                where,
                parameters,
                "history.area_code",
                requestedAreaCode
        );

        addUppercaseFilter(
                where,
                parameters,
                "history.device_code",
                requestedDeviceCode
        );

        addUppercaseFilter(
                where,
                parameters,
                "history.device_type",
                requestedDeviceType
        );

        addUppercaseFilter(
                where,
                parameters,
                "history.event_type",
                requestedEventType
        );

        addUppercaseFilter(
                where,
                parameters,
                "history.severity",
                requestedSeverity
        );

        if (from != null) {
            where.append(
                    " AND history.detected_at >= ?"
            );
            parameters.add(Timestamp.from(from));
        }

        if (to != null) {
            where.append(
                    " AND history.detected_at <= ?"
            );
            parameters.add(Timestamp.from(to));
        }

        return new EventFilter(
                where.toString(),
                parameters
        );
    }

    private void addUppercaseFilter(
            StringBuilder where,
            List<Object> parameters,
            String column,
            String requestedValue
    ) {
        String value = normalize(requestedValue);

        if (value == null) {
            return;
        }

        where.append(" AND UPPER(")
                .append(column)
                .append(") = ?");

        parameters.add(
                value.toUpperCase(Locale.ROOT)
        );
    }

    private SensorEventHistoryResponse mapEvent(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new SensorEventHistoryResponse(
                resultSet.getLong("id"),
                resultSet.getString("device_code"),
                resultSet.getString("device_name"),
                resultSet.getString("area_code"),
                resultSet.getString("area_name"),
                resultSet.getString("device_type"),
                resultSet.getString("plc_state_tag"),
                (Boolean) resultSet.getObject("previous_state"),
                resultSet.getBoolean("current_state"),
                resultSet.getString("event_type"),
                resultSet.getString("severity"),
                resultSet.getString("message"),
                resultSet
                        .getTimestamp("detected_at")
                        .toInstant()
        );
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private record EventFilter(
            String whereClause,
            List<Object> parameters
    ) {
    }
}
