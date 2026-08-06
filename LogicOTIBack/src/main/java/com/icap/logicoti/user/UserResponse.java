package com.icap.logicoti.user;

import java.time.Instant;

public record UserResponse(

        Long id,
        String username,
        String fullName,
        String role,
        boolean active,
        Instant createdAt,
        boolean protectedUser

) {

    public static UserResponse from(
            AppUser user,
            boolean protectedUser
    ) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt(),
                protectedUser
        );
    }
}
