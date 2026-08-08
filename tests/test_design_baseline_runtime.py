from __future__ import annotations

import pathlib
import json
import tempfile
import unittest

from onsure_core.design_baseline_runtime import load_atomic_acceptance, verify_design_baseline


ROOT = pathlib.Path(__file__).resolve().parents[1]


class DesignBaselineRuntimeTest(unittest.TestCase):
    def test_recalculates_all_authorities(self) -> None:
        receipt = verify_design_baseline(ROOT)
        self.assertEqual(13, receipt["document_count"])
        self.assertEqual(22, receipt["requirement_count"])
        self.assertEqual(62, receipt["acceptance_count"])
        self.assertEqual(62, len({item["item_id"] for item in receipt["acceptance"]}))
        self.assertFalse(receipt["final_claim_allowed"])

    def test_atomic_items_are_source_bound(self) -> None:
        items = load_atomic_acceptance(ROOT)
        self.assertTrue(all(len(item.source_sha256) == 64 for item in items))
        self.assertEqual("FIN-ACC-44-14", items[-1].item_id)

    def test_source_mutation_is_detected(self) -> None:
        with tempfile.TemporaryDirectory() as folder:
            clone = pathlib.Path(folder)
            for relative in (
                "contracts/final-acceptance-source-registry.v1.json",
                "status/final-product-requirement-coverage.v1.json",
                "docs/official-baseline/v2026.07.29/onsure-design-baseline-registry.v1.json",
            ):
                target = clone / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes((ROOT / relative).read_bytes())
            registry = verify_design_baseline(ROOT)
            sources = json.loads(
                (ROOT / "contracts/final-acceptance-source-registry.v1.json").read_text()
            )["sources"]
            for source_spec in sources:
                source = source_spec["document"]
                target = clone / source
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes((ROOT / source).read_bytes())
            source = sources[0]["document"]
            target = clone / source
            body = target.read_text(encoding="utf-8")
            anchor = sources[0]["section_anchor"]
            target.write_text(body.replace(anchor, anchor + "\n\n- injected", 1), encoding="utf-8")
            for item in registry["documents"]:
                original = ROOT / item["path"]
                copied = clone / item["path"]
                copied.parent.mkdir(parents=True, exist_ok=True)
                copied.write_bytes(original.read_bytes())
            with self.assertRaisesRegex(ValueError, "ACCEPTANCE_COUNT_MISMATCH"):
                verify_design_baseline(clone)


if __name__ == "__main__":
    unittest.main()
