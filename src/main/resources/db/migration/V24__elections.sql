-- Election lifecycle tables used by Election and ElectionVote.
CREATE TABLE elections (
    id BIGSERIAL PRIMARY KEY,
    civilization_id BIGINT NOT NULL REFERENCES civilizations(id),
    status VARCHAR(255) NOT NULL,
    started_at TIMESTAMP,
    ends_at TIMESTAMP,
    winner_name VARCHAR(255),
    ticks_remaining INTEGER
);

CREATE INDEX idx_elections_civilization ON elections(civilization_id);

CREATE TABLE election_votes (
    id BIGSERIAL PRIMARY KEY,
    election_id BIGINT NOT NULL REFERENCES elections(id),
    voter_name VARCHAR(255),
    candidate_name VARCHAR(255),
    voted_at TIMESTAMP
);

CREATE INDEX idx_election_votes_election ON election_votes(election_id);