package com.icap.logicoti.config;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

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

    @Test
    void playbackRequiresALoopbackMediaMtxApi() {
        CameraHistoryProperties properties = configuredProperties();
        properties.setPlaybackEnabled(true);
        properties.setMediaMtxTimeout(Duration.ofSeconds(5));
        properties.setPlaybackSessionTtl(Duration.ofHours(2));

        properties.setMediaMtxControlUrl(
                URI.create("http://127.0.0.1:9997")
        );
        assertThat(properties.isPlaybackConfigured()).isTrue();

        properties.setMediaMtxControlUrl(
                URI.create("http://192.0.2.20:9997")
        );
        assertThat(properties.isPlaybackConfigured()).isFalse();
    }

    private CameraHistoryProperties configuredProperties() {
        CameraHistoryProperties properties = new CameraHistoryProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(URI.create("http://192.0.2.18"));
        properties.setUsername("history-user");
        properties.setPassword("test-password");
        return properties;
    }
}
