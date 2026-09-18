package com.icap.logicoti.auth;

import com.icap.logicoti.exception.UnauthorizedException;
import com.icap.logicoti.user.AppUser;
import com.icap.logicoti.user.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TwoFactorService twoFactorService;

    public AuthService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TwoFactorService twoFactorService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.twoFactorService = twoFactorService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        AppUser user = userRepository
                .findByUsernameIgnoreCase(request.username().trim())
                .orElseThrow(() ->
                        new UnauthorizedException(
                                "Usuario o contraseña incorrectos."
                        )
                );

        if (!user.isActive()) {
            throw new UnauthorizedException(
                    "El usuario se encuentra inactivo."
            );
        }

        boolean passwordMatches = passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        );

        if (!passwordMatches) {
            throw new UnauthorizedException(
                    "Usuario o contraseña incorrectos."
            );
        }

        if (twoFactorService.isEnabled(user.getId())) {
            return LoginResponse.challenge(
                    twoFactorService.createChallenge(user)
            );
        }

        return authenticatedResponse(user);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public LoginResponse verifyTwoFactor(TwoFactorVerifyRequest request) {
        return authenticatedResponse(
                twoFactorService.verifyChallenge(request)
        );
    }

    @Transactional(readOnly = true)
    public TwoFactorStatusResponse getTwoFactorStatus(String username) {
        return twoFactorService.getStatus(username);
    }

    @Transactional
    public TwoFactorSetupResponse setupTwoFactor(
            String username,
            TwoFactorSetupRequest request
    ) {
        return twoFactorService.beginSetup(username, request);
    }

    @Transactional
    public TwoFactorConfirmationResponse confirmTwoFactor(
            String username,
            TwoFactorCodeRequest request
    ) {
        return twoFactorService.confirmSetup(username, request);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public TwoFactorStatusResponse disableTwoFactor(
            String username,
            TwoFactorDisableRequest request
    ) {
        return twoFactorService.disable(username, request);
    }

    private LoginResponse authenticatedResponse(AppUser user) {
        return LoginResponse.authenticated(
                jwtService.generateToken(user),
                jwtService.getExpirationSeconds(),
                user
        );
    }
}
