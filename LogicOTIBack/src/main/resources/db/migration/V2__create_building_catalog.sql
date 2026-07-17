CREATE TABLE building_floor (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    display_order SMALLINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE building_area (
    id BIGSERIAL PRIMARY KEY,
    floor_id BIGINT NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    area_type VARCHAR(80) NOT NULL,
    display_order SMALLINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_building_area_floor
        FOREIGN KEY (floor_id)
        REFERENCES building_floor(id),

    CONSTRAINT uq_building_area_order
        UNIQUE (floor_id, display_order)
);

CREATE TABLE area_inventory (
    area_id BIGINT PRIMARY KEY,
    lamps INTEGER NOT NULL DEFAULT 0 CHECK (lamps >= 0),
    motion_sensors INTEGER NOT NULL DEFAULT 0 CHECK (motion_sensors >= 0),
    door_sensors INTEGER NOT NULL DEFAULT 0 CHECK (door_sensors >= 0),
    smoke_sensors INTEGER NOT NULL DEFAULT 0 CHECK (smoke_sensors >= 0),
    outlets INTEGER NOT NULL DEFAULT 0 CHECK (outlets >= 0),
    switches INTEGER NOT NULL DEFAULT 0 CHECK (switches >= 0),
    minisplits INTEGER NOT NULL DEFAULT 0 CHECK (minisplits >= 0),

    CONSTRAINT fk_area_inventory_area
        FOREIGN KEY (area_id)
        REFERENCES building_area(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_building_area_floor_id
    ON building_area(floor_id);

INSERT INTO building_floor (code, name, display_order)
VALUES
    ('PB', 'Planta Baja', 1),
    ('P1', 'Piso 1', 2),
    ('P2', 'Piso 2', 3);

INSERT INTO building_area (
    floor_id,
    code,
    name,
    area_type,
    display_order
)
SELECT
    floor.id,
    area.code,
    area.name,
    area.area_type,
    area.display_order
FROM (
    VALUES
        ('PB', 'PB_A01', 'Recepción', 'Recepción', 1),
        ('PB', 'PB_A02', 'Comedor', 'Comedor', 2),
        ('PB', 'PB_A03', 'Exterior comedor', 'Pasillo', 3),
        ('PB', 'PB_A04', 'Taller', 'Taller', 4),
        ('PB', 'PB_A05', 'Bodega', 'Bodega', 5),

        ('P1', 'P1_A01', 'Dirección', 'Oficina', 1),
        ('P1', 'P1_A02', 'Sala de estar', 'Sala', 2),
        ('P1', 'P1_A03', 'Pasillo principal', 'Pasillo', 3),
        ('P1', 'P1_A04', 'Recepción escalera principal', 'Escalera', 4),
        ('P1', 'P1_A05', 'Sala de juntas', 'Sala de juntas', 5),
        ('P1', 'P1_A06', 'Operaciones', 'Oficina', 6),
        ('P1', 'P1_A07', 'Carlos', 'Oficina', 7),
        ('P1', 'P1_A08', 'César', 'Oficina', 8),
        ('P1', 'P1_A09', 'Hernán', 'Oficina', 9),
        ('P1', 'P1_A10', 'Sala de agua', 'Espacio', 10),
        ('P1', 'P1_A11', 'RH', 'Recursos Humanos', 11),
        ('P1', 'P1_A12', 'Calidad', 'Calidad', 12),
        ('P1', 'P1_A13', 'Ingeniería', 'Ingeniería', 13),
        ('P1', 'P1_A14', 'Planeaciones', 'Planeación', 14),
        ('P1', 'P1_A15', 'Compras', 'Compras', 15),
        ('P1', 'P1_A16', 'Recepción escalera trasera', 'Escalera', 16),
        ('P1', 'P1_A17', 'Baño', 'Baño', 17),

        ('P2', 'P2_A01', 'Recepción escalera trasera', 'Escalera', 1),
        ('P2', 'P2_A02', 'Sala de juntas', 'Sala de juntas', 2),
        ('P2', 'P2_A03', 'Baño', 'Baño', 3),
        ('P2', 'P2_A04', 'Área de agua', 'Espacio', 4),
        ('P2', 'P2_A05', 'Administración', 'Oficina', 5),
        ('P2', 'P2_A06', 'Contabilidad', 'Oficina', 6),
        ('P2', 'P2_A07', 'Control de presupuesto', 'Oficina', 7),
        ('P2', 'P2_A08', 'Diseño de imagen', 'Oficina', 8),
        ('P2', 'P2_A09', 'Fluxtronics', 'Oficina', 9),
        ('P2', 'P2_A10', 'Ventas', 'Oficina', 10),
        ('P2', 'P2_A11', 'Sala de estar', 'Sala', 11),
        ('P2', 'P2_A12', 'Recepción escalera principal', 'Escalera', 12),
        ('P2', 'P2_A13', 'Pasillo', 'Pasillo', 13),
        ('P2', 'P2_A14', 'Automatización', 'Oficina', 14)
) AS area (
    floor_code,
    code,
    name,
    area_type,
    display_order
)
INNER JOIN building_floor floor
    ON floor.code = area.floor_code;

INSERT INTO area_inventory (
    area_id,
    lamps,
    motion_sensors,
    door_sensors,
    smoke_sensors,
    outlets,
    switches,
    minisplits
)
SELECT
    area.id,
    inventory.lamps,
    inventory.motion_sensors,
    inventory.door_sensors,
    inventory.smoke_sensors,
    inventory.outlets,
    inventory.switches,
    inventory.minisplits
FROM (
    VALUES
        ('PB_A01', 1, 1, 1, 1, 6, 4, 1),
        ('PB_A02', 4, 0, 0, 1, 4, 2, 1),
        ('PB_A03', 2, 1, 1, 1, 0, 1, 0),
        ('PB_A04', 16, 3, 2, 1, 16, 3, 0),
        ('PB_A05', 19, 1, 1, 1, 2, 2, 0),

        ('P1_A01', 6, 1, 1, 1, 3, 2, 2),
        ('P1_A02', 10, 2, 0, 2, 3, 2, 3),
        ('P1_A03', 9, 0, 1, 1, 0, 0, 0),
        ('P1_A04', 0, 1, 1, 0, 0, 1, 0),
        ('P1_A05', 7, 1, 0, 1, 3, 1, 1),
        ('P1_A06', 5, 1, 0, 1, 2, 1, 1),
        ('P1_A07', 5, 1, 0, 1, 2, 1, 1),
        ('P1_A08', 4, 1, 0, 1, 2, 1, 1),
        ('P1_A09', 5, 1, 0, 1, 2, 1, 1),
        ('P1_A10', 1, 0, 0, 0, 2, 1, 0),
        ('P1_A11', 2, 1, 0, 1, 3, 1, 1),
        ('P1_A12', 2, 0, 0, 0, 4, 1, 1),
        ('P1_A13', 2, 1, 0, 1, 3, 1, 1),
        ('P1_A14', 2, 1, 0, 1, 4, 1, 1),
        ('P1_A15', 5, 1, 0, 1, 3, 1, 1),
        ('P1_A16', 1, 0, 2, 0, 0, 1, 0),
        ('P1_A17', 2, 0, 0, 1, 1, 1, 0),

        ('P2_A01', 1, 0, 1, 0, 0, 1, 0),
        ('P2_A02', 7, 1, 0, 1, 4, 1, 1),
        ('P2_A03', 2, 0, 0, 0, 0, 1, 0),
        ('P2_A04', 1, 0, 0, 0, 2, 1, 0),
        ('P2_A05', 5, 1, 0, 1, 2, 1, 1),
        ('P2_A06', 4, 1, 0, 1, 4, 1, 1),
        ('P2_A07', 5, 1, 0, 1, 3, 1, 1),
        ('P2_A08', 5, 1, 0, 1, 3, 1, 1),
        ('P2_A09', 5, 1, 0, 1, 2, 1, 1),
        ('P2_A10', 5, 1, 0, 1, 4, 1, 1),
        ('P2_A11', 7, 1, 0, 1, 3, 1, 1),
        ('P2_A12', 4, 0, 0, 0, 0, 0, 0),
        ('P2_A13', 5, 0, 1, 0, 0, 0, 0),
        ('P2_A14', 4, 1, 0, 1, 7, 1, 1)
) AS inventory (
    area_code,
    lamps,
    motion_sensors,
    door_sensors,
    smoke_sensors,
    outlets,
    switches,
    minisplits
)
INNER JOIN building_area area
    ON area.code = inventory.area_code;