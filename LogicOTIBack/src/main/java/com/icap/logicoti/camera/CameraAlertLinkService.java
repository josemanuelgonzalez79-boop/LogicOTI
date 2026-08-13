package com.icap.logicoti.camera;

import com.icap.logicoti.config.CameraProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;

@Service
public class CameraAlertLinkService {

    private static final String SUBJECT = "camera-alert";
    private static final String PURPOSE = "motion-camera";
    private static final String KEY_CONTEXT =
            "LogicOTI-camera-alert-link:";
    private static final long MINIMUM_EXPIRATION_SECONDS = 60;

    private final SecretKey secretKey;
    private final long expirationSeconds;
    private final Clock clock;

    public CameraAlertLinkService(
            @Value("${security.jwt.secret}") String secret,
            CameraProperties cameraProperties
    ) {
        this(
                secret,
                cameraProperties.getAlertLinkExpirationSeconds(),
                Clock.systemUTC()
        );
    }

    private CameraAlertLinkService(
            String secret,
            long expirationSeconds,
            Clock clock
    ) {
        this.secretKey = deriveSecretKey(secret);
        this.expirationSeconds = Math.max(
                expirationSeconds,
                MINIMUM_EXPIRATION_SECONDS
        );
        this.clock = clock;
    }

    static CameraAlertLinkService forTesting(
            String secret,
            long expirationSeconds,
            Clock clock
    ) {
        return new CameraAlertLinkService(
                secret,
                expirationSeconds,
                clock
        );
    }

    public String createTargetUrl(String requestedCameraCode) {
        String cameraCode = normalizeCameraCode(requestedCameraCode);
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(expirationSeconds);

        String token = Jwts.builder()
                .subject(SUBJECT)
                .claim("purpose", PURPOSE)
                .claim("cameraCode", cameraCode)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();

        return "/camera-alert?token=" + token;
    }

    public CameraAlertAccess validate(String token) {
        if (token == null || token.isBlank()) {
            throw invalidLink();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token.trim())
                    .getPayload();

            String purpose = claims.get("purpose", String.class);
            String cameraCode = claims.get("cameraCode", String.class);
            Date expiration = claims.getExpiration();

            if (!SUBJECT.equals(claims.getSubject())
                    || !PURPOSE.equals(purpose)
                    || cameraCode == null
                    || cameraCode.isBlank()
                    || expiration == null
                    || !expiration.toInstant().isAfter(clock.instant())) {
                throw invalidLink();
            }

            return new CameraAlertAccess(
                    normalizeCameraCode(cameraCode),
                    expiration.toInstant()
            );
        } catch (CameraAlertLinkException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            throw invalidLink();
        }
    }

    private String normalizeCameraCode(String cameraCode) {
        if (cameraCode == null || cameraCode.isBlank()) {
            throw new IllegalArgumentException(
                    "La cámara del aviso es obligatoria."
            );
        }

        return cameraCode.trim().toUpperCase(Locale.ROOT);
    }

    private SecretKey deriveSecretKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET es obligatorio para crear enlaces de cámara."
            );
        }

        try {
            byte[] derivedKey = MessageDigest
                    .getInstance("SHA-256")
                    .digest(
                            (KEY_CONTEXT + secret)
                                    .getBytes(StandardCharsets.UTF_8)
                    );

            return Keys.hmacShaKeyFor(derivedKey);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "No fue posible preparar la firma de enlaces de cámara.",
                    exception
            );
        }
    }

    private CameraAlertLinkException invalidLink() {
        return new CameraAlertLinkException(
                "El enlace de la cámara es inválido o ya venció. Espera una nueva alerta."
        );
    }

    public record CameraAlertAccess(
            String cameraCode,
            Instant expiresAt
    ) {
    }
}
