package com.icap.logicoti.notification;

import com.icap.logicoti.config.WebPushProperties;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.jose4j.lang.JoseException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutionException;

import java.security.Security;

@Component
public class WebPushClient {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(WebPushClient.class);

    private final PushService pushService;

    public WebPushClient(WebPushProperties properties) {
        ensureBouncyCastleProvider();

        this.pushService = properties.isConfigured()
                ? createPushService(properties)
                : null;
    }

    private PushService createPushService(
            WebPushProperties properties
    ) {
        try {
            return new PushService(
                    properties.getPublicKey(),
                    properties.getPrivateKey(),
                    properties.getSubject()
            );

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "No fue posible inicializar el servicio Web Push.",
                    exception
            );
        }
    }

        public int send(
                WebPushDeliveryTarget target,
                String payload
        ) throws GeneralSecurityException,
                IOException,
                JoseException,
                ExecutionException,
                InterruptedException {

        if (pushService == null) {
                throw new IllegalStateException(
                        "El servicio Web Push no está configurado."
                );
        }

        Notification notification = new Notification(
                target.endpoint(),
                target.p256dh(),
                target.auth(),
                payload
        );

        HttpResponse response =
                pushService.send(notification);

        return response
                .getStatusLine()
                .getStatusCode();
        }

    private static synchronized void ensureBouncyCastleProvider() {

        if (Security.getProvider(
                BouncyCastleProvider.PROVIDER_NAME
        ) != null) {
            return;
        }

        int position = Security.addProvider(
                new BouncyCastleProvider()
        );

        if (position < 0
                || Security.getProvider(
                        BouncyCastleProvider.PROVIDER_NAME
                ) == null) {

            throw new IllegalStateException(
                    "No fue posible registrar el proveedor criptográfico BC."
            );
        }

        LOGGER.info(
                "Proveedor criptográfico Bouncy Castle registrado en la posición {}.",
                position
        );
    }
}