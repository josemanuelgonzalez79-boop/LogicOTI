package com.icap.logicoti.auth;

import com.icap.logicoti.user.AppUser;
import com.icap.logicoti.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private TwoFactorService twoFactorService;

    @Mock
    private AppUser user;

    @InjectMocks
    private AuthService service;

    @Test
    void passwordDoesNotCreateJwtWhenTwoFactorIsEnabled() {
        when(userRepository.findByUsernameIgnoreCase("operador"))
                .thenReturn(Optional.of(user));
        when(user.isActive()).thenReturn(true);
        when(user.getPasswordHash()).thenReturn("hash");
        when(user.getId()).thenReturn(7L);
        when(passwordEncoder.matches("correcta", "hash")).thenReturn(true);
        when(twoFactorService.isEnabled(7L)).thenReturn(true);
        when(twoFactorService.createChallenge(user))
                .thenReturn(new TwoFactorService.Challenge("desafio", 300));

        LoginResponse response = service.login(
                new LoginRequest("operador", "correcta")
        );

        assertThat(response.requiresTwoFactor()).isTrue();
        assertThat(response.challengeToken()).isEqualTo("desafio");
        assertThat(response.token()).isNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    void jwtIsCreatedAfterTheSecondFactorIsAccepted() {
        var request = new TwoFactorVerifyRequest("desafio", "123456");
        when(twoFactorService.verifyChallenge(request)).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("jwt");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);
        when(user.getId()).thenReturn(7L);
        when(user.getUsername()).thenReturn("operador");
        when(user.getFullName()).thenReturn("Operador");
        when(user.getRole()).thenReturn("OPERATOR");

        LoginResponse response = service.verifyTwoFactor(request);

        assertThat(response.requiresTwoFactor()).isFalse();
        assertThat(response.token()).isEqualTo("jwt");
        assertThat(response.user().username()).isEqualTo("operador");
    }
}
