package com.icap.logicoti.event;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SensorEventHistoryService {

    private static final String ACTIVE_SENSORS_QUERY = """
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
            WHERE device.active = TRUE
              AND area.active = TRUE
              AND device.device_type IN (
                  'MOTION',
                  'SMOKE'
              )
              AND device.plc_state_tag IS NOT NULL
              AND TRIM(device.plc_state_tag) <> ''
            ORDER BY area.display_order, device.display_order
            """;

    private static final String LATEST_STATES_QUERY = """
            SELECT DISTINCT ON (device_id)
                device_id,
                current_state
            FROM device_event_history
            ORDER BY
                device_id,
                detected_at DESC,
                id DESC
            """;

    private static final String INSERT_EVENT_QUERY = """
            INSERT INTO device_event_history (
                device_id,
                device_code,
                area_code,
                device_type,
                plc_state_tag,
                previous_state,
                current_state,
                event_type,
                severity,
                message
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public SensorEventHistoryService(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<SensorDefinition> findActiveSensors() {

        return jdbcTemplate.query(
                ACTIVE_SENSORS_QUERY,
                (resultSet, rowNumber) -> new SensorDefinition(
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

    @Transactional(readOnly = true)
    public Map<Long, Boolean> findLatestStates() {

        return jdbcTemplate.query(
                LATEST_STATES_QUERY,
                resultSet -> {

                    Map<Long, Boolean> states =
                            new HashMap<>();

                    while (resultSet.next()) {
                        states.put(
                                resultSet.getLong("device_id"),
                                resultSet.getBoolean("current_state")
                        );
                    }

                    return states;
                }
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveChange(
            SensorDefinition sensor,
            Boolean previousState,
            boolean currentState
    ) {
        String eventType = currentState
                ? "ACTIVATED"
                : "CLEARED";

        String severity = determineSeverity(
                sensor.type(),
                currentState
        );

        String message = createMessage(
                sensor,
                currentState
        );

        int insertedRows = jdbcTemplate.update(
                INSERT_EVENT_QUERY,
                sensor.id(),
                sensor.code(),
                sensor.areaCode(),
                sensor.type(),
                sensor.stateTag(),
                previousState,
                currentState,
                eventType,
                severity,
                message
        );

        if (insertedRows != 1) {
            throw new IllegalStateException(
                    "No se pudo guardar el evento de "
                            + sensor.code()
                            + "."
            );
        }
    }

    private String determineSeverity(
            String requestedType,
            boolean currentState
    ) {
        String type = requestedType
                .toUpperCase(Locale.ROOT);

        if ("SMOKE".equals(type) && currentState) {
            return "CRITICAL";
        }

        return "INFO";
    }

    private String createMessage(
            SensorDefinition sensor,
            boolean currentState
    ) {
        String type = sensor.type()
                .toUpperCase(Locale.ROOT);

        if ("SMOKE".equals(type)) {
            return currentState
                    ? "Humo detectado en "
                        + sensor.areaName()
                        + "."
                    : "Sensor de humo restablecido en "
                        + sensor.areaName()
                        + ".";
        }

        if ("MOTION".equals(type)) {
            return currentState
                    ? "Movimiento detectado en "
                        + sensor.areaName()
                        + "."
                    : "Movimiento finalizado en "
                        + sensor.areaName()
                        + ".";
        }

        return "El sensor "
                + sensor.name()
                + " cambió de estado.";
    }
}