package com.icap.logicoti.report;

import java.time.Instant;

public record HistoryRetentionRunResponse(
        Instant cutoffAt,
        HistoryRetentionCounts deleted,
        Instant executedAt,
        String executedBy
) {
}

