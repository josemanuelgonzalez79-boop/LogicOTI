/*
 * Divide la seguridad de intrusión en cuatro zonas operativas:
 * planta baja, piso 1, piso 2 y patio/exterior.
 *
 * Los circuitos nuevos se registran inactivos porque sus tags son
 * provisionales. Deben activarse únicamente después de validar el
 * cableado, el comando y la confirmación con el programador del PLC.
 */

INSERT INTO building_floor (
    code,
    name,
    display_order,
    active
)
VALUES (
    'EXT',
    'Patio y exterior',
    4,
    TRUE
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO building_area (
    floor_id,
    code,
    name,
    area_type,
    display_order,
    active
)
SELECT
    floor.id,
    area.code,
    area.name,
    area.area_type,
    area.display_order,
    TRUE
FROM (
    VALUES
        ('EXT_A01', 'Patio', 'Exterior', 1),
        ('EXT_A02', 'Entrada exterior', 'Acceso', 2)
) AS area(code, name, area_type, display_order)
INNER JOIN building_floor floor
    ON floor.code = 'EXT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO building_area (
    floor_id,
    code,
    name,
    area_type,
    display_order,
    active
)
SELECT
    floor.id,
    'PB_A06',
    'Entrada interior',
    'Acceso',
    6,
    TRUE
FROM building_floor floor
WHERE floor.code = 'PB'
ON CONFLICT (code) DO NOTHING;

INSERT INTO area_inventory (
    area_id,
    lamps,
    motion_sensors,
    door_sensors,
    smoke_sensors,
    outlets,
    switches,
    minisplits
)
SELECT
    area.id,
    inventory.lamps,
    0,
    0,
    0,
    0,
    inventory.switches,
    0
FROM (
    VALUES
        ('EXT_A01', 7, 1),
        ('EXT_A02', 4, 1),
        -- Cantidad de luminarias pendiente; se confirmaron dos circuitos.
        ('PB_A06', 0, 2)
) AS inventory(area_code, lamps, switches)
INNER JOIN building_area area
    ON area.code = inventory.area_code
ON CONFLICT (area_id) DO NOTHING;

INSERT INTO building_device (
    area_id,
    code,
    name,
    device_type,
    device_number,
    controllable,
    plc_data_type,
    plc_command_tag,
    plc_state_tag,
    plc_fault_tag,
    display_order,
    active
)
SELECT
    area.id,
    device.code,
    device.name,
    'LIGHT',
    device.device_number,
    TRUE,
    'BOOL',
    device.command_tag,
    device.state_tag,
    NULL,
    device.display_order,
    FALSE
FROM (
    VALUES
        (
            'EXT_A01',
            'EXT_A01_LUZ01',
            'Iluminación general del patio',
            1,
            'OTI_EXT_A01_LUZ01_CMD',
            'OTI_EXT_A01_LUZ01_FB',
            1
        ),
        (
            'EXT_A02',
            'EXT_A02_LUZ01',
            'Iluminación exterior de la entrada',
            1,
            'OTI_EXT_A02_LUZ01_CMD',
            'OTI_EXT_A02_LUZ01_FB',
            1
        ),
        (
            'PB_A06',
            'PB_A06_LUZ01',
            'Entrada interior - Circuito A',
            1,
            'OTI_PB_A06_LUZ01_CMD',
            'OTI_PB_A06_LUZ01_FB',
            1
        ),
        (
            'PB_A06',
            'PB_A06_LUZ02',
            'Entrada interior - Circuito B',
            2,
            'OTI_PB_A06_LUZ02_CMD',
            'OTI_PB_A06_LUZ02_FB',
            2
        )
) AS device(
    area_code,
    code,
    name,
    device_number,
    command_tag,
    state_tag,
    display_order
)
INNER JOIN building_area area
    ON area.code = device.area_code
ON CONFLICT (code) DO NOTHING;

