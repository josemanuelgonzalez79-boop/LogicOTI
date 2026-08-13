package com.icap.logicoti.notification;

import com.icap.logicoti.config.WebPushProperties;
import com.icap.logicoti.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebPushSubscriptionServiceTests {

    @Mock
    private WebPushSubscriptionRepository subscriptionRepository;

    @Mock
    private WebPushDeliveryRepository deliveryRepository;

    @Mock
    private WebPushClient webPushClient;

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private WebPushSubscription subscription;

    private WebPushProperties properties;
    private WebPushSubscriptionService service;

    @BeforeEach
    void setUp() {
        properties = new WebPushProperties();
        properties.setEnabled(true);
        properties.setPublicKey("public-key");
        properties.setPrivateKey("private-key");
        properties.setSubject("mailto:test@logicoti.local");
        properties.setMaxAttempts(4);
        properties.setInitialRetryDelaySeconds(30);
        properties.setRetryBatchSize(100);

        service = new WebPushSubscriptionService(
                properties,
                subscriptionRepository,
                deliveryRepository,
                webPushClient,
                userRepository,
                jsonMapper
        );
    }

    @Test
    void queuesAlarmNotificationsBeforeAnyNetworkAttempt() {
        when(subscriptionRepository.findActiveForEnabledUsers())
                .thenReturn(List.of(subscription));
        when(deliveryRepository.enqueue(
                any(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                eq(true)
        )).thenReturn("batch-1");

        service.sendToAll(
                "Alarma de humo",
                "Humo detectado.",
                "smoke-1",
                "/alarms",
                true
        );

        verify(deliveryRepository).enqueue(
                eq(List.of(subscription)),
                eq("Alarma de humo"),
                eq("Humo detectado."),
                eq("smoke-1"),
                eq("/alarms"),
                eq(true)
        );
        verifyNoInteractions(webPushClient);
    }

    @Test
    void recordsAcceptedProviderResponses() throws Exception {
        WebPushDeliveryTarget target = target(0);
        prepareDispatch(target);
        when(webPushClient.send(eq(target), anyString()))
                .thenReturn(201);

        service.dispatchPendingDeliveries();

        verify(deliveryRepository).recordAccepted(
                eq(target),
                eq(201),
                any(Instant.class)
        );
        verify(subscription).markSuccess();
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    void schedulesRetryForTransientProviderErrors() throws Exception {
        WebPushDeliveryTarget target = target(0);
        prepareDispatch(target);
        when(webPushClient.send(eq(target), anyString()))
                .thenReturn(503);

        service.dispatchPendingDeliveries();

        verify(deliveryRepository).recordRetryableFailure(
                eq(target),
                eq(503),
                contains("503"),
                any(Instant.class),
                eq(false),
                any(Instant.class)
        );
        verify(subscription).markFailure(false);
    }

    @Test
    void closesDeliveryWhenRetryLimitIsReached() throws Exception {
        WebPushDeliveryTarget target = target(3);
        prepareDispatch(target);
        when(webPushClient.send(eq(target), anyString()))
                .thenReturn(503);

        service.dispatchPendingDeliveries();

        verify(deliveryRepository).recordRetryableFailure(
                eq(target),
                eq(503),
                contains("503"),
                isNull(),
                eq(true),
                any(Instant.class)
        );
    }

    @Test
    void deactivatesExpiredSubscriptions() throws Exception {
        WebPushDeliveryTarget target = target(0);
        prepareDispatch(target);
        when(webPushClient.send(eq(target), anyString()))
                .thenReturn(410);

        service.dispatchPendingDeliveries();

        verify(deliveryRepository).recordPermanentFailure(
                eq(target),
                eq(410),
                anyString(),
                eq(true),
                any(Instant.class)
        );
        verify(subscription).markFailure(true);
    }

    private void prepareDispatch(
            WebPushDeliveryTarget target
    ) {
        when(deliveryRepository.findDue(
                any(Instant.class),
                anyInt()
        )).thenReturn(List.of(target));
        when(subscriptionRepository.findById(7L))
                .thenReturn(Optional.of(subscription));
        when(jsonMapper.writeValueAsString(any()))
                .thenReturn("{}");
    }

    private WebPushDeliveryTarget target(int attemptCount) {
        return new WebPushDeliveryTarget(
                20L,
                7L,
                3L,
                "https://push.example/subscription",
                "p256dh",
                "auth",
                "Alarma de humo",
                "Humo detectado.",
                "smoke-1",
                "/alarms",
                true,
                attemptCount
        );
    }
}
