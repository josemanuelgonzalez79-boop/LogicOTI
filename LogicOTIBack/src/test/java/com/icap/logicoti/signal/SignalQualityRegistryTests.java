package com.icap.logicoti.signal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SignalQualityRegistryTests {

    private MutableClock clock;
    private SignalQualityRegistry registry;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(
                Instant.parse("2026-08-13T20:00:00Z")
        );
        registry = new SignalQualityRegistry(
                Duration.ofSeconds(15),
                clock
        );
    }

    @Test
    void startsAsBadUntilTheFirstValidReading() {
        SignalQualitySnapshot snapshot = registry.snapshot(10L);

        assertThat(snapshot.quality()).isEqualTo(SignalQuality.BAD);
        assertThat(snapshot.value()).isNull();
        assertThat(snapshot.lastUpdatedAt()).isNull();
    }

    @Test
    void preservesTheLastValueWhenAReadingFails() {
        registry.recordGood(10L, true);
        Instant successfulAt = clock.instant();
        clock.advance(Duration.ofSeconds(5));

        registry.recordBad(10L, "Respuesta NOT_FOUND del PLC.");

        SignalQualitySnapshot snapshot = registry.snapshot(10L);
        assertThat(snapshot.quality()).isEqualTo(SignalQuality.BAD);
        assertThat(snapshot.value()).isTrue();
        assertThat(snapshot.lastUpdatedAt()).isEqualTo(successfulAt);
        assertThat(snapshot.detail()).contains("NOT_FOUND");
    }

    @Test
    void marksTheLastKnownValueAsStaleAfterTheConfiguredLimit() {
        registry.recordGood(10L, false);
        Instant successfulAt = clock.instant();
        clock.advance(Duration.ofSeconds(15));

        SignalQualitySnapshot snapshot = registry.snapshot(10L);

        assertThat(snapshot.quality()).isEqualTo(SignalQuality.STALE);
        assertThat(snapshot.value()).isFalse();
        assertThat(snapshot.lastUpdatedAt()).isEqualTo(successfulAt);
    }

    @Test
    void returnsToGoodWhenAValidReadingArrivesAgain() {
        registry.recordGood(10L, false);
        registry.recordBad(10L, "Fallo temporal.");
        clock.advance(Duration.ofSeconds(2));

        registry.recordGood(10L, true);

        SignalQualitySnapshot snapshot = registry.snapshot(10L);
        assertThat(snapshot.quality()).isEqualTo(SignalQuality.GOOD);
        assertThat(snapshot.value()).isTrue();
        assertThat(snapshot.lastUpdatedAt()).isEqualTo(clock.instant());
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
