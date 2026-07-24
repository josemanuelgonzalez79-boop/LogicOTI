package com.icap.logicoti.auth;

import com.icap.logicoti.user.AppUser;

public record LoginResponse(
        String token,
        long expiresIn,
        User user
) {

    public static LoginResponse from(
            String token,
            long expiresIn,
            AppUser user
    ) {
        return new LoginResponse(
                token,
                expiresIn,
                new User(
                        user.getId(),
                        user.getUsername(),
                        user.getFullName(),
                        user.getRole()
                )
        );
    }

    public record User(
            Long id,
            String username,
            String fullName,
            String role
    ) {
    }
}