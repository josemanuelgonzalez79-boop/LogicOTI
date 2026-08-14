package com.icap.logicoti.report;

public record HistoryRetentionCounts(
        long events,
        long commands,
        long securityTransitions,
        long diagnostics,
        long revokedBypasses,
        long notifications,
        long total
) {

    static HistoryRetentionCounts of(
            long events,
            long commands,
            long securityTransitions,
            long diagnostics,
            long revokedBypasses,
            long notifications
    ) {
        return new HistoryRetentionCounts(
                events,
                commands,
                securityTransitions,
                diagnostics,
                revokedBypasses,
                notifications,
                events
                        + commands
                        + securityTransitions
                        + diagnostics
                        + revokedBypasses
                        + notifications
        );
    }
}

