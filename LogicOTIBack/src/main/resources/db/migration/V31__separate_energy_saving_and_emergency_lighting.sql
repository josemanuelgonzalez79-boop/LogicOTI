/*
 * Separa las dos responsabilidades de iluminación:
 *
 * 1. Ahorro por área mientras su zona está desarmada.
 * 2. Selección global de luces de emergencia cuando una zona armada
 *    detecta movimiento.
 */
CREATE TABLE security_area_energy_saving (
    area_id BIGINT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(50),

    CONSTRAINT fk_security_area_energy_saving_area
        FOREIGN KEY (area_id)
        REFERENCES building_area(id)
        ON DELETE CASCADE
);

/*
 * Conserva el comportamiento de las instalaciones que ya tenían
 * habilitado el ahorro global, pero permite desactivar cada área después.
 */
INSERT INTO security_area_energy_saving (
    area_id,
    enabled,
    updated_by
)
SELECT DISTINCT
    area.id,
    settings.area_inactivity_enabled,
    'SYSTEM'
FROM building_area area
INNER JOIN building_floor floor
    ON floor.id = area.floor_id
CROSS JOIN security_settings settings
WHERE settings.id = 1
  AND area.active = TRUE
  AND floor.active = TRUE
  AND EXISTS (
      SELECT 1
      FROM building_device sensor
      WHERE sensor.area_id = area.id
        AND sensor.active = TRUE
        AND sensor.device_type = 'MOTION'
  );

/*
 * El runtime anterior pertenecía al encendido global por movimiento.
 * Desde esta versión la selección configurada se usa únicamente durante
 * una alarma; por ello no debe conservar temporizadores antiguos.
 */
DELETE FROM security_automatic_lighting_runtime;
DELETE FROM security_zone_light_runtime;
