package com.icap.logicoti.auth;

import com.icap.logicoti.exception.ConflictException;
import com.icap.logicoti.exception.ResourceNotFoundException;
import com.icap.logicoti.exception.UnauthorizedException;
import com.icap.logicoti.user.AppUser;
import com.icap.logicoti.user.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
public class TwoFactorService {

    private static final int RECOVERY_CODE_COUNT = 8;
    private static final int SETUP_EXPIRATION_SECONDS = 600;
    private static final String RECOVERY_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final JdbcTemplate jdbcTemplate;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final TwoFactorSecretCipher secretCipher;
    private final QrCodeService qrCodeService;
    private final String issuer;
    private final long challengeExpirationSeconds;
    private final int challengeMaxAttempts;
    private final int accountMaxFailures;
    private final long accountLockSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public TwoFactorService(
            JdbcTemplate jdbcTemplate,
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TotpService totpService,
            TwoFactorSecretCipher secretCipher,
            QrCodeService qrCodeService,
            @Value("${security.two-factor.issuer:LogicOTI}") String issuer,
            @Value("${security.two-factor.challenge-expiration-seconds:300}")
            long challengeExpirationSeconds,
            @Value("${security.two-factor.challenge-max-attempts:5}")
            int challengeMaxAttempts,
            @Value("${security.two-factor.account-max-failures:10}")
            int accountMaxFailures,
            @Value("${security.two-factor.account-lock-seconds:900}")
            long accountLockSeconds
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.totpService = totpService;
        this.secretCipher = secretCipher;
        this.qrCodeService = qrCodeService;
        this.issuer = issuer == null || issuer.isBlank() ? "LogicOTI" : issuer.trim();
        this.challengeExpirationSeconds = Math.clamp(challengeExpirationSeconds, 60, 900);
        this.challengeMaxAttempts = Math.clamp(challengeMaxAttempts, 1, 10);
        this.accountMaxFailures = Math.clamp(accountMaxFailures, 5, 20);
        this.accountLockSeconds = Math.clamp(accountLockSeconds, 60, 3600);
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(long userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_two_factor
                WHERE user_id = ? AND enabled = TRUE
                """, Integer.class, userId);
        return count != null && count > 0;
    }

    @Transactional(readOnly = true)
    public TwoFactorStatusResponse getStatus(String username) {
        AppUser user = findActiveUser(username);
        List<TwoFactorRow> rows = findTwoFactor(user.getId(), false);
        boolean enabled = !rows.isEmpty() && rows.getFirst().enabled();
        Integer recoveryCodes = enabled
                ? jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM user_two_factor_recovery_code
                        WHERE user_id = ? AND used_at IS NULL
                        """, Integer.class, user.getId())
                : 0;
        return new TwoFactorStatusResponse(
                enabled,
                recoveryCodes == null ? 0 : recoveryCodes
        );
    }

