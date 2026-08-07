package com.icap.logicoti.intrusion;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/security")
public class SecurityController {

    private final IntrusionAlarmService alarmService;
    private final SecurityScheduleService scheduleService;

    public SecurityController(
            IntrusionAlarmService alarmService,
            SecurityScheduleService scheduleService
    ) {
        this.alarmService = alarmService;
        this.scheduleService = scheduleService;
    }

    @GetMapping("/status")
    public SecurityStatusResponse status() {
        return alarmService.getStatus();
    }

    @GetMapping("/precheck")
    public SecurityPrecheckResponse precheck() {
        return alarmService.precheck();
    }

    @PostMapping("/arm")
    public SecurityActionResponse arm(
            Authentication authentication
    ) {
        return alarmService.armManually(
                authentication.getName()
        );
    }

    @PostMapping("/disarm")
    public SecurityActionResponse disarm(
            Authentication authentication
    ) {
        return alarmService.disarmManually(
                authentication.getName()
        );
    }

    @GetMapping("/schedules")
    public SecuritySettingsResponse schedules() {
        return scheduleService.getSettings();
    }

    @PutMapping("/schedules")
    public SecuritySettingsResponse updateSchedules(
            @Valid @RequestBody
            SecuritySettingsUpdateRequest request,
            Authentication authentication
    ) {
        return scheduleService.updateSettings(
                request,
                authentication.getName()
        );
    }
}
