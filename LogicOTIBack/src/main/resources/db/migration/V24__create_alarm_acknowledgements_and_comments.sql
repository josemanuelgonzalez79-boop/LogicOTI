CREATE TABLE alarm_acknowledgement (
    id BIGSERIAL PRIMARY KEY,

    event_id BIGINT NOT NULL UNIQUE,
    acknowledged_by VARCHAR(50) NOT NULL,
    acknowledged_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_alarm_acknowledgement_event
        FOREIGN KEY (event_id)
        REFERENCES device_event_history(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_alarm_acknowledgement_user
        CHECK (TRIM(acknowledged_by) <> '')
);

CREATE INDEX idx_alarm_acknowledgement_date
    ON alarm_acknowledgement(acknowledged_at DESC);

CREATE TABLE alarm_comment (
    id BIGSERIAL PRIMARY KEY,

    event_id BIGINT NOT NULL,
    comment_text VARCHAR(500) NOT NULL,
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_alarm_comment_event
        FOREIGN KEY (event_id)
        REFERENCES device_event_history(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_alarm_comment_text
        CHECK (TRIM(comment_text) <> ''),

    CONSTRAINT chk_alarm_comment_user
        CHECK (TRIM(created_by) <> '')
);

CREATE INDEX idx_alarm_comment_event_date
    ON alarm_comment(event_id, created_at ASC, id ASC);
