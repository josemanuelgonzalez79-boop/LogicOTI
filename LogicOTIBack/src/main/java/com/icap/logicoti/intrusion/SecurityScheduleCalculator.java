package com.icap.logicoti.intrusion;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

final class SecurityScheduleCalculator {

    private SecurityScheduleCalculator() {
    }

    static ScheduleDecision evaluate(
            SecuritySettingsResponse settings,
            Instant instant
    ) {
        if (!settings.automaticScheduleEnabled()) {
            return new ScheduleDecision(
                    false,
                    false,
                    "AUTOMATIC_DISABLED",
                    instant
            );
        }

        ZoneId zoneId = ZoneId.of(settings.timezone());
        ZonedDateTime localDateTime = instant.atZone(zoneId);
        LocalDate currentDate = localDateTime.toLocalDate();
        LocalTime currentTime = localDateTime.toLocalTime();

        Map<Integer, SecuritySettingsResponse.ScheduleDay> days =
                settings.days()
                        .stream()
                        .collect(Collectors.toMap(
                                SecuritySettingsResponse.ScheduleDay::dayOfWeek,
                                Function.identity()
                        ));

        SecuritySettingsResponse.ScheduleDay currentDay =
                days.get(localDateTime.getDayOfWeek().getValue());

        LocalDate previousDate = currentDate.minusDays(1);
        DayOfWeek previousDayOfWeek =
                localDateTime.getDayOfWeek().minus(1);

        SecuritySettingsResponse.ScheduleDay previousDay =
                days.get(previousDayOfWeek.getValue());

        if (currentDay != null
                && currentDay.enabled()
                && currentDay.allDayArmed()) {

            return armedDecision(
                    currentDate + "-ALL_DAY",
                    instant
            );
        }

        if (currentDay != null
                && currentDay.enabled()
                && !currentDay.allDayArmed()
                && !currentTime.isBefore(currentDay.armTime())) {

            return armedDecision(
                    currentDate + "-ARM",
                    instant
            );
        }

        if (previousDay != null && previousDay.enabled()) {
            LocalTime previousWindowEnd = previousDay.allDayArmed()
                    && currentDay != null
                    ? currentDay.disarmTime()
                    : previousDay.disarmTime();

            if (currentTime.isBefore(previousWindowEnd)) {
                return armedDecision(
                        previousDate + "-ARM",
                        instant
                );
            }
        }

        return new ScheduleDecision(
                true,
                false,
                currentDate + "-DISARM",
                instant
        );
    }

    private static ScheduleDecision armedDecision(
            String transitionKey,
            Instant instant
    ) {
        return new ScheduleDecision(
                true,
                true,
                transitionKey,
                instant
        );
    }
}
