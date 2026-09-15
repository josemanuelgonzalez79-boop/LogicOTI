/*
 * Activa los cuatro circuitos agregados para la seguridad por zonas.
 *
 * Conserva los nombres de comando y retroalimentacion establecidos en
 * V26. Debe desplegarse despues de que los cuatro pares de tags existan
 * en el PLC y su rutina de confirmacion haya sido validada.
 */

WITH confirmed_device (
    code,
    command_tag,
    state_tag
) AS (
    VALUES
        (
            'EXT_A01_LUZ01',
            'OTI_EXT_A01_LUZ01_CMD',
            'OTI_EXT_A01_LUZ01_FB'
        ),
        (
            'EXT_A02_LUZ01',
            'OTI_EXT_A02_LUZ01_CMD',
            'OTI_EXT_A02_LUZ01_FB'
        ),
        (
            'PB_A06_LUZ01',
            'OTI_PB_A06_LUZ01_CMD',
            'OTI_PB_A06_LUZ01_FB'
        ),
        (
            'PB_A06_LUZ02',
            'OTI_PB_A06_LUZ02_CMD',
            'OTI_PB_A06_LUZ02_FB'
        )
)
UPDATE building_device device
SET controllable = TRUE,
    plc_data_type = 'BOOL',
    plc_command_tag = confirmed.command_tag,
    plc_state_tag = confirmed.state_tag,
    active = TRUE
FROM confirmed_device confirmed
WHERE device.code = confirmed.code;

DO $$
DECLARE
    configured_count INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO configured_count
    FROM building_device
    WHERE code IN (
        'EXT_A01_LUZ01',
        'EXT_A02_LUZ01',
        'PB_A06_LUZ01',
        'PB_A06_LUZ02'
    )
      AND active = TRUE
      AND controllable = TRUE
      AND plc_data_type = 'BOOL'
      AND plc_command_tag IS NOT NULL
      AND plc_state_tag IS NOT NULL;

    IF configured_count <> 4 THEN
        RAISE EXCEPTION
            'No fue posible activar los cuatro circuitos de seguridad por zonas.';
    END IF;
END
$$;
