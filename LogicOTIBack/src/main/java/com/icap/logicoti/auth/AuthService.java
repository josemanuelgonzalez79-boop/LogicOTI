package com.icap.logicoti.auth;

import com.icap.logicoti.exception.UnauthorizedException;
import com.icap.logicoti.user.AppUser;
import com.icap.logicoti.user.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

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

        String token = jwtService.generateToken(user);

        return LoginResponse.from(
                token,
                jwtService.getExpirationSeconds(),
                user
        );
    }
}