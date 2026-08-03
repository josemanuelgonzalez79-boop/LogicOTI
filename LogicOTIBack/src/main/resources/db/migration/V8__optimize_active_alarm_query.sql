CREATE INDEX idx_device_event_history_latest_sensor
    ON device_event_history(
        device_type,
        device_id,
        detected_at DESC,
        id DESC
    );
