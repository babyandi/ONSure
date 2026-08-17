#!/usr/bin/env python3
"""Materialize the requirement-based Test Coverage Universe (159 SS7 / Batch 9 execution queue
item 6, contracts/test-coverage-universe.candidate.v1.json).

Denominator = the ACTIVE Requirement Universe (.onsure/requirement-universe/requirement-records.json,
EPOCH::REQUIREMENT::0002), not raw test count: per contracts/test-coverage-universe.candidate.v1.json's
own rules.raw_test_count_not_accepted_as_coverage, "a test file cites this requirement ID somewhere"
is NOT accepted as coverage for any of the 7 test_classes -- each class's applicability is judged
per-requirement from real signal (criticality/claim_effect/taxonomy/contract_refs/normative_text),
and each APPLICABLE class's coverage is judged from a real test-METHOD-name keyword match found near
an actual citation line in a cited test file, not merely file-level citation presence.

This is a first materialization pass with a disclosed, mechanical methodology -- not a claim that
every heuristic is perfectly precise (e.g., a positive test whose method name happens to contain a
negative-sounding word could be miscounted). See methodology_limitations in the output.
"""
from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
UNIVERSE_DIR = ROOT / ".onsure" / "requirement-universe"

TEST_CLASSES = [
    "POSITIVE", "NEGATIVE", "SEMANTIC_INVALID", "CROSS_CONTRACT",
    "ADVERSARIAL", "RECOVERY", "RUNTIME_EVIDENCE",
]

FAILURE_MODE_RE = re.compile(
    r"(?:금지|필수|MUST NOT|PROHIBITED|반드시|불가|forbidden|cannot|막는다|차단)", re.IGNORECASE)
CROSS_CONTRACT_TEXT_RE = re.compile(r"(?:간의? 관계|관계를 검증|cross-contract|상호 참조|참조 무결성)", re.IGNORECASE)
RECOVERY_TEXT_RE = re.compile(
    r"(?:rollback|롤백|재현|복구|retry|재시도|failover|resume|재개|Rollback Receipt|DR\b)", re.IGNORECASE)

METHOD_DECL_RE = re.compile(r"(?:void|def)\s+(\w*test\w*|\w+)\s*\(", re.IGNORECASE)
CLASS_KEYWORDS = {
    "NEGATIVE": re.compile(r"invalid|reject|forbid|denied|blocked|fails?\b|cannot|missing|malformed|violat|refuse", re.IGNORECASE),
    "SEMANTIC_INVALID": re.compile(r"semanticinvalid|schemainvalid|invalidfixture|invalid_json|invalidjson", re.IGNORECASE),
    "CROSS_CONTRACT": re.compile(r"crosscontract|crossreference|crosscheck|cross_contract", re.IGNORECASE),
    "ADVERSARIAL": re.compile(r"adversarial|attack|malicious|exploit|injection|poison|tamper", re.IGNORECASE),
    "RECOVERY": re.compile(r"recover|rollback|retry|resume|failover|reconstruct", re.IGNORECASE),
}
GENERIC_EVIDENCE_FILES = {
    "status/orphan-severity-recalculation-report.v1.json",
}


def applicable_classes(record: dict[str, Any]) -> dict[str, str]:
    """Returns {class_name: reason} for every class judged applicable to this requirement."""
    applicable: dict[str, str] = {"POSITIVE": "all_applicable_requirements_require_positive"}
    text = record.get("normative_text", "")
    criticality = record.get("criticality", "")
    taxonomy = record.get("taxonomy", "")
    claim_effect = record.get("claim_effect", "")
    contract_refs = record.get("_contract_refs", [])

    if criticality in ("CRITICAL", "HIGH") or FAILURE_MODE_RE.search(text):
        applicable["NEGATIVE"] = "criticality_or_explicit_failure_mode"
    if any(ref.endswith(".schema.json") for ref in contract_refs):
        applicable["SEMANTIC_INVALID"] = "structured_semantic_contract_referenced"
    if len({ref for ref in contract_refs}) >= 2 or CROSS_CONTRACT_TEXT_RE.search(text):
        applicable["CROSS_CONTRACT"] = "multiple_contracts_or_explicit_relation_text"
    if taxonomy in ("SECURITY", "AI_SPECIFIC") and criticality in ("CRITICAL", "HIGH"):
        applicable["ADVERSARIAL"] = "security_or_ai_specific_taxonomy_at_high_criticality"
    if RECOVERY_TEXT_RE.search(text):
        applicable["RECOVERY"] = "retry_rollback_failover_reconstruction_text"
    if claim_effect in ("POSITIVE_CLAIM_GATE", "QUALIFICATION_GATE"):
        applicable["RUNTIME_EVIDENCE"] = "claims_runtime_gate_behavior"
    return applicable


def method_names_near_citation(path: Path, requirement_id: str, window: int = 15) -> list[str]:
    if not path.exists():
        return []
    try:
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return []
    citation_lines = [i for i, line in enumerate(lines) if requirement_id in line]
    if not citation_lines:
        return []
    found: list[str] = []
    for cite in citation_lines:
        start, end = max(0, cite - window), min(len(lines), cite + window)
        for line in lines[start:end]:
            match = METHOD_DECL_RE.search(line)
            if match:
                found.append(match.group(1))
    return found


