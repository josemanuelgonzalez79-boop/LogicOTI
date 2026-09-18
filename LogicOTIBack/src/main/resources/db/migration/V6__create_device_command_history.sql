CREATE TABLE device_command_history (
    id BIGSERIAL PRIMARY KEY,

    device_id BIGINT NOT NULL,
    device_code VARCHAR(40) NOT NULL,
    area_code VARCHAR(20) NOT NULL,

    plc_command_tag VARCHAR(80),

    requested_value BOOLEAN NOT NULL,
    command_value BOOLEAN,
    feedback_value BOOLEAN,

    status VARCHAR(30) NOT NULL,
    message VARCHAR(500),

    requested_by VARCHAR(50) NOT NULL,
    requested_by_role VARCHAR(30) NOT NULL,
    source_ip VARCHAR(45),

    duration_ms BIGINT,

    requested_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    completed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_device_command_history_device
        FOREIGN KEY (device_id)
        REFERENCES building_device(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_device_command_history_status
        CHECK (
            status IN (
                'PENDING',
                'CONFIRMED',
                'NOT_CONFIRMED',
                'FAILED',
                'REJECTED'
            )
        ),

    CONSTRAINT chk_device_command_history_role
        CHECK (
            requested_by_role IN (
                'ADMIN',
                'OPERATOR'
            )
        ),

    CONSTRAINT chk_device_command_history_duration
        CHECK (
            duration_ms IS NULL
            OR duration_ms >= 0
        )
);

CREATE INDEX idx_command_history_device
    ON device_command_history(device_id);

CREATE INDEX idx_command_history_area
    ON device_command_history(area_code);

CREATE INDEX idx_command_history_user
    ON device_command_history(requested_by);

CREATE INDEX idx_command_history_date
    ON device_command_history(requested_at DESC);

CREATE INDEX idx_command_history_status
    ON device_command_history(status);