/*
 * Agrega un control lógico de iluminación general a cada área activa.
 *
 * Este dispositivo representa el circuito general del área, no cada
 * luminaria física del inventario. Si posteriormente un área se divide
 * en más circuitos, podrán agregarse LUZ02, LUZ03, etc.
 */
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
    area.code || '_LUZ01',
    'Iluminación general',
    'LIGHT',
    1,
    TRUE,
    'BOOL',
    'OTI_' || area.code || '_LUZ01_CMD',
    'OTI_' || area.code || '_LUZ01_FB',
    NULL,
    1
FROM building_area area
WHERE area.active = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM building_device device
      WHERE device.area_id = area.id
        AND device.device_type = 'LIGHT'
        AND device.device_number = 1
  )
ON CONFLICT DO NOTHING;

UPDATE building_device
SET name = 'Iluminación general'
WHERE device_type = 'LIGHT'
  AND device_number = 1;
