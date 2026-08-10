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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SecurityScheduleService {

    private static final String SETTINGS_QUERY = """
            SELECT
                automatic_schedule_enabled,
                automatic_lighting_enabled,
                automatic_lighting_start_time,
                automatic_lighting_end_time,
                area_inactivity_enabled,
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

    private static final String LIGHTING_TARGETS_QUERY = """
            SELECT
                device.id,
                device.code,
                device.name,
                area.code AS area_code,
                area.name AS area_name,
                floor.code AS floor_code,
                floor.name AS floor_name,
                EXISTS (
                    SELECT 1
                    FROM security_automatic_lighting_target target
                    WHERE target.device_id = device.id
                ) AS selected
            FROM building_device device
            INNER JOIN building_area area
                ON area.id = device.area_id
            INNER JOIN building_floor floor
                ON floor.id = area.floor_id
            WHERE device.active = TRUE
              AND area.active = TRUE
              AND floor.active = TRUE
              AND device.device_type = 'LIGHT'
              AND device.controllable = TRUE
              AND device.plc_command_tag IS NOT NULL
              AND device.plc_state_tag IS NOT NULL
            ORDER BY floor.display_order,
                     area.display_order,
                     device.display_order,
                     device.id
            """;

    private static final String UPDATE_SETTINGS = """
            UPDATE security_settings
            SET automatic_schedule_enabled = ?,
                automatic_lighting_enabled = ?,
                automatic_lighting_start_time = ?,
                automatic_lighting_end_time = ?,
                area_inactivity_enabled = ?,
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

    private static final String DELETE_LIGHTING_TARGETS = """
            DELETE FROM security_automatic_lighting_target
            """;

    private static final String INSERT_LIGHTING_TARGET = """
            INSERT INTO security_automatic_lighting_target (device_id)
            SELECT id
            FROM building_device
            WHERE UPPER(code) = ?
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
                        resultSet.getBoolean(
                                "automatic_schedule_enabled"
                        ),
                        resultSet.getBoolean(
                                "automatic_lighting_enabled"
                        ),
                        resultSet.getObject(
                                "automatic_lighting_start_time",
                                LocalTime.class
                        ),
                        resultSet.getObject(
                                "automatic_lighting_end_time",
                                LocalTime.class
                        ),
                        resultSet.getBoolean(
                                "area_inactivity_enabled"
                        ),
                        resultSet.getString("timezone"),
                        resultSet.getInt("exit_delay_seconds"),
                        resultSet.getInt("light_inactivity_minutes"),
                        resultSet.getInt(
                                "minisplit_inactivity_minutes"
                        ),
                        resultSet.getInt(
                                "diagnostic_timeout_seconds"
                        ),
                        resultSet.getInt(
                                "diagnostic_validity_months"
                        ),
                        toInstant(
                                resultSet.getTimestamp("updated_at")
                        ),
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
                                    resultSet.getBoolean(
                                            "all_day_armed"
                                    ),
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
                settings.automaticLightingEnabled(),
                settings.automaticLightingStartTime(),
                settings.automaticLightingEndTime(),
                settings.areaInactivityEnabled(),
                settings.timezone(),
                settings.exitDelaySeconds(),
                settings.lightInactivityMinutes(),
                settings.minisplitInactivityMinutes(),
                settings.diagnosticTimeoutSeconds(),
                settings.diagnosticValidityMonths(),
                days,
                findLightingTargets(),
                settings.updatedAt(),
                settings.updatedBy()
        );
    }

    @Transactional
    public SecuritySettingsResponse updateSettings(
            SecuritySettingsUpdateRequest request,
            String username
    ) {
        Set<String> selectedLightCodes = validateRequest(request);

        jdbcTemplate.update(
                UPDATE_SETTINGS,
                request.automaticScheduleEnabled(),
                request.automaticLightingEnabled(),
                request.automaticLightingStartTime(),
                request.automaticLightingEndTime(),
                request.areaInactivityEnabled(),
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

        replaceLightingTargets(selectedLightCodes);

        if (!request.areaInactivityEnabled()) {
            jdbcTemplate.update(
                    "DELETE FROM security_area_inactivity_runtime"
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

    private Set<String> validateRequest(
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

        if (request.automaticLightingStartTime().equals(
                request.automaticLightingEndTime()
        )) {
            throw new BadRequestException(
                    "El inicio y fin de iluminación automática no pueden ser iguales."
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

        Set<String> selected = new LinkedHashSet<>();

        for (String requestedCode
                : request.automaticLightingTargetDeviceCodes()) {
            String code = requestedCode
                    .trim()
                    .toUpperCase(Locale.ROOT);

            if (!selected.add(code)) {
                throw new BadRequestException(
                        "La luz " + code + " está repetida."
                );
            }
        }

        if (request.automaticLightingEnabled() && selected.isEmpty()) {
            throw new BadRequestException(
                    "Selecciona al menos una luz para la automatización."
            );
        }

        Set<String> available = findLightingTargets()
                .stream()
                .map(SecuritySettingsResponse.LightingTarget::deviceCode)
                .map(code -> code.toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());

        if (!available.containsAll(selected)) {
            throw new BadRequestException(
                    "Se seleccionó una luz inexistente o no controlable."
            );
        }

        return selected;
    }

    private List<SecuritySettingsResponse.LightingTarget>
    findLightingTargets() {
        return jdbcTemplate.query(
                LIGHTING_TARGETS_QUERY,
                (resultSet, rowNumber) ->
                        new SecuritySettingsResponse.LightingTarget(
                                resultSet.getLong("id"),
                                resultSet.getString("code"),
                                resultSet.getString("name"),
                                resultSet.getString("area_code"),
                                resultSet.getString("area_name"),
                                resultSet.getString("floor_code"),
                                resultSet.getString("floor_name"),
                                resultSet.getBoolean("selected")
                        )
        );
    }

    private void replaceLightingTargets(Set<String> selectedCodes) {
        jdbcTemplate.update(DELETE_LIGHTING_TARGETS);

        selectedCodes.forEach(code ->
                jdbcTemplate.update(
                        INSERT_LIGHTING_TARGET,
                        code
                )
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record SettingsRow(
            boolean automaticScheduleEnabled,
            boolean automaticLightingEnabled,
            LocalTime automaticLightingStartTime,
            LocalTime automaticLightingEndTime,
            boolean areaInactivityEnabled,
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
