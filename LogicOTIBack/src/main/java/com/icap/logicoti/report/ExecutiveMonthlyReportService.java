package com.icap.logicoti.report;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.icap.logicoti.report.ExecutiveMonthlyReportResponse.AlarmSummary;
import com.icap.logicoti.report.ExecutiveMonthlyReportResponse.CommandSummary;
import com.icap.logicoti.report.ExecutiveMonthlyReportResponse.CountMetric;
import com.icap.logicoti.report.ExecutiveMonthlyReportResponse.DailyMetric;
import com.icap.logicoti.report.ExecutiveMonthlyReportResponse.DeviceFailureMetric;
import com.icap.logicoti.report.ExecutiveMonthlyReportResponse.MaintenanceSummary;
import com.icap.logicoti.report.ExecutiveMonthlyReportResponse.SecuritySummary;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.icap.logicoti.report.ExecutiveMonthlyReportResponse.AlarmSummary;
import static com.icap.logicoti.report.ExecutiveMonthlyReportResponse.CommandSummary;
import static com.icap.logicoti.report.ExecutiveMonthlyReportResponse.CountMetric;
import static com.icap.logicoti.report.ExecutiveMonthlyReportResponse.DailyMetric;
import static com.icap.logicoti.report.ExecutiveMonthlyReportResponse.DeviceFailureMetric;
import static com.icap.logicoti.report.ExecutiveMonthlyReportResponse.MaintenanceSummary;
import static com.icap.logicoti.report.ExecutiveMonthlyReportResponse.SecuritySummary;

@Service
public class ExecutiveMonthlyReportService {

    private static final ZoneId DEFAULT_ZONE =
            ZoneId.of("America/Mazatlan");

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private static final String REJECTED = "REJECTED";


