CREATE TABLE web_push_delivery (
    id BIGSERIAL PRIMARY KEY,
    batch_id VARCHAR(36) NOT NULL,
    subscription_id BIGINT,
    user_id BIGINT,
    title VARCHAR(180) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    notification_tag VARCHAR(200) NOT NULL,
    target_url VARCHAR(500) NOT NULL,
    require_interaction BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_http_status INTEGER,
    last_error VARCHAR(2000),
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    accepted_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_web_push_delivery_subscription
        FOREIGN KEY (subscription_id)
        REFERENCES web_push_subscription(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_web_push_delivery_user
        FOREIGN KEY (user_id)
        REFERENCES app_user(id)
        ON DELETE SET NULL,

    CONSTRAINT chk_web_push_delivery_status
        CHECK (
            status IN (
                'QUEUED',
                'RETRY_PENDING',
                'ACCEPTED',
                'FAILED',
                'EXPIRED_SUBSCRIPTION'
            )
        ),

    CONSTRAINT chk_web_push_delivery_attempt_count
        CHECK (attempt_count >= 0)
);

CREATE INDEX idx_web_push_delivery_batch
    ON web_push_delivery(batch_id);

CREATE INDEX idx_web_push_delivery_due
    ON web_push_delivery(status, next_attempt_at);

CREATE INDEX idx_web_push_delivery_created
    ON web_push_delivery(created_at DESC);

CREATE INDEX idx_web_push_delivery_subscription
    ON web_push_delivery(subscription_id);

CREATE TABLE web_push_delivery_attempt (
    id BIGSERIAL PRIMARY KEY,
    delivery_id BIGINT NOT NULL,
    attempt_number INTEGER NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    http_status INTEGER,
    error_message VARCHAR(2000),
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_web_push_delivery_attempt_delivery
        FOREIGN KEY (delivery_id)
        REFERENCES web_push_delivery(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_web_push_delivery_attempt
        UNIQUE (delivery_id, attempt_number),

    CONSTRAINT chk_web_push_delivery_attempt_number
        CHECK (attempt_number > 0),

    CONSTRAINT chk_web_push_delivery_attempt_outcome
        CHECK (
            outcome IN (
                'ACCEPTED',
                'RETRY_SCHEDULED',
                'FAILED',
                'EXPIRED_SUBSCRIPTION'
            )
        )
);

CREATE INDEX idx_web_push_delivery_attempt_delivery
    ON web_push_delivery_attempt(delivery_id, attempted_at);
