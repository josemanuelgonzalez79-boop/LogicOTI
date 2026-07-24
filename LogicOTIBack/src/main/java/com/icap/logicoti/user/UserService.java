package com.icap.logicoti.user;

import com.icap.logicoti.exception.ConflictException;
import com.icap.logicoti.exception.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new ConflictException(
                    "El nombre de usuario ya está registrado: " + request.username()
            );
        }

        AppUser user = new AppUser(
                request.username().trim(),
                passwordEncoder.encode(request.password()),
                request.fullName().trim(),
                request.role().trim(),
                request.active()
        );

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        AppUser user = findUserById(id);

        boolean usernameBelongsToAnotherUser =
                userRepository.findByUsernameIgnoreCase(request.username())
                        .filter(existingUser -> !existingUser.getId().equals(id))
                        .isPresent();

        if (usernameBelongsToAnotherUser) {
            throw new ConflictException(
                    "El nombre de usuario ya está registrado: " + request.username()
            );
        }

        String passwordHash = user.getPasswordHash();

        if (request.password() != null && !request.password().isBlank()) {
            passwordHash = passwordEncoder.encode(request.password());
        }

        user.update(
                request.username().trim(),
                passwordHash,
                request.fullName().trim(),
                request.role().trim(),
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
}