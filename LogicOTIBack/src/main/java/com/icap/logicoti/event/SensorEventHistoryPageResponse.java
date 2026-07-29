package com.icap.logicoti.event;

import java.util.List;

public record SensorEventHistoryPageResponse(
        List<SensorEventHistoryResponse> items,
        long total,
        int limit,
        int offset
) {
}