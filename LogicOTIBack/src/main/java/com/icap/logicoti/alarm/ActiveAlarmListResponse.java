package com.icap.logicoti.alarm;

import com.icap.logicoti.event.SensorEventHistoryResponse;

import java.time.Instant;
import java.util.List;

public record ActiveAlarmListResponse(
        List<SensorEventHistoryResponse> items,
        long total,
        Instant timestamp
) {
}
