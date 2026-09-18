-- La cámara CAM-016 corresponde a la entrada de Planta Baja.
-- Se conserva el código, canal, transmisión e histórico existentes.
UPDATE camera
SET floor_code = 'PB'
WHERE code = 'CAM-016';
