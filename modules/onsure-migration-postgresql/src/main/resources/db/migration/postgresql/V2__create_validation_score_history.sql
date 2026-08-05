CREATE TABLE validation_run_score (
    run_id VARCHAR(160) PRIMARY KEY,
    project_id VARCHAR(128) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    source_sha256 CHAR(64) NOT NULL CHECK (source_sha256 ~ '^[0-9a-f]{64}$'),
    receipt_sha256 CHAR(64) NOT NULL CHECK (receipt_sha256 ~ '^[0-9a-f]{64}$'),
    validation_outcome VARCHAR(24) NOT NULL CHECK (
        validation_outcome IN ('PASS_NONFINAL', 'FAIL', 'BLOCKED', 'NOT_RUN', 'INCONCLUSIVE')
    ),
    earned_points NUMERIC(5,2) NOT NULL CHECK (earned_points BETWEEN 0 AND 100),
    max_points NUMERIC(5,2) NOT NULL CHECK (max_points = 100),
    scorecard_json JSONB NOT NULL,
    previous_run_id VARCHAR(160) REFERENCES validation_run_score(run_id),
    observed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (target_id, receipt_sha256)
);

CREATE INDEX validation_run_score_target_time_idx
    ON validation_run_score (target_id, observed_at DESC);

CREATE TABLE validation_score_node (
    run_id VARCHAR(160) NOT NULL REFERENCES validation_run_score(run_id) ON DELETE CASCADE,
    node_type VARCHAR(16) NOT NULL CHECK (node_type IN ('DOMAIN', 'PHASE', 'GROUP', 'AREA', 'STEP')),
    node_id VARCHAR(200) NOT NULL,
    parent_node_id VARCHAR(200),
    outcome VARCHAR(24) NOT NULL CHECK (
        outcome IN ('PASS_NONFINAL', 'FAIL', 'BLOCKED', 'NOT_RUN', 'INCONCLUSIVE')
    ),
    possible_points NUMERIC(5,2) NOT NULL CHECK (possible_points BETWEEN 0 AND 100),
    earned_points NUMERIC(5,2) NOT NULL CHECK (earned_points BETWEEN 0 AND possible_points),
    diagnosis VARCHAR(4000) NOT NULL,
    improvement_guide VARCHAR(4000) NOT NULL,
    output_sha256 CHAR(64) CHECK (output_sha256 IS NULL OR output_sha256 ~ '^[0-9a-f]{64}$'),
    environment_sha256 CHAR(64) CHECK (environment_sha256 IS NULL OR environment_sha256 ~ '^[0-9a-f]{64}$'),
    PRIMARY KEY (run_id, node_type, node_id)
);

CREATE INDEX validation_score_node_outcome_idx
    ON validation_score_node (outcome, node_type);

CREATE TABLE validation_run_comparison (
    comparison_id VARCHAR(160) PRIMARY KEY,
    target_id VARCHAR(128) NOT NULL,
    baseline_run_id VARCHAR(160) NOT NULL REFERENCES validation_run_score(run_id),
    current_run_id VARCHAR(160) NOT NULL REFERENCES validation_run_score(run_id),
    total_delta_points NUMERIC(6,2) NOT NULL CHECK (total_delta_points BETWEEN -100 AND 100),
    comparison_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (baseline_run_id <> current_run_id),
    UNIQUE (baseline_run_id, current_run_id)
);

CREATE INDEX validation_run_comparison_target_idx
    ON validation_run_comparison (target_id, created_at DESC);
