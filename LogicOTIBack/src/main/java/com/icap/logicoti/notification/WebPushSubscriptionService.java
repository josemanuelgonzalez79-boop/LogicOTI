package com.icap.logicoti.notification;

import com.icap.logicoti.config.WebPushProperties;
import com.icap.logicoti.exception.ResourceNotFoundException;
import com.icap.logicoti.user.AppUser;
import com.icap.logicoti.user.AppUserRepository;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.security.Security;
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
            LOGGER.warn(
                    "Se omitió una notificación Web Push porque el servidor no está configurado."
            );
            return;
        }

        List<WebPushSubscription> subscriptions =
                subscriptionRepository.findActiveForEnabledUsers();

        if (subscriptions.isEmpty()) {
            LOGGER.info(
                    "Se omitió una notificación Web Push porque no hay suscripciones activas."
            );
            return;
        }

        sendToSubscriptions(
                subscriptions,
                title,
                body,
                tag,
                targetUrl,
                requireInteraction
        );
    }

    @Transactional
    public WebPushTestResponse sendTestToUser(String username) {
        AppUser user = findUser(username);

        if (!properties.isConfigured()) {
            return new WebPushTestResponse(
                    0,
                    0,
                    0,
                    "El servidor todavía no tiene una configuración Web Push válida.",
                    Instant.now()
            );
        }

        List<WebPushSubscription> subscriptions = subscriptionRepository
                .findByUserIdAndActiveTrue(user.getId());

        if (subscriptions.isEmpty()) {
            return new WebPushTestResponse(
                    0,
                    0,
                    0,
                    "Esta cuenta no tiene dispositivos activos para recibir avisos.",
                    Instant.now()
            );
        }

        int accepted = sendToSubscriptions(
                subscriptions,
                "Prueba de notificación LogicOTI",
                "Los avisos de este dispositivo están funcionando correctamente.",
                "logicoti-push-test",
                "/security",
                false
        );
        int attempted = subscriptions.size();
        int failed = attempted - accepted;
        String message = failed == 0
                ? "El servicio Push aceptó la notificación para "
                        + accepted + " dispositivo(s)."
                : "El servicio Push aceptó " + accepted + " de "
                        + attempted + " envío(s); revisa el log del backend para el error.";

        return new WebPushTestResponse(
                attempted,
                accepted,
                failed,
                message,
                Instant.now()
        );
    }

    private int sendToSubscriptions(
            List<WebPushSubscription> subscriptions,
            String title,
            String body,
            String tag,
            String targetUrl,
            boolean requireInteraction
    ) {

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
            return 0;
        }

        PushService pushService;

        try {
            ensureBouncyCastleProvider();
            pushService = new PushService(
                    properties.getPublicKey(),
                    properties.getPrivateKey(),
                    properties.getSubject()
            );
        } catch (Exception | LinkageError exception) {
            LOGGER.error(
                    "No se pudo inicializar Web Push. Verifica las llaves VAPID y las dependencias criptográficas: {}",
                    exception.getMessage()
            );
            return 0;
        }

        int accepted = 0;

        for (WebPushSubscription subscription : subscriptions) {
            if (sendOne(pushService, subscription, payload)) {
                accepted++;
            }
        }

        return accepted;
    }

    private static synchronized void ensureBouncyCastleProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) != null) {
            return;
        }

        int position = Security.addProvider(new BouncyCastleProvider());

        if (position < 0 || Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            throw new IllegalStateException("No fue posible registrar el proveedor criptográfico BC.");
        }

        LOGGER.info("Proveedor criptográfico Bouncy Castle registrado en la posición {}.", position);
    }

    private boolean sendOne(
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
                LOGGER.info(
                        "Notificación Web Push aceptada con HTTP {} para la suscripción {}.",
                        status,
                        subscription.getId()
                );
                return true;
            }

            boolean expired = status == 404 || status == 410;
            subscription.markFailure(expired);

            LOGGER.warn(
                    "El servicio Web Push respondió {} para la suscripción {}.",
                    status,
                    subscription.getId()
            );
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            subscription.markFailure(false);
            LOGGER.warn(
                    "Se interrumpió el envío Web Push a la suscripción {}.",
                    subscription.getId()
            );
            return false;
        } catch (Exception exception) {
            subscription.markFailure(false);
            LOGGER.warn(
                    "No se pudo enviar una notificación Web Push a {}: {}",
                    subscription.getId(),
                    exception.getMessage()
            );
            return false;
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
                "url", normalizeTargetUrl(targetUrl)
        );

        Map<String, Object> data = Map.of(
                "onActionClick", Map.of(
                        "default", defaultAction
                )
        );

        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("title", title);
        notification.put("body", body);
        notification.put("icon", "icons/icon-192.png");
        notification.put("badge", "icons/badge-96.png");
        notification.put("tag", tag);
        notification.put("renotify", true);
        notification.put("requireInteraction", requireInteraction);
        notification.put("data", data);

        return jsonMapper.writeValueAsString(
                Map.of("notification", notification)
        );
    }

    private String normalizeTargetUrl(String targetUrl) {
        if (targetUrl == null || targetUrl.isBlank() || "/".equals(targetUrl)) {
            return "./";
        }

        return targetUrl.startsWith("/")
                ? targetUrl.substring(1)
                : targetUrl;
    }

    private AppUser findUser(String username) {
        return userRepository
                .findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró el usuario autenticado."
                ));
    }
}
