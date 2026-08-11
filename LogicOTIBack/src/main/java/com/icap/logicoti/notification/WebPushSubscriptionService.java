package com.icap.logicoti.notification;

import com.icap.logicoti.config.WebPushProperties;
import com.icap.logicoti.exception.ResourceNotFoundException;
import com.icap.logicoti.user.AppUser;
import com.icap.logicoti.user.AppUserRepository;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WebPushSubscriptionService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(WebPushSubscriptionService.class);

    private final WebPushProperties properties;
    private final WebPushSubscriptionRepository subscriptionRepository;
    private final AppUserRepository userRepository;
    private final JsonMapper jsonMapper;

    public WebPushSubscriptionService(
            WebPushProperties properties,
            WebPushSubscriptionRepository subscriptionRepository,
            AppUserRepository userRepository,
            JsonMapper jsonMapper
    ) {
        this.properties = properties;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(readOnly = true)
    public WebPushConfigResponse getConfig(String username) {
        AppUser user = findUser(username);
        long count = subscriptionRepository
                .countByUserIdAndActiveTrue(user.getId());
        boolean configured = properties.isConfigured();

        return new WebPushConfigResponse(
                true,
                configured,
                configured ? properties.getPublicKey() : "",
                count > 0,
                count,
                configured
                        ? "Las notificaciones móviles están disponibles."
                        : "Falta configurar las llaves Web Push en el servidor."
        );
    }

    @Transactional
    public WebPushSubscriptionResponse subscribe(
            String username,
            WebPushSubscriptionRequest request,
            String requestUserAgent
    ) {
        AppUser user = findUser(username);
        String userAgent = request.userAgent() == null
                || request.userAgent().isBlank()
                ? requestUserAgent
                : request.userAgent();

        WebPushSubscription subscription = subscriptionRepository
                .findByEndpoint(request.endpoint())
                .orElseGet(() -> new WebPushSubscription(
                        user.getId(),
                        request.endpoint(),
                        request.keys().p256dh(),
                        request.keys().auth(),
                        userAgent
                ));

        if (subscription.getId() != null) {
            subscription.refresh(
                    user.getId(),
                    request.keys().p256dh(),
                    request.keys().auth(),
                    userAgent
            );
        }

        subscriptionRepository.save(subscription);

        return new WebPushSubscriptionResponse(
                true,
                "Este dispositivo recibirá avisos de humo y movimiento con la alarma armada.",
                Instant.now()
        );
    }

    @Transactional
    public WebPushSubscriptionResponse unsubscribe(
            String username,
            WebPushUnsubscribeRequest request
    ) {
        AppUser user = findUser(username);

        subscriptionRepository
                .findByEndpointAndUserId(request.endpoint(), user.getId())
                .ifPresent(subscriptionRepository::delete);

        return new WebPushSubscriptionResponse(
                false,
                "Este dispositivo dejó de recibir notificaciones.",
                Instant.now()
        );
    }

    @Async
    @Transactional
    public void sendToAll(
            String title,
            String body,
            String tag,
            String targetUrl,
            boolean requireInteraction
    ) {
        if (!properties.isConfigured()) {
            return;
        }

        List<WebPushSubscription> subscriptions =
                subscriptionRepository.findActiveForEnabledUsers();

        if (subscriptions.isEmpty()) {
            return;
        }

        String payload;

        try {
            payload = createPayload(
                    title,
                    body,
                    tag,
                    targetUrl,
                    requireInteraction
            );
        } catch (JacksonException exception) {
            LOGGER.error(
                    "No se pudo generar el contenido de la notificación: {}",
                    exception.getMessage()
            );
            return;
        }

        PushService pushService;

        try {
            pushService = new PushService(
                    properties.getPublicKey(),
                    properties.getPrivateKey(),
                    properties.getSubject()
            );
        } catch (Exception exception) {
            LOGGER.error(
                    "Las llaves Web Push no son válidas: {}",
                    exception.getMessage()
            );
            return;
        }

        for (WebPushSubscription subscription : subscriptions) {
            sendOne(pushService, subscription, payload);
        }
    }

    private void sendOne(
            PushService pushService,
            WebPushSubscription subscription,
            String payload
    ) {
        try {
            Notification notification = new Notification(
                    subscription.getEndpoint(),
                    subscription.getP256dh(),
                    subscription.getAuth(),
                    payload
            );

            HttpResponse response = pushService.send(notification);
            int status = response.getStatusLine().getStatusCode();

            if (status >= 200 && status < 300) {
                subscription.markSuccess();
                return;
            }

            boolean expired = status == 404 || status == 410;
            subscription.markFailure(expired);

            LOGGER.warn(
                    "El servicio Web Push respondió {} para la suscripción {}.",
                    status,
                    subscription.getId()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            subscription.markFailure(false);
        } catch (Exception exception) {
            subscription.markFailure(false);
            LOGGER.warn(
                    "No se pudo enviar una notificación Web Push a {}: {}",
                    subscription.getId(),
                    exception.getMessage()
            );
        }
    }

    private String createPayload(
            String title,
            String body,
            String tag,
            String targetUrl,
            boolean requireInteraction
    ) {
        Map<String, Object> defaultAction = Map.of(
                "operation", "openWindow",
                "url", targetUrl
        );

        Map<String, Object> data = Map.of(
                "onActionClick", Map.of(
                        "default", defaultAction
                )
        );

        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("title", title);
        notification.put("body", body);
        notification.put("icon", "/icons/icon-192.png");
        notification.put("badge", "/icons/badge-96.png");
        notification.put("tag", tag);
        notification.put("renotify", true);
        notification.put("requireInteraction", requireInteraction);
        notification.put("data", data);

        return jsonMapper.writeValueAsString(
                Map.of("notification", notification)
        );
    }

    private AppUser findUser(String username) {
        return userRepository
                .findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró el usuario autenticado."
                ));
    }
}
