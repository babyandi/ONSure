from __future__ import annotations

import importlib.util
import json
import pathlib
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "acceptance_execution", ROOT / "scripts/validate-final-acceptance-execution.py"
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class FinalAcceptanceExecutionTest(unittest.TestCase):
    def test_expands_every_source_item_to_a_source_bound_case(self) -> None:
        with tempfile.TemporaryDirectory() as folder:
            root = pathlib.Path(folder)
            (root / "docs").mkdir()
            sources = []
            groups = []
            total = 0
            for group_index, style in enumerate(("BULLET", "NUMBERED"), start=1):
                source_id = f"FIN-ACC-T{group_index}"
                document = f"docs/{source_id}.md"
                anchor = f"## {source_id}"
                values = ["first criterion", "second criterion"]
                rendered = "\n".join(
                    f"- {value}" if style == "BULLET" else f"{index}. {value}"
                    for index, value in enumerate(values, start=1)
                )
                (root / document).write_text(f"{anchor}\n{rendered}\n", encoding="utf-8")
                sources.append({"id": source_id, "document": document, "section_anchor": anchor,
                                "item_style": style, "expected_count": len(values)})
                groups.append({"id": source_id, "executor": "EXECUTOR", "oracle": "ORACLE"})
                total += len(values)
            registry = {"sources": sources, "total_expected_items": total}
            contract = {
                "contract": "ONSURE_FINAL_ACCEPTANCE_EXECUTION_V1",
                "source_registry": "contracts/final-acceptance-source-registry.v1.json",
                "required_case_fields": ["case_id", "source_sha256", "executor", "oracle",
                                         "negative_oracle", "evidence_contract", "receipt_contract"],
                "groups": groups,
                "negative_oracle": "BLOCK",
                "evidence_contract": "EVIDENCE",
                "receipt_contract": "RECEIPT",
                "runtime_execution": "NOT_RUN",
                "final_claim_allowed": False,
            }
            errors, cases = MODULE.validate(root, registry, contract)
            self.assertEqual([], errors)
            self.assertEqual(total, len(cases))
            self.assertEqual(total, len({case["case_id"] for case in cases}))
            self.assertTrue(all(len(case["source_sha256"]) == 64 for case in cases))

    def test_contract_is_explicitly_nonfinal(self) -> None:
        contract = json.loads(
            (ROOT / "contracts/final-acceptance-execution.v1.json").read_text(encoding="utf-8")
        )
        self.assertEqual("NOT_RUN", contract["runtime_execution"])
        self.assertFalse(contract["final_claim_allowed"])


if __name__ == "__main__":
    unittest.main()
