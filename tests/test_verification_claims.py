from __future__ import annotations

import importlib.util
import json
import pathlib
import tempfile
import unittest
from unittest import mock

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "validate_verification_claims", ROOT / "scripts" / "validate-verification-claims.py"
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)

COUNT_AUTHORITY = {
    "contract": "ONSURE_OMISSION_FAILURE_INJECTION_COUNTS_V1",
    "counts": {
        "design_process_lineage_cases": 28,
        "atomic_requirement_cases": 10,
        "automation_boundary_cases": 6,
        "verification_claim_cases": 15,
        "legacy_product_subrequirement_cases": 5,
        "workflow_surface_cases": 6,
        "critical_callpath_cases": 24,
        "legacy_mvp_acceptance_cases": 8,
        "final_product_requirement_cases": 8,
        "final_acceptance_cases": 8,
    },
    "total": 118,
}


def safe_status() -> dict:
    return {
        "assessment_source_ref": "main",
        "runtime_source_commit": None,
        "runtime_source_binding_state": "PENDING_ONE_SHOT_RECEIPT",
        "active_remediation_issues": [20],
        "validation_execution_policy": {
            "github_actions": "DISABLED_BY_USER",
            "workflow_files_allowed": False,
            "allowed_execution_modes": ["LOCAL_STATIC_ONE_SHOT", "LOCAL_FULL_GATE", "LOCAL_FINAL_STAGE"],
        },
        "historical_automation_evidence": {"retained_for_audit_only": True, "current_source_bound": False},
        "design_coverage": {"source_bound_receipt": "LOCAL_RECEIPT_REQUIRED"},
        "legacy_product_decomposition": {"source_bound_receipt": "LOCAL_RECEIPT_REQUIRED"},
        "legacy_mvp_acceptance": {
            "source_bound_receipt": "LOCAL_RECEIPT_REQUIRED",
            "state": "LEGACY_ALL_NOT_RUN",
        },
        "final_product_requirement_coverage": {"source_bound_receipt": "LOCAL_RECEIPT_REQUIRED"},
        "final_acceptance_coverage": {"source_bound_receipt": "LOCAL_RECEIPT_REQUIRED"},
        "workflow_surface_parity": {"source_bound_receipt": "LOCAL_RECEIPT_REQUIRED"},
        "critical_callpath_boundary": {"source_bound_receipt": "LOCAL_RECEIPT_REQUIRED"},
        "product_process_lineage": {"source_bound_receipt": "LOCAL_RECEIPT_REQUIRED"},
        "omission_failure_injection": {
            "authority": MODULE.COUNT_AUTHORITY,
            **COUNT_AUTHORITY["counts"],
            "all_registered_failure_injections": COUNT_AUTHORITY["total"],
            "current_head_execution": "LOCAL_EXECUTION_REQUIRED",
        },
        "approval_authority_boundary": {
            "request_path_override_allowed": False,
            "workspace_symlink_alias_allowed": False,
            "contained_worktree_authority_discovery": "UNIQUE_EXISTING_AUTHORITY_REQUIRED",
            "public_key_must_be_inside_authority_root": True,
            "registry_cross_process_lock": True,
            "receipt_verify_consume_binding": "IMMUTABLE_SNAPSHOT_RETURNED_AND_CONSUMED_LOCAL_EXECUTION_REQUIRED",
            "external_replay_anchor": "IMPLEMENTED_APPEND_ONLY_OUTSIDE_MUTABLE_AUTHORITY_ROOT_LOCAL_FULL_GATE_REQUIRED",
            "current_source_execution": "NOT_RUN",
        },
        "sandbox_attack_tests": {
            "state": "PARTIAL_1_OF_2_LOCAL_RECEIPT_REQUIRED",
            "verified_count": 1,
            "required_count": 2,
            "unverified": ["CROSS_TENANT_READ"],
        },
    }


def safe_sandbox() -> dict:
    return {
        "required_attack_fixtures": ["NETWORK_EGRESS", "CROSS_TENANT_READ"],
        "verified_attack_fixtures": ["NETWORK_EGRESS"],
        "unverified_attack_fixtures": ["CROSS_TENANT_READ"],
    }


