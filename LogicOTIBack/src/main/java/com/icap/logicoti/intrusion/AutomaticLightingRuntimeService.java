package com.icap.logicoti.intrusion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class AutomaticLightingRuntimeService {

    private final JdbcTemplate jdbcTemplate;

    public AutomaticLightingRuntimeService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<LightingDevice> findTargets() {
        return jdbcTemplate.query("""
                SELECT
                    device.id,
                    device.code,
                    device.name,
                    area.code AS area_code,
                    area.name AS area_name
                FROM security_automatic_lighting_target target
                INNER JOIN building_device device
                    ON device.id = target.device_id
                INNER JOIN building_area area
                    ON area.id = device.area_id
                INNER JOIN building_floor floor
                    ON floor.id = area.floor_id
                WHERE device.active = TRUE
                  AND area.active = TRUE
                  AND floor.active = TRUE
                  AND device.device_type = 'LIGHT'
                  AND device.controllable = TRUE
                ORDER BY area.display_order, device.display_order, device.id
                """,
                (resultSet, rowNumber) -> new LightingDevice(
                        resultSet.getLong("id"),
                        resultSet.getString("code"),
                        resultSet.getString("name"),
                        resultSet.getString("area_code"),
                        resultSet.getString("area_name")
                )
        );
    }

    @Transactional(readOnly = true)
    public List<RuntimeLight> findOwnedLights() {
        return findRuntimeLights("", new Object[0]);
    }

    @Transactional(readOnly = true)
    public List<RuntimeLight> findExpiredLights(Instant instant) {
        return findRuntimeLights(
                "AND runtime.turn_off_at <= ?",
                new Object[]{Timestamp.from(instant)}
        );
    }

    private List<RuntimeLight> findRuntimeLights(
            String whereClause,
            Object[] parameters
    ) {
        String query = """
                SELECT
                    device.id,
                    device.code,
                    device.name,
                    area.code AS area_code,
                    area.name AS area_name,
                    runtime.activated_at,
                    runtime.last_motion_at,
                    runtime.turn_off_at
                FROM security_automatic_lighting_runtime runtime
                INNER JOIN building_device device
                    ON device.id = runtime.device_id
                INNER JOIN building_area area
                    ON area.id = device.area_id
                INNER JOIN building_floor floor
                    ON floor.id = area.floor_id
                WHERE device.active = TRUE
                  AND area.active = TRUE
                  AND floor.active = TRUE
                  AND device.device_type = 'LIGHT'
                  AND device.controllable = TRUE
                %s
                ORDER BY runtime.turn_off_at, device.id
                """.formatted(whereClause);

        return jdbcTemplate.query(
                query,
                (resultSet, rowNumber) -> new RuntimeLight(
                        resultSet.getLong("id"),
                        resultSet.getString("code"),
                        resultSet.getString("name"),
                        resultSet.getString("area_code"),
                        resultSet.getString("area_name"),
                        resultSet.getTimestamp("activated_at").toInstant(),
                        resultSet.getTimestamp("last_motion_at").toInstant(),
                        resultSet.getTimestamp("turn_off_at").toInstant()
                ),
                parameters
        );
    }

    @Transactional
    public void claim(
            long deviceId,
            Instant motionAt,
            Instant turnOffAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO security_automatic_lighting_runtime (
                    device_id,
                    activated_at,
                    last_motion_at,
                    turn_off_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (device_id) DO UPDATE
                SET last_motion_at = EXCLUDED.last_motion_at,
                    turn_off_at = EXCLUDED.turn_off_at,
                    updated_at = CURRENT_TIMESTAMP
                """,
                deviceId,
                Timestamp.from(motionAt),
                Timestamp.from(motionAt),
                Timestamp.from(turnOffAt)
        );
    }

    @Transactional
    public void extendAll(Instant motionAt, Instant turnOffAt) {
        jdbcTemplate.update("""
                UPDATE security_automatic_lighting_runtime runtime
                SET last_motion_at = ?,
                    turn_off_at = ?,
                    updated_at = CURRENT_TIMESTAMP
                FROM building_device device
                INNER JOIN building_area area
                    ON area.id = device.area_id
                INNER JOIN building_floor floor
                    ON floor.id = area.floor_id
                WHERE runtime.device_id = device.id
                  AND device.active = TRUE
                  AND area.active = TRUE
                  AND floor.active = TRUE
                  AND device.device_type = 'LIGHT'
                  AND device.controllable = TRUE
                """,
                Timestamp.from(motionAt),
                Timestamp.from(turnOffAt)
        );
    }

    @Transactional
    public int removeInactiveEntries() {
        return jdbcTemplate.update("""
                DELETE FROM security_automatic_lighting_runtime runtime
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM building_device device
                    INNER JOIN building_area area
                        ON area.id = device.area_id
                    INNER JOIN building_floor floor
                        ON floor.id = area.floor_id
                    WHERE device.id = runtime.device_id
                      AND device.active = TRUE
                      AND area.active = TRUE
                      AND floor.active = TRUE
                      AND device.device_type = 'LIGHT'
                      AND device.controllable = TRUE
                )
                """);
    }

    @Transactional
    public void postpone(long deviceId, Instant retryAt) {
        jdbcTemplate.update("""
                UPDATE security_automatic_lighting_runtime
                SET turn_off_at = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE device_id = ?
                """,
                Timestamp.from(retryAt),
                deviceId
        );
    }

    @Transactional
    public void release(String deviceCode) {
        jdbcTemplate.update("""
                DELETE FROM security_automatic_lighting_runtime runtime
                USING building_device device
                WHERE runtime.device_id = device.id
                  AND UPPER(device.code) = UPPER(?)
                """,
                deviceCode
        );
    }

    public record LightingDevice(
            long id,
            String code,
            String name,
            String areaCode,
            String areaName
    ) {
    }

    public record RuntimeLight(
            long id,
            String code,
            String name,
            String areaCode,
            String areaName,
            Instant activatedAt,
            Instant lastMotionAt,
            Instant turnOffAt
    ) {
    }
}
