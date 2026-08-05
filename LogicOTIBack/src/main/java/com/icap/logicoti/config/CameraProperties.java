package com.icap.logicoti.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "cameras")
public class CameraProperties {

    private boolean enabled;
    private String playbackBaseUrl = "";
    private List<String> availableStreams = new ArrayList<>();

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

    public List<String> getAvailableStreams() {
        return availableStreams;
    }

    public void setAvailableStreams(List<String> availableStreams) {
        this.availableStreams = availableStreams;
    }
}
