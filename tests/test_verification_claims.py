from __future__ import annotations

import importlib.util
import json
import pathlib
import tempfile
import unittest
from unittest import mock

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("validate_verification_claims", ROOT / "scripts/validate-verification-claims.py")
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)

COUNTS = {
    "design_process_lineage_cases":28,"atomic_requirement_cases":10,
    "automation_boundary_cases":6,"verification_claim_cases":15,
    "legacy_product_subrequirement_cases":5,"workflow_surface_cases":6,
    "critical_callpath_cases":24,"legacy_mvp_acceptance_cases":8,
    "final_product_requirement_cases":8,"final_acceptance_cases":8,
}
COUNT_AUTHORITY = {"contract":"ONSURE_OMISSION_FAILURE_INJECTION_COUNTS_V1","counts":COUNTS,"total":118}


def safe_status() -> dict:
    return {
        "assessment_source_ref":"main","runtime_source_commit":None,
        "runtime_source_binding_state":"PENDING_ONE_SHOT_RECEIPT","active_remediation_issues":[20],
        "validation_execution_policy":{"github_actions":"DISABLED_BY_USER","workflow_files_allowed":False,"allowed_execution_modes":["LOCAL_STATIC_ONE_SHOT","LOCAL_FULL_GATE","LOCAL_FINAL_STAGE"]},
        "historical_automation_evidence":{"retained_for_audit_only":True,"current_source_bound":False},
        "design_coverage":{"source_bound_receipt":"LOCAL_RECEIPT_REQUIRED"},
        "legacy_product_decomposition":{"source_bound_receipt":"LOCAL_RECEIPT_REQUIRED","state":"LEGACY_NONAUTHORITATIVE_FOR_FINAL_PRODUCT"},
        "legacy_mvp_acceptance":{"source_bound_receipt":"LOCAL_RECEIPT_REQUIRED","state":"LEGACY_ALL_NOT_RUN"},
        "final_product_requirement_coverage":{"source_bound_receipt":"LOCAL_RECEIPT_REQUIRED","state":"NOT_RUN_ALL_22_FINAL_REQUIREMENTS"},
        "final_acceptance_coverage":{"source_bound_receipt":"LOCAL_RECEIPT_REQUIRED","state":"NOT_RUN_ALL_61_FINAL_ACCEPTANCE_CRITERIA"},
        "workflow_surface_parity":{"source_bound_receipt":"LOCAL_RECEIPT_REQUIRED"},
        "critical_callpath_boundary":{"source_bound_receipt":"LOCAL_RECEIPT_REQUIRED"},
        "product_process_lineage":{"source_bound_receipt":"LOCAL_RECEIPT_REQUIRED"},
        "omission_failure_injection":{"authority":MODULE.COUNT_AUTHORITY,**COUNTS,"all_registered_failure_injections":118,"current_head_execution":"LOCAL_EXECUTION_REQUIRED"},
        "approval_authority_boundary":{"request_path_override_allowed":False,"workspace_symlink_alias_allowed":False,"contained_worktree_authority_discovery":"UNIQUE_EXISTING_AUTHORITY_REQUIRED","public_key_must_be_inside_authority_root":True,"registry_cross_process_lock":True,"receipt_verify_consume_binding":"IMMUTABLE_SNAPSHOT_RETURNED_AND_CONSUMED_LOCAL_EXECUTION_REQUIRED","external_replay_anchor":"NOT_IMPLEMENTED","current_source_execution":"NOT_RUN"},
        "sandbox_attack_tests":{"state":"PARTIAL_1_OF_2_LOCAL_RECEIPT_REQUIRED","verified_count":1,"required_count":2,"unverified":["CROSS_TENANT_READ"]},
    }


def safe_sandbox() -> dict:
    return {"required_attack_fixtures":["NETWORK_EGRESS","CROSS_TENANT_READ"],"verified_attack_fixtures":["NETWORK_EGRESS"],"unverified_attack_fixtures":["CROSS_TENANT_READ"]}


