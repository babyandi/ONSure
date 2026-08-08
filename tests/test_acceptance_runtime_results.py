from __future__ import annotations

import copy
import hashlib
import importlib.util
import json
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "acceptance_runtime", ROOT / "scripts/validate-acceptance-runtime-results.py"
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)

H = "a" * 64
C = "b" * 40


def execution(case: dict, case_id: str | None = None) -> dict:
    value = {
        "case_id": case_id or case["case_id"],
        "source_sha256": case["source_sha256"],
        "source_commit": C,
        "execution_id": "run-1",
        "executor": case["executor"],
        "started_at": "2026-07-29T00:00:00Z",
        "finished_at": "2026-07-29T00:00:01Z",
        "exit_code": 0,
        "result": "PASS",
        "oracle_result": "PASS",
        "negative_oracle_result": "PASS",
        "input_sha256": H,
        "output_sha256": H,
        "environment_sha256": H,
        "evidence_sha256": [H],
        "parent_receipt_sha256": H,
        "replay_nonce": "nonce-1",
    }
    value["receipt_sha256"] = hashlib.sha256(
        json.dumps(value, sort_keys=True, separators=(",", ":")).encode()
    ).hexdigest()
    return value


class AcceptanceRuntimeResultsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.cases = [
            {"case_id": "FIN-ACC-X-01", "source_sha256": H, "executor": "EXEC"},
            {"case_id": "FIN-ACC-X-02", "source_sha256": "c" * 64, "executor": "EXEC"},
        ]
        self.status = {
            "contract": "ONSURE_ACCEPTANCE_RUNTIME_RESULTS_V1",
            "case_contract": "contracts/final-acceptance-execution.v1.json",
            "source_tree_sha256": "NOT_RUN",
            "runtime_execution": "NOT_RUN",
            "executions": [],
            "independent_verification": "NOT_RUN",
            "human_approval": "NOT_RUN",
            "final_claim_allowed": False,
        }

    def test_not_run_is_valid_but_nonfinal(self) -> None:
        self.assertEqual([], MODULE.validate(ROOT, self.status, self.cases))

    def test_partial_source_bound_execution_is_valid(self) -> None:
        candidate = copy.deepcopy(self.status)
        candidate["runtime_execution"] = "PARTIAL"
        candidate["executions"] = [execution(self.cases[0])]
        self.assertEqual([], MODULE.validate(ROOT, candidate, self.cases))

    def test_fake_pass_and_replay_are_blocked(self) -> None:
        first = execution(self.cases[0])
        first["exit_code"] = 7
        second = execution(self.cases[1])
        candidate = copy.deepcopy(self.status)
        candidate["runtime_execution"] = "COMPLETE"
        candidate["executions"] = [first, second]
        errors = MODULE.validate(ROOT, candidate, self.cases)
        self.assertTrue(any("PASS_WITHOUT_ORACLES" in item for item in errors))
        self.assertTrue(any("EXECUTION_ID_REPLAY" in item for item in errors))
        self.assertTrue(any("NONCE_REPLAY" in item for item in errors))
        self.assertTrue(any("RECEIPT_MISMATCH" in item for item in errors))

    def test_complete_requires_every_case_and_all_pass(self) -> None:
        candidate = copy.deepcopy(self.status)
        candidate["runtime_execution"] = "COMPLETE"
        candidate["executions"] = [execution(self.cases[0])]
        errors = MODULE.validate(ROOT, candidate, self.cases)
        self.assertIn("RUNTIME_COMPLETE_CASE_SET_MISMATCH", errors)


if __name__ == "__main__":
    unittest.main()