    @Transactional
    public TwoFactorSetupResponse beginSetup(
            String username,
            TwoFactorSetupRequest request
    ) {
        AppUser user = findActiveUser(username);
        verifyCurrentPassword(user, request.currentPassword());

        if (isEnabled(user.getId())) {
            throw new ConflictException("La autenticación en dos pasos ya está activa.");
        }

        String secret = totpService.generateSecret();
        String encryptedSecret = secretCipher.encrypt(secret);
        Instant expiresAt = Instant.now().plusSeconds(SETUP_EXPIRATION_SECONDS);
        int updated = jdbcTemplate.update("""
                UPDATE user_two_factor
                SET encrypted_secret = ?, enabled = FALSE,
                    setup_expires_at = ?, confirmed_at = NULL,
                    last_accepted_time_step = NULL,
                    failed_attempts = 0, blocked_until = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                """, encryptedSecret, Timestamp.from(expiresAt), user.getId());

        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO user_two_factor (
                        user_id, encrypted_secret, enabled, setup_expires_at
                    ) VALUES (?, ?, FALSE, ?)
                    """, user.getId(), encryptedSecret, Timestamp.from(expiresAt));
        }

        jdbcTemplate.update(
                "DELETE FROM user_two_factor_recovery_code WHERE user_id = ?",
                user.getId()
        );

        String uri = createOtpAuthUri(user.getUsername(), secret);
        return new TwoFactorSetupResponse(
                groupSecret(secret),
                qrCodeService.createDataUrl(uri),
                user.getUsername(),
                issuer,
                expiresAt
        );
    }

    @Transactional
    public TwoFactorConfirmationResponse confirmSetup(
            String username,
            TwoFactorCodeRequest request
    ) {
        AppUser user = findActiveUser(username);
        List<TwoFactorRow> rows = findTwoFactor(user.getId(), true);

        if (rows.isEmpty() || rows.getFirst().enabled()) {
            throw new ConflictException("No existe una configuración de 2FA pendiente.");
        }

        TwoFactorRow row = rows.getFirst();
        if (row.setupExpiresAt() == null || !row.setupExpiresAt().isAfter(Instant.now())) {
            throw new ConflictException("La configuración de 2FA venció. Genere un código QR nuevo.");
        }

        String secret = secretCipher.decrypt(row.encryptedSecret());
        Long timeStep = totpService.findMatchingTimeStep(secret, request.code(), Instant.now());
        if (timeStep == null) {
            throw new UnauthorizedException("El código temporal no es válido.");
        }

        List<String> recoveryCodes = generateRecoveryCodes();
        jdbcTemplate.update(
                "DELETE FROM user_two_factor_recovery_code WHERE user_id = ?",
                user.getId()
        );
        recoveryCodes.forEach(code -> jdbcTemplate.update("""
                INSERT INTO user_two_factor_recovery_code (user_id, code_hash)
                VALUES (?, ?)
                """, user.getId(), passwordEncoder.encode(normalizeRecoveryCode(code))));
        jdbcTemplate.update("""
                UPDATE user_two_factor
                SET enabled = TRUE, setup_expires_at = NULL,
                    confirmed_at = CURRENT_TIMESTAMP,
                    last_accepted_time_step = ?,
                    failed_attempts = 0, blocked_until = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                """, timeStep, user.getId());

        return new TwoFactorConfirmationResponse(true, recoveryCodes);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public TwoFactorStatusResponse disable(
            String username,
            TwoFactorDisableRequest request
    ) {
        AppUser user = findActiveUser(username);
        verifyCurrentPassword(user, request.currentPassword());

        if (!isEnabled(user.getId())) {
            throw new ConflictException("La autenticación en dos pasos no está activa.");
        }

        if (!verifyEnabledCode(user.getId(), request.code(), true)) {
            throw new UnauthorizedException("El código de verificación no es válido.");
        }

        jdbcTemplate.update(
                "DELETE FROM auth_two_factor_challenge WHERE user_id = ?",
                user.getId()
        );
        jdbcTemplate.update(
                "DELETE FROM user_two_factor WHERE user_id = ?",
                user.getId()
        );
        return new TwoFactorStatusResponse(false, 0);
    }

    @Transactional
    public Challenge createChallenge(AppUser user) {
        Instant expiresAt = Instant.now().plusSeconds(challengeExpirationSeconds);
        // Sólo un desafío puede permanecer activo por usuario. Además de
        // simplificar el flujo, evita acumular intentos paralelos de 2FA.
        jdbcTemplate.update(
                "DELETE FROM auth_two_factor_challenge WHERE user_id = ?",
                user.getId()
        );
        String token = randomUrlToken(32);
        jdbcTemplate.update("""
                INSERT INTO auth_two_factor_challenge (
                    token_hash, user_id, attempts_remaining, expires_at
                ) VALUES (?, ?, ?, ?)
                """, sha256(token), user.getId(), challengeMaxAttempts, Timestamp.from(expiresAt));
        return new Challenge(token, challengeExpirationSeconds);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AppUser verifyChallenge(TwoFactorVerifyRequest request) {
        List<ChallengeRow> rows = jdbcTemplate.query("""
                SELECT user_id, attempts_remaining, expires_at, used_at
                FROM auth_two_factor_challenge
                WHERE token_hash = ?
                FOR UPDATE
                """, (resultSet, rowNumber) -> new ChallengeRow(
                        resultSet.getLong("user_id"),
                        resultSet.getInt("attempts_remaining"),
                        resultSet.getTimestamp("expires_at").toInstant(),
                        resultSet.getTimestamp("used_at") == null
                                ? null
                                : resultSet.getTimestamp("used_at").toInstant()
                ), sha256(request.challengeToken().trim()));

        if (rows.isEmpty()) {
            throw invalidChallenge();
        }

        ChallengeRow challenge = rows.getFirst();
        if (challenge.usedAt() != null
                || !challenge.expiresAt().isAfter(Instant.now())
                || challenge.attemptsRemaining() <= 0) {
            throw invalidChallenge();
        }

        AppUser user = userRepository.findById(challenge.userId())
                .filter(AppUser::isActive)
                .orElseThrow(this::invalidChallenge);

        if (!verifyEnabledCode(user.getId(), request.code(), true)) {
            jdbcTemplate.update("""
                    UPDATE auth_two_factor_challenge
                    SET attempts_remaining = GREATEST(attempts_remaining - 1, 0),
                        used_at = CASE WHEN attempts_remaining <= 1
                            THEN CURRENT_TIMESTAMP ELSE used_at END
                    WHERE token_hash = ?
                    """, sha256(request.challengeToken().trim()));
            throw new UnauthorizedException("El código de verificación no es válido.");
        }

        jdbcTemplate.update("""
                UPDATE auth_two_factor_challenge
                SET used_at = CURRENT_TIMESTAMP
                WHERE token_hash = ?
                """, sha256(request.challengeToken().trim()));
        return user;
    }

    private boolean verifyEnabledCode(
            long userId,
            String requestedCode,
            boolean preventTotpReplay
    ) {
        List<TwoFactorRow> rows = findTwoFactor(userId, true);
        if (rows.isEmpty() || !rows.getFirst().enabled()) {
            return false;
        }

        TwoFactorRow row = rows.getFirst();
        String normalizedCode = requestedCode == null ? "" : requestedCode.trim();
        Instant now = Instant.now();

        if (row.blockedUntil() != null && row.blockedUntil().isAfter(now)) {
            return false;
        }

        if (normalizedCode.matches("\\d{6}")) {
            Long timeStep = totpService.findMatchingTimeStep(
                    secretCipher.decrypt(row.encryptedSecret()),
                    normalizedCode,
                    now
            );

            if (timeStep == null
                    || (preventTotpReplay
                    && row.lastAcceptedTimeStep() != null
                    && timeStep <= row.lastAcceptedTimeStep())) {
                recordFailedVerification(userId, row.failedAttempts(), now);
                return false;
            }
            if (preventTotpReplay) {
                jdbcTemplate.update("""
                        UPDATE user_two_factor
                        SET last_accepted_time_step = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE user_id = ?
                        """, timeStep, userId);
            }
            resetFailedVerifications(userId);
            return true;
        }

        String recoveryCode = normalizeRecoveryCode(requestedCode);
        if (recoveryCode.isBlank()) {
            recordFailedVerification(userId, row.failedAttempts(), now);
            return false;
        }

        List<RecoveryCodeRow> recoveryCodes = jdbcTemplate.query("""
                SELECT id, code_hash FROM user_two_factor_recovery_code
                WHERE user_id = ? AND used_at IS NULL
                ORDER BY id
                FOR UPDATE
                """, (resultSet, rowNumber) -> new RecoveryCodeRow(
                        resultSet.getLong("id"),
                        resultSet.getString("code_hash")
                ), userId);

        for (RecoveryCodeRow candidate : recoveryCodes) {
            if (passwordEncoder.matches(recoveryCode, candidate.codeHash())) {
                jdbcTemplate.update("""
                        UPDATE user_two_factor_recovery_code
                        SET used_at = CURRENT_TIMESTAMP
                        WHERE id = ? AND used_at IS NULL
                        """, candidate.id());
                resetFailedVerifications(userId);
                return true;
            }
        }
        recordFailedVerification(userId, row.failedAttempts(), now);
        return false;
    }

    private void recordFailedVerification(
            long userId,
            int previousFailures,
            Instant attemptedAt
    ) {
        int failures = Math.min(previousFailures + 1, accountMaxFailures);
        Timestamp blockedUntil = failures >= accountMaxFailures
                ? Timestamp.from(attemptedAt.plusSeconds(accountLockSeconds))
                : null;
        jdbcTemplate.update("""
                UPDATE user_two_factor
                SET failed_attempts = ?, blocked_until = ?, updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                """, failures, blockedUntil, userId);
    }

    private void resetFailedVerifications(long userId) {
        jdbcTemplate.update("""
                UPDATE user_two_factor
                SET failed_attempts = 0, blocked_until = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                """, userId);
    }

    private List<TwoFactorRow> findTwoFactor(long userId, boolean forUpdate) {
        return jdbcTemplate.query("""
                SELECT encrypted_secret, enabled, setup_expires_at, last_accepted_time_step,
                       failed_attempts, blocked_until
                FROM user_two_factor
                WHERE user_id = ?
                """ + (forUpdate ? " FOR UPDATE" : ""),
                (resultSet, rowNumber) -> new TwoFactorRow(
                        resultSet.getString("encrypted_secret"),
                        resultSet.getBoolean("enabled"),
                        resultSet.getTimestamp("setup_expires_at") == null
                                ? null
                                : resultSet.getTimestamp("setup_expires_at").toInstant(),
                        resultSet.getObject("last_accepted_time_step", Long.class),
                        resultSet.getInt("failed_attempts"),
                        resultSet.getTimestamp("blocked_until") == null
                                ? null
                                : resultSet.getTimestamp("blocked_until").toInstant()
                ), userId);
    }

    private AppUser findActiveUser(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .filter(AppUser::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario activo no encontrado."));
    }

    private void verifyCurrentPassword(AppUser user, String password) {
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedException("La contraseña actual no es correcta.");
        }
    }

    private List<String> generateRecoveryCodes() {
        List<String> codes = new ArrayList<>();
        while (codes.size() < RECOVERY_CODE_COUNT) {
            String code = randomRecoveryGroup() + "-" + randomRecoveryGroup()
                    + "-" + randomRecoveryGroup();
            if (!codes.contains(code)) {
                codes.add(code);
            }
        }
        return List.copyOf(codes);
    }

    private String randomRecoveryGroup() {
        StringBuilder result = new StringBuilder(4);
        for (int index = 0; index < 4; index++) {
            result.append(RECOVERY_ALPHABET.charAt(
                    secureRandom.nextInt(RECOVERY_ALPHABET.length())
            ));
        }
        return result.toString();
    }

    private String randomUrlToken(int byteCount) {
        byte[] bytes = new byte[byteCount];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible.", exception);
        }
    }

    private String createOtpAuthUri(String username, String secret) {
        String label = urlEncode(issuer + ":" + username);
        return "otpauth://totp/" + label
                + "?secret=" + urlEncode(secret)
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1&digits=6&period=30";
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private String groupSecret(String secret) {
        return secret.replaceAll("(.{4})(?!$)", "$1 ");
    }

    private String normalizeRecoveryCode(String code) {
        return code == null
                ? ""
                : code.replace("-", "")
                        .replace(" ", "")
                        .trim()
                        .toUpperCase(Locale.ROOT);
    }

    private UnauthorizedException invalidChallenge() {
        return new UnauthorizedException(
                "El desafío de autenticación venció o ya fue utilizado. Inicie sesión nuevamente."
        );
    }

    public record Challenge(String token, long expiresIn) {
    }

    private record TwoFactorRow(
            String encryptedSecret,
            boolean enabled,
            Instant setupExpiresAt,
            Long lastAcceptedTimeStep,
            int failedAttempts,
            Instant blockedUntil
    ) {
    }

    private record ChallengeRow(
            long userId,
            int attemptsRemaining,
            Instant expiresAt,
            Instant usedAt
    ) {
    }

    private record RecoveryCodeRow(long id, String codeHash) {
    }
}
