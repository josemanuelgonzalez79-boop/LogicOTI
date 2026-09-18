-- La cámara de acceso a la puerta principal vigila el exterior.
-- Se conserva su código, canal, transmisión e histórico.
UPDATE camera
SET floor_code = 'EXT'
WHERE code = 'CAM-011';
