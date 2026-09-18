CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    role VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_app_user_role
        CHECK (role IN ('ADMIN', 'OPERATOR', 'MONITORING'))
);

CREATE INDEX idx_app_user_role
    ON app_user(role);

CREATE INDEX idx_app_user_active
    ON app_user(active);