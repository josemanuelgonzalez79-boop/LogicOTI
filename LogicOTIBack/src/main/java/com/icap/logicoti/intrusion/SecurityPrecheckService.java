package com.icap.logicoti.intrusion;

import com.icap.logicoti.config.PlcProperties;
import com.icap.logicoti.intrusion.SecurityPrecheckResponse.Issue;
import com.icap.logicoti.plc.PlcCommunicationService;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SecurityPrecheckService {

    private static final String MOTION_SENSORS_QUERY = """
            SELECT
                device.id,
                device.code,
                device.name,
                area.code AS area_code,
                area.name AS area_name,
                device.plc_state_tag,
                bypass.id AS bypass_id,
                bypass.reason AS bypass_reason,
                latest_diagnostic.completed_at AS last_diagnostic_at
            FROM building_device device
            INNER JOIN building_area area
                ON area.id = device.area_id
            LEFT JOIN sensor_bypass_history bypass
                ON bypass.device_id = device.id
               AND bypass.active = TRUE
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
            ) latest_diagnostic ON TRUE
            WHERE device.active = TRUE
              AND area.active = TRUE
              AND device.device_type = 'MOTION'
            ORDER BY area.display_order, device.display_order
            """;

    private final JdbcTemplate jdbcTemplate;
    private final PlcProperties plcProperties;
    private final PlcCommunicationService plcCommunicationService;
    private final SecurityScheduleService scheduleService;

    public SecurityPrecheckService(
            JdbcTemplate jdbcTemplate,
            PlcProperties plcProperties,
            PlcCommunicationService plcCommunicationService,
            SecurityScheduleService scheduleService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.plcProperties = plcProperties;
        this.plcCommunicationService = plcCommunicationService;
        this.scheduleService = scheduleService;
    }

    @Transactional(readOnly = true)
    public SecurityPrecheckResponse check() {
        List<MotionSensor> sensors = findMotionSensors();
        List<Issue> initialIssues = new ArrayList<>();
        SecuritySettingsResponse settings =
                scheduleService.getSettings();

        if (sensors.isEmpty()) {
            initialIssues.add(systemIssue(
                    "NO_MOTION_SENSORS",
                    "ERROR",
                    true,
                    "No hay sensores de movimiento activos configurados."
            ));
        }

        for (MotionSensor sensor : sensors) {
            if (sensor.bypassed()) {
                initialIssues.add(sensorIssue(
                        "SENSOR_BYPASSED",
                        "WARNING",
                        false,
                        sensor,
                        "El sensor está omitido temporalmente. Motivo: "
                                + sensor.bypassReason()
                ));
                continue;
            }

            addDiagnosticIssue(
                    sensor,
                    settings,
                    initialIssues
            );

            if (!hasText(sensor.stateTag())) {
                initialIssues.add(sensorIssue(
                        "TAG_NOT_CONFIGURED",
                        "ERROR",
                        true,
                        sensor,
                        "El sensor no tiene plc_state_tag configurado."
                ));
            }
        }

        if (!plcProperties.isEnabled()) {
            initialIssues.add(systemIssue(
                    "PLC_DISABLED",
                    "ERROR",
                    true,
                    "La comunicación con el PLC está deshabilitada."
            ));

            return response(
                    false,
                    sensors,
                    0,
                    initialIssues
            );
        }

        if (!hasText(plcProperties.getConnectionString())) {
            initialIssues.add(systemIssue(
                    "PLC_NOT_CONFIGURED",
                    "ERROR",
                    true,
                    "No se configuró PLC_CONNECTION_STRING."
            ));

            return response(
                    false,
                    sensors,
                    0,
                    initialIssues
            );
        }

        List<MotionSensor> configuredSensors = sensors.stream()
                .filter(sensor -> !sensor.bypassed())
                .filter(sensor -> hasText(sensor.stateTag()))
                .toList();

        try {
            PlcReadOutcome outcome = plcCommunicationService.read(
                    connection -> {
                        if (!connection.getMetadata().isReadSupported()) {
                            throw new IllegalStateException(
                                    "La conexión no permite leer tags."
                            );
                        }

                        if (configuredSensors.isEmpty()) {
                            return new PlcReadOutcome(
                                    0,
                                    List.of()
                            );
                        }

                        PlcReadRequest.Builder builder =
                                connection.readRequestBuilder();

                        configuredSensors.forEach(sensor ->
                                builder.addTagAddress(
                                        alias(sensor),
                                        sensor.stateTag()
                                )
                        );

                        PlcReadResponse plcResponse = builder
                                .build()
                                .execute()
                                .get(
                                        plcProperties
                                                .getTimeout()
                                                .toMillis(),
                                        TimeUnit.MILLISECONDS
                                );

                        return inspectResponse(
                                configuredSensors,
                                plcResponse
                        );
                    }
            );

            List<Issue> allIssues = new ArrayList<>(initialIssues);
            allIssues.addAll(outcome.issues());

            return response(
                    true,
                    sensors,
                    outcome.readableSensors(),
                    allIssues
            );

        } catch (Exception exception) {
            initialIssues.add(systemIssue(
                    "PLC_UNAVAILABLE",
                    "ERROR",
                    true,
                    "No fue posible revisar los sensores: "
                            + safeMessage(exception)
            ));

            return response(
                    false,
                    sensors,
                    0,
                    initialIssues
            );
        }
    }

    private PlcReadOutcome inspectResponse(
            List<MotionSensor> sensors,
            PlcReadResponse response
    ) {
        int readableSensors = 0;
        List<Issue> issues = new ArrayList<>();

        for (MotionSensor sensor : sensors) {
            String alias = alias(sensor);
            PlcResponseCode responseCode =
                    response.getResponseCode(alias);

            if (responseCode != PlcResponseCode.OK) {
                issues.add(sensorIssue(
                        "TAG_READ_FAILED",
                        "ERROR",
                        true,
                        sensor,
                        "El PLC respondió "
                                + responseCode
                                + " al leer "
                                + sensor.stateTag()
                                + "."
                ));
                continue;
            }

            if (!response.isValidBoolean(alias)) {
                issues.add(sensorIssue(
                        "INVALID_TAG_TYPE",
                        "ERROR",
                        true,
                        sensor,
                        "El tag "
                                + sensor.stateTag()
                                + " no devolvió un BOOL."
                ));
                continue;
            }

            readableSensors++;

            if (response.getBoolean(alias)) {
                issues.add(sensorIssue(
                        "SENSOR_ACTIVE",
                        "WARNING",
                        false,
                        sensor,
                        "El sensor detecta movimiento. "
                                + "No bloquea el tiempo de salida."
                ));
            }
        }

        return new PlcReadOutcome(
                readableSensors,
                issues
        );
    }

    private SecurityPrecheckResponse response(
            boolean plcConnected,
            List<MotionSensor> sensors,
            int readableSensors,
            List<Issue> issues
    ) {
        boolean ready = issues.stream()
                .noneMatch(Issue::blocking);

        return new SecurityPrecheckResponse(
                ready,
                plcProperties.isEnabled(),
                plcConnected,
                sensors.size(),
                readableSensors,
                List.copyOf(issues),
                Instant.now()
        );
    }

    private List<MotionSensor> findMotionSensors() {
        return jdbcTemplate.query(
                MOTION_SENSORS_QUERY,
                (resultSet, rowNumber) -> new MotionSensor(
                        resultSet.getLong("id"),
                        resultSet.getString("code"),
                        resultSet.getString("name"),
                        resultSet.getString("area_code"),
                        resultSet.getString("area_name"),
                        resultSet.getString("plc_state_tag"),
                        resultSet.getObject("bypass_id") != null,
                        resultSet.getString("bypass_reason"),
                        toInstant(resultSet.getTimestamp(
                                "last_diagnostic_at"
                        ))
                )
        );
    }

    private void addDiagnosticIssue(
            MotionSensor sensor,
            SecuritySettingsResponse settings,
            List<Issue> issues
    ) {
        if (sensor.lastDiagnosticAt() == null) {
            issues.add(sensorIssue(
                    "DIAGNOSTIC_MISSING",
                    "ERROR",
                    true,
                    sensor,
                    "El sensor todavía no tiene un diagnóstico aprobado."
            ));
            return;
        }

        ZoneId zoneId = ZoneId.of(settings.timezone());
        Instant validUntil = ZonedDateTime.ofInstant(
                sensor.lastDiagnosticAt(),
                zoneId
        ).plusMonths(
                settings.diagnosticValidityMonths()
        ).toInstant();

        if (!validUntil.isAfter(Instant.now())) {
            issues.add(sensorIssue(
                    "DIAGNOSTIC_EXPIRED",
                    "ERROR",
                    true,
                    sensor,
                    "El diagnóstico del sensor venció el "
                            + validUntil
                            + "."
            ));
        }
    }

    private Issue systemIssue(
            String code,
            String severity,
            boolean blocking,
            String message
    ) {
        return new Issue(
                code,
                severity,
                blocking,
                null,
                null,
                null,
                null,
                message
        );
    }

    private Issue sensorIssue(
            String code,
            String severity,
            boolean blocking,
            MotionSensor sensor,
            String message
    ) {
        return new Issue(
                code,
                severity,
                blocking,
                sensor.code(),
                sensor.name(),
                sensor.areaCode(),
                sensor.areaName(),
                message
        );
    }

    private String alias(MotionSensor sensor) {
        return "motion_" + sensor.id();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeMessage(Exception exception) {
        if (exception.getMessage() == null
                || exception.getMessage().isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return exception.getMessage();
    }

    private Instant toInstant(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record MotionSensor(
            long id,
            String code,
            String name,
            String areaCode,
            String areaName,
            String stateTag,
            boolean bypassed,
            String bypassReason,
            Instant lastDiagnosticAt
    ) {
    }

    private record PlcReadOutcome(
            int readableSensors,
            List<Issue> issues
    ) {
    }
}
