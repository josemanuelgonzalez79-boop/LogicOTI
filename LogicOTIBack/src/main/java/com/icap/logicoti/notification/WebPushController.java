package com.icap.logicoti.notification;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
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

    @PostMapping("/test")
    public WebPushTestResponse test(Authentication authentication) {
        return service.sendTestToUser(authentication.getName());
    }

    @GetMapping("/deliveries")
    public WebPushDeliveryPageResponse deliveries(
            @RequestParam(defaultValue = "100")
            @Min(1)
            @Max(500)
            int limit,

            @RequestParam(defaultValue = "0")
            @Min(0)
            @Max(1000000)
            int offset
    ) {
        return service.findDeliveries(limit, offset);
    }

    @DeleteMapping("/subscriptions")
    public WebPushSubscriptionResponse unsubscribe(
            Authentication authentication,
            @Valid @RequestBody WebPushUnsubscribeRequest request
    ) {
        return service.unsubscribe(authentication.getName(), request);
    }
}
