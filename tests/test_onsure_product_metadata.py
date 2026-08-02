import copy
import json
import pathlib
import sys
import unittest

import yaml


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import validate_onsure_product_metadata as metadata  # noqa: E402


class ONSureProductMetadataTest(unittest.TestCase):
    def setUp(self):
        self.product = yaml.safe_load((ROOT / "product.yaml").read_text(encoding="utf-8"))
        self.build = json.loads(
            (ROOT / "contracts/onsure-build-boundary.v1.json").read_text(encoding="utf-8")
        )
        self.obuilder = yaml.safe_load(
            (ROOT / ".obuilder/product-build.yaml").read_text(encoding="utf-8")
        )

    def test_repository_metadata_is_consistent_and_nonfinal(self):
        result = metadata.validate()
        self.assertEqual("PASS_NONFINAL", result["decision"])
        self.assertFalse(result["final_claim_allowed"])

    def test_namespace_rename_authority_fails_closed(self):
        product = copy.deepcopy(self.product)
        product["namespace"]["rename_authorized"] = True
        self.assertIn(
            "NAMESPACE_RENAME_AUTHORITY",
            metadata.validate_documents(product, self.build, self.obuilder),
        )

    def test_modular_build_cannot_promote_itself_to_release_authority(self):
        obuilder = copy.deepcopy(self.obuilder)
        obuilder["compatibility_build"]["release_authority"] = True
        self.assertIn(
            "OBUILDER_COMPATIBILITY_RELEASE_AUTHORITY",
            metadata.validate_documents(self.product, self.build, obuilder),
        )


if __name__ == "__main__":
    unittest.main()
