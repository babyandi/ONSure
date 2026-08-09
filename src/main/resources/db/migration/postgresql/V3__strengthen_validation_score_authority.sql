ALTER TABLE validation_run_score
    ADD COLUMN source_commit_sha VARCHAR(64),
    ADD COLUMN profile_id VARCHAR(128),
    ADD COLUMN profile_sha256 CHAR(64),
    ADD COLUMN environment_sha256 CHAR(64),
    ADD COLUMN toolchain_sha256 CHAR(64),
    ADD COLUMN input_sha256 CHAR(64),
    ADD COLUMN output_sha256 CHAR(64),
    ADD COLUMN evidence_manifest_sha256 CHAR(64),
    ADD COLUMN report_sha256 CHAR(64),
    ADD COLUMN scorecard_sha256 CHAR(64),
    ADD COLUMN run_status VARCHAR(24),
    ADD COLUMN run_record_json JSONB,
    ADD COLUMN improvement_baseline_run_id VARCHAR(160),
    ADD COLUMN patch_apply_receipt_sha256 CHAR(64),
    ADD COLUMN improvement_proof_sha256 CHAR(64);

ALTER TABLE validation_run_score
    ADD CONSTRAINT validation_run_score_project_target_run_key
        UNIQUE (project_id, target_id, run_id);

ALTER TABLE validation_run_score
    DROP CONSTRAINT validation_run_score_previous_run_id_fkey,
    ADD CONSTRAINT validation_run_score_previous_run_scope_fk FOREIGN KEY (
        project_id, target_id, previous_run_id)
        REFERENCES validation_run_score(project_id, target_id, run_id);