CREATE TABLE security_zone (
    code VARCHAR(20) PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    display_order SMALLINT NOT NULL UNIQUE,
    motion_detection_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO security_zone (
    code,
    name,
    display_order,
    motion_detection_enabled
)
VALUES
    ('PB', 'Planta Baja', 1, TRUE),
    ('P1', 'Piso 1', 2, TRUE),
    ('P2', 'Piso 2', 3, TRUE),
    ('PATIO', 'Patio y exterior', 4, FALSE);

CREATE TABLE security_zone_area (
    area_id BIGINT PRIMARY KEY,
    zone_code VARCHAR(20) NOT NULL,

    CONSTRAINT fk_security_zone_area_area
        FOREIGN KEY (area_id)
        REFERENCES building_area(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_security_zone_area_zone
        FOREIGN KEY (zone_code)
        REFERENCES security_zone(code)
        ON DELETE CASCADE
);

INSERT INTO security_zone_area (area_id, zone_code)
SELECT
    area.id,
    CASE floor.code
        WHEN 'PB' THEN 'PB'
        WHEN 'P1' THEN 'P1'
        WHEN 'P2' THEN 'P2'
        WHEN 'EXT' THEN 'PATIO'
    END
FROM building_area area
INNER JOIN building_floor floor
    ON floor.id = area.floor_id
WHERE floor.code IN ('PB', 'P1', 'P2', 'EXT');

CREATE INDEX idx_security_zone_area_zone
    ON security_zone_area(zone_code);

CREATE TABLE security_zone_state (
    zone_code VARCHAR(20) PRIMARY KEY,
    mode VARCHAR(30) NOT NULL DEFAULT 'DISARMED',
    mode_before_alarm VARCHAR(30),
    message VARCHAR(400) NOT NULL,
    changed_by VARCHAR(50) NOT NULL,
    change_source VARCHAR(20) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    arming_completes_at TIMESTAMP WITH TIME ZONE,
    automatic_transition_key VARCHAR(40),
    alarm_event_id BIGINT,

    CONSTRAINT fk_security_zone_state_zone
        FOREIGN KEY (zone_code)
        REFERENCES security_zone(code)
        ON DELETE CASCADE,

    CONSTRAINT fk_security_zone_state_event
        FOREIGN KEY (alarm_event_id)
        REFERENCES device_event_history(id)
        ON DELETE SET NULL,

    CONSTRAINT chk_security_zone_state_mode
        CHECK (
            mode IN (
                'DISARMED',
                'ARMING',
                'ARMED',
                'ARMED_WITH_BYPASS',
                'REJECTED',
                'ALARM'
            )
        ),

    CONSTRAINT chk_security_zone_state_previous_mode
        CHECK (
            mode_before_alarm IS NULL
            OR mode_before_alarm IN ('ARMED', 'ARMED_WITH_BYPASS')
        ),

    CONSTRAINT chk_security_zone_state_source
        CHECK (change_source IN ('MANUAL', 'SCHEDULE', 'SYSTEM'))
);

INSERT INTO security_zone_state (
    zone_code,
    mode,
    message,
    changed_by,
    change_source
)
SELECT
    zone.code,
    'DISARMED',
    'La zona se encuentra desarmada.',
    'SYSTEM',
    'SYSTEM'
FROM security_zone zone;

CREATE TABLE security_zone_history (
    id BIGSERIAL PRIMARY KEY,
    zone_code VARCHAR(20) NOT NULL,
    previous_mode VARCHAR(30) NOT NULL,
    current_mode VARCHAR(30) NOT NULL,
    message VARCHAR(400) NOT NULL,
    changed_by VARCHAR(50) NOT NULL,
    change_source VARCHAR(20) NOT NULL,
    alarm_event_id BIGINT,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_security_zone_history_zone
        FOREIGN KEY (zone_code)
        REFERENCES security_zone(code),

    CONSTRAINT fk_security_zone_history_event
        FOREIGN KEY (alarm_event_id)
        REFERENCES device_event_history(id)
        ON DELETE SET NULL
);

CREATE INDEX idx_security_zone_history_changed_at
    ON security_zone_history(changed_at DESC);

CREATE INDEX idx_security_zone_history_zone_changed_at
    ON security_zone_history(zone_code, changed_at DESC);

/*
 * Contiene únicamente luces que estaban apagadas y que fueron
 * encendidas por la seguridad. Una luz encendida manualmente nunca
 * se registra aquí y, por lo tanto, no se apaga al reconocer o desarmar.
 */
CREATE TABLE security_zone_light_runtime (
    device_id BIGINT PRIMARY KEY,
    zone_code VARCHAR(20) NOT NULL,
    activation_reason VARCHAR(20) NOT NULL,
    trigger_event_id BIGINT,
    activated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_security_zone_light_device
        FOREIGN KEY (device_id)
        REFERENCES building_device(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_security_zone_light_zone
        FOREIGN KEY (zone_code)
        REFERENCES security_zone(code)
        ON DELETE CASCADE,

    CONSTRAINT fk_security_zone_light_event
        FOREIGN KEY (trigger_event_id)
        REFERENCES device_event_history(id)
        ON DELETE SET NULL,

    CONSTRAINT chk_security_zone_light_reason
        CHECK (activation_reason IN ('ARMING', 'ALARM'))
);

CREATE INDEX idx_security_zone_light_runtime_zone
    ON security_zone_light_runtime(zone_code);