def safe_mvp() -> dict:
    return {
        "assurance": {
            "mvp_full_chain": "NOT_RUN",
            "two_consecutive_real_repository_runs": "NOT_RUN",
            "final_claim_allowed": False,
        }
    }


LOCAL_GATE = """#!/usr/bin/env bash
python3 scripts/validate-product-subrequirements.py --self-test
python3 scripts/validate-mvp-acceptance-coverage.py --self-test
python3 scripts/validate-mvp-status-consistency.py
python3 scripts/validate-workflow-surface-parity.py --self-test
python3 scripts/validate-critical-callpaths.py --self-test
python3 scripts/validate-verification-claims.py
bash scripts/test-fixture-sandbox-boundary.sh
printf '%s' 'contracts/omission-failure-injection-counts.v1.json'
printf '%s' '"github_actions":"DISABLED"'
"""


class VerificationClaimFailureInjectionTest(unittest.TestCase):
    def run_case(self, status=None, sandbox=None, mvp=None, count_authority=None,
                 add_workflow=False, local_gate=LOCAL_GATE):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root / "status").mkdir(parents=True)
            (root / "contracts").mkdir(parents=True)
            (root / ".github/workflows").mkdir(parents=True)
            (root / "scripts").mkdir(parents=True)
            (root / "src/test/java/kr/co/oruda/onsure/platform").mkdir(parents=True)
            (root / "src/test/java/kr/co/oruda/onsure/assurance").mkdir(parents=True)
            (root / "src/main/java/kr/co/oruda/onsure/assurance").mkdir(parents=True)
            (root / "status/verification-status.v1.json").write_text(
                json.dumps(status or safe_status()), encoding="utf-8")
            (root / "status/mvp-acceptance-coverage.v1.json").write_text(
                json.dumps(mvp or safe_mvp()), encoding="utf-8")
            (root / "contracts/sandbox-boundary.v1.json").write_text(
                json.dumps(sandbox or safe_sandbox()), encoding="utf-8")
            (root / MODULE.COUNT_AUTHORITY).write_text(
                json.dumps(count_authority or COUNT_AUTHORITY), encoding="utf-8")
            if add_workflow:
                (root / ".github/workflows/build.yml").write_text("on: [push]\njobs: {}\n", encoding="utf-8")
            (root / "scripts/onsure-local-gate.sh").write_text(local_gate, encoding="utf-8")
            (root / "scripts/test-fixture-sandbox-boundary.sh").write_text(
                "echo ONSURE_FIXTURE_SANDBOX_BOUNDARY_PASS 12\n", encoding="utf-8")
            for name in (
                "AdversarialConcurrencyAndOutputTest.java", "ApprovalAuthorityPathsTest.java",
                "BoundedProcessRunnerTest.java", "GitWorkflowServiceTest.java"):
                (root / "src/test/java/kr/co/oruda/onsure/platform" / name).write_text(
                    f"class {name.removesuffix('.java')} {{}}\n", encoding="utf-8")
            (root / "src/main/java/kr/co/oruda/onsure/assurance/ApprovalReplayExternalAnchor.java").write_text(
                "previous_anchor_hash ledger_sha256 APPROVAL_REPLAY_EXTERNAL_ANCHOR_HEAD_MISMATCH\n",
                encoding="utf-8")
            (root / "src/test/java/kr/co/oruda/onsure/assurance/ApprovalReceiptVerifierTest.java").write_text(
                "externalAnchorRejectsRollbackToStaleLedgerSnapshot "
                "externalAnchorRejectsWholeLedgerRewriteEvenWithValidInternalHashes\n",
                encoding="utf-8")
            with mock.patch.object(MODULE, "ROOT", root):
                return MODULE.validate()

    def test_safe_nonfinal_local_claim_model_passes(self):
        self.assertEqual([], self.run_case())

    def test_historical_automation_cannot_claim_current_source_binding(self):
        status = safe_status(); status["historical_automation_evidence"]["current_source_bound"] = True
        self.assertIn("HISTORICAL_AUTOMATION_FALSELY_BOUND_TO_CURRENT_SOURCE", self.run_case(status=status))

    def test_committed_dynamic_receipt_cannot_replace_local_receipt(self):
        status = safe_status(); status["design_coverage"]["source_bound_receipt"] = "REMOTE_RUN_123"
        self.assertTrue(any(value.startswith("COMMITTED_DYNAMIC_RECEIPT_OVERCLAIM") for value in self.run_case(status=status)))

    def test_closed_umbrella_issue_cannot_remain_active(self):
        status = safe_status(); status["active_remediation_issues"] = [20, 23]
        self.assertIn("CLOSED_REMEDIATION_ISSUE_STILL_ACTIVE:23", self.run_case(status=status))

    def test_sandbox_partial_scope_cannot_be_marked_pass(self):
        status = safe_status(); status["sandbox_attack_tests"]["state"] = "PASS_LOCAL_SELF_VALIDATION_NONFINAL"
        self.assertIn("SANDBOX_PARTIAL_SCOPE_OVERCLAIMED_AS_PASS", self.run_case(status=status))

    def test_sandbox_attack_partition_mismatch_is_detected(self):
        sandbox = safe_sandbox(); sandbox["unverified_attack_fixtures"] = []
        self.assertIn("SANDBOX_ATTACK_PARTITION_MISMATCH", self.run_case(sandbox=sandbox))

    def test_github_actions_policy_must_be_disabled(self):
        status = safe_status(); status["validation_execution_policy"]["github_actions"] = "ENABLED"
        self.assertIn("GITHUB_ACTIONS_POLICY_NOT_DISABLED", self.run_case(status=status))

    def test_any_workflow_file_is_detected(self):
        self.assertIn("GITHUB_ACTIONS_WORKFLOW_FORBIDDEN:build.yml", self.run_case(add_workflow=True))

    def test_local_claim_gate_must_include_critical_callpaths(self):
        local_gate = LOCAL_GATE.replace("python3 scripts/validate-critical-callpaths.py --self-test\n", "")
        self.assertTrue(any("LOCAL_VALIDATION_GATE_CONTROL_MISSING" in value for value in self.run_case(local_gate=local_gate)))

    def test_failure_injection_total_cannot_stay_at_old_baseline(self):
        status = safe_status(); status["omission_failure_injection"]["all_registered_failure_injections"] = 106
        self.assertIn("FAILURE_INJECTION_TOTAL_STALE", self.run_case(status=status))

    def test_failure_injection_count_contract_must_balance(self):
        authority = json.loads(json.dumps(COUNT_AUTHORITY)); authority["total"] = 108
        self.assertIn("FAILURE_COUNT_AUTHORITY_TOTAL_MISMATCH", self.run_case(count_authority=authority))

    def test_worktree_authority_discovery_cannot_be_removed(self):
        status = safe_status(); status["approval_authority_boundary"].pop("contained_worktree_authority_discovery")
        self.assertIn("APPROVAL_AUTHORITY_WORKTREE_DISCOVERY_MISSING", self.run_case(status=status))

    def test_invalid_external_replay_anchor_state_is_rejected(self):
        status = safe_status(); status["approval_authority_boundary"]["external_replay_anchor"] = "PASS"
        self.assertIn("APPROVAL_REPLAY_EXTERNAL_ANCHOR_STATE_INVALID", self.run_case(status=status))

    def test_mvp_acceptance_steps_cannot_be_claimed_complete(self):
        status = safe_status(); status["legacy_mvp_acceptance"]["state"] = "PASS"
        self.assertIn("MVP_ACCEPTANCE_STATE_OVERCLAIMED", self.run_case(status=status))

    def test_two_consecutive_real_repository_runs_cannot_be_invented(self):
        mvp = safe_mvp(); mvp["assurance"]["two_consecutive_real_repository_runs"] = "PASS"
        self.assertIn("MVP_ACCEPTANCE_REGISTER_OVERCLAIMED", self.run_case(mvp=mvp))

    def test_approval_snapshot_state_must_use_canonical_vocabulary(self):
        status = safe_status(); status["approval_authority_boundary"]["receipt_verify_consume_binding"] = "OLD_STATE"
        self.assertIn("APPROVAL_RECEIPT_SNAPSHOT_BINDING_MISSING", self.run_case(status=status))


if __name__ == "__main__":
    unittest.main()
