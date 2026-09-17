ALTER TABLE device_event_history
    DROP CONSTRAINT chk_device_event_history_event;

ALTER TABLE device_event_history
    ADD CONSTRAINT chk_device_event_history_event
    CHECK (event_type IN ('ACTIVATED', 'CLEARED', 'TEST_ACTIVATED', 'TEST_CLEARED'));

CREATE INDEX idx_sensor_diagnostic_session_window
    ON sensor_diagnostic_session(expires_at, started_at);
