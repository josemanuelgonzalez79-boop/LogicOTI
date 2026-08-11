package com.icap.logicoti.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notifications.web-push")
public class WebPushProperties {

    private boolean enabled;
    private String publicKey = "";
    private String privateKey = "";
    private String subject = "mailto:soporte@icap.com.mx";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public boolean isConfigured() {
        return enabled
                && publicKey != null
                && !publicKey.isBlank()
                && privateKey != null
                && !privateKey.isBlank()
                && subject != null
                && !subject.isBlank();
    }
}
