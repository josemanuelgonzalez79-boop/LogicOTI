CREATE TABLE building_device (
    id BIGSERIAL PRIMARY KEY,

    area_id BIGINT NOT NULL,

    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    device_type VARCHAR(30) NOT NULL,
    device_number SMALLINT NOT NULL,

    controllable BOOLEAN NOT NULL DEFAULT FALSE,

    plc_data_type VARCHAR(20) NOT NULL DEFAULT 'BOOL',
    plc_command_tag VARCHAR(80),
    plc_state_tag VARCHAR(80) NOT NULL,
    plc_fault_tag VARCHAR(80),

    display_order SMALLINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_building_device_area
        FOREIGN KEY (area_id)
        REFERENCES building_area(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_building_device_number
        CHECK (device_number > 0),

    CONSTRAINT chk_building_device_type
        CHECK (
            device_type IN (
                'LIGHT',
                'MOTION',
                'SMOKE',
                'DOOR',
                'MINISPLIT',
                'OUTLET'
            )
        ),

    CONSTRAINT chk_building_device_plc_type
        CHECK (
            plc_data_type IN (
                'BOOL',
                'DINT',
                'REAL'
            )
        ),

    CONSTRAINT chk_building_device_command
        CHECK (
            controllable = FALSE
            OR plc_command_tag IS NOT NULL
        ),

    CONSTRAINT uq_building_device_area_type_number
        UNIQUE (
            area_id,
            device_type,
            device_number
        )
);

CREATE INDEX idx_building_device_area_id
    ON building_device(area_id);

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
    display_order
)
SELECT
    area.id,
    device.code,
    device.name,
    device.device_type,
    device.device_number,
    device.controllable,
    device.plc_data_type,
    device.plc_command_tag,
    device.plc_state_tag,
    device.plc_fault_tag,
    device.display_order
FROM building_area area
CROSS JOIN (
    VALUES
        (
            'PB_A01_LUZ01',
            'Luz 1',
            'LIGHT',
            1,
            TRUE,
            'BOOL',
            'OTI_PB_A01_LUZ01_CMD',
            'OTI_PB_A01_LUZ01_FB',
            CAST(NULL AS VARCHAR),
            1
        ),
        (
            'PB_A01_MOV01',
            'Sensor de movimiento 1',
            'MOTION',
            1,
            FALSE,
            'BOOL',
            NULL,
            'OTI_PB_A01_MOV01_ST',
            CAST(NULL AS VARCHAR),
            2
        ),
        (
            'PB_A01_HUM01',
            'Sensor de humo 1',
            'SMOKE',
            1,
            FALSE,
            'BOOL',
            NULL,
            'OTI_PB_A01_HUM01_ALM',
            CAST(NULL AS VARCHAR),
            3
        ),
        (
            'PB_A01_PUE01',
            'Sensor de puerta 1',
            'DOOR',
            1,
            FALSE,
            'BOOL',
            NULL,
            'OTI_PB_A01_PUE01_ST',
            CAST(NULL AS VARCHAR),
            4
        ),
        (
            'PB_A01_MS01',
            'Minisplit 1',
            'MINISPLIT',
            1,
            TRUE,
            'BOOL',
            'OTI_PB_A01_MS01_CMD',
            'OTI_PB_A01_MS01_FB',
            CAST(NULL AS VARCHAR),
            5
        )
) AS device (
    code,
    name,
    device_type,
    device_number,
    controllable,
    plc_data_type,
    plc_command_tag,
    plc_state_tag,
    plc_fault_tag,
    display_order
)
WHERE area.code = 'PB_A01';