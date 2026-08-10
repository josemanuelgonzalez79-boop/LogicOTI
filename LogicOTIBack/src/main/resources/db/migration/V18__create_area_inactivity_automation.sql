ALTER TABLE security_settings
    ADD COLUMN area_inactivity_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE security_area_inactivity_runtime (
    area_id BIGINT PRIMARY KEY,
    last_motion_at TIMESTAMP WITH TIME ZONE NOT NULL,
    light_turn_off_at TIMESTAMP WITH TIME ZONE NOT NULL,
    minisplit_turn_off_at TIMESTAMP WITH TIME ZONE NOT NULL,
    light_processed BOOLEAN NOT NULL DEFAULT FALSE,
    minisplit_processed BOOLEAN NOT NULL DEFAULT FALSE,
    lights_turned_off INTEGER NOT NULL DEFAULT 0,
    minisplits_turned_off INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_security_area_inactivity_area
        FOREIGN KEY (area_id)
        REFERENCES building_area(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_security_area_inactivity_light_time
        CHECK (light_turn_off_at >= last_motion_at),

    CONSTRAINT chk_security_area_inactivity_minisplit_time
        CHECK (minisplit_turn_off_at >= last_motion_at),

    CONSTRAINT chk_security_area_inactivity_light_count
        CHECK (lights_turned_off >= 0),

    CONSTRAINT chk_security_area_inactivity_minisplit_count
        CHECK (minisplits_turned_off >= 0)
);

CREATE INDEX idx_security_area_inactivity_light_due
    ON security_area_inactivity_runtime(
        light_processed,
        light_turn_off_at
    );

CREATE INDEX idx_security_area_inactivity_minisplit_due
    ON security_area_inactivity_runtime(
        minisplit_processed,
        minisplit_turn_off_at
    );
