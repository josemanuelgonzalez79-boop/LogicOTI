package com.icap.logicoti.camera;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cameras")
public class CameraController {

    private final CameraService cameraService;

    public CameraController(CameraService cameraService) {
        this.cameraService = cameraService;
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
}
