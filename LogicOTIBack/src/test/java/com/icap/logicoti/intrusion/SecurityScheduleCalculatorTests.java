package com.icap.logicoti.intrusion;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityScheduleCalculatorTests {

    @Test
    void armsAtSixInTheAfternoonOnWeekdays() {
        SecuritySettingsResponse settings = settings();

        ScheduleDecision before = SecurityScheduleCalculator.evaluate(
                settings,
                localInstant(
                        LocalDate.of(2026, 8, 6),
                        LocalTime.of(17, 59, 59)
                )
        );

        ScheduleDecision atSix = SecurityScheduleCalculator.evaluate(
                settings,
                localInstant(
                        LocalDate.of(2026, 8, 6),
                        LocalTime.of(18, 0)
                )
        );

        assertThat(before.shouldBeArmed()).isFalse();
        assertThat(atSix.shouldBeArmed()).isTrue();
    }

    @Test
    void remainsArmedUntilEightTheNextMorning() {
        SecuritySettingsResponse settings = settings();

        ScheduleDecision beforeEight =
                SecurityScheduleCalculator.evaluate(
                        settings,
                        localInstant(
                                LocalDate.of(2026, 8, 6),
                                LocalTime.of(7, 59, 59)
                        )
                );

        ScheduleDecision atEight =
                SecurityScheduleCalculator.evaluate(
                        settings,
                        localInstant(
                                LocalDate.of(2026, 8, 6),
                                LocalTime.of(8, 0)
                        )
                );

        assertThat(beforeEight.shouldBeArmed()).isTrue();
        assertThat(atEight.shouldBeArmed()).isFalse();
    }

    @Test
    void remainsArmedAllDayOnSaturday() {
        ScheduleDecision saturday =
                SecurityScheduleCalculator.evaluate(
                        settings(),
                        localInstant(
                                LocalDate.of(2026, 8, 8),
                                LocalTime.of(12, 0)
                        )
                );

        assertThat(saturday.shouldBeArmed()).isTrue();
        assertThat(saturday.transitionKey())
                .endsWith("-ALL_DAY");
    }

    private SecuritySettingsResponse settings() {
        return new SecuritySettingsResponse(
                true,
                "America/Mazatlan",
                60,
                10,
                30,
                120,
                4,
                List.of(
                        day(1, false),
                        day(2, false),
                        day(3, false),
                        day(4, false),
                        day(5, false),
                        day(6, true),
                        day(7, true)
                ),
                Instant.parse("2026-08-06T12:00:00Z"),
                "admin"
        );
    }

    private SecuritySettingsResponse.ScheduleDay day(
            int number,
            boolean allDay
    ) {
        return new SecuritySettingsResponse.ScheduleDay(
                number,
                "Día " + number,
                true,
                allDay,
                LocalTime.of(18, 0),
                LocalTime.of(8, 0)
        );
    }

    private Instant localInstant(
            LocalDate date,
            LocalTime time
    ) {
        return ZonedDateTime.of(
                date,
                time,
                ZoneId.of("America/Mazatlan")
        ).toInstant();
    }
}
