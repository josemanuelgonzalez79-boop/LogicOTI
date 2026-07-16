package com.icap.logicoti.system;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.icap.logicoti.config.PlcProperties;

import java.time.Instant;

@Service
public class SystemStatusService {

    private final JdbcTemplate jdbcTemplate;
    private final PlcProperties plcProperties;
    private final String applicationName;

    public SystemStatusService(
            JdbcTemplate jdbcTemplate,
            PlcProperties plcProperties,
            @Value("${spring.application.name}") String applicationName
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.plcProperties = plcProperties;
        this.applicationName = applicationName;
    }

    public SystemStatusResponse getStatus() {
        String database = databaseIsAvailable() ? "UP" : "DOWN";
        String status = "UP".equals(database) ? "UP" : "DEGRADED";
        return new SystemStatusResponse(applicationName, status, database, plcProperties.isEnabled(), Instant.now());
    }

    private boolean databaseIsAvailable() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
