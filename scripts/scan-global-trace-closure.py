#!/usr/bin/env python3
"""RU-07 Global Trace Closure Scanner.

The scanner defaults to the historical live Requirement Universe for backward compatibility,
but callers may select an exact candidate universe with --universe-dir. This avoids mutating
or temporarily swapping the live EPOCH merely to validate a candidate denominator.

The scan remains mechanical/self-validation NONFINAL evidence. It does not replace semantic or
independent review and never emits a Final/Lock/GO claim.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_UNIVERSE_DIR = ROOT / ".onsure" / "requirement-universe"

SEVERITY_HEURISTIC_NOTE = (
    "Mechanical severity heuristic: an orphan dimension is escalated to P0 only when "
    "claim_effect=POSITIVE_CLAIM_GATE and criticality=CRITICAL and the dimension is "
    "REQUIREMENT_WITHOUT_CONTRACT_WHERE_MACHINE_ENFORCEMENT_REQUIRED or "
    "REQUIREMENT_WITHOUT_EVIDENCE_PATH; all other found orphans are P1. This "
    "approximates but does not replace human/semantic review."
)
BACKTICK_CONTRACT_RE = re.compile(r"`(contracts/[A-Za-z0-9_./-]+\.json)`")
META_TEST_FILES = {
    "tests/test_requirement_universe.py",
    "tests/test_target_assurance_wave1.py",
    "tests/test_repository_contracts.py",
}
SCAN_GLOBS = {
    "contract_refs": ["contracts/**/*.json"],
    "test_refs": ["src/test/**/*.java", "tests/**/*.py", "modules/**/src/test/**/*.java"],
    "evidence_refs": ["findings/**/*.json", "findings/**/*.jsonl", "status/**/*.json", "evidence/**/*.json"],
    "design_refs": ["docs/master/**/*.md", "docs/0*.md", "docs/4*.md"],
}
OPERATION_REGISTRY = ROOT / "contracts" / "workflow-operation-registry.v1.json"


def git_tracked_glob(patterns: list[str]) -> list[Path]:
    result = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True, check=False, text=True)
    if result.returncode != 0:
        raise RuntimeError("GIT_LS_FILES_FAILED")
    tracked = {ROOT / line for line in result.stdout.splitlines() if line}
    matched: list[Path] = []
    for pattern in patterns:
        matched.extend(p for p in ROOT.glob(pattern) if p in tracked and p.is_file())
    return sorted(set(matched))


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def scan(universe_dir: Path) -> dict[str, Any]:
    snapshot = load_json(universe_dir / "requirement-universe-snapshot.json")
    records = {r["requirement_id"]: r for r in load_json(universe_dir / "requirement-records.json")}
    requirement_ids = list(snapshot.get("requirement_ids", []))
    missing_records = sorted(set(requirement_ids) - set(records))
    if missing_records:
        raise RuntimeError(f"REQUIREMENT_RECORDS_MISSING:{missing_records[:20]}")

    file_sets = {field: git_tracked_glob(patterns) for field, patterns in SCAN_GLOBS.items()}
    file_sets["test_refs"] = [
        p for p in file_sets["test_refs"] if p.relative_to(ROOT).as_posix() not in META_TEST_FILES
    ]
    file_texts: dict[str, dict[str, str]] = {}
    for field, files in file_sets.items():
        file_texts[field] = {}
        for path in files:
            try:
                file_texts[field][path.relative_to(ROOT).as_posix()] = path.read_text(encoding="utf-8", errors="replace")
            except OSError:
                continue

    operation_registry_text = OPERATION_REGISTRY.read_text(encoding="utf-8", errors="replace") if OPERATION_REGISTRY.exists() else ""
    rows: list[dict[str, Any]] = []
    orphan_p0: list[str] = []
    orphan_p1: list[str] = []
    orphan_p2: list[str] = []
    missing_negative_fixture: list[str] = []
    closed_count = 0

    for requirement_id in requirement_ids:
        record = records[requirement_id]
        token = record.get("explicit_id") or requirement_id
        is_explicit = record.get("source_class") == "EXPLICIT_ID" or requirement_id.startswith(("FR-", "NFR-", "DD-"))
        own_doc = record.get("authority_document", "")
        design_refs = sorted(path for path, text in file_texts["design_refs"].items() if token in text and path != own_doc)
        backward_contract_refs = {path for path, text in file_texts["contract_refs"].items() if token in text}
        forward_contract_refs = {
            ref for ref in BACKTICK_CONTRACT_RE.findall(record.get("normative_text", ""))
            if ref in file_texts["contract_refs"]
        }
        contract_refs = sorted(backward_contract_refs | forward_contract_refs)
        test_refs = sorted(path for path, text in file_texts["test_refs"].items() if token in text)
        evidence_refs = sorted(path for path, text in file_texts["evidence_refs"].items() if token in text)
        operation_refs = [token] if token in operation_registry_text else []

        orphan_dimensions: list[str] = []
        if is_explicit and not design_refs:
            orphan_dimensions.append("REQUIREMENT_WITHOUT_DESIGN")
        claim_effect = record.get("claim_effect")
        if claim_effect in ("POSITIVE_CLAIM_GATE", "QUALIFICATION_GATE") and not contract_refs:
            orphan_dimensions.append("REQUIREMENT_WITHOUT_CONTRACT_WHERE_MACHINE_ENFORCEMENT_REQUIRED")
        if not test_refs:
            orphan_dimensions.append("REQUIREMENT_WITHOUT_TEST")
        if not evidence_refs:
            orphan_dimensions.append("REQUIREMENT_WITHOUT_EVIDENCE_PATH")

        if orphan_dimensions:
            escalate_p0 = (
                claim_effect == "POSITIVE_CLAIM_GATE"
                and record.get("criticality") == "CRITICAL"
                and any(d in (
                    "REQUIREMENT_WITHOUT_CONTRACT_WHERE_MACHINE_ENFORCEMENT_REQUIRED",
                    "REQUIREMENT_WITHOUT_EVIDENCE_PATH",
                ) for d in orphan_dimensions)
            )
            (orphan_p0 if escalate_p0 else orphan_p1).append(requirement_id)
        else:
            closed_count += 1

        if record.get("taxonomy") == "SECURITY" and not test_refs:
            missing_negative_fixture.append(requirement_id)

        rows.append({
            "requirement_id": requirement_id,
            "requirement_digest": record.get("normative_text_digest"),
            "design_refs": design_refs,
            "contract_refs": contract_refs,
            "operation_refs": operation_refs,
            "api_refs": [], "event_refs": [], "receipt_refs": [],
            "test_refs": test_refs,
            "evidence_refs": evidence_refs,
            "policy_refs": [], "authority_refs": [], "final_gate_refs": [],
            "orphan_dimensions": orphan_dimensions,
        })

    total = len(rows)
    return {
        "contract": "ONSURE_GLOBAL_TRACE_SCAN_REPORT_V1",
        "universe_directory": universe_dir.relative_to(ROOT).as_posix() if universe_dir.is_relative_to(ROOT) else str(universe_dir),
        "universe_digest": snapshot.get("requirement_manifest_digest"),
        "scanned_requirement_count": total,
        "rows": rows,
        "orphans": {"p0": sorted(orphan_p0), "p1": sorted(orphan_p1), "p2": sorted(orphan_p2)},
        "unresolved_semantic_conflicts": [],
        "missing_negative_fixture_requirement_ids": sorted(missing_negative_fixture),
        "trace_completeness_ratio": round(closed_count / total, 6) if total else 0.0,
        "lock_eligible": False,
        "scanned_at": datetime.now(timezone.utc).isoformat(),
        "self_validation_nonfinal": True,
        "final_claim_allowed": False,
        "methodology_note": SEVERITY_HEURISTIC_NOTE,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--universe-dir", type=Path, default=DEFAULT_UNIVERSE_DIR)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    universe_dir = args.universe_dir if args.universe_dir.is_absolute() else (ROOT / args.universe_dir)
    universe_dir = universe_dir.resolve()
    snapshot = universe_dir / "requirement-universe-snapshot.json"
    records = universe_dir / "requirement-records.json"
    if not snapshot.is_file() or not records.is_file():
        print(f"ONSURE_GLOBAL_TRACE_SCAN_FAIL requirement universe missing under {universe_dir}", file=sys.stderr)
        return 2
    report = scan(universe_dir)
    output = args.output or (universe_dir / "global-trace-scan-report.json")
    output = output if output.is_absolute() else (ROOT / output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({
        "universe_dir": report["universe_directory"],
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
    except (OSError, RuntimeError, ValueError, KeyError) as error:
        print(f"ONSURE_GLOBAL_TRACE_SCAN_FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
