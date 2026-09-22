package com.icap.logicoti.config;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class CameraHistoryPropertiesTests {

    @Test
    void isConfiguredOnlyWithAnHttpEndpointAndCredentials() {
        CameraHistoryProperties properties = new CameraHistoryProperties();
        properties.setEnabled(true);
        properties.setUsername("history-user");
        properties.setPassword("test-password");

        properties.setBaseUrl(URI.create("http://192.0.2.18"));
        assertThat(properties.isConfigured()).isTrue();

        properties.setBaseUrl(URI.create("/relative/path"));
        assertThat(properties.isConfigured()).isFalse();

        properties.setBaseUrl(URI.create("ftp://192.0.2.18"));
        assertThat(properties.isConfigured()).isFalse();
    }

    @Test
    void isNotConfiguredWhenCredentialsAreIncomplete() {
        CameraHistoryProperties properties = new CameraHistoryProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(URI.create("http://192.0.2.18"));
        properties.setUsername("history-user");

        assertThat(properties.isConfigured()).isFalse();
    }
}
