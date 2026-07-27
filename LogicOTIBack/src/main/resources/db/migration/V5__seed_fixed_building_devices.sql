/*
 * Crea los dispositivos cuya cantidad ya está confirmada
 * en el levantamiento:
 *
 * - Sensores de movimiento
 * - Sensores de humo
 * - Sensores de puerta
 * - Minisplits
 *
 * Las luces y contactos se agregarán posteriormente,
 * cuando se definan los circuitos que realmente controlará el PLC.
 */

WITH device_config (
    device_type,
    code_part,
    name_part,
    state_suffix,
    controllable,
    display_base
) AS (
    VALUES
        ('MOTION',    'MOV', 'Sensor de movimiento', 'ST',  FALSE, 100),
        ('SMOKE',     'HUM', 'Sensor de humo',       'ALM', FALSE, 200),
        ('DOOR',      'PUE', 'Sensor de puerta',     'ST',  FALSE, 300),
        ('MINISPLIT', 'MS',  'Minisplit',            'FB',  TRUE,  400)
),
generated_devices AS (
    SELECT
        area.id AS area_id,

        area.code || '_' ||
        config.code_part ||
        LPAD(series.device_number::TEXT, 2, '0') AS code,

        config.name_part || ' ' ||
        series.device_number AS name,

        config.device_type,
        series.device_number::SMALLINT AS device_number,
        config.controllable,
        'BOOL' AS plc_data_type,

        CASE
            WHEN config.controllable THEN
                'OTI_' || area.code || '_' ||
                config.code_part ||
                LPAD(series.device_number::TEXT, 2, '0') ||
                '_CMD'
            ELSE NULL
        END AS plc_command_tag,

        'OTI_' || area.code || '_' ||
        config.code_part ||
        LPAD(series.device_number::TEXT, 2, '0') ||
        '_' || config.state_suffix AS plc_state_tag,

        CAST(NULL AS VARCHAR) AS plc_fault_tag,

        (
            config.display_base +
            series.device_number
        )::SMALLINT AS display_order

    FROM building_area area

    INNER JOIN area_inventory inventory
        ON inventory.area_id = area.id

    CROSS JOIN device_config config

    CROSS JOIN LATERAL generate_series(
        1,
        CASE config.device_type
            WHEN 'MOTION' THEN inventory.motion_sensors
            WHEN 'SMOKE' THEN inventory.smoke_sensors
            WHEN 'DOOR' THEN inventory.door_sensors
            WHEN 'MINISPLIT' THEN inventory.minisplits
            ELSE 0
        END
    ) AS series(device_number)
)

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
FROM generated_devices
ON CONFLICT DO NOTHING;