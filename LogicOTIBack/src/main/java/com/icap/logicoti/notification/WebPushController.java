package com.icap.logicoti.notification;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications/push")
public class WebPushController {

    private final WebPushSubscriptionService service;

    public WebPushController(WebPushSubscriptionService service) {
        this.service = service;
    }

    @GetMapping("/config")
    public WebPushConfigResponse config(Authentication authentication) {
        return service.getConfig(authentication.getName());
    }

    @PostMapping("/subscriptions")
    public WebPushSubscriptionResponse subscribe(
            Authentication authentication,
            HttpServletRequest httpRequest,
            @Valid @RequestBody WebPushSubscriptionRequest request
    ) {
        return service.subscribe(
                authentication.getName(),
                request,
                httpRequest.getHeader("User-Agent")
        );
    }

    @DeleteMapping("/subscriptions")
    public WebPushSubscriptionResponse unsubscribe(
            Authentication authentication,
            @Valid @RequestBody WebPushUnsubscribeRequest request
    ) {
        return service.unsubscribe(authentication.getName(), request);
    }
}
