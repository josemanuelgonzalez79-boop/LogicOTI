package com.icap.logicoti.alarm;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alarms")
public class ActiveAlarmController {

    private final ActiveAlarmService activeAlarmService;
    private final AlarmInteractionService interactionService;

    public ActiveAlarmController(
            ActiveAlarmService activeAlarmService,
            AlarmInteractionService interactionService
    ) {
        this.activeAlarmService = activeAlarmService;
        this.interactionService = interactionService;
    }

    @GetMapping("/active")
    public ActiveAlarmListResponse findActive() {
        return activeAlarmService.findActive();
    }

    @GetMapping("/{eventId}/activity")
    public AlarmActivityResponse findActivity(
            @PathVariable long eventId
    ) {
        return interactionService.findActivity(eventId);
    }

    @PostMapping("/{eventId}/acknowledgement")
    public AlarmActivityResponse acknowledge(
            @PathVariable long eventId,
            @Valid @RequestBody AlarmAcknowledgeRequest request,
            Authentication authentication
    ) {
        return interactionService.acknowledge(
                eventId,
                request,
                authentication.getName()
        );
    }

    @PostMapping("/{eventId}/comments")
    public AlarmActivityResponse addComment(
            @PathVariable long eventId,
            @Valid @RequestBody AlarmCommentRequest request,
            Authentication authentication
    ) {
        return interactionService.addComment(
                eventId,
                request,
                authentication.getName()
        );
    }
}
