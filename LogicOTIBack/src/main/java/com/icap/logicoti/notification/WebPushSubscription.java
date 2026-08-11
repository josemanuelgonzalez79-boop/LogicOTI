package com.icap.logicoti.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "web_push_subscription")
public class WebPushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    private String endpoint;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String p256dh;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String auth;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;

    protected WebPushSubscription() {
    }

    public WebPushSubscription(
            Long userId,
            String endpoint,
            String p256dh,
            String auth,
            String userAgent
    ) {
        Instant now = Instant.now();
        this.userId = userId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.userAgent = userAgent;
        this.active = true;
        this.failureCount = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public String getAuth() {
        return auth;
    }

    public boolean isActive() {
        return active;
    }

    public void refresh(
            Long userId,
            String p256dh,
            String auth,
            String userAgent
    ) {
        this.userId = userId;
        this.p256dh = p256dh;
        this.auth = auth;
        this.userAgent = userAgent;
        this.active = true;
        this.failureCount = 0;
        this.updatedAt = Instant.now();
    }

    public void markSuccess() {
        Instant now = Instant.now();
        this.active = true;
        this.failureCount = 0;
        this.lastSuccessAt = now;
        this.updatedAt = now;
    }

    public void markFailure(boolean deactivate) {
        Instant now = Instant.now();
        this.failureCount++;
        this.active = !deactivate;
        this.lastFailureAt = now;
        this.updatedAt = now;
    }
}
