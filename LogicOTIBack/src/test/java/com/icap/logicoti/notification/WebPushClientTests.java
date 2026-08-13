package com.icap.logicoti.notification;

import com.icap.logicoti.config.WebPushProperties;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

import java.security.Security;

import static org.assertj.core.api.Assertions.assertThat;

class WebPushClientTests {

    @Test
    void registersBouncyCastleBeforeTheFirstNotificationIsCreated() {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);

        new WebPushClient(new WebPushProperties());

        assertThat(Security.getProvider(
                BouncyCastleProvider.PROVIDER_NAME
        )).isInstanceOf(BouncyCastleProvider.class);
    }
}
