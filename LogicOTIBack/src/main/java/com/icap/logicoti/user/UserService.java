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
        return userRepository.findAll()
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    public UserResponse findById(Long id) {
        return UserResponse.from(findUserById(id));
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

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        AppUser user = findUserById(id);
        String username = request.username().trim();

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
                normalizeRole(request.role()),
                request.active()
        );

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void delete(Long id) {
        AppUser user = findUserById(id);
        userRepository.delete(user);
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
