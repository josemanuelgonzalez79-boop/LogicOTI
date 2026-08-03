package com.icap.logicoti.alarm;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alarms")
public class ActiveAlarmController {

    private final ActiveAlarmService activeAlarmService;

    public ActiveAlarmController(
            ActiveAlarmService activeAlarmService
    ) {
        this.activeAlarmService = activeAlarmService;
    }

    @GetMapping("/active")
    public ActiveAlarmListResponse findActive() {
        return activeAlarmService.findActive();
    }
}
