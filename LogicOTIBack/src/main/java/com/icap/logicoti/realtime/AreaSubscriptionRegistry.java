package com.icap.logicoti.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class AreaSubscriptionRegistry {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AreaSubscriptionRegistry.class);

    private static final String DESTINATION_PREFIX =
            "/topic/areas/";

    private static final String DESTINATION_SUFFIX =
            "/state";

    private final ConcurrentMap<
            String,
            ConcurrentMap<String, String>
            > subscriptionsBySession = new ConcurrentHashMap<>();

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {

        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(event.getMessage());

        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();
        String areaCode =
                extractAreaCode(accessor.getDestination());

        if (sessionId == null
                || subscriptionId == null
                || areaCode == null) {
            return;
        }

        subscriptionsBySession
                .computeIfAbsent(
                        sessionId,
                        ignored -> new ConcurrentHashMap<>()
                )
                .put(subscriptionId, areaCode);

        LOGGER.info(
                "Sesión {} suscrita al área {}",
                sessionId,
                areaCode
        );
    }

    @EventListener
    public void handleUnsubscribe(SessionUnsubscribeEvent event) {

        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(event.getMessage());

        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();

        if (sessionId == null || subscriptionId == null) {
            return;
        }

        ConcurrentMap<String, String> sessionSubscriptions =
                subscriptionsBySession.get(sessionId);

        if (sessionSubscriptions == null) {
            return;
        }

        String removedArea =
                sessionSubscriptions.remove(subscriptionId);

        if (sessionSubscriptions.isEmpty()) {
            subscriptionsBySession.remove(
                    sessionId,
                    sessionSubscriptions
            );
        }

        if (removedArea != null) {
            LOGGER.info(
                    "Sesión {} dejó de consultar el área {}",
                    sessionId,
                    removedArea
            );
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {

        String sessionId = event.getSessionId();

        Map<String, String> removedSubscriptions =
                subscriptionsBySession.remove(sessionId);

        if (removedSubscriptions != null) {
            LOGGER.info(
                    "Sesión WebSocket {} desconectada",
                    sessionId
            );
        }
    }

    public Set<String> getActiveAreaCodes() {

        Set<String> activeAreas = new TreeSet<>();

        subscriptionsBySession
                .values()
                .forEach(subscriptions ->
                        activeAreas.addAll(
                                subscriptions.values()
                        )
                );

        return activeAreas;
    }

    public int getSessionCount() {
        return subscriptionsBySession.size();
    }

    public int getSubscriptionCount() {

        return subscriptionsBySession
                .values()
                .stream()
                .mapToInt(Map::size)
                .sum();
    }

    private String extractAreaCode(String destination) {

        if (destination == null
                || !destination.startsWith(
                        DESTINATION_PREFIX
                )
                || !destination.endsWith(
                        DESTINATION_SUFFIX
                )) {
            return null;
        }

        int endIndex =
                destination.length()
                        - DESTINATION_SUFFIX.length();

        String areaCode = destination
                .substring(
                        DESTINATION_PREFIX.length(),
                        endIndex
                )
                .trim()
                .toUpperCase(Locale.ROOT);

        if (areaCode.isBlank()
                || !areaCode.matches("[A-Z0-9_]+")) {
            return null;
        }

        return areaCode;
    }
}