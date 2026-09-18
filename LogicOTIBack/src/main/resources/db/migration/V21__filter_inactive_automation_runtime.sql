-- Elimina únicamente estados temporales de ejecución.
-- Los catálogos y los históricos se conservan.
DELETE FROM security_automatic_lighting_runtime runtime
WHERE NOT EXISTS (
    SELECT 1
    FROM building_device device
    INNER JOIN building_area area
        ON area.id = device.area_id
    INNER JOIN building_floor floor
        ON floor.id = area.floor_id
    WHERE device.id = runtime.device_id
      AND device.active = TRUE
      AND area.active = TRUE
      AND floor.active = TRUE
      AND device.device_type = 'LIGHT'
      AND device.controllable = TRUE
);

DELETE FROM security_area_inactivity_runtime runtime
WHERE NOT EXISTS (
    SELECT 1
    FROM building_area area
    INNER JOIN building_floor floor
        ON floor.id = area.floor_id
    WHERE area.id = runtime.area_id
      AND area.active = TRUE
      AND floor.active = TRUE
);
