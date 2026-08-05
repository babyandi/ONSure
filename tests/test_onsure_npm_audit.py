from __future__ import annotations

import hashlib
import json
import pathlib
import sys
import tempfile
import unittest
from unittest import mock


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import onsure_npm_audit as npm_audit  # noqa: E402


class ONSureNpmAuditTest(unittest.TestCase):
    def test_evidence_is_bound_to_lock_raw_result_and_nonfinal_authority(self):
        body = {"metadata": {"vulnerabilities": {
            "info": 0, "low": 1, "moderate": 2, "high": 0, "critical": 0, "total": 3,
        }}}
        raw = json.dumps(body)
        with tempfile.TemporaryDirectory(dir="/workspace") as directory, \
                mock.patch.object(npm_audit, "run", side_effect=[raw, "10.8.2"]):
            output = pathlib.Path(directory) / "audit.json"
            evidence = npm_audit.generate(output)
        canonical = (json.dumps(body, sort_keys=True, separators=(",", ":")) + "\n").encode()
        self.assertEqual(hashlib.sha256(npm_audit.LOCK.read_bytes()).hexdigest(),
                         evidence["package_lock_sha256"])
        self.assertEqual(hashlib.sha256(canonical).hexdigest(), evidence["audit_result_sha256"])
        self.assertEqual(3, evidence["vulnerabilities"]["total"])
        self.assertEqual("10.8.2", evidence["npm_version"])
        self.assertFalse(evidence["final_claim_allowed"])
        self.assertFalse(evidence["release_authority"])


if __name__ == "__main__":
    unittest.main()
