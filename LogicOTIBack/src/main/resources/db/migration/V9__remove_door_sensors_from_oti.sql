
UPDATE building_device
SET active = FALSE
WHERE device_type = 'DOOR'
  AND active = TRUE;

UPDATE area_inventory
SET door_sensors = 0
WHERE door_sensors <> 0;
