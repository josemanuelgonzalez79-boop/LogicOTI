package com.icap.logicoti.auth;

import com.icap.logicoti.user.AppUser;

public record LoginResponse(
        String token,
        long expiresIn,
        User user,
        boolean requiresTwoFactor,
        String challengeToken,
        long challengeExpiresIn
) {

    public static LoginResponse authenticated(
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
                ),
                false,
                null,
                0
        );
    }

    public static LoginResponse challenge(
            TwoFactorService.Challenge challenge
    ) {
        return new LoginResponse(
                null,
                0,
                null,
                true,
                challenge.token(),
                challenge.expiresIn()
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
