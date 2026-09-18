package com.icap.logicoti.auth;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<LoginResponse> verifyTwoFactor(
            @Valid @RequestBody TwoFactorVerifyRequest request
    ) {
        return ResponseEntity.ok(authService.verifyTwoFactor(request));
    }

    @PostMapping("/2fa/setup")
    public ResponseEntity<TwoFactorSetupResponse> setupTwoFactor(
            @Valid @RequestBody TwoFactorSetupRequest request,
            Principal principal
    ) {
        return ResponseEntity.ok(
                authService.setupTwoFactor(principal.getName(), request)
        );
    }

    @GetMapping("/2fa/status")
    public ResponseEntity<TwoFactorStatusResponse> twoFactorStatus(
            Principal principal
    ) {
        return ResponseEntity.ok(
                authService.getTwoFactorStatus(principal.getName())
        );
    }

    @PostMapping("/2fa/confirm")
    public ResponseEntity<TwoFactorConfirmationResponse> confirmTwoFactor(
            @Valid @RequestBody TwoFactorCodeRequest request,
            Principal principal
    ) {
        return ResponseEntity.ok(
                authService.confirmTwoFactor(principal.getName(), request)
        );
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<TwoFactorStatusResponse> disableTwoFactor(
            @Valid @RequestBody TwoFactorDisableRequest request,
            Principal principal
    ) {
        return ResponseEntity.ok(
                authService.disableTwoFactor(principal.getName(), request)
        );
    }
}
