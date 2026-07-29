package com.icap.logicoti.event;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Validated
@RestController
@RequestMapping("/api/history/events")
public class SensorEventHistoryController {

    private static final Set<String> DEVICE_TYPES =
            Set.of("MOTION", "SMOKE");

    private static final Set<String> EVENT_TYPES =
            Set.of("ACTIVATED", "CLEARED");

    private static final Set<String> SEVERITIES =
            Set.of("INFO", "WARNING", "CRITICAL");

    private final SensorEventHistoryQueryService queryService;

    public SensorEventHistoryController(
            SensorEventHistoryQueryService queryService
    ) {
        this.queryService = queryService;
    }

    @GetMapping
    public SensorEventHistoryPageResponse find(
            @RequestParam(required = false)
            String areaCode,

            @RequestParam(required = false)
            String deviceCode,

            @RequestParam(required = false)
            String deviceType,

            @RequestParam(required = false)
            String eventType,

            @RequestParam(required = false)
            String severity,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,

            @RequestParam(defaultValue = "50")
            @Min(1)
            @Max(500)
            int limit,

            @RequestParam(defaultValue = "0")
            @Min(0)
            @Max(1000000)
            int offset
    ) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La fecha inicial no puede ser posterior a la fecha final."
            );
        }

        return queryService.find(
                areaCode,
                deviceCode,
                validate(
                        "tipo de dispositivo",
                        deviceType,
                        DEVICE_TYPES
                ),
                validate(
                        "tipo de evento",
                        eventType,
                        EVENT_TYPES
                ),
                validate(
                        "severidad",
                        severity,
                        SEVERITIES
                ),
                from,
                to,
                limit,
                offset
        );
    }

    private String validate(
            String fieldName,
            String requestedValue,
            Set<String> allowedValues
    ) {
        if (requestedValue == null
                || requestedValue.isBlank()) {
            return null;
        }

        String value = requestedValue
                .trim()
                .toUpperCase(Locale.ROOT);

        if (!allowedValues.contains(value)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El valor "
                            + value
                            + " no es válido para "
                            + fieldName
                            + "."
            );
        }

        return value;
    }
}