import copy
import importlib.util
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "seal_repeatability", ROOT / "scripts" / "seal_universal_validation_repeatability.py")
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class SealUniversalValidationRepeatabilityTest(unittest.TestCase):
    def observation(self, profile: str) -> dict:
        return {
            "profile_id": profile, "assurance_class": "SELF_VALIDATION_NONFINAL",
            "decision": "PASS_NONFINAL", "phase_outcomes": {"a": "PASS_NONFINAL"},
            "verification_group_outcomes": {"b": "PASS_NONFINAL"}, "not_run_reasons": {},
            "source_digest": "a" * 64, "snapshot_digest": "a" * 64,
            "source_mutation_detected": False, "environment_sha256": "b" * 64,
            "result_sha256": ("c" if profile.endswith("1") else "d") * 64,
            "finalization_sha256": ("e" if profile.endswith("1") else "f") * 64,
            "verified_pass_step_count": 1,
            "steps": [{
                "step_id": "build", "phase": "a", "kind": "BUILD", "required": True,
                "outcome": "PASS_NONFINAL", "exit_code": 0, "output_sha256": "1" * 64,
                "environment_sha256": "b" * 64, "output_truncated": False,
                "reason": "EXECUTED", "started_at": "one", "completed_at": "two",
            }],
            "production_authority": False, "final_claim_allowed": False,
        }

    def test_accepts_same_semantics_with_different_logs_and_profiles(self):
        first, second = self.observation("repeat-1"), self.observation("repeat-2")
        second["steps"][0]["output_sha256"] = "9" * 64
        body = MODULE.build_receipt([first, second], "1" * 40)
        self.assertEqual("PASS_NONFINAL", body["decision"])
        self.assertEqual(2, body["run_count"])

    def test_rejects_semantic_drift(self):
        first, second = self.observation("repeat-1"), self.observation("repeat-2")
        second = copy.deepcopy(second)
        second["steps"][0]["outcome"] = "FAIL"
        with self.assertRaisesRegex(ValueError, "REPEAT_RUN_NOT_PASS|REPEAT_RUN_SEMANTIC_DRIFT"):
            MODULE.build_receipt([first, second], "1" * 40)


if __name__ == "__main__":
    unittest.main()
