"""Runtime authority for the ONSure v2026.07.29 design baseline.

The runtime reads the normative Markdown sections registered by the approved
DOCX companion baseline.  It never trusts pre-computed acceptance totals.
Every atomic item is source-hash bound and receives a stable trace identifier.
"""
from __future__ import annotations

import hashlib
import json
import pathlib
import re
from dataclasses import dataclass
from typing import Any


BASELINE = pathlib.Path("docs/official-baseline/v2026.07.29")
REGISTRY = BASELINE / "onsure-design-baseline-registry.v1.json"
ACCEPTANCE_REGISTRY = pathlib.Path("contracts/final-acceptance-source-registry.v1.json")
REQUIREMENT_COVERAGE = pathlib.Path("status/final-product-requirement-coverage.v1.json")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def canonical(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()


@dataclass(frozen=True)
class AtomicAcceptance:
    item_id: str
    group_id: str
    ordinal: int
    source: str
    source_sha256: str
    section_anchor: str
    text: str

    def as_dict(self) -> dict[str, Any]:
        return self.__dict__.copy()


def _section_items(body: str, anchor: str, style: str) -> list[str]:
    start = body.find(anchor)
    if start < 0:
        raise ValueError(f"SECTION_ANCHOR_MISSING:{anchor}")
    section = body[start + len(anchor):]
    heading = re.search(r"(?m)^##\s+", section)
    if heading:
        section = section[:heading.start()]
    pattern = r"(?m)^-\s+(.+)$" if style == "BULLET" else r"(?m)^\d+\.\s+(.+)$"
    items = [re.sub(r"\s+", " ", item).strip() for item in re.findall(pattern, section)]
    if not items:
        raise ValueError(f"SECTION_ITEMS_MISSING:{anchor}")
    return items


def load_atomic_acceptance(root: pathlib.Path) -> list[AtomicAcceptance]:
    registry = json.loads((root / ACCEPTANCE_REGISTRY).read_text(encoding="utf-8"))
    result: list[AtomicAcceptance] = []
    for source in registry["sources"]:
        path = root / source["document"]
        body_bytes = path.read_bytes()
        items = _section_items(body_bytes.decode("utf-8"), source["section_anchor"], source["item_style"])
        if len(items) != source["expected_count"]:
            raise ValueError(
                f"ACCEPTANCE_COUNT_MISMATCH:{source['id']}:{len(items)}:{source['expected_count']}"
            )
        digest = sha256_bytes(body_bytes)
        for ordinal, text in enumerate(items, 1):
            result.append(
                AtomicAcceptance(
                    item_id=f"{source['id']}-{ordinal:02d}",
                    group_id=source["id"],
                    ordinal=ordinal,
                    source=source["document"],
                    source_sha256=digest,
                    section_anchor=source["section_anchor"],
                    text=text,
                )
            )
    if len(result) != registry["total_expected_items"]:
        raise ValueError(f"TOTAL_ACCEPTANCE_COUNT_MISMATCH:{len(result)}")
    if len({item.item_id for item in result}) != len(result):
        raise ValueError("DUPLICATE_ACCEPTANCE_ID")
    return result


def verify_design_baseline(root: pathlib.Path) -> dict[str, Any]:
    baseline = json.loads((root / REGISTRY).read_text(encoding="utf-8"))
    document_results = []
    for document in baseline["documents"]:
        path = root / document["path"]
        actual = sha256_bytes(path.read_bytes())
        if actual != document["sha256"]:
            raise ValueError(f"BASELINE_DOCUMENT_HASH_MISMATCH:{document['document_key']}")
        document_results.append(
            {"document_key": document["document_key"], "path": document["path"], "sha256": actual}
        )
    requirements = json.loads((root / REQUIREMENT_COVERAGE).read_text(encoding="utf-8"))["requirements"]
    requirement_ids = [item["id"] for item in requirements]
    if len(requirement_ids) != 22 or len(set(requirement_ids)) != 22:
        raise ValueError("FINAL_REQUIREMENT_AUTHORITY_NOT_22_UNIQUE")
    acceptance = load_atomic_acceptance(root)
    payload = {
        "contract": "ONSURE_DESIGN_RUNTIME_RECEIPT_V1",
        "baseline_version": baseline["baseline_version"],
        "documents": document_results,
        "requirements": requirement_ids,
        "acceptance": [item.as_dict() for item in acceptance],
        "document_count": len(document_results),
        "requirement_count": len(requirement_ids),
        "acceptance_count": len(acceptance),
        "self_validation_ceiling": "SELF_VALIDATION_NONFINAL",
        "independent_otester": "NOT_RUN",
        "independent_oaudit": "NOT_RUN",
        "final_claim_allowed": False,
    }
    payload["receipt_sha256"] = sha256_bytes(canonical(payload))
    return payload

