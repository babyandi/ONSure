#!/usr/bin/env python3
"""RU-07: Global Trace Closure Scanner (90_GLOBAL_TRACE_CLOSURE_SCANNER_DESIGN.md).

Cross-references the RequirementUniverseSnapshot against the repository's real
Contract/Test/Evidence/Operation registries and reports orphan dimensions per
requirement. This is a mechanical, ID-string based scan: it can only find a
reference to a requirement where the requirement's own id/explicit_id literally
appears in the referencing artifact. It cannot assess semantic correctness of a
reference, and it cannot reliably compute the P0-vs-P1 severity judgment calls in
90 SS6 (e.g. "strong claim") -- those are approximated by a disclosed, conservative
heuristic (see SEVERITY_HEURISTIC_NOTE) and MUST be treated as a starting point for
human/Design-Change-Queue review, not as a final severity determination.

lock_eligible is unconditionally False in Batch 0: exact denominator confirmation,
applicability resolution and P0 conflict closure have not happened yet (91 SS5).
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
UNIVERSE_DIR = ROOT / ".onsure" / "requirement-universe"

SEVERITY_HEURISTIC_NOTE = (
    "Mechanical severity heuristic: an orphan dimension is escalated to P0 only when "
    "claim_effect=POSITIVE_CLAIM_GATE and criticality=CRITICAL and the dimension is "
    "REQUIREMENT_WITHOUT_CONTRACT_WHERE_MACHINE_ENFORCEMENT_REQUIRED or "
    "REQUIREMENT_WITHOUT_EVIDENCE_PATH; all other found orphans are P1. This "
    "approximates but does not replace the human/semantic judgment described in "
    "90_GLOBAL_TRACE_CLOSURE_SCANNER_DESIGN.md SS6."
)

BACKTICK_CONTRACT_RE = re.compile(r"`(contracts/[A-Za-z0-9_./-]+\.json)`")

META_TEST_FILES = {
    "tests/test_requirement_universe.py",
    "tests/test_target_assurance_wave1.py",
    "tests/test_repository_contracts.py",
}
"""These test files exercise the RU generator/scanner tooling itself and cite
requirement IDs (e.g. FR-COM-008) as worked examples in assertions/comments.
Counting such a citation as "a test exists for this requirement" would be a
false positive -- the requirement's own behavior is not what these tests
verify. Excluded from test_refs scanning for that reason."""

SCAN_GLOBS = {
    "contract_refs": ["contracts/**/*.json"],
    "test_refs": ["src/test/**/*.java", "tests/**/*.py"],
    "evidence_refs": ["findings/**/*.json", "findings/**/*.jsonl", "status/**/*.json"],
    "design_refs": ["docs/master/**/*.md"],
}
OPERATION_REGISTRY = ROOT / "contracts" / "workflow-operation-registry.v1.json"


def git_tracked_glob(patterns: list[str]) -> list[Path]:
    result = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True, check=False, text=True)
    if result.returncode != 0:
        raise RuntimeError("GIT_LS_FILES_FAILED")
    tracked = [ROOT / line for line in result.stdout.splitlines() if line]
    matched: list[Path] = []
    for pattern in patterns:
        matched.extend(p for p in ROOT.glob(pattern) if p in tracked and p.is_file())
    return sorted(set(matched))


def build_reference_index(field: str, patterns: list[str], own_document: str | None = None) -> dict[str, list[Path]]:
    """Return {requirement_id/explicit_id token -> [files mentioning it]} lazily via full text scan."""
    files = git_tracked_glob(patterns)
    return {"__files__": files}  # placeholder; actual search done per-id in scan() for memory reasons


def load_snapshot() -> dict[str, Any]:
    return json.loads((UNIVERSE_DIR / "requirement-universe-snapshot.json").read_text(encoding="utf-8"))


def load_records() -> list[dict[str, Any]]:
    return json.loads((UNIVERSE_DIR / "requirement-records.json").read_text(encoding="utf-8"))


def load_operation_registry_text() -> str:
    if not OPERATION_REGISTRY.exists():
        return ""
    return OPERATION_REGISTRY.read_text(encoding="utf-8", errors="replace")


def scan() -> dict[str, Any]:
    snapshot = load_snapshot()
    records = {r["requirement_id"]: r for r in load_records()}

    file_sets = {field: git_tracked_glob(patterns) for field, patterns in SCAN_GLOBS.items()}
    file_sets["test_refs"] = [
        p for p in file_sets["test_refs"] if p.relative_to(ROOT).as_posix() not in META_TEST_FILES
    ]
    file_texts: dict[str, dict[str, str]] = {}
    for field, files in file_sets.items():
        file_texts[field] = {}
        for path in files:
            try:
                file_texts[field][path.relative_to(ROOT).as_posix()] = path.read_text(
                    encoding="utf-8", errors="replace"
                )
            except OSError:
                continue

    operation_registry_text = load_operation_registry_text()

    rows: list[dict[str, Any]] = []
    orphan_p0: list[str] = []
    orphan_p1: list[str] = []
    orphan_p2: list[str] = []
    missing_negative_fixture: list[str] = []
    closed_count = 0

    for requirement_id in snapshot["requirement_ids"]:
        record = records[requirement_id]
        token = record.get("explicit_id") or requirement_id
        is_explicit = record["source_class"] == "EXPLICIT_ID"
        own_doc = record["authority_document"]

        design_refs = sorted(
            path for path, text in file_texts["design_refs"].items()
            if token in text and path != own_doc
        )
        backward_contract_refs = {path for path, text in file_texts["contract_refs"].items() if token in text}
        forward_contract_refs = {
            ref for ref in BACKTICK_CONTRACT_RE.findall(record["normative_text"])
            if ref in file_texts["contract_refs"]
        }
        contract_refs = sorted(backward_contract_refs | forward_contract_refs)
        test_refs = sorted(path for path, text in file_texts["test_refs"].items() if token in text)
        evidence_refs = sorted(path for path, text in file_texts["evidence_refs"].items() if token in text)
        operation_refs = [token] if is_explicit and token in operation_registry_text else []

        orphan_dimensions: list[str] = []
        if is_explicit and not design_refs:
            orphan_dimensions.append("REQUIREMENT_WITHOUT_DESIGN")
        if record["claim_effect"] in ("POSITIVE_CLAIM_GATE", "QUALIFICATION_GATE") and not contract_refs:
            orphan_dimensions.append("REQUIREMENT_WITHOUT_CONTRACT_WHERE_MACHINE_ENFORCEMENT_REQUIRED")
        if not test_refs:
            orphan_dimensions.append("REQUIREMENT_WITHOUT_TEST")
        if not evidence_refs:
            orphan_dimensions.append("REQUIREMENT_WITHOUT_EVIDENCE_PATH")

        if orphan_dimensions:
            escalate_p0 = (
                record["claim_effect"] == "POSITIVE_CLAIM_GATE"
                and record["criticality"] == "CRITICAL"
                and any(
                    d in ("REQUIREMENT_WITHOUT_CONTRACT_WHERE_MACHINE_ENFORCEMENT_REQUIRED", "REQUIREMENT_WITHOUT_EVIDENCE_PATH")
                    for d in orphan_dimensions
                )
            )
            if escalate_p0:
                orphan_p0.append(requirement_id)
            else:
                orphan_p1.append(requirement_id)
        else:
            closed_count += 1

        if record["taxonomy"] == "SECURITY" and not test_refs:
            missing_negative_fixture.append(requirement_id)

        rows.append({
            "requirement_id": requirement_id,
            "requirement_digest": record["normative_text_digest"],
            "design_refs": design_refs,
            "contract_refs": contract_refs,
            "operation_refs": operation_refs,
            "api_refs": [],
            "event_refs": [],
            "receipt_refs": [],
            "test_refs": test_refs,
            "evidence_refs": evidence_refs,
            "policy_refs": [],
            "authority_refs": [],
            "final_gate_refs": [],
            "orphan_dimensions": orphan_dimensions,
        })

    total = len(rows)
    trace_completeness_ratio = round(closed_count / total, 6) if total else 0.0

    report = {
        "contract": "ONSURE_GLOBAL_TRACE_SCAN_REPORT_V1",
        "universe_digest": snapshot["requirement_manifest_digest"],
        "scanned_requirement_count": total,
        "rows": rows,
        "orphans": {"p0": sorted(orphan_p0), "p1": sorted(orphan_p1), "p2": sorted(orphan_p2)},
        "unresolved_semantic_conflicts": [],
        "missing_negative_fixture_requirement_ids": sorted(missing_negative_fixture),
        "trace_completeness_ratio": trace_completeness_ratio,
        "lock_eligible": False,
        "scanned_at": datetime.now(timezone.utc).isoformat(),
        "self_validation_nonfinal": True,
        "final_claim_allowed": False,
        "methodology_note": SEVERITY_HEURISTIC_NOTE,
    }
    return report


def main() -> int:
    if not (UNIVERSE_DIR / "requirement-universe-snapshot.json").exists():
        print("ONSURE_GLOBAL_TRACE_SCAN_FAIL universe snapshot missing; run generate-requirement-universe.py first", file=sys.stderr)
        return 2
    report = scan()
    UNIVERSE_DIR.mkdir(parents=True, exist_ok=True)
    (UNIVERSE_DIR / "global-trace-scan-report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    print(json.dumps({
        "scanned_requirement_count": report["scanned_requirement_count"],
        "orphan_p0": len(report["orphans"]["p0"]),
        "orphan_p1": len(report["orphans"]["p1"]),
        "trace_completeness_ratio": report["trace_completeness_ratio"],
        "lock_eligible": report["lock_eligible"],
    }))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError) as error:
        print(f"ONSURE_GLOBAL_TRACE_SCAN_FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
