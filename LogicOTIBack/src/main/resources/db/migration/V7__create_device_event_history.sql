CREATE TABLE device_event_history (
    id BIGSERIAL PRIMARY KEY,

    device_id BIGINT NOT NULL,
    device_code VARCHAR(40) NOT NULL,
    area_code VARCHAR(20) NOT NULL,
    device_type VARCHAR(30) NOT NULL,
    plc_state_tag VARCHAR(80) NOT NULL,

    previous_state BOOLEAN,
    current_state BOOLEAN NOT NULL,

    event_type VARCHAR(30) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    message VARCHAR(500) NOT NULL,

    detected_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_device_event_history_device
        FOREIGN KEY (device_id)
        REFERENCES building_device(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_device_event_history_type
        CHECK (
            device_type IN (
                'MOTION',
                'SMOKE',
                'DOOR'
            )
        ),

    CONSTRAINT chk_device_event_history_event
        CHECK (
            event_type IN (
                'ACTIVATED',
                'CLEARED'
            )
        ),

    CONSTRAINT chk_device_event_history_severity
        CHECK (
            severity IN (
                'INFO',
                'WARNING',
                'CRITICAL'
            )
        ),

    CONSTRAINT chk_device_event_history_state_change
        CHECK (
            previous_state IS NULL
            OR previous_state <> current_state
        )
);

CREATE INDEX idx_device_event_history_date
    ON device_event_history(detected_at DESC);

CREATE INDEX idx_device_event_history_device
    ON device_event_history(
        device_id,
        detected_at DESC
    );

CREATE INDEX idx_device_event_history_area
    ON device_event_history(
        area_code,
        detected_at DESC
    );

CREATE INDEX idx_device_event_history_type
    ON device_event_history(
        device_type,
        detected_at DESC
    );

CREATE INDEX idx_device_event_history_event
    ON device_event_history(
        event_type,
        detected_at DESC
    );

CREATE INDEX idx_device_event_history_severity
    ON device_event_history(
        severity,
        detected_at DESC
    );