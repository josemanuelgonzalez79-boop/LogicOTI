package com.icap.logicoti.intrusion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/security")
public class SecurityMaintenanceController {

    private final SensorBypassService bypassService;

    public SecurityMaintenanceController(
            SensorBypassService bypassService
    ) {
        this.bypassService = bypassService;
    }

    @GetMapping("/bypasses")
    public SensorBypassListResponse activeBypasses() {
        return bypassService.findActive();
    }

    @PostMapping("/bypasses")
    public SensorBypassResponse createBypass(
            @Valid @RequestBody SensorBypassRequest request,
            Authentication authentication
    ) {
        return bypassService.create(
                request,
                authentication.getName()
        );
    }

    @DeleteMapping("/bypasses/{sensorCode}")
    public SensorBypassResponse revokeBypass(
            @PathVariable String sensorCode,
            Authentication authentication
    ) {
        return bypassService.revoke(
                sensorCode,
                authentication.getName()
        );
    }

    @GetMapping("/warnings")
    public SecurityWarningListResponse warnings(
            @RequestParam(defaultValue = "50")
            @Min(1) @Max(200) int limit
    ) {
        return bypassService.findWarnings(limit);
    }
}
