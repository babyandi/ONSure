CREATE TABLE assurance_event (
    event_id VARCHAR(128) PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    evidence_sha256 CHAR(64) NOT NULL CHECK (evidence_sha256 ~ '^[0-9a-f]{64}$'),
    source_commit_sha256 CHAR(64) CHECK (
        source_commit_sha256 IS NULL OR source_commit_sha256 ~ '^[0-9a-f]{64}$'
    ),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX assurance_event_observed_at_idx ON assurance_event (observed_at);
