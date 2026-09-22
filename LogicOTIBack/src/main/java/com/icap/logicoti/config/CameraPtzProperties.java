package com.icap.logicoti.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "cameras.ptz")
public class CameraPtzProperties {

    private boolean enabled;
    private URI baseUrl;
    private String username = "";
    private String password = "";
    private String endpointMode = "direct";
    private List<Integer> channels = List.of(25, 26, 27, 28);
    private int speed = 40;
    private Duration pulseDuration = Duration.ofMillis(350);
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration responseTimeout = Duration.ofSeconds(5);

    public boolean isConfigured() {
        return enabled
                && baseUrl != null
                && baseUrl.getHost() != null
                && ("http".equalsIgnoreCase(baseUrl.getScheme())
                || "https".equalsIgnoreCase(baseUrl.getScheme()))
                && baseUrl.getUserInfo() == null
                && (baseUrl.getPath() == null || baseUrl.getPath().isBlank()
                || "/".equals(baseUrl.getPath()))
                && baseUrl.getQuery() == null
                && baseUrl.getFragment() == null
                && username != null && !username.isBlank()
                && password != null && !password.isBlank()
                && ("direct".equalsIgnoreCase(endpointMode)
                || "proxy".equalsIgnoreCase(endpointMode))
                && speed >= 1 && speed <= 100
                && pulseDuration != null
                && !pulseDuration.isNegative()
                && pulseDuration.toMillis() >= 100
                && pulseDuration.toMillis() <= 500
                && connectTimeout != null && connectTimeout.toMillis() > 0
                && responseTimeout != null && responseTimeout.toMillis() > 0;
    }

    public boolean isChannelAllowed(int channel) {
        return channels != null && channels.contains(channel);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public URI getBaseUrl() { return baseUrl; }
    public void setBaseUrl(URI baseUrl) { this.baseUrl = baseUrl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getEndpointMode() { return endpointMode; }
    public void setEndpointMode(String endpointMode) { this.endpointMode = endpointMode; }
    public List<Integer> getChannels() { return channels; }
    public void setChannels(List<Integer> channels) { this.channels = channels; }
    public int getSpeed() { return speed; }
    public void setSpeed(int speed) { this.speed = speed; }
    public Duration getPulseDuration() { return pulseDuration; }
    public void setPulseDuration(Duration pulseDuration) { this.pulseDuration = pulseDuration; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getResponseTimeout() { return responseTimeout; }
    public void setResponseTimeout(Duration responseTimeout) { this.responseTimeout = responseTimeout; }
}
