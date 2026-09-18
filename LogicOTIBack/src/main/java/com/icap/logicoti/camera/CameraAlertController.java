package com.icap.logicoti.camera;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/camera-alerts")
public class CameraAlertController {

    private final CameraAlertLinkService linkService;
    private final CameraService cameraService;

    public CameraAlertController(
            CameraAlertLinkService linkService,
            CameraService cameraService
    ) {
        this.linkService = linkService;
        this.cameraService = cameraService;
    }

    @GetMapping("/view")
    public CameraAlertViewResponse findView(
            @RequestParam String token
    ) {
        CameraAlertLinkService.CameraAlertAccess access =
                linkService.validate(token);
        CameraResponse camera = cameraService.findByCode(
                access.cameraCode()
        );

        if (!camera.active()
                || !camera.videoAvailable()
                || camera.viewUrl() == null
                || camera.viewUrl().isBlank()) {
            throw new CameraAlertLinkException(
                    "La cámara relacionada no está disponible en este momento."
            );
        }

        return new CameraAlertViewResponse(
                camera.code(),
                camera.name(),
                camera.floorCode(),
                camera.areaCode(),
                camera.viewUrl(),
                access.expiresAt(),
                Instant.now()
        );
    }
}
