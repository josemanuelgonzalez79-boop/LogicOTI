package com.icap.logicoti.intrusion;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final AutomaticLightingService automaticLightingService;
    private final AreaInactivityService areaInactivityService;
    private final SecurityZoneService zoneService;

    public SecurityController(
            IntrusionAlarmService alarmService,
            SecurityScheduleService scheduleService,
            AutomaticLightingService automaticLightingService,
            AreaInactivityService areaInactivityService,
            SecurityZoneService zoneService
    ) {
        this.alarmService = alarmService;
        this.scheduleService = scheduleService;
        this.automaticLightingService = automaticLightingService;
        this.areaInactivityService = areaInactivityService;
        this.zoneService = zoneService;
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

    @GetMapping("/zones")
    public SecurityZoneListResponse zones() {
        return zoneService.getStatus();
    }

    @PostMapping("/zones/precheck")
    public SecurityPrecheckResponse zonePrecheck(
            @Valid @RequestBody SecurityZoneSelectionRequest request
    ) {
        return zoneService.precheck(request.zoneCodes());
    }

    @PostMapping("/zones/arm")
    public SecurityZoneActionResponse armZones(
            @Valid @RequestBody SecurityZoneSelectionRequest request,
            Authentication authentication
    ) {
        return zoneService.armManually(
                request.zoneCodes(),
                authentication.getName()
        );
    }

    @PostMapping("/zones/disarm")
    public SecurityZoneActionResponse disarmZones(
            @Valid @RequestBody SecurityZoneSelectionRequest request,
            Authentication authentication
    ) {
        return zoneService.disarmManually(
                request.zoneCodes(),
                authentication.getName()
        );
    }

    @PostMapping("/zones/{zoneCode}/acknowledgement")
    public SecurityZoneListResponse acknowledgeZone(
            @PathVariable String zoneCode,
            Authentication authentication
    ) {
        return zoneService.acknowledge(
                zoneCode,
                authentication.getName()
        );
    }

    @GetMapping("/schedules")
    public SecuritySettingsResponse schedules() {
        return scheduleService.getSettings();
    }

    @GetMapping("/automatic-lighting/status")
    public AutomaticLightingStatusResponse automaticLightingStatus() {
        return automaticLightingService.getStatus();
    }

    @GetMapping("/inactivity/status")
    public AreaInactivityStatusResponse inactivityStatus() {
        return areaInactivityService.getStatus();
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
