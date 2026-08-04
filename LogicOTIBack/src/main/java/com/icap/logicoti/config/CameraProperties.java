package com.icap.logicoti.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cameras")
public class CameraProperties {

    private boolean enabled;
    private String playbackBaseUrl = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPlaybackBaseUrl() {
        return playbackBaseUrl;
    }

    public void setPlaybackBaseUrl(String playbackBaseUrl) {
        this.playbackBaseUrl = playbackBaseUrl;
    }
}
