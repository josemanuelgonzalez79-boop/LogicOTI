ALTER TABLE security_settings
    ADD COLUMN automatic_lighting_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN automatic_lighting_start_time TIME WITHOUT TIME ZONE NOT NULL DEFAULT '18:00:00',
    ADD COLUMN automatic_lighting_end_time TIME WITHOUT TIME ZONE NOT NULL DEFAULT '08:00:00';

ALTER TABLE security_settings
    ADD CONSTRAINT chk_security_automatic_lighting_window
        CHECK (
            automatic_lighting_start_time
                <> automatic_lighting_end_time
        );

CREATE TABLE security_automatic_lighting_target (
    device_id BIGINT PRIMARY KEY,
    configured_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_security_automatic_lighting_device
        FOREIGN KEY (device_id)
        REFERENCES building_device(id)
        ON DELETE CASCADE
);

-- Selección inicial: pasillos, escaleras y áreas comunes del levantamiento.
-- Después puede cambiarse desde Seguridad y horarios.
INSERT INTO security_automatic_lighting_target (device_id)
SELECT device.id
FROM building_device device
INNER JOIN building_area area
    ON area.id = device.area_id
WHERE device.active = TRUE
  AND device.device_type = 'LIGHT'
  AND device.controllable = TRUE
  AND area.code IN (
      'PB_A03',
      'P1_A03',
      'P1_A04',
      'P1_A16',
      'P2_A01',
      'P2_A12',
      'P2_A13'
  )
ON CONFLICT (device_id) DO NOTHING;

-- Esta tabla únicamente contiene luces encendidas por la automatización.
-- Así el apagado por inactividad no afecta una luz encendida manualmente.
CREATE TABLE security_automatic_lighting_runtime (
    device_id BIGINT PRIMARY KEY,
    activated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_motion_at TIMESTAMP WITH TIME ZONE NOT NULL,
    turn_off_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_security_automatic_lighting_runtime_device
        FOREIGN KEY (device_id)
        REFERENCES building_device(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_security_automatic_lighting_turn_off
        CHECK (turn_off_at >= last_motion_at)
);

CREATE INDEX idx_security_automatic_lighting_turn_off
    ON security_automatic_lighting_runtime(turn_off_at);

ALTER TABLE device_command_history
    DROP CONSTRAINT chk_device_command_history_role;

ALTER TABLE device_command_history
    ADD CONSTRAINT chk_device_command_history_role
        CHECK (
            requested_by_role IN (
                'ADMIN',
                'OPERATOR',
                'AUTOMATION'
            )
        );
