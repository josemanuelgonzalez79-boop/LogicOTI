package com.icap.logicoti.realtime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/realtime")
public class RealtimeController {

    private final AreaSubscriptionRegistry subscriptionRegistry;

    public RealtimeController(
            AreaSubscriptionRegistry subscriptionRegistry
    ) {
        this.subscriptionRegistry = subscriptionRegistry;
    }

    @GetMapping("/status")
    public RealtimeStatusResponse status() {

        return new RealtimeStatusResponse(
                subscriptionRegistry.getSessionCount(),
                subscriptionRegistry.getSubscriptionCount(),
                subscriptionRegistry.getActiveAreaCodes(),
                Instant.now()
        );
    }
}