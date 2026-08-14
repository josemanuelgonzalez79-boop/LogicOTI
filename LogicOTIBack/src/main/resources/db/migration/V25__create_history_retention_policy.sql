CREATE TABLE history_retention_policy (
    id SMALLINT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    retention_months SMALLINT NOT NULL DEFAULT 24,
    last_run_at TIMESTAMP WITH TIME ZONE,
    last_run_by VARCHAR(50),
    last_cutoff_at TIMESTAMP WITH TIME ZONE,
    last_deleted_events BIGINT NOT NULL DEFAULT 0,
    last_deleted_commands BIGINT NOT NULL DEFAULT 0,
    last_deleted_security BIGINT NOT NULL DEFAULT 0,
    last_deleted_diagnostics BIGINT NOT NULL DEFAULT 0,
    last_deleted_bypasses BIGINT NOT NULL DEFAULT 0,
    last_deleted_notifications BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',

    CONSTRAINT chk_history_retention_singleton
        CHECK (id = 1),

    CONSTRAINT chk_history_retention_months
        CHECK (retention_months BETWEEN 6 AND 120),

    CONSTRAINT chk_history_retention_deleted_counts
        CHECK (
            last_deleted_events >= 0
            AND last_deleted_commands >= 0
            AND last_deleted_security >= 0
            AND last_deleted_diagnostics >= 0
            AND last_deleted_bypasses >= 0
            AND last_deleted_notifications >= 0
        )
);

INSERT INTO history_retention_policy (
    id,
    enabled,
    retention_months,
    updated_by
)
VALUES (
    1,
    FALSE,
    24,
    'SYSTEM'
);

CREATE INDEX idx_sensor_diagnostic_session_started_at
    ON sensor_diagnostic_session(started_at);
