package com.icap.logicoti.building;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/building")
public class BuildingController {

    private final BuildingService buildingService;
    private final DeviceSummaryService deviceSummaryService;

    public BuildingController(
            BuildingService buildingService,
            DeviceSummaryService deviceSummaryService
    ) {
        this.buildingService = buildingService;
        this.deviceSummaryService = deviceSummaryService;
    }

    @GetMapping
    public BuildingResponse getBuilding() {
        return buildingService.getBuilding();
    }

    @GetMapping("/device-summary")
    public DeviceSummaryResponse getDeviceSummary() {
        return deviceSummaryService.getSummary();
    }
}
