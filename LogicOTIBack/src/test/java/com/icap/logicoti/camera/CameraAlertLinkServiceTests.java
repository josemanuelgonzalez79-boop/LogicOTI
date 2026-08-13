package com.icap.logicoti.camera;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraAlertLinkServiceTests {

    private static final String SECRET =
            "LogicOTI-Test-Secret-Camera-Link-2026-0123456789";
    private static final Instant NOW =
            Instant.parse("2026-08-13T20:00:00Z");

    @Test
    void createsAValidShortLivedLinkForTheRequestedCamera() {
        CameraAlertLinkService service = serviceAt(NOW);

        String targetUrl = service.createTargetUrl("cam-008");
        String token = targetUrl.substring(
                targetUrl.indexOf("token=") + "token=".length()
        );
        CameraAlertLinkService.CameraAlertAccess access =
                service.validate(token);

        assertTrue(targetUrl.startsWith("/camera-alert?token="));
        assertEquals("CAM-008", access.cameraCode());
        assertEquals(NOW.plusSeconds(900), access.expiresAt());
    }

    @Test
    void rejectsATamperedLink() {
        CameraAlertLinkService service = serviceAt(NOW);
        String targetUrl = service.createTargetUrl("CAM-008");
        String token = targetUrl.substring(
                targetUrl.indexOf("token=") + "token=".length()
        );
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");

        assertThrows(
                CameraAlertLinkException.class,
                () -> service.validate(tampered)
        );
    }

    @Test
    void rejectsAnExpiredLink() {
        CameraAlertLinkService issuer = serviceAt(NOW);
        String targetUrl = issuer.createTargetUrl("CAM-008");
        String token = targetUrl.substring(
                targetUrl.indexOf("token=") + "token=".length()
        );
        CameraAlertLinkService validator = serviceAt(
                NOW.plusSeconds(901)
        );

        assertThrows(
                CameraAlertLinkException.class,
                () -> validator.validate(token)
        );
    }

    private CameraAlertLinkService serviceAt(Instant instant) {
        return CameraAlertLinkService.forTesting(
                SECRET,
                900,
                Clock.fixed(instant, ZoneOffset.UTC)
        );
    }
}
