package com.icap.logicoti.alarm;

import com.icap.logicoti.event.SensorEventHistoryQueryService;
import com.icap.logicoti.event.SensorEventHistoryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveAlarmServiceTests {

    @Mock
    private SensorEventHistoryQueryService eventHistory;

    @InjectMocks
    private ActiveAlarmService service;

    @Test
    void returnsOnlyTheActiveAlarmsFoundByTheHistory() {
        SensorEventHistoryResponse alarm =
                new SensorEventHistoryResponse(
                        7L,
                        "P1_A01_HUM01",
                        "Sensor de humo 1",
                        "P1_A01",
                        "Dirección",
                        "SMOKE",
                        "OTI_P1_A01_HUM01_ALM",
                        false,
                        true,
                        "ACTIVATED",
                        "CRITICAL",
                        "Humo detectado en Dirección.",
                        Instant.parse("2026-07-29T22:33:50Z"),
                        false,
                        null,
                        null,
                        0
                );

        when(eventHistory.findActiveSmokeAlarms())
                .thenReturn(List.of(alarm));

        ActiveAlarmListResponse response =
                service.findActive();

        assertThat(response.items()).containsExactly(alarm);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.timestamp()).isNotNull();
    }
}
