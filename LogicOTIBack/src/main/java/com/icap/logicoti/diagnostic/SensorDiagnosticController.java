package com.icap.logicoti.diagnostic;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sensor-diagnostics")
public class SensorDiagnosticController {

    private final SensorDiagnosticService diagnosticService;

    public SensorDiagnosticController(
            SensorDiagnosticService diagnosticService
    ) {
        this.diagnosticService = diagnosticService;
    }

    @PostMapping
    public SensorDiagnosticResponse start(
            @Valid @RequestBody SensorDiagnosticStartRequest request,
            Authentication authentication
    ) {
        return diagnosticService.start(
                request,
                authentication.getName(),
                authentication.getAuthorities()
                        .stream()
                        .findFirst()
                        .map(authority -> authority.getAuthority()
                                .replaceFirst("^ROLE_", ""))
                        .orElse("OPERATOR")
        );
    }

    @GetMapping
    public SensorDiagnosticListResponse find(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int limit
    ) {
        return diagnosticService.find(status, limit);
    }

    @GetMapping("/due")
    public SensorDiagnosticDueResponse due() {
        return diagnosticService.findDueSensors();
    }

    @GetMapping("/{id}")
    public SensorDiagnosticResponse get(
            @PathVariable long id
    ) {
        return diagnosticService.get(id);
    }

    @PostMapping("/{id}/cancel")
    public SensorDiagnosticResponse cancel(
            @PathVariable long id,
            Authentication authentication
    ) {
        return diagnosticService.cancel(
                id,
                authentication.getName()
        );
    }
}
