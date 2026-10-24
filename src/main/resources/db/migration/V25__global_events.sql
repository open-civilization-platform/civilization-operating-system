CREATE TABLE global_events (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(255) NOT NULL,
    affected_civ_ids TEXT,
    severity DOUBLE PRECISION NOT NULL,
    description TEXT,
    started_at TIMESTAMP,
    ends_at TIMESTAMP,
    active BOOLEAN NOT NULL,
    ticks_remaining INTEGER
);

CREATE INDEX idx_global_events_active ON global_events(active);
CREATE INDEX idx_global_events_ends_at ON global_events(ends_at);