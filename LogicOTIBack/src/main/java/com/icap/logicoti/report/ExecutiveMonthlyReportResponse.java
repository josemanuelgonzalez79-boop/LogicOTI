package com.icap.logicoti.report;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ExecutiveMonthlyReportResponse(
        String month,
        LocalDate periodStart,
        LocalDate periodEnd,
        String timezone,
        Instant generatedAt,
        AlarmSummary alarms,
        CommandSummary commands,
        MaintenanceSummary maintenance,
        SecuritySummary security,
        List<CountMetric> alarmsByArea,
        List<CountMetric> alarmsBySensor,
        List<DailyMetric> alarmsByDay,
        List<CountMetric> alarmsByTimeSlot,
        List<DeviceFailureMetric> commandFailures,
        List<CountMetric> armRejectionReasons
) {

    public record AlarmSummary(
            long total,
            long smoke,
            long motion,
            long critical,
            long acknowledged,
            double acknowledgementRate,
            Double averageRestoreMinutes
    ) {
    }

    public record CommandSummary(
            long total,
            long confirmed,
            long failed,
            long pending,
            double confirmationRate,
            Double averageLatencyMs
    ) {
    }

    public record MaintenanceSummary(
            long diagnosticsPassed,
            long diagnosticsRejected,
            long diagnosticsCancelled,
            long bypassesCreated
    ) {
    }

    public record SecuritySummary(
            long rejectedArmings
    ) {
    }

    public record CountMetric(
            String code,
            String label,
            long count
    ) {
    }

    public record DailyMetric(
            LocalDate date,
            long count
    ) {
    }

    public record DeviceFailureMetric(
            String deviceCode,
            String deviceName,
            String areaCode,
            String areaName,
            long failures
    ) {
    }
}
