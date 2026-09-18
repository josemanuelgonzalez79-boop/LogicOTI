UPDATE building_area
SET name = CASE code
    WHEN 'PB_A01' THEN 'Recepción y sala de espera'
    WHEN 'PB_A02' THEN 'Comedor'
    WHEN 'PB_A03' THEN 'Área de carga y descarga'
    WHEN 'PB_A04' THEN 'Taller'
    WHEN 'PB_A05' THEN 'Almacén'
    WHEN 'P1_A01' THEN 'Dirección'
    WHEN 'P1_A02' THEN 'Sala de espera'
    WHEN 'P1_A03' THEN 'Pasillo principal'
    WHEN 'P1_A04' THEN 'Escalera principal'
    WHEN 'P1_A05' THEN 'Sala de juntas N1'
    WHEN 'P1_A06' THEN 'Operaciones'
    WHEN 'P1_A10' THEN 'Sala de agua'
    WHEN 'P1_A11' THEN 'Recursos Humanos'
    WHEN 'P1_A12' THEN 'César'
    WHEN 'P1_A13' THEN 'Hernán'
    WHEN 'P1_A14' THEN 'Carlos'
    WHEN 'P1_A15' THEN 'Compras'
    WHEN 'P1_A16' THEN 'Escalera trasera'
    WHEN 'P1_A17' THEN 'Baño'
    WHEN 'P2_A01' THEN 'Escalera trasera'
    WHEN 'P2_A02' THEN 'Sala de juntas N2'
    WHEN 'P2_A03' THEN 'Baño'
    WHEN 'P2_A04' THEN 'Cuarto de máquinas'
    WHEN 'P2_A05' THEN 'Administración'
    WHEN 'P2_A06' THEN 'Contabilidad'
    WHEN 'P2_A07' THEN 'Ingeniería'
    WHEN 'P2_A08' THEN 'Diseño e Imagen'
    WHEN 'P2_A09' THEN 'Fluxtronics'
    WHEN 'P2_A10' THEN 'Ventas'
    WHEN 'P2_A11' THEN 'Sala de espera'
    WHEN 'P2_A12' THEN 'Escalera principal'
    WHEN 'P2_A13' THEN 'Pasillo'
    WHEN 'P2_A14' THEN 'Automatización'
    ELSE name
END
WHERE code IN (
    'PB_A01', 'PB_A02', 'PB_A03', 'PB_A04', 'PB_A05',
    'P1_A01', 'P1_A02', 'P1_A03', 'P1_A04', 'P1_A05',
    'P1_A06', 'P1_A10', 'P1_A11', 'P1_A12', 'P1_A13',
    'P1_A14', 'P1_A15', 'P1_A16', 'P1_A17',
    'P2_A01', 'P2_A02', 'P2_A03', 'P2_A04', 'P2_A05',
    'P2_A06', 'P2_A07', 'P2_A08', 'P2_A09', 'P2_A10',
    'P2_A11', 'P2_A12', 'P2_A13', 'P2_A14'
);
