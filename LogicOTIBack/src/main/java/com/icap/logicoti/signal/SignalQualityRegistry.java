package com.icap.logicoti.signal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class SignalQualityRegistry {

    private static final String NO_READING_DETAIL =
            "Todavía no existe una lectura válida para esta señal.";

    private final ConcurrentMap<Long, SignalReading> readings =
            new ConcurrentHashMap<>();
    private final Duration staleAfter;
    private final Clock clock;

    @Autowired
    public SignalQualityRegistry(
            @Value("${signal.quality.stale-after:15s}")
            Duration staleAfter
    ) {
        this(staleAfter, Clock.systemUTC());
    }

    SignalQualityRegistry(Duration staleAfter, Clock clock) {
        if (staleAfter == null
                || staleAfter.isZero()
                || staleAfter.isNegative()) {
            throw new IllegalArgumentException(
                    "signal.quality.stale-after debe ser mayor que cero."
            );
        }

        this.staleAfter = staleAfter;
        this.clock = clock;
    }

    public void recordGood(long deviceId, boolean value) {
        Instant now = clock.instant();

        readings.put(
                deviceId,
                new SignalReading(
                        value,
                        now,
                        null,
                        null
                )
        );
    }

    public void recordBad(long deviceId, String detail) {
        Instant now = clock.instant();
        String normalizedDetail = normalizeDetail(detail);

        readings.compute(
                deviceId,
                (key, current) -> new SignalReading(
                        current == null ? null : current.value(),
                        current == null ? null : current.lastUpdatedAt(),
                        now,
                        normalizedDetail
                )
        );
    }

    public void recordBad(
            Collection<Long> deviceIds,
            String detail
    ) {
        deviceIds.forEach(deviceId -> recordBad(deviceId, detail));
    }

    public SignalQualitySnapshot snapshot(long deviceId) {
        SignalReading reading = readings.get(deviceId);

        if (reading == null) {
            return new SignalQualitySnapshot(
                    SignalQuality.BAD,
                    null,
                    null,
                    NO_READING_DETAIL
            );
        }

        if (reading.lastUpdatedAt() == null) {
            return new SignalQualitySnapshot(
                    SignalQuality.BAD,
                    null,
                    null,
                    reading.failureDetail()
            );
        }

        Instant staleAt = reading.lastUpdatedAt().plus(staleAfter);

        if (!clock.instant().isBefore(staleAt)) {
            return new SignalQualitySnapshot(
                    SignalQuality.STALE,
                    reading.value(),
                    reading.lastUpdatedAt(),
                    "La última lectura válida ya superó el tiempo máximo de actualización."
            );
        }

        if (reading.failedAt() != null) {
            return new SignalQualitySnapshot(
                    SignalQuality.BAD,
                    reading.value(),
                    reading.lastUpdatedAt(),
                    reading.failureDetail()
            );
        }

        return new SignalQualitySnapshot(
                SignalQuality.GOOD,
                reading.value(),
                reading.lastUpdatedAt(),
                "Lectura válida recibida desde el PLC."
        );
    }

    private String normalizeDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return "La lectura de la señal falló.";
        }

        return detail.trim();
    }

    private record SignalReading(
            Boolean value,
            Instant lastUpdatedAt,
            Instant failedAt,
            String failureDetail
    ) {
    }
}
