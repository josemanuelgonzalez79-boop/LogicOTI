package com.icap.logicoti.camera;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cameras")
public class CameraController {

    private final CameraService cameraService;
    private final CameraHistoryService cameraHistoryService;

    public CameraController(
            CameraService cameraService,
            CameraHistoryService cameraHistoryService
    ) {
        this.cameraService = cameraService;
        this.cameraHistoryService = cameraHistoryService;
    }

    @GetMapping
    public CameraListResponse findAll(
            @RequestParam(required = false)
            String floorCode,
            @RequestParam(required = false)
            String areaCode
    ) {
        return cameraService.findAll(floorCode, areaCode);
    }

    @GetMapping("/{cameraCode}")
    public CameraResponse findByCode(
            @PathVariable String cameraCode
    ) {
        return cameraService.findByCode(cameraCode);
    }

    @PostMapping("/{cameraCode}/recordings/search")
    public CameraRecordingSearchResponse searchRecordings(
            @PathVariable String cameraCode,
            @Valid @RequestBody CameraRecordingSearchRequest request
    ) {
        return cameraHistoryService.search(cameraCode, request);
    }
}
