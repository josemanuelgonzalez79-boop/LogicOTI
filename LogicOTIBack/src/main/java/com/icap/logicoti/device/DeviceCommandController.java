package com.icap.logicoti.device;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
public class DeviceCommandController {

    private final DeviceCommandExecutionService executionService;

    public DeviceCommandController(
            DeviceCommandExecutionService executionService
    ) {
        this.executionService = executionService;
    }

    @PutMapping("/{deviceCode}/command")
    public AreaStateResponse command(
            @PathVariable String deviceCode,
            @Valid @RequestBody DeviceCommandRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        return executionService.execute(
                deviceCode,
                request.on(),
                authentication.getName(),
                extractRole(authentication),
                httpRequest.getRemoteAddr()
        );
    }

    private String extractRole(
            Authentication authentication
    ) {
        return authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority ->
                        authority.startsWith("ROLE_")
                )
                .map(authority ->
                        authority.substring(5)
                )
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "El usuario autenticado no tiene un rol."
                        )
                );
    }
}