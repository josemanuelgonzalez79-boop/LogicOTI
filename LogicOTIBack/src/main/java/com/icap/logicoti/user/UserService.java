package com.icap.logicoti.user;

import com.icap.logicoti.exception.ConflictException;
import com.icap.logicoti.exception.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class UserService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponse> findAll() {
        Long protectedAdministratorId = findProtectedAdministratorId();

        return userRepository.findAll()
                .stream()
                .map(user -> toResponse(
                        user,
                        protectedAdministratorId
                ))
                .toList();
    }

    public UserResponse findById(Long id) {
        return toResponse(
                findUserById(id),
                findProtectedAdministratorId()
        );
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        String username = request.username().trim();

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException(
                    "El nombre de usuario ya está registrado: " + username
            );
        }

        AppUser user = new AppUser(
                username,
                passwordEncoder.encode(request.password()),
                request.fullName().trim(),
                normalizeRole(request.role()),
                request.active()
        );

        AppUser savedUser = userRepository.save(user);

        return toResponse(
                savedUser,
                findProtectedAdministratorId()
        );
    }

    @Transactional
    public UserResponse update(
            Long id,
            UserUpdateRequest request,
            String requestedBy
    ) {
        AppUser user = findUserById(id);
        String username = request.username().trim();
        String normalizedRole = normalizeRole(request.role());
        Long protectedAdministratorId =
                findProtectedAdministratorId();

        validateProtectedAdministratorUpdate(
                user,
                protectedAdministratorId,
                username,
                normalizedRole,
                request.active()
        );

        validateLastActiveAdministratorUpdate(
                user,
                normalizedRole,
                request.active()
        );

        if (user.getUsername().equalsIgnoreCase(requestedBy)
                && !request.active()) {
            throw new ConflictException(
                    "No puedes bloquear la cuenta con la que tienes la sesión iniciada."
            );
        }

        boolean usernameBelongsToAnotherUser =
                userRepository.findByUsernameIgnoreCase(username)
                        .filter(existingUser -> !existingUser.getId().equals(id))
                        .isPresent();

        if (usernameBelongsToAnotherUser) {
            throw new ConflictException(
                    "El nombre de usuario ya está registrado: " + username
            );
        }

        String passwordHash = user.getPasswordHash();

        if (request.password() != null && !request.password().isBlank()) {
            passwordHash = passwordEncoder.encode(request.password());
        }

        user.update(
                username,
                passwordHash,
                request.fullName().trim(),
                normalizedRole,
                request.active()
        );

        AppUser savedUser = userRepository.save(user);

        return toResponse(
                savedUser,
                protectedAdministratorId
        );
    }

    @Transactional
    public void delete(Long id, String requestedBy) {
        AppUser user = findUserById(id);

        if (user.getUsername().equalsIgnoreCase(requestedBy)) {
            throw new ConflictException(
                    "No puedes eliminar la cuenta con la que tienes la sesión iniciada."
            );
        }

        Long protectedAdministratorId =
                findProtectedAdministratorId();

        if (user.getId().equals(protectedAdministratorId)) {
            throw new ConflictException(
                    "El administrador principal no se puede eliminar."
            );
        }

        if (isActiveAdministrator(user)
                && activeAdministratorCount() <= 1) {
            throw new ConflictException(
                    "Debe permanecer al menos un administrador activo."
            );
        }

        userRepository.delete(user);
    }

    private void validateProtectedAdministratorUpdate(
            AppUser user,
            Long protectedAdministratorId,
            String username,
            String role,
            boolean active
    ) {
        if (!user.getId().equals(protectedAdministratorId)) {
            return;
        }

        if (!user.getUsername().equals(username)) {
            throw new ConflictException(
                    "El nombre de usuario del administrador principal no se puede cambiar."
            );
        }

        if (!ADMIN_ROLE.equals(role) || !active) {
            throw new ConflictException(
                    "El administrador principal debe conservar el rol ADMIN y permanecer activo."
            );
        }
    }

    private void validateLastActiveAdministratorUpdate(
            AppUser user,
            String newRole,
            boolean newActive
    ) {
        boolean stopsBeingActiveAdministrator =
                isActiveAdministrator(user)
                        && (!ADMIN_ROLE.equals(newRole) || !newActive);

        if (stopsBeingActiveAdministrator
                && activeAdministratorCount() <= 1) {
            throw new ConflictException(
                    "Debe permanecer al menos un administrador activo."
            );
        }
    }

    private boolean isActiveAdministrator(AppUser user) {
        return ADMIN_ROLE.equalsIgnoreCase(user.getRole())
                && user.isActive();
    }

    private long activeAdministratorCount() {
        return userRepository
                .countByRoleIgnoreCaseAndActiveTrue(ADMIN_ROLE);
    }

    private Long findProtectedAdministratorId() {
        return userRepository
                .findFirstByRoleIgnoreCaseOrderByIdAsc(ADMIN_ROLE)
                .map(AppUser::getId)
                .orElse(null);
    }

    private UserResponse toResponse(
            AppUser user,
            Long protectedAdministratorId
    ) {
        return UserResponse.from(
                user,
                user.getId().equals(protectedAdministratorId)
        );
    }

    private AppUser findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Usuario no encontrado con id: " + id
                        )
                );
    }

    private String normalizeRole(String role) {
        return role.trim().toUpperCase(Locale.ROOT);
    }
}
