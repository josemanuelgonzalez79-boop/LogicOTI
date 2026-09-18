CREATE TABLE camera (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    channel_number SMALLINT NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    floor_code VARCHAR(10) NOT NULL,
    area_code VARCHAR(20),
    source_name VARCHAR(30) NOT NULL,
    stream_key VARCHAR(40) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order SMALLINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_camera_channel_number
        CHECK (channel_number > 0),

    CONSTRAINT chk_camera_floor_code
        CHECK (floor_code IN ('PB', 'P1', 'P2', 'EXT')),

    CONSTRAINT fk_camera_area
        FOREIGN KEY (area_code)
        REFERENCES building_area(code)
        ON DELETE SET NULL
);

CREATE INDEX idx_camera_floor_code
    ON camera(floor_code);

CREATE INDEX idx_camera_area_code
    ON camera(area_code);

CREATE INDEX idx_camera_active
    ON camera(active);

INSERT INTO camera (
    code,
    channel_number,
    name,
    floor_code,
    area_code,
    source_name,
    stream_key,
    display_order
)
VALUES
    ('CAM-001', 1, 'Frente acceso', 'PB', NULL, 'NVR-1', 'oti-cam-01', 1),
    ('CAM-002', 2, 'Frente portón patio', 'EXT', NULL, 'NVR-2', 'oti-cam-02', 2),
    ('CAM-003', 3, 'Patio', 'EXT', NULL, 'NVR-3', 'oti-cam-03', 3),
    ('CAM-004', 4, 'Almacén 1', 'PB', 'PB_A05', 'NVR-4', 'oti-cam-04', 4),
    ('CAM-005', 5, 'Acceso puerta', 'PB', NULL, 'NVR-5', 'oti-cam-05', 5),
    ('CAM-006', 6, 'Taller 1', 'PB', 'PB_A04', 'NVR-SWT', 'oti-cam-06', 6),
    ('CAM-007', 7, 'Pasillo taller', 'PB', 'PB_A04', 'NVR-SWT', 'oti-cam-07', 7),
    ('CAM-008', 8, 'Recepción', 'PB', 'PB_A01', 'NVR-6', 'oti-cam-08', 8),
    ('CAM-009', 9, 'Taller puerta', 'PB', 'PB_A04', 'NVR-SWT', 'oti-cam-09', 9),
    ('CAM-010', 10, 'Comedor', 'PB', 'PB_A02', 'NVR-7', 'oti-cam-10', 10),
    ('CAM-011', 11, 'Acceso puerta principal', 'PB', NULL, 'NVR-8', 'oti-cam-11', 11),
    ('CAM-012', 12, 'Escalera primera planta', 'P1', 'P1_A04', 'NVR-9', 'oti-cam-12', 12),
    ('CAM-013', 13, 'Pasillo segundo piso', 'P1', 'P1_A03', 'NVR-10', 'oti-cam-13', 13),
    ('CAM-014', 14, 'SITE segundo piso', 'P1', NULL, 'NVR-11', 'oti-cam-14', 14),
    ('CAM-015', 15, 'Sala de espera segundo piso', 'P1', 'P1_A02', 'NVR-12', 'oti-cam-15', 15),
    ('CAM-016', 16, 'Entrada', 'P1', NULL, 'NVR-13', 'oti-cam-16', 16),
    ('CAM-017', 17, 'Pasillo tercer piso', 'P2', 'P2_A13', 'SW2', 'oti-cam-17', 17),
    ('CAM-018', 18, 'Escalera tercer piso', 'P2', NULL, 'SW2', 'oti-cam-18', 18),
    ('CAM-019', 19, 'Sala de espera tercer piso', 'P2', 'P2_A11', 'SW2', 'oti-cam-19', 19),
    ('CAM-020', 20, 'Video portero', 'PB', NULL, 'SW1', 'oti-cam-20', 20),
    ('CAM-021', 21, 'Calle estacionamiento', 'EXT', NULL, 'NVR-14', 'oti-cam-21', 21),
    ('CAM-022', 22, 'Taller 2', 'PB', 'PB_A04', 'NVR-SWA', 'oti-cam-22', 22),
    ('CAM-023', 23, 'Almacén 2', 'PB', 'PB_A05', 'NVR-SWA', 'oti-cam-23', 23),
    ('CAM-024', 24, 'Almacén 3', 'PB', 'PB_A05', 'NVR-SWA', 'oti-cam-24', 24),
    ('CAM-025', 25, 'PTZ frente izquierdo', 'EXT', NULL, 'SW3', 'oti-cam-25', 25),
    ('CAM-026', 26, 'PTZ frente derecho', 'EXT', NULL, 'SW3', 'oti-cam-26', 26),
    ('CAM-027', 27, 'PTZ atrás izquierdo', 'EXT', NULL, 'SW3', 'oti-cam-27', 27),
    ('CAM-028', 28, 'PTZ atrás derecho', 'EXT', NULL, 'SW3', 'oti-cam-28', 28);
