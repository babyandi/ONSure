import pathlib
import json
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import onsure_supply_chain as supply_chain  # noqa: E402


class ONSureSupplyChainTest(unittest.TestCase):
    def test_normalization_removes_nondeterministic_identity_and_timestamp(self):
        body = {
            "serialNumber": "urn:uuid:changes",
            "metadata": {"timestamp": "changes", "component": {"name": "onsure"}},
            "components": [
                {"purl": "pkg:maven/z/z@1"},
                {"purl": "pkg:maven/a/a@1"},
            ],
            "dependencies": [
                {"ref": "z", "dependsOn": ["b", "a"]},
                {"ref": "a", "dependsOn": []},
            ],
        }
        normalized = supply_chain.normalize_sbom(body)
        self.assertNotIn("serialNumber", normalized)
        self.assertNotIn("timestamp", normalized["metadata"])
        self.assertEqual("pkg:maven/a/a@1", normalized["components"][0]["purl"])
        self.assertEqual(["a", "b"], normalized["dependencies"][1]["dependsOn"])

    def test_missing_license_is_explicitly_review_required(self):
        inventory = supply_chain.build_inventory({
            "components": [{"group": "x", "name": "y", "version": "1", "purl": "p"}]
        })
        self.assertEqual(1, inventory["dependency_license_review_required_count"])
        self.assertEqual("REVIEW_REQUIRED", inventory["decision"])

    def test_policy_requires_unique_purl_sha256_and_sbom_bound_vulnerability_evidence(self):
        sbom = json.loads(supply_chain.DEFAULT_SBOM.read_text(encoding="utf-8"))
        inventory = supply_chain.build_inventory(sbom)
        policy = json.loads(supply_chain.POLICY_PATH.read_text(encoding="utf-8"))
        vulnerability = json.loads(supply_chain.DEFAULT_VULNERABILITY.read_text(encoding="utf-8"))
        npm_audit = json.loads(supply_chain.DEFAULT_NPM_AUDIT.read_text(encoding="utf-8"))
        violations, blockers = supply_chain.validate_policy(
            sbom, inventory, policy, vulnerability, npm_audit
        )
        self.assertEqual([], violations)
        self.assertIn("ROOT_SOURCE_LICENSE_UNDECLARED", blockers)
        self.assertIn("VULNERABILITY_SCAN_NOT_RUN", blockers)
        self.assertEqual("NOT_RUN", vulnerability["state"])
        self.assertTrue(all(
            vulnerability[level] == "NOT_RUN"
            for level in ("critical", "high", "medium", "low")
        ))

        changed = json.loads(json.dumps(sbom))
        changed["components"][0]["hashes"] = []
        violations, _ = supply_chain.validate_policy(
            changed, inventory, policy, vulnerability, npm_audit
        )
        self.assertTrue(any("SHA256_MISSING" in value for value in violations))

        stale = json.loads(json.dumps(vulnerability))
        stale["source_sbom_file_sha256"] = "0" * 64
        violations, _ = supply_chain.validate_policy(sbom, inventory, policy, stale, npm_audit)
        self.assertIn("VULNERABILITY_EVIDENCE_SBOM_BINDING_INVALID", violations)

        stale_npm = json.loads(json.dumps(npm_audit))
        stale_npm["package_lock_sha256"] = "0" * 64
        violations, _ = supply_chain.validate_policy(
            sbom, inventory, policy, vulnerability, stale_npm
        )
        self.assertIn("NPM_AUDIT_EVIDENCE_BINDING_INVALID", violations)


if __name__ == "__main__":
    unittest.main()
