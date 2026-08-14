package com.icap.logicoti.report;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/reports/executive")
public class ExecutiveMonthlyReportController {

    private static final ZoneId DEFAULT_ZONE =
            ZoneId.of("America/Mazatlan");

    private final ExecutiveMonthlyReportService reportService;

    public ExecutiveMonthlyReportController(
            ExecutiveMonthlyReportService reportService
    ) {
        this.reportService = reportService;
    }

    @GetMapping("/monthly")
    public ExecutiveMonthlyReportResponse monthly(
            @RequestParam(required = false) String month
    ) {
        return reportService.generate(parseMonth(month));
    }

    private YearMonth parseMonth(String requestedMonth) {
        if (requestedMonth == null || requestedMonth.isBlank()) {
            return YearMonth.now(DEFAULT_ZONE);
        }

        try {
            return YearMonth.parse(requestedMonth.trim());
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El mes debe tener el formato AAAA-MM."
            );
        }
    }
}