def safe_legacy_authority() -> dict:
    return {"may_satisfy_final_requirement":False,"may_satisfy_final_acceptance":False}


def safe_legacy_mvp() -> dict:
    return {"authority_state":"LEGACY_MVP_NONAUTHORITATIVE_FOR_FINAL_PRODUCT"}


def safe_final_requirement() -> dict:
    return {"assurance":{"final_product_full_chain":"NOT_RUN","final_claim_allowed":False}}


def safe_final_acceptance() -> dict:
    return {"assurance":{"financial_scenarios_3_sets":"NOT_RUN","external_ai_product_types_5":"NOT_RUN","white_gray_black_box":"NOT_RUN","same_identity_repeat_2":"NOT_RUN","independent_otester_two_clean":"NOT_RUN","independent_oaudit_two_clean":"NOT_RUN","human_approval":"NOT_RUN","final_claim_allowed":False}}


LOCAL_GATE = """#!/usr/bin/env bash
python3 scripts/validate-legacy-product-subrequirements.py --self-test
python3 scripts/validate-mvp-acceptance-coverage.py --self-test
python3 scripts/validate-final-product-requirements.py --self-test
python3 scripts/validate-final-acceptance-coverage.py --self-test
python3 scripts/validate-workflow-surface-parity.py --self-test
python3 scripts/validate-critical-callpaths.py --self-test
printf '%s' 'contracts/omission-failure-injection-counts.v1.json'
printf '%s' '"github_actions":"DISABLED"'
"""


