CREATE TABLE treaties (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    proposer_civ_id BIGINT NOT NULL,
    invited_civ_ids TEXT,
    signatory_civ_ids TEXT,
    status VARCHAR(255) NOT NULL,
    proposed_at TIMESTAMP,
    expires_at TIMESTAMP
);

CREATE INDEX idx_treaties_proposer ON treaties(proposer_civ_id);
CREATE INDEX idx_treaties_status ON treaties(status);