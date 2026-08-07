package com.icap.logicoti.intrusion;

import com.icap.logicoti.exception.BadRequestException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SecurityScheduleService {

    private static final String SETTINGS_QUERY = """
            SELECT
                automatic_schedule_enabled,
                timezone,
                exit_delay_seconds,
                light_inactivity_minutes,
                minisplit_inactivity_minutes,
                diagnostic_timeout_seconds,
                diagnostic_validity_months,
                updated_at,
                updated_by
            FROM security_settings
            WHERE id = 1
            """;

    private static final String DAYS_QUERY = """
            SELECT
                day_of_week,
                enabled,
                all_day_armed,
                arm_time,
                disarm_time
            FROM security_schedule_day
            ORDER BY day_of_week
            """;

    private static final String UPDATE_SETTINGS = """
            UPDATE security_settings
            SET automatic_schedule_enabled = ?,
                timezone = ?,
                exit_delay_seconds = ?,
                light_inactivity_minutes = ?,
                minisplit_inactivity_minutes = ?,
                diagnostic_timeout_seconds = ?,
                diagnostic_validity_months = ?,
                updated_at = CURRENT_TIMESTAMP,
                updated_by = ?
            WHERE id = 1
            """;

    private static final String UPDATE_DAY = """
            UPDATE security_schedule_day
            SET enabled = ?,
                all_day_armed = ?,
                arm_time = ?,
                disarm_time = ?
            WHERE day_of_week = ?
            """;

    private static final List<String> DAY_NAMES = List.of(
            "",
            "Lunes",
            "Martes",
            "Miércoles",
            "Jueves",
            "Viernes",
            "Sábado",
            "Domingo"
    );

    private final JdbcTemplate jdbcTemplate;

    public SecurityScheduleService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public SecuritySettingsResponse getSettings() {
        SettingsRow settings = jdbcTemplate.queryForObject(
                SETTINGS_QUERY,
                (resultSet, rowNumber) -> new SettingsRow(
                        resultSet.getBoolean("automatic_schedule_enabled"),
                        resultSet.getString("timezone"),
                        resultSet.getInt("exit_delay_seconds"),
                        resultSet.getInt("light_inactivity_minutes"),
                        resultSet.getInt("minisplit_inactivity_minutes"),
                        resultSet.getInt("diagnostic_timeout_seconds"),
                        resultSet.getInt("diagnostic_validity_months"),
                        toInstant(resultSet.getTimestamp("updated_at")),
                        resultSet.getString("updated_by")
                )
        );

        if (settings == null) {
            throw new IllegalStateException(
                    "No existe la configuración de seguridad."
            );
        }

        List<SecuritySettingsResponse.ScheduleDay> days =
                jdbcTemplate.query(
                        DAYS_QUERY,
                        (resultSet, rowNumber) -> {
                            int dayOfWeek =
                                    resultSet.getInt("day_of_week");

                            return new SecuritySettingsResponse.ScheduleDay(
                                    dayOfWeek,
                                    DAY_NAMES.get(dayOfWeek),
                                    resultSet.getBoolean("enabled"),
                                    resultSet.getBoolean("all_day_armed"),
                                    resultSet.getObject(
                                            "arm_time",
                                            LocalTime.class
                                    ),
                                    resultSet.getObject(
                                            "disarm_time",
                                            LocalTime.class
                                    )
                            );
                        }
                );

        return new SecuritySettingsResponse(
                settings.automaticScheduleEnabled(),
                settings.timezone(),
                settings.exitDelaySeconds(),
                settings.lightInactivityMinutes(),
                settings.minisplitInactivityMinutes(),
                settings.diagnosticTimeoutSeconds(),
                settings.diagnosticValidityMonths(),
                days,
                settings.updatedAt(),
                settings.updatedBy()
        );
    }

    @Transactional
    public SecuritySettingsResponse updateSettings(
            SecuritySettingsUpdateRequest request,
            String username
    ) {
        validateRequest(request);

        jdbcTemplate.update(
                UPDATE_SETTINGS,
                request.automaticScheduleEnabled(),
                request.timezone().trim(),
                request.exitDelaySeconds(),
                request.lightInactivityMinutes(),
                request.minisplitInactivityMinutes(),
                request.diagnosticTimeoutSeconds(),
                request.diagnosticValidityMonths(),
                username
        );

        for (SecuritySettingsUpdateRequest.ScheduleDay day
                : request.days()) {

            jdbcTemplate.update(
                    UPDATE_DAY,
                    day.enabled(),
                    day.allDayArmed(),
                    day.armTime(),
                    day.disarmTime(),
                    day.dayOfWeek()
            );
        }

        return getSettings();
    }

    @Transactional(readOnly = true)
    public ScheduleDecision evaluate(Instant instant) {
        return SecurityScheduleCalculator.evaluate(
                getSettings(),
                instant
        );
    }

    private void validateRequest(
            SecuritySettingsUpdateRequest request
    ) {
        try {
            ZoneId.of(request.timezone().trim());
        } catch (ZoneRulesException exception) {
            throw new BadRequestException(
                    "La zona horaria "
                            + request.timezone()
                            + " no es válida."
            );
        }

        Set<Integer> uniqueDays = new HashSet<>();

        for (SecuritySettingsUpdateRequest.ScheduleDay day
                : request.days()) {

            if (!uniqueDays.add(day.dayOfWeek())) {
                throw new BadRequestException(
                        "El día "
                                + day.dayOfWeek()
                                + " está repetido."
                );
            }

            if (!day.allDayArmed()
                    && day.armTime().equals(day.disarmTime())) {

                throw new BadRequestException(
                        "La hora de armado y desarmado no pueden ser iguales."
                );
            }
        }
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record SettingsRow(
            boolean automaticScheduleEnabled,
            String timezone,
            int exitDelaySeconds,
            int lightInactivityMinutes,
            int minisplitInactivityMinutes,
            int diagnosticTimeoutSeconds,
            int diagnosticValidityMonths,
            Instant updatedAt,
            String updatedBy
    ) {
    }
}
