import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "seal_universal", ROOT / "scripts" / "seal_universal_validation_evidence.py")
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class SealUniversalValidationEvidenceTest(unittest.TestCase):
    def make_run(self, root):
        log = root / "step-logs" / "one.log"
        final = root / "step-logs" / "evidence.finalize.log"
        log.parent.mkdir(parents=True)
        log.write_text("pass", encoding="utf-8")
        final.write_text("final", encoding="utf-8")
        sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
        provenance = {
            "contract": "ONSURE_TARGET_PROVENANCE_V1",
            "target_classification": "REAL_REPOSITORY",
            "classification_basis": "GIT_REPOSITORY_DISCOVERED",
            "repository_type": "GIT",
            "repository_identity_basis": "GIT_REMOTE_ORIGIN_HASH",
            "repository_identity_sha256": "c" * 64,
            "repository_commit_sha": "d" * 40,
            "repository_scope": ".",
            "worktree_clean": True,
            "registration_source_sha256": "a" * 64,
            "snapshot_source_sha256": "a" * 64,
            "snapshot_manifest_sha256": "e" * 64,
            "snapshot_file_count": 1,
            "fixture_only": False,
            "real_target_universality_eligible": True,
            "universality_claim_state": "ELIGIBLE_CANDIDATE_REQUIRES_ACTUAL_EXECUTION",
            "review_required": True,
            "runtime_validation": "NOT_RUN",
            "final_claim_allowed": False,
        }
        provenance["provenance_sha256"] = MODULE.canonical_digest(provenance)
        result = {
            "contract": "ONSURE_UNIVERSAL_VALIDATION_RUN_V1", "profile_id": "test",
            "assurance_class": "SELF_VALIDATION_NONFINAL", "technologies": ["PYTHON"],
            "overall_outcome": "PASS_NONFINAL",
            "phase_outcomes": {key: "PASS_NONFINAL" for key in MODULE.PHASES},
            "verification_group_outcomes": {key: "PASS_NONFINAL" for key in MODULE.GROUPS},
            "not_run_reasons": {}, "source_mutation_detected": False,
            "source_digest": "a" * 64, "snapshot_digest": "a" * 64,
            "target_provenance": provenance,
            "target_provenance_binding": {
                "contract": "ONSURE_TARGET_PROVENANCE_RUN_BINDING_V1",
                "state": "VERIFIED_BEFORE_AND_AFTER", "reason": "STABLE",
                "provenance_sha256": provenance["provenance_sha256"],
                "supplied_by_registration": True,
                "source_sha256": "a" * 64, "snapshot_sha256": "a" * 64,
                "snapshot_manifest_sha256": "e" * 64, "final_claim_allowed": False,
            },
            "real_target_universality_evidence_eligible": True,
            "final_claim_allowed": False, "environment_evidence": {"sha256": "b" * 64},
            "started_at": "2026-01-01T00:00:00Z", "completed_at": "2026-01-01T00:00:01Z",
            "steps": [{
                "stepId": "one", "phase": "STRUCTURE_STATIC", "kind": "INVENTORY",
                "required": True, "outcome": "PASS_NONFINAL", "exitCode": 0,
                "outputSha256": sha(log), "environmentSha256": "b" * 64,
                "logFile": str(log), "outputTruncated": False, "reason": "EXECUTED",
            }],
            "final_evidence_integrity": {
                "contract": "ONSURE_PASS_EVIDENCE_FINALIZATION_V1", "outcome": "PASS_NONFINAL",
                "verified_pass_step_count": 1, "output_sha256": sha(final),
                "environment_sha256": "b" * 64, "log_file": str(final),
            },
        }
        path = root / "universal-validation-result.json"
        path.write_text(json.dumps(result), encoding="utf-8")
        return path

    def test_verifies_and_strips_absolute_paths(self):
        with tempfile.TemporaryDirectory() as directory:
            sealed = MODULE.verify_run("python", self.make_run(Path(directory)))
            self.assertEqual("PASS_NONFINAL", sealed["overall_outcome"])
            self.assertNotIn(directory, json.dumps(sealed))

    def test_rejects_tampered_log(self):
        with tempfile.TemporaryDirectory() as directory:
            result = self.make_run(Path(directory))
            (Path(directory) / "step-logs" / "one.log").write_text("tampered", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "RUN_STEP_LOG_DIGEST_MISMATCH"):
                MODULE.verify_run("python", result)

    def test_rejects_fixture_as_real_target_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            result = self.make_run(Path(directory))
            body = json.loads(result.read_text(encoding="utf-8"))
            body["target_provenance"]["target_classification"] = "FIXTURE"
            body["target_provenance"]["fixture_only"] = True
            unsigned = dict(body["target_provenance"])
            unsigned.pop("provenance_sha256")
            body["target_provenance"]["provenance_sha256"] = MODULE.canonical_digest(unsigned)
            body["target_provenance_binding"]["provenance_sha256"] = \
                body["target_provenance"]["provenance_sha256"]
            result.write_text(json.dumps(body), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "RUN_TARGET_NOT_REAL_REPOSITORY"):
                MODULE.verify_run("python", result)

    def test_rejects_unverified_provenance_binding(self):
        with tempfile.TemporaryDirectory() as directory:
            result = self.make_run(Path(directory))
            body = json.loads(result.read_text(encoding="utf-8"))
            body["target_provenance_binding"]["state"] = "BLOCKED_BEFORE_EXECUTION"
            result.write_text(json.dumps(body), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "RUN_TARGET_PROVENANCE_BINDING_NOT_VERIFIED"):
                MODULE.verify_run("python", result)


if __name__ == "__main__":
    unittest.main()
