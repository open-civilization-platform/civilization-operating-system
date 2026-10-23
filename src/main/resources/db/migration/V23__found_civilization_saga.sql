CREATE TABLE saga_executions (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_fingerprint VARCHAR(128) NOT NULL,
    civilization_name VARCHAR(255) NOT NULL,
    civilization_scale VARCHAR(32) NOT NULL,
    region_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_step VARCHAR(40),
    failed_step VARCHAR(40),
    civilization_id BIGINT,
    failure_code VARCHAR(80),
    failure_message VARCHAR(1000),
    sequence BIGINT NOT NULL DEFAULT 0 CHECK (sequence >= 0),
    lease_until TIMESTAMP WITH TIME ZONE,
    heartbeat_at TIMESTAMP WITH TIME ZONE,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_saga_owner_key UNIQUE (owner_id, idempotency_key),
    CONSTRAINT ck_saga_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPENSATING', 'SUCCEEDED', 'FAILED', 'COMPENSATED', 'COMPENSATION_FAILED'))
);

CREATE INDEX idx_saga_due ON saga_executions (status, next_attempt_at, lease_until, created_at);

CREATE TABLE saga_step_executions (
    id UUID PRIMARY KEY,
    saga_id UUID NOT NULL REFERENCES saga_executions(id) ON DELETE CASCADE,
    step_key VARCHAR(40) NOT NULL,
    step_order INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    compensation_status VARCHAR(32),
    execution_attempts INTEGER NOT NULL DEFAULT 0 CHECK (execution_attempts BETWEEN 0 AND 3),
    compensation_attempts INTEGER NOT NULL DEFAULT 0 CHECK (compensation_attempts BETWEEN 0 AND 3),
    last_error_code VARCHAR(80),
    last_error_message VARCHAR(1000),
    compensation_error_code VARCHAR(80),
    compensation_error_message VARCHAR(1000),
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_saga_step_key UNIQUE (saga_id, step_key),
    CONSTRAINT uq_saga_step_order UNIQUE (saga_id, step_order),
    CONSTRAINT ck_saga_step_key CHECK ((step_order = 0 AND step_key = 'CREATE_CIVILIZATION') OR (step_order = 1 AND step_key = 'CLAIM_REGION') OR (step_order = 2 AND step_key = 'DEPLOY_NEXUS_NODE')),
    CONSTRAINT ck_saga_step_status CHECK (status IN ('PENDING', 'POTENTIALLY_APPLIED', 'IN_PROGRESS', 'SUCCEEDED', 'FAILED', 'COMPENSATING', 'COMPENSATED', 'COMPENSATION_FAILED')),
    CONSTRAINT ck_saga_compensation_status CHECK (compensation_status IS NULL OR compensation_status IN ('PENDING', 'POTENTIALLY_APPLIED', 'IN_PROGRESS', 'SUCCEEDED', 'FAILED', 'COMPENSATING', 'COMPENSATED', 'COMPENSATION_FAILED'))
);

CREATE INDEX idx_saga_steps_saga_order ON saga_step_executions (saga_id, step_order);

ALTER TABLE civilizations ADD COLUMN saga_id UUID UNIQUE REFERENCES saga_executions(id);
ALTER TABLE nexus_nodes ADD COLUMN saga_id UUID UNIQUE REFERENCES saga_executions(id);