    @Autowired
    public ExecutiveMonthlyReportService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    ExecutiveMonthlyReportService(
            JdbcTemplate jdbcTemplate,
            Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ExecutiveMonthlyReportResponse generate(YearMonth month) {
        ZoneId timezone = resolveTimezone();
        Instant from = month
                .atDay(1)
                .atStartOfDay(timezone)
                .toInstant();
        Instant to = month
                .plusMonths(1)
                .atDay(1)
                .atStartOfDay(timezone)
                .toInstant();

        List<EventRow> eventRows = findEvents(
                from,
                to.plus(Duration.ofDays(31))
        );
        List<EventRow> periodEvents = eventRows.stream()
                .filter(event -> event.detectedAt().isBefore(to))
                .toList();
        List<EventRow> activations = periodEvents.stream()
                .filter(event -> "ACTIVATED".equals(event.eventType()))
                .toList();

        List<CommandRow> commands = findCommands(from, to);
        MaintenanceSummary maintenance = findMaintenance(from, to);
        List<CountMetric> rejectionReasons = findRejectionReasons(
                from,
                to
        );

        return new ExecutiveMonthlyReportResponse(
                month.toString(),
                month.atDay(1),
                month.atEndOfMonth(),
                timezone.getId(),
                clock.instant(),
                alarmSummary(activations, eventRows, from, to),
                commandSummary(commands),
                maintenance,
                new SecuritySummary(
                        rejectionReasons.stream()
                                .mapToLong(CountMetric::count)
                                .sum()
                ),
                countEvents(
                        activations,
                        EventRow::areaCode,
                        EventRow::areaName,
                        10
                ),
                countEvents(
                        activations,
                        EventRow::deviceCode,
                        EventRow::deviceName,
                        10
                ),
                dailyMetrics(month, timezone, activations),
                timeSlotMetrics(timezone, activations),
                commandFailures(commands),
                rejectionReasons
        );
    }

    private ZoneId resolveTimezone() {
        try {
            String value = jdbcTemplate.queryForObject(
                    "SELECT timezone FROM security_settings WHERE id = 1",
                    String.class
            );

            return value == null || value.isBlank()
                    ? DEFAULT_ZONE
                    : ZoneId.of(value);
        } catch (RuntimeException exception) {
            return DEFAULT_ZONE;
        }
    }

    private List<EventRow> findEvents(Instant from, Instant to) {
        return jdbcTemplate.query(
                """
                SELECT
                    history.device_id,
                    history.device_code,
                    device.name AS device_name,
                    history.area_code,
                    area.name AS area_name,
                    history.device_type,
                    history.event_type,
                    history.severity,
                    history.detected_at,
                    CASE
                        WHEN acknowledgement.event_id IS NULL THEN FALSE
                        ELSE TRUE
                    END AS acknowledged
                FROM device_event_history history
                INNER JOIN building_device device
                    ON device.id = history.device_id
                INNER JOIN building_area area
                    ON area.id = device.area_id
                LEFT JOIN alarm_acknowledgement acknowledgement
                    ON acknowledgement.event_id = history.id
                WHERE history.detected_at >= ?
                  AND history.detected_at < ?
                ORDER BY
                    history.detected_at ASC,
                    history.id ASC
                """,
                this::mapEvent,
                Timestamp.from(from),
                Timestamp.from(to)
        );
    }

    private List<CommandRow> findCommands(Instant from, Instant to) {
        return jdbcTemplate.query(
                """
                SELECT
                    history.device_code,
                    device.name AS device_name,
                    history.area_code,
                    area.name AS area_name,
                    history.requested_value,
                    history.feedback_value,
                    history.status,
                    history.duration_ms
                FROM device_command_history history
                INNER JOIN building_device device
                    ON device.id = history.device_id
                INNER JOIN building_area area
                    ON area.id = device.area_id
                WHERE history.requested_at >= ?
                  AND history.requested_at < ?
                ORDER BY history.requested_at ASC, history.id ASC
                """,
                this::mapCommand,
                Timestamp.from(from),
                Timestamp.from(to)
        );
    }

    private AlarmSummary alarmSummary(
            List<EventRow> activations,
            List<EventRow> allEvents,
            Instant from,
            Instant to
    ) {
        long acknowledged = activations.stream()
                .filter(EventRow::acknowledged)
                .count();
        List<Double> restoreMinutes = restoreMinutes(
                allEvents,
                from,
                to
        );

        return new AlarmSummary(
                activations.size(),
                countValue(activations, "SMOKE", EventRow::deviceType),
                countValue(activations, "MOTION", EventRow::deviceType),
                countValue(activations, "CRITICAL", EventRow::severity),
                acknowledged,
                percentage(acknowledged, activations.size()),
                average(restoreMinutes)
        );
    }

    private List<Double> restoreMinutes(
            List<EventRow> events,
            Instant from,
            Instant to
    ) {
        Map<Long, ArrayDeque<Instant>> pending = new LinkedHashMap<>();
        List<Double> durations = new ArrayList<>();

        for (EventRow event : events) {
            if ("ACTIVATED".equals(event.eventType())
                    && !event.detectedAt().isBefore(from)
                    && event.detectedAt().isBefore(to)) {
                pending.computeIfAbsent(
                        event.deviceId(),
                        ignored -> new ArrayDeque<>()
                ).add(event.detectedAt());
                continue;
            }

            if (!"CLEARED".equals(event.eventType())) {
                continue;
            }

            ArrayDeque<Instant> devicePending = pending.get(
                    event.deviceId()
            );

            if (devicePending == null || devicePending.isEmpty()) {
                continue;
            }

            Instant activatedAt = devicePending.removeFirst();
            durations.add(
                    Duration.between(
                            activatedAt,
                            event.detectedAt()
                    ).toMillis() / 60000.0
            );
        }

        return durations;
    }

    private CommandSummary commandSummary(List<CommandRow> commands) {
        long confirmed = commands.stream()
                .filter(command -> "CONFIRMED".equals(command.status()))
                .count();
        long pending = commands.stream()
                .filter(command -> "PENDING".equals(command.status()))
                .count();
        long failed = commands.stream()
                .filter(this::isCommandFailure)
                .count();
        List<Double> latencies = commands.stream()
                .filter(command -> "CONFIRMED".equals(command.status()))
                .map(CommandRow::durationMs)
                .filter(value -> value != null)
                .map(Long::doubleValue)
                .toList();

        return new CommandSummary(
                commands.size(),
                confirmed,
                failed,
                pending,
                percentage(confirmed, commands.size()),
                average(latencies)
        );
    }

    private MaintenanceSummary findMaintenance(
            Instant from,
            Instant to
    ) {
        Map<String, Long> diagnostics = new LinkedHashMap<>();

        jdbcTemplate.query(
                """
                SELECT status, COUNT(*) AS total
                FROM sensor_diagnostic_session
                WHERE started_at >= ?
                  AND started_at < ?
                GROUP BY status
                """,
                resultSet -> {
                    diagnostics.put(
                            resultSet.getString("status"),
                            resultSet.getLong("total")
                    );
                },
                Timestamp.from(from),
                Timestamp.from(to)
        );

        Long bypasses = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM sensor_bypass_history
                WHERE created_at >= ?
                  AND created_at < ?
                """,
                Long.class,
                Timestamp.from(from),
                Timestamp.from(to)
        );

        return new MaintenanceSummary(
                diagnostics.getOrDefault("PASSED", 0L),
                diagnostics.getOrDefault(REJECTED, 0L),
                diagnostics.getOrDefault("CANCELLED", 0L),
                bypasses == null ? 0 : bypasses
        );
    }

    private List<CountMetric> findRejectionReasons(
            Instant from,
            Instant to
    ) {
        return jdbcTemplate.query(
                """
                SELECT message, COUNT(*) AS total
                FROM intrusion_alarm_history
                WHERE current_mode = 'REJECTED'
                  AND changed_at >= ?
                  AND changed_at < ?
                GROUP BY message
                ORDER BY total DESC, message ASC
                """,
                (resultSet, rowNumber) -> new CountMetric(
                        REJECTED,
                        resultSet.getString("message"),
                        resultSet.getLong("total")
                ),
                Timestamp.from(from),
                Timestamp.from(to)
        );
    }

    private List<CountMetric> countEvents(
            List<EventRow> events,
            ValueExtractor codeExtractor,
            ValueExtractor labelExtractor,
            int limit
    ) {
        Map<String, MutableCount> values = new LinkedHashMap<>();

        for (EventRow event : events) {
            String code = codeExtractor.value(event);
            MutableCount metric = values.computeIfAbsent(
                    code,
                    ignored -> new MutableCount(
                            code,
                            labelExtractor.value(event)
                    )
            );
            metric.increment();
        }

        return values.values().stream()
                .sorted(
                        Comparator.comparingLong(MutableCount::count)
                                .reversed()
                                .thenComparing(MutableCount::label)
                )
                .limit(limit)
                .map(MutableCount::toMetric)
                .toList();
    }

    private List<DailyMetric> dailyMetrics(
            YearMonth month,
            ZoneId timezone,
            List<EventRow> activations
    ) {
        Map<LocalDate, Long> totals = new LinkedHashMap<>();

        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            totals.put(month.atDay(day), 0L);
        }

        for (EventRow activation : activations) {
            LocalDate date = activation.detectedAt()
                    .atZone(timezone)
                    .toLocalDate();
            totals.computeIfPresent(date, (ignored, value) -> value + 1);
        }

        return totals.entrySet().stream()
                .map(entry -> new DailyMetric(
                        entry.getKey(),
                        entry.getValue()
                ))
                .toList();
    }

    private List<CountMetric> timeSlotMetrics(
            ZoneId timezone,
            List<EventRow> activations
    ) {
        long[] totals = new long[4];

        for (EventRow activation : activations) {
            int hour = activation.detectedAt()
                    .atZone(timezone)
                    .getHour();
            totals[hour / 6]++;
        }

        return List.of(
                new CountMetric("00-05", "Madrugada", totals[0]),
                new CountMetric("06-11", "Mañana", totals[1]),
                new CountMetric("12-17", "Tarde", totals[2]),
                new CountMetric("18-23", "Noche", totals[3])
        );
    }

    private List<DeviceFailureMetric> commandFailures(
            List<CommandRow> commands
    ) {
        Map<String, MutableFailure> values = new LinkedHashMap<>();

        commands.stream()
                .filter(this::isCommandFailure)
                .forEach(command -> values.computeIfAbsent(
                                command.deviceCode(),
                                ignored -> new MutableFailure(command)
                        )
                        .increment());

        return values.values().stream()
                .sorted(
                        Comparator.comparingLong(MutableFailure::failures)
                                .reversed()
                                .thenComparing(MutableFailure::deviceName)
                )
                .limit(10)
                .map(MutableFailure::toMetric)
                .toList();
    }

    private boolean isCommandFailure(CommandRow command) {
        if ("FAILED".equals(command.status())
                || "NOT_CONFIRMED".equals(command.status())
                || REJECTED.equals(command.status())) {
            return true;
        }

        return command.feedbackValue() != null
                && command.requestedValue() != command.feedbackValue();
    }

    private long countValue(
            List<EventRow> events,
            String expected,
            ValueExtractor extractor
    ) {
        return events.stream()
                .filter(event -> expected.equals(extractor.value(event)))
                .count();
    }

    private double percentage(long value, long total) {
        return total == 0 ? 0 : value * 100.0 / total;
    }

    private Double average(List<Double> values) {
        return values.isEmpty()
                ? null
                : values.stream().mapToDouble(Double::doubleValue).average()
                        .orElse(0);
    }

    private EventRow mapEvent(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new EventRow(
                resultSet.getLong("device_id"),
                resultSet.getString("device_code"),
                resultSet.getString("device_name"),
                resultSet.getString("area_code"),
                resultSet.getString("area_name"),
                resultSet.getString("device_type")
                        .toUpperCase(Locale.ROOT),
                resultSet.getString("event_type")
                        .toUpperCase(Locale.ROOT),
                resultSet.getString("severity")
                        .toUpperCase(Locale.ROOT),
                resultSet.getTimestamp("detected_at").toInstant(),
                resultSet.getBoolean("acknowledged")
        );
    }

    private CommandRow mapCommand(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        Number duration = (Number) resultSet.getObject("duration_ms");

        return new CommandRow(
                resultSet.getString("device_code"),
                resultSet.getString("device_name"),
                resultSet.getString("area_code"),
                resultSet.getString("area_name"),
                resultSet.getBoolean("requested_value"),
                (Boolean) resultSet.getObject("feedback_value"),
                resultSet.getString("status").toUpperCase(Locale.ROOT),
                duration == null ? null : duration.longValue()
        );
    }

    @FunctionalInterface
    private interface ValueExtractor {
        String value(EventRow event);
    }

    private record EventRow(
            long deviceId,
            String deviceCode,
            String deviceName,
            String areaCode,
            String areaName,
            String deviceType,
            String eventType,
            String severity,
            Instant detectedAt,
            boolean acknowledged
    ) {
    }

    private record CommandRow(
            String deviceCode,
            String deviceName,
            String areaCode,
            String areaName,
            boolean requestedValue,
            Boolean feedbackValue,
            String status,
            Long durationMs
    ) {
    }

    private static final class MutableCount {

        private final String code;
        private final String label;
        private long count;

        private MutableCount(String code, String label) {
            this.code = code;
            this.label = label;
        }

        private void increment() {
            count++;
        }

        private long count() {
            return count;
        }

        private String label() {
            return label;
        }

        private CountMetric toMetric() {
            return new CountMetric(code, label, count);
        }
    }

    private static final class MutableFailure {

        private final CommandRow command;
        private long failures;

        private MutableFailure(CommandRow command) {
            this.command = command;
        }

        private void increment() {
            failures++;
        }

        private long failures() {
            return failures;
        }

        private String deviceName() {
            return command.deviceName();
        }

        private DeviceFailureMetric toMetric() {
            return new DeviceFailureMetric(
                    command.deviceCode(),
                    command.deviceName(),
                    command.areaCode(),
                    command.areaName(),
                    failures
            );
        }
    }
}