ALTER TABLE validation_run_score
    ADD CONSTRAINT validation_run_score_source_commit_sha_check CHECK (
        source_commit_sha IS NULL OR source_commit_sha ~ '^[0-9a-f]{40,64}$'),
    ADD CONSTRAINT validation_run_score_profile_sha256_check CHECK (
        profile_sha256 IS NULL OR profile_sha256 ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT validation_run_score_environment_sha256_check CHECK (
        environment_sha256 IS NULL OR environment_sha256 ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT validation_run_score_toolchain_sha256_check CHECK (
        toolchain_sha256 IS NULL OR toolchain_sha256 ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT validation_run_score_input_sha256_check CHECK (
        input_sha256 IS NULL OR input_sha256 ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT validation_run_score_output_sha256_check CHECK (
        output_sha256 IS NULL OR output_sha256 ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT validation_run_score_evidence_manifest_sha256_check CHECK (
        evidence_manifest_sha256 IS NULL OR evidence_manifest_sha256 ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT validation_run_score_report_sha256_check CHECK (
        report_sha256 IS NULL OR report_sha256 ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT validation_run_score_scorecard_sha256_check CHECK (
        scorecard_sha256 IS NULL OR scorecard_sha256 ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT validation_run_score_run_status_check CHECK (
        run_status IS NULL OR run_status IN ('PASS_NONFINAL', 'FAIL', 'BLOCKED', 'NOT_RUN', 'INCONCLUSIVE')),
    ADD CONSTRAINT validation_run_score_run_record_json_check CHECK (
        run_record_json IS NULL OR jsonb_typeof(run_record_json) = 'object'),
    ADD CONSTRAINT validation_run_score_patch_receipt_sha256_check CHECK (
        patch_apply_receipt_sha256 IS NULL OR patch_apply_receipt_sha256 ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT validation_run_score_improvement_proof_sha256_check CHECK (
        improvement_proof_sha256 IS NULL OR improvement_proof_sha256 ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT validation_run_score_improvement_baseline_scope_fk FOREIGN KEY (
        project_id, target_id, improvement_baseline_run_id)
        REFERENCES validation_run_score(project_id, target_id, run_id),
    ADD CONSTRAINT validation_run_score_improvement_lineage_complete_check CHECK (
        (improvement_baseline_run_id IS NULL AND patch_apply_receipt_sha256 IS NULL
            AND improvement_proof_sha256 IS NULL)
        OR
        (improvement_baseline_run_id IS NOT NULL AND improvement_baseline_run_id <> run_id
            AND patch_apply_receipt_sha256 IS NOT NULL AND improvement_proof_sha256 IS NOT NULL));

ALTER TABLE validation_run_score
    DROP CONSTRAINT validation_run_score_target_id_receipt_sha256_key;

ALTER TABLE validation_run_score
    ADD CONSTRAINT validation_run_score_project_target_receipt_key
        UNIQUE (project_id, target_id, receipt_sha256);

CREATE INDEX validation_run_score_project_target_time_idx
    ON validation_run_score (project_id, target_id, observed_at DESC);

ALTER TABLE validation_score_node
    ADD COLUMN node_json JSONB;

ALTER TABLE validation_score_node
    ADD CONSTRAINT validation_score_node_json_check CHECK (
        node_json IS NULL OR jsonb_typeof(node_json) = 'object');

CREATE TABLE validation_run_finding (
    run_id VARCHAR(160) NOT NULL REFERENCES validation_run_score(run_id) ON DELETE CASCADE,
    finding_id VARCHAR(200) NOT NULL,
    severity VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    diagnosis VARCHAR(4000) NOT NULL,
    improvement_guide VARCHAR(4000) NOT NULL,
    finding_json JSONB NOT NULL CHECK (jsonb_typeof(finding_json) = 'object'),
    PRIMARY KEY (run_id, finding_id)
);

CREATE INDEX validation_run_finding_severity_idx
    ON validation_run_finding (severity, status);

ALTER TABLE validation_run_comparison
    ADD COLUMN project_id VARCHAR(128),
    ADD COLUMN comparison_type VARCHAR(32),
    ADD COLUMN comparison_state VARCHAR(32),
    ADD COLUMN comparison_reason VARCHAR(256),
    ADD COLUMN baseline_source_sha256 CHAR(64),
    ADD COLUMN current_source_sha256 CHAR(64),
    ADD COLUMN baseline_environment_sha256 CHAR(64),
    ADD COLUMN current_environment_sha256 CHAR(64),
    ADD COLUMN baseline_profile_sha256 CHAR(64),
    ADD COLUMN current_profile_sha256 CHAR(64),
    ADD COLUMN baseline_toolchain_sha256 CHAR(64),
    ADD COLUMN current_toolchain_sha256 CHAR(64);

UPDATE validation_run_comparison comparison
SET project_id = current_run.project_id,
    comparison_type = 'LEGACY_UNVERIFIED',
    comparison_state = 'LEGACY_UNVERIFIED',
    comparison_reason = 'LEGACY_COMPARISON_CONTEXT_NOT_RECORDED',
    baseline_source_sha256 = baseline_run.source_sha256,
    current_source_sha256 = current_run.source_sha256
FROM validation_run_score current_run, validation_run_score baseline_run
WHERE current_run.run_id = comparison.current_run_id
  AND baseline_run.run_id = comparison.baseline_run_id;

ALTER TABLE validation_run_comparison
    ALTER COLUMN project_id SET NOT NULL,
    ALTER COLUMN comparison_type SET NOT NULL,
    ALTER COLUMN comparison_state SET NOT NULL,
    ALTER COLUMN comparison_reason SET NOT NULL,
    DROP CONSTRAINT validation_run_comparison_baseline_run_id_fkey,
    DROP CONSTRAINT validation_run_comparison_current_run_id_fkey,
    ADD CONSTRAINT validation_run_comparison_baseline_scope_fk FOREIGN KEY (
        project_id, target_id, baseline_run_id)
        REFERENCES validation_run_score(project_id, target_id, run_id),
    ADD CONSTRAINT validation_run_comparison_current_scope_fk FOREIGN KEY (
        project_id, target_id, current_run_id)
        REFERENCES validation_run_score(project_id, target_id, run_id),
    ADD CONSTRAINT validation_run_comparison_state_check CHECK (
        comparison_state IN ('COMPARABLE', 'NOT_COMPARABLE', 'LEGACY_UNVERIFIED')),
    ADD CONSTRAINT validation_run_comparison_type_check CHECK (
        comparison_type IN ('REPEATABILITY', 'IMPROVEMENT', 'NOT_COMPARABLE', 'LEGACY_UNVERIFIED')),
    ADD CONSTRAINT validation_run_comparison_context_digest_check CHECK (
        (baseline_source_sha256 IS NULL OR baseline_source_sha256 ~ '^[0-9a-f]{64}$') AND
        (current_source_sha256 IS NULL OR current_source_sha256 ~ '^[0-9a-f]{64}$') AND
        (baseline_environment_sha256 IS NULL OR baseline_environment_sha256 ~ '^[0-9a-f]{64}$') AND
        (current_environment_sha256 IS NULL OR current_environment_sha256 ~ '^[0-9a-f]{64}$') AND
        (baseline_profile_sha256 IS NULL OR baseline_profile_sha256 ~ '^[0-9a-f]{64}$') AND
        (current_profile_sha256 IS NULL OR current_profile_sha256 ~ '^[0-9a-f]{64}$') AND
        (baseline_toolchain_sha256 IS NULL OR baseline_toolchain_sha256 ~ '^[0-9a-f]{64}$') AND
        (current_toolchain_sha256 IS NULL OR current_toolchain_sha256 ~ '^[0-9a-f]{64}$'));

CREATE INDEX validation_run_comparison_project_target_idx
    ON validation_run_comparison (project_id, target_id, created_at DESC);
