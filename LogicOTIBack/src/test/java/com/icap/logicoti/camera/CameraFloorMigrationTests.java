package com.icap.logicoti.camera;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CameraFloorMigrationTests {

    @Test
    void movesEntranceCameraToGroundFloor() throws IOException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl(
                "jdbc:h2:mem:camera_"
                        + UUID.randomUUID()
                        + ";MODE=PostgreSQL"
                        + ";DB_CLOSE_DELAY=-1"
                        + ";DATABASE_TO_LOWER=TRUE"
        );
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE camera (
                    code VARCHAR(20) PRIMARY KEY,
                    floor_code VARCHAR(10) NOT NULL
                )
                """);
        jdbcTemplate.update(
                "INSERT INTO camera VALUES ('CAM-016', 'P1')"
        );

        jdbcTemplate.execute(readMigration());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT floor_code FROM camera WHERE code = 'CAM-016'",
                String.class
        )).isEqualTo("PB");
    }

    private String readMigration() throws IOException {
        String resource =
                "db/migration/V22__correct_entrance_camera_floor.sql";

        try (InputStream input = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException(
                        "No se encontró la migración " + resource
                );
            }

            return new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }
}
