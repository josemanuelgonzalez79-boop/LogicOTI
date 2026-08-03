package com.icap.logicoti.alarm;

import com.icap.logicoti.event.SensorEventHistoryQueryService;
import com.icap.logicoti.event.SensorEventHistoryResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ActiveAlarmService {

    private final SensorEventHistoryQueryService eventHistory;

    public ActiveAlarmService(
            SensorEventHistoryQueryService eventHistory
    ) {
        this.eventHistory = eventHistory;
    }

    public ActiveAlarmListResponse findActive() {
        List<SensorEventHistoryResponse> items =
                eventHistory.findActiveSmokeAlarms();

        return new ActiveAlarmListResponse(
                items,
                items.size(),
                Instant.now()
        );
    }
}
