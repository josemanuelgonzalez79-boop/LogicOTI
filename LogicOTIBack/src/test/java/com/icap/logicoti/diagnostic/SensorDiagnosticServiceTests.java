package com.icap.logicoti.diagnostic;

import com.icap.logicoti.config.PlcProperties;
import com.icap.logicoti.exception.ConflictException;
import com.icap.logicoti.intrusion.SecurityScheduleService;
import com.icap.logicoti.plc.PlcCommunicationService;
import com.icap.logicoti.signal.SignalQualityRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SensorDiagnosticServiceTests {
    private JdbcTemplate jdbc;
    private SensorDiagnosticService service;
    private PlcCommunicationService plc;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:diagnostics_" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        plc = mock(PlcCommunicationService.class);
        service = new SensorDiagnosticService(jdbc, new NamedParameterJdbcTemplate(jdbc), plc,
                new PlcProperties(), mock(SecurityScheduleService.class),
                mock(SimpMessagingTemplate.class), mock(SignalQualityRegistry.class));
        jdbc.execute("""
                CREATE TABLE building_area (id BIGINT PRIMARY KEY, code VARCHAR, name VARCHAR,
                    active BOOLEAN, display_order INT);
                CREATE TABLE building_device (id BIGINT PRIMARY KEY, area_id BIGINT, code VARCHAR,
                    name VARCHAR, device_type VARCHAR, plc_state_tag VARCHAR, active BOOLEAN, display_order INT);
                CREATE TABLE sensor_diagnostic_session (id BIGINT PRIMARY KEY, status VARCHAR,
                    started_by VARCHAR, started_by_role VARCHAR, started_at TIMESTAMP WITH TIME ZONE,
                    expires_at TIMESTAMP WITH TIME ZONE, completed_at TIMESTAMP WITH TIME ZONE, message VARCHAR);
                CREATE TABLE sensor_diagnostic_item (id BIGINT PRIMARY KEY, session_id BIGINT, device_id BIGINT,
                    device_code VARCHAR, device_name VARCHAR, area_code VARCHAR, area_name VARCHAR,
                    device_type VARCHAR, plc_state_tag VARCHAR, initial_state BOOLEAN, saw_inactive BOOLEAN,
                    saw_active BOOLEAN, status VARCHAR, passed_at TIMESTAMP WITH TIME ZONE);
                INSERT INTO building_area VALUES (1, 'PB_A01', 'Recepción', TRUE, 1);
                INSERT INTO building_device VALUES (7, 1, 'HUM01', 'Humo', 'SMOKE', 'OTI_HUM01', TRUE, 1);
                """);
        jdbc.update("INSERT INTO sensor_diagnostic_session VALUES (1, 'RUNNING', 'operador', 'OPERATOR', ?, ?, NULL, 'Prueba')",
                Timestamp.from(Instant.now().minusSeconds(10)), Timestamp.from(Instant.now().plusSeconds(120)));
        for (long id : List.of(7L, 8L)) {
            jdbc.update("""
                    INSERT INTO sensor_diagnostic_item VALUES (?, 1, ?, ?, 'Sensor', 'PB_A01', 'Recepción',
                        'SMOKE', 'OTI_HUM01', FALSE, TRUE, FALSE, 'RUNNING', NULL)
                    """, id, id, "HUM0" + (id - 6));
        }
    }

    @Test
    void eachSensorMustReturnToRestBeforePassingAndLeavingTestMode() {
        Instant duringTest = Instant.now().minusSeconds(1);
        service.recordStates(service.findRunningItems(), Map.of(7L, true, 8L, true));
        assertThat(service.get(1).sensors()).allMatch(item -> item.status().equals("RUNNING"));
        assertThat(service.findTestingSensorIds(Instant.now())).containsExactlyInAnyOrder(7L, 8L);

        service.recordStates(service.findRunningItems(), Map.of(7L, false, 8L, true));
        assertThat(service.get(1).status()).isEqualTo("RUNNING");
        assertThat(service.findTestingSensorIds(Instant.now())).containsExactly(8L);

        service.recordStates(service.findRunningItems(), Map.of(8L, false));
        assertThat(service.get(1).status()).isEqualTo("PASSED");
        assertThat(service.findTestingSensorIds(Instant.now())).isEmpty();
        // Una muestra ya tomada durante la prueba conserva su clasificación.
        assertThat(service.findTestingSensorIds(duringTest)).containsExactlyInAnyOrder(7L, 8L);
    }

    @Test
    void expiredSessionStopsSuppressionEvenBeforeExpiryJobRuns() {
        jdbc.update("UPDATE sensor_diagnostic_session SET expires_at = ?",
                Timestamp.from(Instant.now().minusSeconds(1)));
        assertThat(service.findTestingSensorIds(Instant.now())).isEmpty();
        assertThat(service.get(1).status()).isEqualTo("RUNNING");
    }

    @Test
    void cancellationRestoresAllSelectedSensors() {
        service.cancel(1, "operador");
        assertThat(service.findTestingSensorIds(Instant.now())).isEmpty();
        assertThat(service.get(1).status()).isEqualTo("CANCELLED");
    }

    @Test
    void staleReadCannotPassASessionThatExpiredDuringPlcRead() {
        var items = service.findRunningItems();
        service.recordStates(items, Map.of(7L, true, 8L, true));
        items = service.findRunningItems();
        jdbc.update("UPDATE sensor_diagnostic_session SET expires_at = ?",
                Timestamp.from(Instant.now().minusSeconds(1)));
        service.recordStates(items, Map.of(7L, false, 8L, false));
        assertThat(service.get(1).sensors()).allMatch(item -> item.status().equals("RUNNING"));
    }

    @Test
    void alreadyActiveSmokeSensorCannotBeHiddenByStartingADiagnostic() {
        jdbc.update("DELETE FROM sensor_diagnostic_item");
        jdbc.update("DELETE FROM sensor_diagnostic_session");
        when(plc.read(any())).thenReturn(Map.of(7L, true));

        assertThatThrownBy(() -> service.start(new SensorDiagnosticStartRequest(List.of("HUM01")),
                "operador", "OPERATOR")).isInstanceOf(ConflictException.class).hasMessageContaining("HUM01");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sensor_diagnostic_session", Integer.class)).isZero();
    }
}
