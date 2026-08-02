import pathlib
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


if __name__ == "__main__":
    unittest.main()
