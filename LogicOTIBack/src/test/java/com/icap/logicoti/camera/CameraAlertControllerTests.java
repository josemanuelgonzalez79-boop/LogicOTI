package com.icap.logicoti.camera;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CameraAlertControllerTests {

    @Mock
    private CameraAlertLinkService linkService;

    @Mock
    private CameraService cameraService;

    @Test
    void exposesOnlyTheCameraLinkedByTheSignedToken() {
        Instant expiresAt = Instant.parse("2026-08-13T20:15:00Z");
        CameraAlertController controller = new CameraAlertController(
                linkService,
                cameraService
        );

        when(linkService.validate("signed-token"))
                .thenReturn(
                        new CameraAlertLinkService.CameraAlertAccess(
                                "CAM-008",
                                expiresAt
                        )
                );
        when(cameraService.findByCode("CAM-008"))
                .thenReturn(camera(true));

        CameraAlertViewResponse response = controller.findView(
                "signed-token"
        );

        assertEquals("CAM-008", response.cameraCode());
        assertEquals("Recepción", response.cameraName());
        assertEquals(
                "https://video.local/camera/oti-cam-08/",
                response.viewUrl()
        );
        assertEquals(expiresAt, response.expiresAt());
    }

    @Test
    void refusesTheLinkWhenTheVideoIsUnavailable() {
        CameraAlertController controller = new CameraAlertController(
                linkService,
                cameraService
        );

        when(linkService.validate("signed-token"))
                .thenReturn(
                        new CameraAlertLinkService.CameraAlertAccess(
                                "CAM-008",
                                Instant.parse("2026-08-13T20:15:00Z")
                        )
                );
        when(cameraService.findByCode("CAM-008"))
                .thenReturn(camera(false));

        assertThrows(
                CameraAlertLinkException.class,
                () -> controller.findView("signed-token")
        );
    }

    private CameraResponse camera(boolean available) {
        return new CameraResponse(
                8L,
                "CAM-008",
                8,
                "Recepción",
                "PB",
                "PB_A01",
                "NVR-6",
                "oti-cam-08",
                true,
                available,
                available
                        ? "https://video.local/camera/oti-cam-08/"
                        : null
        );
    }
}
