import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "seal_observation", ROOT / "scripts" / "seal_universal_validation_observation.py")
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class SealUniversalValidationObservationTest(unittest.TestCase):
    def make_run(self, root: Path) -> Path:
        logs = root / "step-logs"
        logs.mkdir()
        step_log = logs / "environment.preflight.log"
        final_log = logs / "evidence.finalize.log"
        step_log.write_text("missing renderer", encoding="utf-8")
        final_log.write_text("verified_pass_step_count=0", encoding="utf-8")
        sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
        result = {
            "contract": "ONSURE_UNIVERSAL_VALIDATION_RUN_V1", "profile_id": "external",
            "assurance_class": "SELF_VALIDATION_NONFINAL", "overall_outcome": "BLOCKED",
            "phase_outcomes": {key: "NOT_RUN" for key in MODULE.PHASES},
            "verification_group_outcomes": {key: "NOT_RUN" for key in MODULE.GROUPS},
            "not_run_reasons": {}, "source_mutation_detected": False,
            "source_digest": "a" * 64, "snapshot_digest": "a" * 64,
            "final_claim_allowed": False, "environment_evidence": {"sha256": "b" * 64},
            "started_at": "2026-01-01T00:00:00Z", "completed_at": "2026-01-01T00:00:02Z",
            "steps": [{
                "stepId": "environment.preflight", "phase": "STRUCTURE_STATIC",
                "kind": "ENVIRONMENT_PREFLIGHT", "required": True, "outcome": "BLOCKED",
                "exitCode": -1, "outputSha256": sha(step_log), "environmentSha256": "b" * 64,
                "logFile": str(step_log), "outputTruncated": False, "reason": "MISSING",
                "startedAt": "2026-01-01T00:00:00Z", "completedAt": "2026-01-01T00:00:01Z",
            }],
            "final_evidence_integrity": {
                "contract": "ONSURE_PASS_EVIDENCE_FINALIZATION_V1", "outcome": "PASS_NONFINAL",
                "verified_pass_step_count": 0, "output_sha256": sha(final_log),
                "environment_sha256": "b" * 64, "log_file": str(final_log),
            },
        }
        result["phase_outcomes"]["STRUCTURE_STATIC"] = "BLOCKED"
        result["verification_group_outcomes"]["ENVIRONMENT_DEPENDENCY"] = "BLOCKED"
        path = root / "universal-validation-result.json"
        path.write_text(json.dumps(result), encoding="utf-8")
        return path

    def test_seals_blocked_result_without_absolute_paths(self):
        with tempfile.TemporaryDirectory() as directory:
            body = MODULE.verify_observation("external-target", self.make_run(Path(directory)), "c" * 40)
            self.assertEqual("BLOCKED", body["decision"])
            self.assertNotIn(directory, json.dumps(body))

    def test_rejects_tampered_blocked_log(self):
        with tempfile.TemporaryDirectory() as directory:
            result = self.make_run(Path(directory))
            (Path(directory) / "step-logs/environment.preflight.log").write_text("tampered")
            with self.assertRaisesRegex(ValueError, "RUN_STEP_0_LOG_DIGEST_MISMATCH"):
                MODULE.verify_observation("external-target", result, "c" * 40)


if __name__ == "__main__":
    unittest.main()
