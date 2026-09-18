CREATE TABLE security_settings (
    id SMALLINT PRIMARY KEY,
    automatic_schedule_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    timezone VARCHAR(60) NOT NULL DEFAULT 'America/Mazatlan',
    exit_delay_seconds SMALLINT NOT NULL DEFAULT 60,
    light_inactivity_minutes SMALLINT NOT NULL DEFAULT 10,
    minisplit_inactivity_minutes SMALLINT NOT NULL DEFAULT 30,
    diagnostic_timeout_seconds SMALLINT NOT NULL DEFAULT 120,
    diagnostic_validity_months SMALLINT NOT NULL DEFAULT 4,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(50),

    CONSTRAINT chk_security_settings_singleton
        CHECK (id = 1),

    CONSTRAINT chk_security_exit_delay
        CHECK (exit_delay_seconds BETWEEN 0 AND 600),

    CONSTRAINT chk_security_light_inactivity
        CHECK (light_inactivity_minutes BETWEEN 1 AND 1440),

    CONSTRAINT chk_security_minisplit_inactivity
        CHECK (minisplit_inactivity_minutes BETWEEN 1 AND 1440),

    CONSTRAINT chk_security_diagnostic_timeout
        CHECK (diagnostic_timeout_seconds BETWEEN 30 AND 600),

    CONSTRAINT chk_security_diagnostic_validity
        CHECK (diagnostic_validity_months BETWEEN 1 AND 24)
);

INSERT INTO security_settings (
    id,
    automatic_schedule_enabled,
    timezone,
    exit_delay_seconds,
    light_inactivity_minutes,
    minisplit_inactivity_minutes,
    diagnostic_timeout_seconds,
    diagnostic_validity_months,
    updated_by
)
VALUES (
    1,
    TRUE,
    'America/Mazatlan',
    60,
    10,
    30,
    120,
    4,
    'SYSTEM'
);

CREATE TABLE security_schedule_day (
    id BIGSERIAL PRIMARY KEY,
    day_of_week SMALLINT NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    all_day_armed BOOLEAN NOT NULL DEFAULT FALSE,
    arm_time TIME WITHOUT TIME ZONE NOT NULL DEFAULT '18:00:00',
    disarm_time TIME WITHOUT TIME ZONE NOT NULL DEFAULT '08:00:00',

    CONSTRAINT chk_security_schedule_day
        CHECK (day_of_week BETWEEN 1 AND 7),

    CONSTRAINT chk_security_schedule_mode
        CHECK (
            all_day_armed = TRUE
            OR arm_time <> disarm_time
        )
);

/*
 * ISO-8601: lunes = 1 y domingo = 7.
 * Lunes a viernes: armado de 18:00 a 08:00 del día siguiente.
 * Sábado y domingo: armado todo el día como valor inicial seguro.
 */
INSERT INTO security_schedule_day (
    day_of_week,
    enabled,
    all_day_armed,
    arm_time,
    disarm_time
)
VALUES
    (1, TRUE, FALSE, '18:00:00', '08:00:00'),
    (2, TRUE, FALSE, '18:00:00', '08:00:00'),
    (3, TRUE, FALSE, '18:00:00', '08:00:00'),
    (4, TRUE, FALSE, '18:00:00', '08:00:00'),
    (5, TRUE, FALSE, '18:00:00', '08:00:00'),
    (6, TRUE, TRUE,  '18:00:00', '08:00:00'),
    (7, TRUE, TRUE,  '18:00:00', '08:00:00');

CREATE TABLE intrusion_alarm_state (
    id SMALLINT PRIMARY KEY,
    mode VARCHAR(30) NOT NULL DEFAULT 'DISARMED',
    message VARCHAR(300) NOT NULL,
    changed_by VARCHAR(50) NOT NULL,
    change_source VARCHAR(20) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    arming_completes_at TIMESTAMP WITH TIME ZONE,
    automatic_transition_key VARCHAR(40),

    CONSTRAINT chk_intrusion_alarm_state_singleton
        CHECK (id = 1),

    CONSTRAINT chk_intrusion_alarm_mode
        CHECK (
            mode IN (
                'DISARMED',
                'ARMING',
                'ARMED',
                'ARMED_WITH_BYPASS',
                'REJECTED',
                'ALARM'
            )
        ),

    CONSTRAINT chk_intrusion_alarm_source
        CHECK (
            change_source IN (
                'MANUAL',
                'SCHEDULE',
                'SYSTEM'
            )
        )
);

INSERT INTO intrusion_alarm_state (
    id,
    mode,
    message,
    changed_by,
    change_source
)
VALUES (
    1,
    'DISARMED',
    'La alarma se encuentra desarmada.',
    'SYSTEM',
    'SYSTEM'
);

CREATE TABLE intrusion_alarm_history (
    id BIGSERIAL PRIMARY KEY,
    previous_mode VARCHAR(30) NOT NULL,
    current_mode VARCHAR(30) NOT NULL,
    message VARCHAR(300) NOT NULL,
    changed_by VARCHAR(50) NOT NULL,
    change_source VARCHAR(20) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_intrusion_alarm_history_previous
        CHECK (
            previous_mode IN (
                'DISARMED',
                'ARMING',
                'ARMED',
                'ARMED_WITH_BYPASS',
                'REJECTED',
                'ALARM'
            )
        ),

    CONSTRAINT chk_intrusion_alarm_history_current
        CHECK (
            current_mode IN (
                'DISARMED',
                'ARMING',
                'ARMED',
                'ARMED_WITH_BYPASS',
                'REJECTED',
                'ALARM'
            )
        ),

    CONSTRAINT chk_intrusion_alarm_history_source
        CHECK (
            change_source IN (
                'MANUAL',
                'SCHEDULE',
                'SYSTEM'
            )
        )
);

CREATE INDEX idx_intrusion_alarm_history_changed_at
    ON intrusion_alarm_history(changed_at DESC);
