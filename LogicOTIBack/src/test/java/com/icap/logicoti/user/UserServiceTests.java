package com.icap.logicoti.user;

import com.icap.logicoti.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService service;

    @Test
    void doesNotDeleteThePrimaryAdministrator() {
        AppUser primaryAdministrator = user(
                2L,
                "admin",
                "ADMIN",
                true
        );

        when(userRepository.findById(2L))
                .thenReturn(Optional.of(primaryAdministrator));
        when(userRepository.findFirstByRoleIgnoreCaseOrderByIdAsc("ADMIN"))
                .thenReturn(Optional.of(primaryAdministrator));

        assertThatThrownBy(() ->
                service.delete(2L, "otro.admin")
        )
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("administrador principal");

        verify(userRepository, never()).delete(any());
    }

    @Test
    void doesNotDeleteTheAccountThatOwnsTheCurrentSession() {
        AppUser currentAdministrator = user(
                5L,
                "admin.secundario",
                "ADMIN",
                true
        );

        when(userRepository.findById(5L))
                .thenReturn(Optional.of(currentAdministrator));

        assertThatThrownBy(() ->
                service.delete(5L, "admin.secundario")
        )
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("sesión iniciada");

        verify(userRepository, never()).delete(any());
    }

    @Test
    void deletesAnUnprotectedUser() {
        AppUser primaryAdministrator = user(
                2L,
                "admin",
                "ADMIN",
                true
        );
        AppUser operator = user(
                8L,
                "operador2",
                "OPERATOR",
                true
        );

        when(userRepository.findById(8L))
                .thenReturn(Optional.of(operator));
        when(userRepository.findFirstByRoleIgnoreCaseOrderByIdAsc("ADMIN"))
                .thenReturn(Optional.of(primaryAdministrator));

        service.delete(8L, "admin");

        verify(userRepository).delete(operator);
    }

    @Test
    void hashesTheNewPasswordBeforeUpdatingTheUser() {
        AppUser primaryAdministrator = user(
                2L,
                "admin",
                "ADMIN",
                true
        );
        AppUser operator = user(
                8L,
                "operador2",
                "OPERATOR",
                true
        );
        UserUpdateRequest request = new UserUpdateRequest(
                "operador2",
                "claveNueva123",
                "Operador 2",
                "OPERATOR",
                true
        );

        when(userRepository.findById(8L))
                .thenReturn(Optional.of(operator));
        when(userRepository.findFirstByRoleIgnoreCaseOrderByIdAsc("ADMIN"))
                .thenReturn(Optional.of(primaryAdministrator));
        when(userRepository.findByUsernameIgnoreCase("operador2"))
                .thenReturn(Optional.of(operator));
        when(passwordEncoder.encode("claveNueva123"))
                .thenReturn("hash-seguro");
        when(userRepository.save(operator))
                .thenReturn(operator);

        service.update(8L, request);

        verify(passwordEncoder).encode("claveNueva123");
        verify(operator).update(
                "operador2",
                "hash-seguro",
                "Operador 2",
                "OPERATOR",
                true
        );
    }

    private AppUser user(
            Long id,
            String username,
            String role,
            boolean active
    ) {
        AppUser user = org.mockito.Mockito.mock(AppUser.class);

        lenient().when(user.getId()).thenReturn(id);
        lenient().when(user.getUsername()).thenReturn(username);
        lenient().when(user.getPasswordHash()).thenReturn("hash-actual");
        lenient().when(user.getFullName()).thenReturn(username);
        lenient().when(user.getRole()).thenReturn(role);
        lenient().when(user.isActive()).thenReturn(active);
        lenient().when(user.getCreatedAt()).thenReturn(Instant.parse("2026-07-29T16:00:00Z"));

        return user;
    }
}