class VerificationClaimFailureInjectionTest(unittest.TestCase):
    def run_case(self,status=None,sandbox=None,legacy_authority=None,legacy_mvp=None,final_requirement=None,final_acceptance=None,count_authority=None,add_workflow=False,local_gate=LOCAL_GATE):
        with tempfile.TemporaryDirectory() as directory:
            root=pathlib.Path(directory)
            for relative in ("status","contracts",".github/workflows","scripts","src/test/java/io/onsure/platform"):
                (root/relative).mkdir(parents=True,exist_ok=True)
            files={
                "status/verification-status.v1.json":status or safe_status(),
                "contracts/sandbox-boundary.v1.json":sandbox or safe_sandbox(),
                "contracts/legacy-product-subrequirement-authority.v1.json":legacy_authority or safe_legacy_authority(),
                "status/mvp-acceptance-coverage.v1.json":legacy_mvp or safe_legacy_mvp(),
                "status/final-product-requirement-coverage.v1.json":final_requirement or safe_final_requirement(),
                "status/final-acceptance-coverage.v1.json":final_acceptance or safe_final_acceptance(),
                MODULE.COUNT_AUTHORITY:count_authority or COUNT_AUTHORITY,
            }
            for relative,value in files.items():
                path=root/relative;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value),encoding="utf-8")
            if add_workflow:(root/".github/workflows/build.yml").write_text("on: [push]\njobs: {}\n",encoding="utf-8")
            (root/"scripts/onsure-local-gate.sh").write_text(local_gate,encoding="utf-8")
            for name in ("AdversarialConcurrencyAndOutputTest.java","ApprovalAuthorityPathsTest.java","BoundedProcessRunnerTest.java","GitWorkflowServiceTest.java"):
                (root/"src/test/java/io/onsure/platform"/name).write_text(f"class {name.removesuffix('.java')} {{}}\n",encoding="utf-8")
            with mock.patch.object(MODULE,"ROOT",root):return MODULE.validate()

    def test_safe_nonfinal_model_passes(self): self.assertEqual([],self.run_case())
    def test_historical_automation_cannot_bind_current_source(self):
        s=safe_status();s["historical_automation_evidence"]["current_source_bound"]=True;self.assertIn("HISTORICAL_AUTOMATION_SCOPE_INVALID",self.run_case(status=s))
    def test_dynamic_receipt_cannot_be_committed(self):
        s=safe_status();s["final_product_requirement_coverage"]["source_bound_receipt"]="REMOTE";self.assertTrue(any(x.startswith("COMMITTED_DYNAMIC_RECEIPT_OVERCLAIM") for x in self.run_case(status=s)))
    def test_closed_issue_cannot_remain_active(self):
        s=safe_status();s["active_remediation_issues"]=[20,23];self.assertIn("CLOSED_REMEDIATION_ISSUE_STILL_ACTIVE:23",self.run_case(status=s))
    def test_sandbox_partial_cannot_be_pass(self):
        s=safe_status();s["sandbox_attack_tests"]["state"]="PASS";self.assertIn("SANDBOX_PARTIAL_SCOPE_OVERCLAIMED_AS_PASS",self.run_case(status=s))
    def test_sandbox_partition_mismatch(self):
        b=safe_sandbox();b["unverified_attack_fixtures"]=[];self.assertIn("SANDBOX_ATTACK_PARTITION_MISMATCH",self.run_case(sandbox=b))
    def test_actions_policy_must_be_disabled(self):
        s=safe_status();s["validation_execution_policy"]["github_actions"]="ENABLED";self.assertIn("GITHUB_ACTIONS_POLICY_NOT_DISABLED",self.run_case(status=s))
    def test_workflow_file_is_forbidden(self): self.assertIn("GITHUB_ACTIONS_WORKFLOW_FORBIDDEN:build.yml",self.run_case(add_workflow=True))
    def test_local_gate_must_include_final_acceptance(self):
        gate=LOCAL_GATE.replace("python3 scripts/validate-final-acceptance-coverage.py --self-test\n","");self.assertTrue(any("LOCAL_VALIDATION_GATE_CONTROL_MISSING" in x for x in self.run_case(local_gate=gate)))
    def test_failure_total_cannot_be_stale(self):
        s=safe_status();s["omission_failure_injection"]["all_registered_failure_injections"]=117;self.assertIn("FAILURE_INJECTION_AUTHORITY_MISMATCH",self.run_case(status=s))
    def test_failure_count_contract_must_balance(self):
        a=json.loads(json.dumps(COUNT_AUTHORITY));a["total"]=119;self.assertIn("FAILURE_COUNT_AUTHORITY_INVALID",self.run_case(count_authority=a))
    def test_legacy_requirement_cannot_satisfy_final(self):
        a=safe_legacy_authority();a["may_satisfy_final_requirement"]=True;self.assertIn("LEGACY_REQUIREMENT_AUTHORITY_ESCALATION",self.run_case(legacy_authority=a))
    def test_final_requirements_cannot_be_claimed_complete(self):
        s=safe_status();s["final_product_requirement_coverage"]["state"]="PASS";self.assertIn("FINAL_PRODUCT_REQUIREMENT_STATE_OVERCLAIMED",self.run_case(status=s))
    def test_final_acceptance_cannot_be_claimed_complete(self):
        s=safe_status();s["final_acceptance_coverage"]["state"]="PASS";self.assertIn("FINAL_ACCEPTANCE_STATE_OVERCLAIMED",self.run_case(status=s))
    def test_financial_scenarios_cannot_be_invented(self):
        a=safe_final_acceptance();a["assurance"]["financial_scenarios_3_sets"]="PASS";self.assertIn("FINAL_ACCEPTANCE_ASSURANCE_OVERCLAIMED:financial_scenarios_3_sets",self.run_case(final_acceptance=a))
    def test_approval_snapshot_state_is_canonical(self):
        s=safe_status();s["approval_authority_boundary"]["receipt_verify_consume_binding"]="OLD";self.assertIn("APPROVAL_RECEIPT_SNAPSHOT_BINDING_MISSING",self.run_case(status=s))


if __name__=="__main__":unittest.main()
