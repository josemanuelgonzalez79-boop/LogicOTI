CREATE TABLE user_two_factor (
    user_id BIGINT PRIMARY KEY,
    encrypted_secret VARCHAR(1024) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    setup_expires_at TIMESTAMP WITH TIME ZONE,
    confirmed_at TIMESTAMP WITH TIME ZONE,
    last_accepted_time_step BIGINT,
    failed_attempts SMALLINT NOT NULL DEFAULT 0,
    blocked_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_user_two_factor_user
        FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,

    CONSTRAINT chk_user_two_factor_setup
        CHECK (
            (enabled = FALSE AND confirmed_at IS NULL)
            OR
            (enabled = TRUE AND confirmed_at IS NOT NULL)
        ),

    CONSTRAINT chk_user_two_factor_failed_attempts
        CHECK (failed_attempts BETWEEN 0 AND 20)
);

CREATE TABLE user_two_factor_recovery_code (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_user_two_factor_recovery_user
        FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE INDEX idx_two_factor_recovery_available
    ON user_two_factor_recovery_code(user_id, used_at);

CREATE TABLE auth_two_factor_challenge (
    token_hash CHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    attempts_remaining SMALLINT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_auth_two_factor_challenge_user
        FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,

    CONSTRAINT chk_auth_two_factor_challenge_attempts
        CHECK (attempts_remaining BETWEEN 0 AND 10)
);

CREATE INDEX idx_auth_two_factor_challenge_user
    ON auth_two_factor_challenge(user_id, expires_at DESC);

CREATE INDEX idx_auth_two_factor_challenge_expiration
    ON auth_two_factor_challenge(expires_at);
