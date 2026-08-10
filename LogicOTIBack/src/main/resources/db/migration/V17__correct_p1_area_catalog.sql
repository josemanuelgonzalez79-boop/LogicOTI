-- Estas tres áreas personales eran duplicados de las oficinas que ya existen
-- en el plano como Ingeniería, Gerencia de Operaciones y Gerencia de
-- Ingeniería.
-- Se desactivan también sus dispositivos para conservar los históricos
-- sin seguir mostrándolos ni consultándolos al PLC.
UPDATE building_device
SET active = FALSE
WHERE area_id IN (
    SELECT id
    FROM building_area
    WHERE code IN ('P1_A07', 'P1_A08', 'P1_A09')
);

UPDATE building_area
SET active = FALSE
WHERE code IN ('P1_A07', 'P1_A08', 'P1_A09');
