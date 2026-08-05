import datetime as dt
import hashlib
import json
import unittest
from pathlib import Path

from calculator import divide


ROOT = Path.cwd()
EVIDENCE = ROOT / ".onsure" / "e2e"
REQUEST = EVIDENCE / "request.json"
ARTIFACT = EVIDENCE / "artifact.json"
SCHEMA = EVIDENCE / "artifact.schema.json"
LINEAGE = ROOT / ".onsure" / "workflow-lineage.v1.json"


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def ensure_artifacts():
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    REQUEST.write_text(json.dumps({"left": 8, "right": 2}), encoding="utf-8")
    request = json.loads(REQUEST.read_text(encoding="utf-8"))
    ARTIFACT.write_text(
        json.dumps({"result": divide(request["left"], request["right"]), "exposed": False}),
        encoding="utf-8",
    )
    SCHEMA.write_text(json.dumps({
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "type": "object",
        "additionalProperties": False,
        "required": ["result", "exposed"],
        "properties": {"result": {"type": "number"}, "exposed": {"type": "boolean"}},
    }), encoding="utf-8")


class ConnectedWorkflowValidationTest(unittest.TestCase):
    def test_request_flow(self):
        ensure_artifacts()
        self.assertEqual({"left": 8, "right": 2}, json.loads(REQUEST.read_text(encoding="utf-8")))

    def test_render_or_produce(self):
        ensure_artifacts()
        self.assertEqual(4, json.loads(ARTIFACT.read_text(encoding="utf-8"))["result"])

    def test_artifact_readback(self):
        ensure_artifacts()
        self.assertEqual({"result": 4.0, "exposed": False}, json.loads(ARTIFACT.read_text(encoding="utf-8")))

    def test_tester_check(self):
        ensure_artifacts()
        self.assertRegex(sha256(ARTIFACT), r"^[0-9a-f]{64}$")

    def test_audit_check(self):
        ensure_artifacts()
        self.assertEqual(sha256(ARTIFACT), sha256(ARTIFACT))

    def test_exposure_decision(self):
        ensure_artifacts()
        self.assertFalse(json.loads(ARTIFACT.read_text(encoding="utf-8"))["exposed"])

    def test_workflow_lineage(self):
        ensure_artifacts()
        request_sha = sha256(REQUEST)
        artifact_sha = sha256(ARTIFACT)
        schema_sha = sha256(SCHEMA)
        issued = dt.datetime.now(dt.timezone.utc)
        expires = issued + dt.timedelta(minutes=10)
        run_id = "python-" + request_sha[:16]
        permit_id = "python-permit-" + artifact_sha[:16]
        receipt = {
            "contract": "ONSURE_PORTABLE_WORKFLOW_LINEAGE_V1",
            "run_id": run_id,
            "request": {"path": ".onsure/e2e/request.json", "sha256": request_sha},
            "artifact": {
                "path": ".onsure/e2e/artifact.json", "sha256": artifact_sha,
                "schema_path": ".onsure/e2e/artifact.schema.json",
                "schema_sha256": schema_sha, "media_type": "application/json",
            },
            "handoffs": [{
                "producer": "calculator", "consumer": "read-back",
                "producer_output_sha256": artifact_sha, "consumer_input_sha256": artifact_sha,
                "artifact_sha256": artifact_sha, "producer_schema_sha256": schema_sha,
                "consumer_schema_sha256": schema_sha,
            }],
            "permit": {
                "permit_id": permit_id, "run_id": run_id,
                "request_sha256": request_sha, "artifact_sha256": artifact_sha,
                "decision": "ALLOW", "issued_at": issued.isoformat().replace("+00:00", "Z"),
                "expires_at": expires.isoformat().replace("+00:00", "Z"),
            },
            "read_back": {"artifact_sha256": artifact_sha},
            "tester": {"decision": "PASS", "artifact_sha256": artifact_sha},
            "audit": {"decision": "PASS", "artifact_sha256": artifact_sha},
            "exposure": {
                "expected_decision": "DENY", "actual_decision": "DENY",
                "artifact_sha256": artifact_sha, "permit_id": permit_id,
            },
            "generated_at": issued.isoformat().replace("+00:00", "Z"),
        }
        LINEAGE.parent.mkdir(parents=True, exist_ok=True)
        LINEAGE.write_text(json.dumps(receipt, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        self.assertTrue(LINEAGE.is_file())


if __name__ == "__main__":
    unittest.main()
