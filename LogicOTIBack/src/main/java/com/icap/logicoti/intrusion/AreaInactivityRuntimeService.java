package com.icap.logicoti.intrusion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class AreaInactivityRuntimeService {

    private final JdbcTemplate jdbcTemplate;

    public AreaInactivityRuntimeService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public MotionArea findMotionArea(String sensorCode) {
        List<MotionArea> areas = jdbcTemplate.query("""
                SELECT
                    area.id,
                    area.code,
                    area.name,
                    floor.code AS floor_code,
                    floor.name AS floor_name,
                    COALESCE(configuration.enabled, FALSE)
                        AS energy_saving_enabled,
                    COALESCE(zone_state.mode, 'DISARMED') AS zone_mode
                FROM building_device sensor
                INNER JOIN building_area area
                    ON area.id = sensor.area_id
                INNER JOIN building_floor floor
                    ON floor.id = area.floor_id
                LEFT JOIN security_area_energy_saving configuration
                    ON configuration.area_id = area.id
                LEFT JOIN security_zone_area zone_area
                    ON zone_area.area_id = area.id
                LEFT JOIN security_zone_state zone_state
                    ON zone_state.zone_code = zone_area.zone_code
                WHERE UPPER(sensor.code) = UPPER(?)
                  AND sensor.active = TRUE
                  AND area.active = TRUE
                  AND floor.active = TRUE
                  AND sensor.device_type = 'MOTION'
                """,
                (resultSet, rowNumber) -> new MotionArea(
                        resultSet.getLong("id"),
                        resultSet.getString("code"),
                        resultSet.getString("name"),
                        resultSet.getString("floor_code"),
                        resultSet.getString("floor_name"),
                        resultSet.getBoolean("energy_saving_enabled"),
                        resultSet.getString("zone_mode")
                ),
                sensorCode
        );

        return areas.isEmpty() ? null : areas.getFirst();
    }

    @Transactional
    public void recordActivity(
            long areaId,
            Instant motionAt,
            Instant lightTurnOffAt,
            Instant minisplitTurnOffAt
    ) {
        int updated = jdbcTemplate.update("""
                UPDATE security_area_inactivity_runtime
                SET last_motion_at = ?,
                    light_turn_off_at = ?,
                    minisplit_turn_off_at = ?,
                    light_processed = FALSE,
                    minisplit_processed = FALSE,
                    lights_turned_off = 0,
                    minisplits_turned_off = 0,
                    updated_at = CURRENT_TIMESTAMP
                WHERE area_id = ?
                """,
                Timestamp.from(motionAt),
                Timestamp.from(lightTurnOffAt),
                Timestamp.from(minisplitTurnOffAt),
                areaId
        );

        if (updated > 0) {
            return;
        }

        try {
            jdbcTemplate.update("""
                INSERT INTO security_area_inactivity_runtime (
                    area_id,
                    last_motion_at,
                    light_turn_off_at,
                    minisplit_turn_off_at,
                    light_processed,
                    minisplit_processed,
                    lights_turned_off,
                    minisplits_turned_off,
                    updated_at
                )
                VALUES (?, ?, ?, ?, FALSE, FALSE, 0, 0, CURRENT_TIMESTAMP)
                """,
                areaId,
                Timestamp.from(motionAt),
                Timestamp.from(lightTurnOffAt),
                Timestamp.from(minisplitTurnOffAt)
            );
        } catch (DuplicateKeyException exception) {
            recordActivity(
                    areaId,
                    motionAt,
                    lightTurnOffAt,
                    minisplitTurnOffAt
            );
        }
    }

    @Transactional(readOnly = true)
    public List<RuntimeArea> findDueAreas(Instant now) {
        return findRuntimeAreas("""
                AND (
                    (runtime.light_processed = FALSE
                     AND runtime.light_turn_off_at <= ?)
                    OR (runtime.minisplit_processed = FALSE
                        AND runtime.minisplit_turn_off_at <= ?)
                )
                """,
                new Object[]{Timestamp.from(now), Timestamp.from(now)}
        );
    }

    @Transactional(readOnly = true)
    public List<RuntimeArea> findAll() {
        return findRuntimeAreas("", new Object[0]);
    }

    private List<RuntimeArea> findRuntimeAreas(
            String whereClause,
            Object[] parameters
    ) {
        String query = """
                SELECT
                    area.id,
                    area.code,
                    area.name,
                    floor.code AS floor_code,
                    floor.name AS floor_name,
                    runtime.last_motion_at,
                    runtime.light_turn_off_at,
                    runtime.minisplit_turn_off_at,
                    runtime.light_processed,
                    runtime.minisplit_processed,
                    runtime.lights_turned_off,
                    runtime.minisplits_turned_off
                FROM security_area_inactivity_runtime runtime
                INNER JOIN building_area area
                    ON area.id = runtime.area_id
                INNER JOIN building_floor floor
                    ON floor.id = area.floor_id
                WHERE area.active = TRUE
                  AND floor.active = TRUE
                %s
                ORDER BY runtime.last_motion_at DESC, area.id
                """.formatted(whereClause);

        return jdbcTemplate.query(
                query,
                (resultSet, rowNumber) -> new RuntimeArea(
                        resultSet.getLong("id"),
                        resultSet.getString("code"),
                        resultSet.getString("name"),
                        resultSet.getString("floor_code"),
                        resultSet.getString("floor_name"),
                        resultSet.getTimestamp("last_motion_at").toInstant(),
                        resultSet.getTimestamp("light_turn_off_at").toInstant(),
                        resultSet.getTimestamp("minisplit_turn_off_at").toInstant(),
                        resultSet.getBoolean("light_processed"),
                        resultSet.getBoolean("minisplit_processed"),
                        resultSet.getInt("lights_turned_off"),
                        resultSet.getInt("minisplits_turned_off")
                ),
                parameters
        );
    }

    @Transactional
    public int removeInactiveEntries() {
        return jdbcTemplate.update("""
                DELETE FROM security_area_inactivity_runtime runtime
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM building_area area
                    INNER JOIN building_floor floor
                        ON floor.id = area.floor_id
                    WHERE area.id = runtime.area_id
                      AND area.active = TRUE
                      AND floor.active = TRUE
                )
                """);
    }

    @Transactional(readOnly = true)
    public boolean canManageArea(String areaCode) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM building_area area
                INNER JOIN security_area_energy_saving configuration
                    ON configuration.area_id = area.id
                   AND configuration.enabled = TRUE
                LEFT JOIN security_zone_area zone_area
                    ON zone_area.area_id = area.id
                LEFT JOIN security_zone_state zone_state
                    ON zone_state.zone_code = zone_area.zone_code
                WHERE UPPER(area.code) = UPPER(?)
                  AND area.active = TRUE
                  AND COALESCE(zone_state.mode, 'DISARMED')
                      NOT IN ('ARMING', 'ARMED', 'ARMED_WITH_BYPASS', 'ALARM')
                """,
                Integer.class,
                areaCode
        );

        return count != null && count > 0;
    }

    @Transactional
    public void release(long areaId) {
        jdbcTemplate.update(
                "DELETE FROM security_area_inactivity_runtime WHERE area_id = ?",
                areaId
        );
    }

    @Transactional(readOnly = true)
    public boolean isAutomaticLightingTarget(String deviceCode) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM security_automatic_lighting_target target
                INNER JOIN building_device device
                    ON device.id = target.device_id
                INNER JOIN building_area area
                    ON area.id = device.area_id
                INNER JOIN building_floor floor
                    ON floor.id = area.floor_id
                WHERE UPPER(device.code) = UPPER(?)
                  AND device.active = TRUE
                  AND area.active = TRUE
                  AND floor.active = TRUE
                  AND device.device_type = 'LIGHT'
                  AND device.controllable = TRUE
                """,
                Integer.class,
                deviceCode
        );

        return count != null && count > 0;
    }

    @Transactional
    public void markLightProcessed(long areaId, int turnedOff) {
        jdbcTemplate.update("""
                UPDATE security_area_inactivity_runtime
                SET light_processed = TRUE,
                    lights_turned_off = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE area_id = ?
                """,
                turnedOff,
                areaId
        );
    }

    @Transactional
    public void markMinisplitProcessed(long areaId, int turnedOff) {
        jdbcTemplate.update("""
                UPDATE security_area_inactivity_runtime
                SET minisplit_processed = TRUE,
                    minisplits_turned_off = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE area_id = ?
                """,
                turnedOff,
                areaId
        );
    }

    @Transactional
    public void postponeLight(long areaId, Instant retryAt) {
        jdbcTemplate.update("""
                UPDATE security_area_inactivity_runtime
                SET light_turn_off_at = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE area_id = ?
                """,
                Timestamp.from(retryAt),
                areaId
        );
    }

    @Transactional
    public void postponeMinisplit(long areaId, Instant retryAt) {
        jdbcTemplate.update("""
                UPDATE security_area_inactivity_runtime
                SET minisplit_turn_off_at = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE area_id = ?
                """,
                Timestamp.from(retryAt),
                areaId
        );
    }

    public record MotionArea(
            long id,
            String code,
            String name,
            String floorCode,
            String floorName,
            boolean energySavingEnabled,
            String zoneMode
    ) {
        public boolean zoneArmed() {
            return switch (zoneMode) {
                case "ARMING", "ARMED", "ARMED_WITH_BYPASS", "ALARM" -> true;
                default -> false;
            };
        }
    }

    public record RuntimeArea(
            long id,
            String code,
            String name,
            String floorCode,
            String floorName,
            Instant lastMotionAt,
            Instant lightTurnOffAt,
            Instant minisplitTurnOffAt,
            boolean lightProcessed,
            boolean minisplitProcessed,
            int lightsTurnedOff,
            int minisplitsTurnedOff
    ) {
    }
}
