package com.icap.logicoti.auth;

import com.icap.logicoti.exception.UnauthorizedException;
import com.icap.logicoti.user.AppUser;
import com.icap.logicoti.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TwoFactorServiceTests {

    private JdbcTemplate jdbc;
    private AppUser user;
    private TotpService totp;
    private TwoFactorService service;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:two_factor_" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        ));
        createSchema();

        var users = mock(AppUserRepository.class);
        var encoder = new BCryptPasswordEncoder(4);
        user = mock(AppUser.class);
        when(user.getId()).thenReturn(7L);
        when(user.getUsername()).thenReturn("operador");
        when(user.getPasswordHash()).thenReturn(encoder.encode("correcta"));
        when(user.isActive()).thenReturn(true);
        when(users.findByUsernameIgnoreCase("operador")).thenReturn(Optional.of(user));
        when(users.findById(7L)).thenReturn(Optional.of(user));

        totp = new TotpService();
        service = new TwoFactorService(
                jdbc,
                users,
                encoder,
                totp,
                new TwoFactorSecretCipher("clave-maestra-de-prueba"),
                new QrCodeService(),
                "LogicOTI",
                300,
                5,
                10,
                900
        );
    }

    @Test
    void setupCreatesEncryptedSecretAndOneTimeRecoveryCodes() {
        EnabledAccount enabled = enableAccount();

        assertThat(service.getStatus("operador"))
                .isEqualTo(new TwoFactorStatusResponse(true, 8));
        assertThat(enabled.confirmation().recoveryCodes())
                .hasSize(8)
                .doesNotHaveDuplicates();

        String encrypted = jdbc.queryForObject(
                "SELECT encrypted_secret FROM user_two_factor WHERE user_id = 7",
                String.class
        );
        assertThat(encrypted).doesNotContain(enabled.secret());
    }

    @Test
    void disableAcceptsTheCurrentCodeImmediatelyAfterSetup() {
        TwoFactorSetupResponse setup = service.beginSetup(
                "operador",
                new TwoFactorSetupRequest("correcta")
        );
        String secret = setup.manualKey().replace(" ", "");
        String currentCode = totp.generateCode(
                secret,
                Instant.now().getEpochSecond()
                        / TotpService.TIME_STEP_SECONDS
        );
        service.confirmSetup(
                "operador",
                new TwoFactorCodeRequest(currentCode)
        );

        TwoFactorStatusResponse status = service.disable(
                "operador",
                new TwoFactorDisableRequest(
                        "correcta",
                        currentCode
                )
        );

        assertThat(status).isEqualTo(
                new TwoFactorStatusResponse(false, 0)
        );
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_two_factor WHERE user_id = 7",
                Integer.class
        )).isZero();
    }

    @Test
    void loginChallengeAcceptsCurrentTotpOnlyOnce() {
        EnabledAccount enabled = enableAccount();
        String currentCode = totp.generateCode(
                enabled.secret(),
                Instant.now().getEpochSecond() / TotpService.TIME_STEP_SECONDS
        );

        var first = service.createChallenge(user);
        assertThat(service.verifyChallenge(
                new TwoFactorVerifyRequest(first.token(), currentCode)
        )).isSameAs(user);

        var second = service.createChallenge(user);
        assertThatThrownBy(() -> service.verifyChallenge(
                new TwoFactorVerifyRequest(second.token(), currentCode)
        )).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void replacingChallengeInvalidatesThePreviousOneAndLimitsAttempts() {
        enableAccount();
        var previous = service.createChallenge(user);
        var current = service.createChallenge(user);

        assertThatThrownBy(() -> service.verifyChallenge(
                new TwoFactorVerifyRequest(previous.token(), "INVALID-CODE")
        )).isInstanceOf(UnauthorizedException.class);

        assertThatThrownBy(() -> service.verifyChallenge(
                new TwoFactorVerifyRequest(current.token(), "INVALID-CODE")
        )).isInstanceOf(UnauthorizedException.class);

        Integer attempts = jdbc.queryForObject(
                "SELECT attempts_remaining FROM auth_two_factor_challenge",
                Integer.class
        );
        assertThat(attempts).isEqualTo(4);
    }

    @Test
    void recoveryCodeCanBeUsedOnlyOnce() {
        EnabledAccount enabled = enableAccount();
        String recoveryCode = enabled.confirmation().recoveryCodes().getFirst();

        var first = service.createChallenge(user);
        assertThat(service.verifyChallenge(
                new TwoFactorVerifyRequest(first.token(), recoveryCode)
        )).isSameAs(user);

        var second = service.createChallenge(user);
        assertThatThrownBy(() -> service.verifyChallenge(
                new TwoFactorVerifyRequest(second.token(), recoveryCode)
        )).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void repeatedChallengesCannotBypassTheAccountFailureLimit() {
        EnabledAccount enabled = enableAccount();

        for (int attempt = 0; attempt < 10; attempt++) {
            var challenge = service.createChallenge(user);
            assertThatThrownBy(() -> service.verifyChallenge(
                    new TwoFactorVerifyRequest(challenge.token(), "INVALID-CODE")
            )).isInstanceOf(UnauthorizedException.class);
        }

        Instant blockedUntil = jdbc.queryForObject(
                "SELECT blocked_until FROM user_two_factor WHERE user_id = 7",
                (resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant()
        );
        assertThat(blockedUntil).isAfter(Instant.now());

        String validCode = totp.generateCode(
                enabled.secret(),
                Instant.now().getEpochSecond() / TotpService.TIME_STEP_SECONDS
        );
        var challenge = service.createChallenge(user);
        assertThatThrownBy(() -> service.verifyChallenge(
                new TwoFactorVerifyRequest(challenge.token(), validCode)
        )).isInstanceOf(UnauthorizedException.class);
    }

    private EnabledAccount enableAccount() {
        TwoFactorSetupResponse setup = service.beginSetup(
                "operador",
                new TwoFactorSetupRequest("correcta")
        );
        String secret = setup.manualKey().replace(" ", "");
        long currentStep = Instant.now().getEpochSecond()
                / TotpService.TIME_STEP_SECONDS;
        TwoFactorConfirmationResponse confirmation = service.confirmSetup(
                "operador",
                new TwoFactorCodeRequest(totp.generateCode(secret, currentStep))
        );
        // Permite probar un inicio de sesión en el mismo intervalo sin esperar
        // treinta segundos; la prevención de reutilización se prueba después.
        jdbc.update(
                "UPDATE user_two_factor SET last_accepted_time_step = ? WHERE user_id = 7",
                currentStep - 1
        );
        return new EnabledAccount(secret, confirmation);
    }

    private void createSchema() {
        jdbc.execute("""
                CREATE TABLE user_two_factor (
                    user_id BIGINT PRIMARY KEY,
                    encrypted_secret VARCHAR(1024) NOT NULL,
                    enabled BOOLEAN NOT NULL DEFAULT FALSE,
                    setup_expires_at TIMESTAMP WITH TIME ZONE,
                    confirmed_at TIMESTAMP WITH TIME ZONE,
                    last_accepted_time_step BIGINT,
                    failed_attempts SMALLINT NOT NULL DEFAULT 0,
                    blocked_until TIMESTAMP WITH TIME ZONE,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                CREATE TABLE user_two_factor_recovery_code (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    code_hash VARCHAR(255) NOT NULL,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    used_at TIMESTAMP WITH TIME ZONE
                );
                CREATE TABLE auth_two_factor_challenge (
                    token_hash CHAR(64) PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    attempts_remaining SMALLINT NOT NULL,
                    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    used_at TIMESTAMP WITH TIME ZONE,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                """);
    }

    private record EnabledAccount(
            String secret,
            TwoFactorConfirmationResponse confirmation
    ) {
    }
}
