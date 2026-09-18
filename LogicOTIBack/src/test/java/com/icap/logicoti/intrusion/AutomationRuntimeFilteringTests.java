package com.icap.logicoti.intrusion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AutomationRuntimeFilteringTests {

    private JdbcTemplate jdbcTemplate;
    private AutomaticLightingRuntimeService lightingRuntime;
    private AreaInactivityRuntimeService inactivityRuntime;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl(
                "jdbc:h2:mem:automation_"
                        + UUID.randomUUID()
                        + ";MODE=PostgreSQL"
                        + ";DB_CLOSE_DELAY=-1"
                        + ";DATABASE_TO_LOWER=TRUE"
        );
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        jdbcTemplate = new JdbcTemplate(dataSource);
        lightingRuntime =
                new AutomaticLightingRuntimeService(jdbcTemplate);
        inactivityRuntime =
                new AreaInactivityRuntimeService(jdbcTemplate);

        createSchema();
        seedData();
    }

    @Test
    void ignoresAndRemovesRuntimeLightsFromInactiveCatalogEntries() {
        List<AutomaticLightingRuntimeService.RuntimeLight> due =
                lightingRuntime.findExpiredLights(Instant.now());

        assertThat(due)
                .extracting(
                        AutomaticLightingRuntimeService.RuntimeLight::code
                )
                .containsExactly("ACTIVE_LIGHT");

        assertThat(lightingRuntime.removeInactiveEntries()).isEqualTo(3);
        assertThat(count("security_automatic_lighting_runtime"))
                .isEqualTo(1);
    }

    @Test
    void ignoresAndRemovesRuntimeAreasThatAreNoLongerActive() {
        List<AreaInactivityRuntimeService.RuntimeArea> due =
                inactivityRuntime.findDueAreas(Instant.now());

        assertThat(due)
                .extracting(
                        AreaInactivityRuntimeService.RuntimeArea::code
                )
                .containsExactly("ACTIVE_AREA");

        assertThat(inactivityRuntime.removeInactiveEntries()).isEqualTo(2);
        assertThat(count("security_area_inactivity_runtime"))
                .isEqualTo(1);
    }

    @Test
    void staleLightingTargetsAreNotTreatedAsActiveTargets() {
        assertThat(
                inactivityRuntime.isAutomaticLightingTarget("ACTIVE_LIGHT")
        ).isTrue();
        assertThat(
                inactivityRuntime.isAutomaticLightingTarget("INACTIVE_LIGHT")
        ).isFalse();
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE building_floor (
                    id BIGINT PRIMARY KEY,
                    code VARCHAR(40) NOT NULL,
                    name VARCHAR(100) NOT NULL,
                    active BOOLEAN NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE building_area (
                    id BIGINT PRIMARY KEY,
                    floor_id BIGINT NOT NULL,
                    code VARCHAR(40) NOT NULL,
                    name VARCHAR(100) NOT NULL,
                    active BOOLEAN NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE building_device (
                    id BIGINT PRIMARY KEY,
                    area_id BIGINT NOT NULL,
                    code VARCHAR(80) NOT NULL,
                    name VARCHAR(150) NOT NULL,
                    device_type VARCHAR(30) NOT NULL,
                    controllable BOOLEAN NOT NULL,
                    active BOOLEAN NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE security_automatic_lighting_target (
                    device_id BIGINT PRIMARY KEY
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE security_automatic_lighting_runtime (
                    device_id BIGINT PRIMARY KEY,
                    activated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    last_motion_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    turn_off_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE security_area_inactivity_runtime (
                    area_id BIGINT PRIMARY KEY,
                    last_motion_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    light_turn_off_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    minisplit_turn_off_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    light_processed BOOLEAN NOT NULL,
                    minisplit_processed BOOLEAN NOT NULL,
                    lights_turned_off INTEGER NOT NULL,
                    minisplits_turned_off INTEGER NOT NULL,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
    }

    private void seedData() {
        jdbcTemplate.update(
                "INSERT INTO building_floor VALUES "
                        + "(1, 'F1', 'Piso activo', TRUE), "
                        + "(2, 'F2', 'Piso inactivo', FALSE)"
        );
        jdbcTemplate.update(
                "INSERT INTO building_area VALUES "
                        + "(10, 1, 'ACTIVE_AREA', 'Área activa', TRUE), "
                        + "(11, 1, 'INACTIVE_AREA', 'Área inactiva', FALSE), "
                        + "(12, 2, 'INACTIVE_FLOOR_AREA', "
                        + "'Área de piso inactivo', TRUE)"
        );
        jdbcTemplate.update(
                "INSERT INTO building_device VALUES "
                        + "(100, 10, 'ACTIVE_LIGHT', 'Luz activa', "
                        + "'LIGHT', TRUE, TRUE), "
                        + "(101, 10, 'INACTIVE_LIGHT', 'Luz inactiva', "
                        + "'LIGHT', TRUE, FALSE), "
                        + "(102, 11, 'INACTIVE_AREA_LIGHT', "
                        + "'Luz de área inactiva', 'LIGHT', TRUE, TRUE), "
                        + "(103, 12, 'INACTIVE_FLOOR_LIGHT', "
                        + "'Luz de piso inactivo', 'LIGHT', TRUE, TRUE)"
        );
        jdbcTemplate.update(
                "INSERT INTO security_automatic_lighting_target "
                        + "(device_id) VALUES (100), (101), (102), (103)"
        );

        Instant motionAt = Instant.now().minusSeconds(120);
        Instant dueAt = Instant.now().minusSeconds(60);
        for (long deviceId = 100; deviceId <= 103; deviceId++) {
            jdbcTemplate.update("""
                            INSERT INTO security_automatic_lighting_runtime (
                                device_id,
                                activated_at,
                                last_motion_at,
                                turn_off_at,
                                updated_at
                            )
                            VALUES (?, ?, ?, ?, ?)
                            """,
                    deviceId,
                    Timestamp.from(motionAt),
                    Timestamp.from(motionAt),
                    Timestamp.from(dueAt),
                    Timestamp.from(motionAt)
            );
        }

        for (long areaId = 10; areaId <= 12; areaId++) {
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
                            VALUES (?, ?, ?, ?, FALSE, FALSE, 0, 0, ?)
                            """,
                    areaId,
                    Timestamp.from(motionAt),
                    Timestamp.from(dueAt),
                    Timestamp.from(dueAt),
                    Timestamp.from(motionAt)
            );
        }
    }

    private int count(String table) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table,
                Integer.class
        );
        return value == null ? 0 : value;
    }
}
