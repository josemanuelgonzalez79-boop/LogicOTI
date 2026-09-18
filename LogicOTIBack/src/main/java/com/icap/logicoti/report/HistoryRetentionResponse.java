package com.icap.logicoti.report;

import java.time.Instant;

public record HistoryRetentionResponse(
        boolean enabled,
        int retentionMonths,
        Instant cutoffAt,
        HistoryRetentionCounts candidates,
        Instant lastRunAt,
        String lastRunBy,
        Instant lastCutoffAt,
        HistoryRetentionCounts lastDeleted,
        Instant updatedAt,
        String updatedBy,
        Instant timestamp
) {
}
