package com.icap.logicoti.notification;

import com.icap.logicoti.config.WebPushProperties;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.Security;

@Component
public class WebPushClient {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(WebPushClient.class);

    private final WebPushProperties properties;
    private volatile PushService pushService;

    public WebPushClient(WebPushProperties properties) {
        this.properties = properties;
    }

    public int send(
            WebPushDeliveryTarget target,
            String payload
    ) throws Exception {
        Notification notification = new Notification(
                target.endpoint(),
                target.p256dh(),
                target.auth(),
                payload
        );

        HttpResponse response = getPushService().send(notification);
        return response.getStatusLine().getStatusCode();
    }

    private PushService getPushService() throws Exception {
        PushService current = pushService;

        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (pushService == null) {
                ensureBouncyCastleProvider();
                pushService = new PushService(
                        properties.getPublicKey(),
                        properties.getPrivateKey(),
                        properties.getSubject()
                );
            }

            return pushService;
        }
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