def covered_classes(
    record: dict[str, Any], applicable: dict[str, str], test_refs: list[str], evidence_refs: list[str],
) -> dict[str, str]:
    coverage: dict[str, str] = {}
    all_method_names: list[str] = []
    for ref in test_refs:
        all_method_names.extend(method_names_near_citation(ROOT / ref, record["requirement_id"]))

    if "POSITIVE" in applicable and all_method_names:
        coverage["POSITIVE"] = f"{len(all_method_names)}_cited_test_method(s)_found"

    for cls in ("NEGATIVE", "SEMANTIC_INVALID", "CROSS_CONTRACT", "ADVERSARIAL", "RECOVERY"):
        if cls not in applicable:
            continue
        keyword_re = CLASS_KEYWORDS[cls]
        matches = [name for name in all_method_names if keyword_re.search(name)]
        if matches:
            coverage[cls] = f"method_name_keyword_match:{matches[0]}"

    if "RUNTIME_EVIDENCE" in applicable:
        real_evidence = [ref for ref in evidence_refs if ref not in GENERIC_EVIDENCE_FILES]
        if real_evidence:
            coverage["RUNTIME_EVIDENCE"] = f"dedicated_evidence_file:{real_evidence[0]}"

    return coverage


def main() -> int:
    records = json.loads((UNIVERSE_DIR / "requirement-records.json").read_text(encoding="utf-8"))
    trace = json.loads((UNIVERSE_DIR / "global-trace-scan-report.json").read_text(encoding="utf-8"))
    trace_rows = {row["requirement_id"]: row for row in trace["rows"]}

    explicit_records = [r for r in records if r.get("explicit_id")]

    rows_out: list[dict[str, Any]] = []
    class_applicable_count = {c: 0 for c in TEST_CLASSES}
    class_covered_count = {c: 0 for c in TEST_CLASSES}

    for record in explicit_records:
        req_id = record["requirement_id"]
        trace_row = trace_rows.get(req_id, {})
        record_for_rules = dict(record)
        record_for_rules["_contract_refs"] = trace_row.get("contract_refs", [])

        applicable = applicable_classes(record_for_rules)
        coverage = covered_classes(
            record, applicable, trace_row.get("test_refs", []), trace_row.get("evidence_refs", []))

        for cls in applicable:
            class_applicable_count[cls] += 1
            if cls in coverage:
                class_covered_count[cls] += 1

        not_applicable = {c: "not_applicable_no_matching_rule" for c in TEST_CLASSES if c not in applicable}
        gap = sorted(set(applicable) - set(coverage))

        rows_out.append({
            "requirement_id": req_id,
            "applicable_classes": sorted(applicable),
            "applicable_reasons": applicable,
            "covered_classes": sorted(coverage),
            "coverage_evidence": coverage,
            "not_applicable_classes": sorted(not_applicable),
            "gap_classes": gap,
            "fully_covered": len(gap) == 0,
        })

    fully_covered_count = sum(1 for r in rows_out if r["fully_covered"])

    manifest = {
        "contract": "ONSURE_TEST_COVERAGE_UNIVERSE_MATERIALIZATION_V1",
        "authority_ref": "contracts/test-coverage-universe.candidate.v1.json",
        "denominator": "ACTIVE_PRODUCT_DESIGN_REQUIREMENT_UNIVERSE",
        "denominator_requirement_epoch_id": "EPOCH::REQUIREMENT::0002",
        "coverage_unit": "REQUIREMENT",
        "explicit_requirement_count": len(explicit_records),
        "fully_covered_count": fully_covered_count,
        "fully_covered_ratio": round(fully_covered_count / len(explicit_records), 6) if explicit_records else 0,
        "class_applicable_count": class_applicable_count,
        "class_covered_count": class_covered_count,
        "class_gap_count": {c: class_applicable_count[c] - class_covered_count[c] for c in TEST_CLASSES},
        "rows": rows_out,
        "methodology_limitations": [
            "Coverage detection scans for a test-method-declaration keyword within 15 lines of a "
            "requirement-ID citation in a cited test file -- this is a real, mechanical signal, not "
            "a semantic guarantee that the method actually exercises the class it appears to signal.",
            "SEMANTIC_INVALID and CROSS_CONTRACT applicability/coverage rules are heuristic (schema "
            "reference presence, multi-contract-reference presence) and may under- or over-apply "
            "relative to a human reviewer's judgment.",
            "CANDIDATE_EXTRACTED (non-explicit-ID) requirements are excluded from this pass -- only "
            "the explicit_id-bearing subset of the Requirement Universe is materialized here.",
        ],
        "raw_test_count_not_accepted_as_coverage": True,
        "self_validation_nonfinal": True,
        "final_claim_allowed": False,
    }

    out_path = UNIVERSE_DIR / "test-coverage-universe-materialization.v1.json"
    out_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    print(json.dumps({
        "explicit_requirement_count": len(explicit_records),
        "fully_covered_count": fully_covered_count,
        "fully_covered_ratio": manifest["fully_covered_ratio"],
        "class_gap_count": manifest["class_gap_count"],
    }))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
