package com.icap.logicoti.notification;

import com.icap.logicoti.config.WebPushProperties;
import com.icap.logicoti.exception.ResourceNotFoundException;
import com.icap.logicoti.user.AppUser;
import com.icap.logicoti.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WebPushSubscriptionService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(WebPushSubscriptionService.class);

    private static final int TITLE_LIMIT = 180;
    private static final int BODY_LIMIT = 1000;
    private static final int TAG_LIMIT = 200;
    private static final int URL_LIMIT = 500;
    private static final int ERROR_LIMIT = 2000;
    private static final long MAX_RETRY_DELAY_SECONDS = 3600;

    private final WebPushProperties properties;
    private final WebPushSubscriptionRepository subscriptionRepository;
    private final WebPushDeliveryRepository deliveryRepository;
    private final WebPushClient webPushClient;
    private final AppUserRepository userRepository;
    private final JsonMapper jsonMapper;

    public WebPushSubscriptionService(
            WebPushProperties properties,
            WebPushSubscriptionRepository subscriptionRepository,
            WebPushDeliveryRepository deliveryRepository,
            WebPushClient webPushClient,
            AppUserRepository userRepository,
            JsonMapper jsonMapper
    ) {
        this.properties = properties;
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
        this.webPushClient = webPushClient;
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

    /**
     * Registra primero la entrega. El despachador programado hace el envío
     * unos segundos después y conserva cada intento aunque el proveedor falle.
     */
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

        String batchId = enqueue(
                subscriptions,
                title,
                body,
                tag,
                targetUrl,
                requireInteraction
        );

        LOGGER.info(
                "Se registró el lote Web Push {} para {} dispositivo(s).",
                batchId,
                subscriptions.size()
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

        String batchId = enqueue(
                subscriptions,
                "Prueba de notificación LogicOTI",
                "Los avisos de este dispositivo están funcionando correctamente.",
                "logicoti-push-test",
                "/security",
                false
        );

        int accepted = processDeliveries(
                deliveryRepository.findBatch(batchId)
        );
        int attempted = subscriptions.size();
        int failed = attempted - accepted;
        long retryPending = deliveryRepository.countBatchByStatus(
                batchId,
                "RETRY_PENDING"
        );

        String message;

        if (failed == 0) {
            message = "El servicio Push aceptó la notificación para "
                    + accepted + " dispositivo(s).";
        } else if (retryPending > 0) {
            message = "El servicio Push aceptó " + accepted + " de "
                    + attempted + " envío(s); " + retryPending
                    + " quedó en reintento automático.";
        } else {
            message = "El servicio Push aceptó " + accepted + " de "
                    + attempted + " envío(s). Los demás quedaron registrados como fallidos.";
        }

        return new WebPushTestResponse(
                attempted,
                accepted,
                failed,
                message,
                Instant.now()
        );
    }

    @Scheduled(
            initialDelayString = "${notifications.web-push.retry-poll-ms:5000}",
            fixedDelayString = "${notifications.web-push.retry-poll-ms:5000}"
    )
    @Transactional
    public void dispatchPendingDeliveries() {
        if (!properties.isConfigured()) {
            return;
        }

        Instant now = Instant.now();
        int abandoned = deliveryRepository
                .markUndeliverableSubscriptions(now);

        if (abandoned > 0) {
            LOGGER.info(
                    "Se cerraron {} entregas Web Push sin una suscripción activa.",
                    abandoned
            );
        }

        List<WebPushDeliveryTarget> deliveries =
                deliveryRepository.findDue(
                        now,
                        properties.getRetryBatchSize()
                );

        if (!deliveries.isEmpty()) {
            processDeliveries(deliveries);
        }
    }

    @Transactional(readOnly = true)
    public WebPushDeliveryPageResponse findDeliveries(
            int limit,
            int offset
    ) {
        return deliveryRepository.findRecent(limit, offset);
    }

    private String enqueue(
            List<WebPushSubscription> subscriptions,
            String title,
            String body,
            String tag,
            String targetUrl,
            boolean requireInteraction
    ) {
        return deliveryRepository.enqueue(
                subscriptions,
                limit(title, TITLE_LIMIT, "Notificación LogicOTI"),
                limit(body, BODY_LIMIT, "Revisa LogicOTI."),
                limit(tag, TAG_LIMIT, "logicoti-notification"),
                limit(targetUrl, URL_LIMIT, "/"),
                requireInteraction
        );
    }

    private int processDeliveries(
            List<WebPushDeliveryTarget> deliveries
    ) {
        int accepted = 0;

        for (WebPushDeliveryTarget delivery : deliveries) {
            if (processDelivery(delivery)) {
                accepted++;
            }

            if (Thread.currentThread().isInterrupted()) {
                break;
            }
        }

        return accepted;
    }

    private boolean processDelivery(
            WebPushDeliveryTarget delivery
    ) {
        Instant now = Instant.now();

        try {
            String payload = createPayload(
                    delivery.title(),
                    delivery.body(),
                    delivery.tag(),
                    delivery.targetUrl(),
                    delivery.requireInteraction()
            );

            int status = webPushClient.send(delivery, payload);

            if (status >= 200 && status < 300) {
                deliveryRepository.recordAccepted(
                        delivery,
                        status,
                        now
                );
                markSubscriptionSuccess(delivery.subscriptionId());
                LOGGER.info(
                        "Notificación Web Push {} aceptada con HTTP {}.",
                        delivery.deliveryId(),
                        status
                );
                return true;
            }

            if (status == 404 || status == 410) {
                deliveryRepository.recordPermanentFailure(
                        delivery,
                        status,
                        "La suscripción expiró o dejó de existir.",
                        true,
                        now
                );
                markSubscriptionFailure(
                        delivery.subscriptionId(),
                        true
                );
                return false;
            }

            if (isRetryable(status)) {
                scheduleRetry(
                        delivery,
                        status,
                        "El servicio Web Push respondió HTTP " + status + ".",
                        now
                );
                return false;
            }

            deliveryRepository.recordPermanentFailure(
                    delivery,
                    status,
                    "El servicio Web Push rechazó el envío con HTTP "
                            + status + ".",
                    false,
                    now
            );
            markSubscriptionFailure(
                    delivery.subscriptionId(),
                    false
            );
            return false;
        } catch (InterruptedException exception) {
            scheduleRetry(
                    delivery,
                    null,
                    "El envío fue interrumpido.",
                    now
            );
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception | LinkageError exception) {
            scheduleRetry(
                    delivery,
                    null,
                    exceptionMessage(exception),
                    now
            );
            LOGGER.warn(
                    "No se pudo enviar la notificación Web Push {}: {}",
                    delivery.deliveryId(),
                    exception.getMessage()
            );
            return false;
        }
    }

    private void scheduleRetry(
            WebPushDeliveryTarget delivery,
            Integer httpStatus,
            String error,
            Instant now
    ) {
        int attemptNumber = delivery.attemptCount() + 1;
        boolean exhausted = attemptNumber
                >= properties.getMaxAttempts();
        Instant nextAttemptAt = exhausted
                ? null
                : now.plusSeconds(retryDelaySeconds(attemptNumber));

        deliveryRepository.recordRetryableFailure(
                delivery,
                httpStatus,
                limit(error, ERROR_LIMIT, "Error de envío Web Push."),
                nextAttemptAt,
                exhausted,
                now
        );
        markSubscriptionFailure(
                delivery.subscriptionId(),
                false
        );
    }

    private long retryDelaySeconds(int attemptNumber) {
        int exponent = Math.min(
                Math.max(0, attemptNumber - 1),
                10
        );
        long multiplier = 1L << exponent;
        long baseDelay = properties
                .getInitialRetryDelaySeconds();

        if (baseDelay > MAX_RETRY_DELAY_SECONDS / multiplier) {
            return MAX_RETRY_DELAY_SECONDS;
        }

        return Math.min(
                baseDelay * multiplier,
                MAX_RETRY_DELAY_SECONDS
        );
    }

    private boolean isRetryable(int status) {
        return status == 408
                || status == 429
                || status >= 500;
    }

    private void markSubscriptionSuccess(Long subscriptionId) {
        subscriptionRepository.findById(subscriptionId)
                .ifPresent(subscription -> {
                    subscription.markSuccess();
                    subscriptionRepository.save(subscription);
                });
    }

    private void markSubscriptionFailure(
            Long subscriptionId,
            boolean deactivate
    ) {
        subscriptionRepository.findById(subscriptionId)
                .ifPresent(subscription -> {
                    subscription.markFailure(deactivate);
                    subscriptionRepository.save(subscription);
                });
    }

    private String createPayload(
            String title,
            String body,
            String tag,
            String targetUrl,
            boolean requireInteraction
    ) {
        Map<String, Object> defaultAction = Map.of(
                "operation", "navigateLastFocusedOrOpen",
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
        if (targetUrl == null
                || targetUrl.isBlank()
                || "/".equals(targetUrl)) {
            return "./";
        }

        return targetUrl.startsWith("/")
                ? targetUrl.substring(1)
                : targetUrl;
    }

    private String exceptionMessage(Throwable throwable) {
        String message = throwable.getMessage();

        if (message == null || message.isBlank()) {
            message = throwable.getClass().getSimpleName();
        }

        return limit(
                message,
                ERROR_LIMIT,
                "Error de envío Web Push."
        );
    }

    private String limit(
            String value,
            int maximumLength,
            String fallback
    ) {
        String normalized = value == null || value.isBlank()
                ? fallback
                : value.trim();

        if (normalized.length() <= maximumLength) {
            return normalized;
        }

        return normalized.substring(0, maximumLength);
    }

    private AppUser findUser(String username) {
        return userRepository
                .findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró el usuario autenticado."
                ));
    }
}
