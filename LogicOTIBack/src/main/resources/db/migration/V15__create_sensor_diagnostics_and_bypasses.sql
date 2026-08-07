CREATE TABLE sensor_diagnostic_session (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    started_by VARCHAR(50) NOT NULL,
    started_by_role VARCHAR(30) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    message VARCHAR(300) NOT NULL,

    CONSTRAINT chk_sensor_diagnostic_session_status
        CHECK (status IN ('RUNNING', 'PASSED', 'REJECTED', 'CANCELLED')),

    CONSTRAINT chk_sensor_diagnostic_session_role
        CHECK (started_by_role IN ('ADMIN', 'OPERATOR')),

    CONSTRAINT chk_sensor_diagnostic_session_dates
        CHECK (expires_at > started_at)
);

CREATE TABLE sensor_diagnostic_item (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    device_code VARCHAR(80) NOT NULL,
    device_name VARCHAR(150) NOT NULL,
    area_code VARCHAR(40) NOT NULL,
    area_name VARCHAR(150) NOT NULL,
    device_type VARCHAR(30) NOT NULL,
    plc_state_tag VARCHAR(120) NOT NULL,
    initial_state BOOLEAN NOT NULL,
    saw_inactive BOOLEAN NOT NULL DEFAULT FALSE,
    saw_active BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    passed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_sensor_diagnostic_item_session
        FOREIGN KEY (session_id)
        REFERENCES sensor_diagnostic_session(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_sensor_diagnostic_item_device
        FOREIGN KEY (device_id)
        REFERENCES building_device(id),

    CONSTRAINT uq_sensor_diagnostic_item
        UNIQUE (session_id, device_id),

    CONSTRAINT chk_sensor_diagnostic_item_type
        CHECK (device_type IN ('MOTION', 'SMOKE')),

    CONSTRAINT chk_sensor_diagnostic_item_status
        CHECK (status IN ('RUNNING', 'PASSED', 'REJECTED', 'CANCELLED'))
);

CREATE UNIQUE INDEX uq_sensor_diagnostic_running_device
    ON sensor_diagnostic_item(device_id)
    WHERE status = 'RUNNING';

CREATE INDEX idx_sensor_diagnostic_session_status_expires
    ON sensor_diagnostic_session(status, expires_at);

CREATE INDEX idx_sensor_diagnostic_item_device_status
    ON sensor_diagnostic_item(device_id, status);

CREATE TABLE sensor_bypass_history (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL,
    device_code VARCHAR(80) NOT NULL,
    device_name VARCHAR(150) NOT NULL,
    area_code VARCHAR(40) NOT NULL,
    area_name VARCHAR(150) NOT NULL,
    reason VARCHAR(300) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_by VARCHAR(50),
    revoked_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_sensor_bypass_device
        FOREIGN KEY (device_id)
        REFERENCES building_device(id),

    CONSTRAINT chk_sensor_bypass_dates
        CHECK (
            (active = TRUE AND revoked_at IS NULL AND revoked_by IS NULL)
            OR
            (active = FALSE AND revoked_at IS NOT NULL AND revoked_by IS NOT NULL)
        )
);

CREATE UNIQUE INDEX uq_sensor_bypass_active_device
    ON sensor_bypass_history(device_id)
    WHERE active = TRUE;

CREATE INDEX idx_sensor_bypass_active_created
    ON sensor_bypass_history(active, created_at DESC);

CREATE TABLE sensor_bypass_warning (
    id BIGSERIAL PRIMARY KEY,
    bypass_id BIGINT NOT NULL,
    warning_date DATE NOT NULL,
    message VARCHAR(300) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_sensor_bypass_warning
        FOREIGN KEY (bypass_id)
        REFERENCES sensor_bypass_history(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_sensor_bypass_warning_day
        UNIQUE (bypass_id, warning_date)
);

CREATE INDEX idx_sensor_bypass_warning_created
    ON sensor_bypass_warning(created_at DESC);
