package com.icap.logicoti.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "cameras.history")
public class CameraHistoryProperties {

    private boolean enabled;
    private URI baseUrl;
    private String username = "";
    private String password = "";
    private String timeZone = "America/Mazatlan";
    private boolean localTimeAsUtc = true;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration responseTimeout = Duration.ofSeconds(15);
    private int maxSearchHours = 24;
    private int maxResults = 100;

    public boolean isConfigured() {
        return enabled
                && baseUrl != null
                && baseUrl.getHost() != null
                && ("http".equalsIgnoreCase(baseUrl.getScheme())
                || "https".equalsIgnoreCase(baseUrl.getScheme()))
                && baseUrl.getUserInfo() == null
                && username != null
                && !username.isBlank()
                && password != null
                && !password.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public boolean isLocalTimeAsUtc() {
        return localTimeAsUtc;
    }

    public void setLocalTimeAsUtc(boolean localTimeAsUtc) {
        this.localTimeAsUtc = localTimeAsUtc;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(Duration responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    public int getMaxSearchHours() {
        return maxSearchHours;
    }

    public void setMaxSearchHours(int maxSearchHours) {
        this.maxSearchHours = maxSearchHours;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }
}
