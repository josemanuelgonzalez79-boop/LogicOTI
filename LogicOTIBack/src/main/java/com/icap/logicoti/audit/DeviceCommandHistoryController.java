package com.icap.logicoti.audit;

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
@RequestMapping("/api/history/commands")
public class DeviceCommandHistoryController {

    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "PENDING",
            "CONFIRMED",
            "NOT_CONFIRMED",
            "FAILED",
            "REJECTED"
    );

    private final DeviceCommandHistoryService historyService;

    public DeviceCommandHistoryController(
            DeviceCommandHistoryService historyService
    ) {
        this.historyService = historyService;
    }

    @GetMapping
    public DeviceCommandHistoryPageResponse find(
            @RequestParam(required = false)
            String areaCode,

            @RequestParam(required = false)
            String deviceCode,

            @RequestParam(required = false)
            String status,

            @RequestParam(required = false)
            String requestedBy,

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

        return historyService.find(
                areaCode,
                deviceCode,
                validateStatus(status),
                requestedBy,
                from,
                to,
                limit,
                offset
        );
    }

    private String validateStatus(String requestedStatus) {
        if (requestedStatus == null || requestedStatus.isBlank()) {
            return null;
        }

        String status = requestedStatus
                .trim()
                .toUpperCase(Locale.ROOT);

        if (!ALLOWED_STATUSES.contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El estado " + status + " no es válido."
            );
        }

        return status;
    }
}