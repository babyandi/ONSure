#!/usr/bin/env python3
"""Materialize the full Requirement Authority Source Manifest.

The allowlist defines the scanned source population. Disposition controls whether a source may
originate CURRENT Product Design requirements. Unknown content remains UNREVIEWED and therefore
ineligible. Backward-compatible review_summary.unreviewed_count/disputed_count are denominator
blocker counts for ELIGIBLE sources; total/ineligible counts remain separately disclosed.
"""
from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
SEED_FILES = [
    "contracts/requirement-authority-source-review.seed.v1.json",
    "contracts/requirement-authority-source-review.seed-semantic.v1.json",
    "contracts/requirement-authority-source-review.seed-learning-026-077.compact.v1.json",
    "contracts/requirement-authority-source-review.seed-learning-gates.v1.json",
    "contracts/requirement-authority-source-review.seed-final-target.v1.json",
]
DISPOSITION_TOKENS = [
    "NORMATIVE_CURRENT", "NORMATIVE_REFINEMENT", "REFERENCE_ONLY", "HANDOFF_ONLY",
    "QA_EVIDENCE_ONLY", "SUPERSEDED_PROVENANCE_ONLY", "RETIRED_PROVENANCE_ONLY",
    "OPEN_DECISION_INPUT_ONLY",
]
NOT_AUTHORITY_PHRASES = ["not design authority", "설계 권위가 아니", "권위 문서가 아니"]
SUPERSEDED_MARKERS = ["SUPERSEDED"]
RETIRED_MARKERS = ["RETIRED"]
HANDOFF_MARKERS = ["DEVELOPMENT_HANDOFF", "HANDOFF"]
DESIGN_AUTHORITY_DECISION_MARKERS = ["DESIGN_AUTHORITY_DECISION"]
QA_MARKERS = [
    "QA_EXECUTION", "QA_STATUS", "QA_EVIDENCE", "QA_RERUN", "DESIGN_QA", "QA_",
    "EXECUTION_EVIDENCE", "BLOCKER_CLOSURE", "PROGRESS_REPORT", "PHASE_", "REVIEW_RESULT",
]
BARE_DRAFT_MARKERS = {"DESIGN_ONLY", "DRAFT", "NON_FINAL"}
ELIGIBLE_DISPOSITIONS = {"NORMATIVE_CURRENT", "NORMATIVE_REFINEMENT"}
ALLOWLIST_PATH = ROOT / "contracts" / "requirement-authority-source-allowlist.v1.json"


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def git_tracked_files() -> set[str]:
    result = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, check=False)
    if result.returncode != 0:
        raise RuntimeError("GIT_LS_FILES_FAILED")
    return {item.decode("utf-8") for item in result.stdout.split(b"\0") if item}


def allowlisted_authority_docs() -> list[Path]:
    allowlist = json.loads(ALLOWLIST_PATH.read_text(encoding="utf-8"))
    listed = list(allowlist["master_tree"]["paths"]) + list(allowlist["final_target_tree"]["paths"])
    tracked = git_tracked_files()
    missing = [path for path in listed if path not in tracked]
    if missing:
        raise RuntimeError(f"ALLOWLIST_ENTRY_NOT_GIT_TRACKED:{missing}")
    return sorted(ROOT / path for path in listed)


def load_seed_rows() -> dict[str, dict[str, Any]]:
    rows: dict[str, dict[str, Any]] = {}
    for seed_path in SEED_FILES:
        data = json.loads((ROOT / seed_path).read_text(encoding="utf-8"))
        for row in data["rows"]:
            rows[row["artifact_path"]] = row
    return rows


def extract_status_line(text: str) -> str:
    for line in text.splitlines()[:10]:
        if line.strip().startswith("Status:") or line.strip().startswith("Status :"):
            return line
    return ""


def classify_by_content(relative: str, text: str) -> tuple[str, str]:
    status_line = extract_status_line(text)
    lowered_text = text[:600].lower()
    for token in DISPOSITION_TOKENS:
        if token in status_line:
            return token, f"Status line self-declares '{token}'."
    if any(phrase in lowered_text for phrase in NOT_AUTHORITY_PHRASES):
        return "REFERENCE_ONLY", "Document explicitly states it is not design authority."
    if any(marker in status_line for marker in SUPERSEDED_MARKERS):
        return "SUPERSEDED_PROVENANCE_ONLY", "Status line marks document SUPERSEDED."
    if any(marker in status_line for marker in RETIRED_MARKERS):
        return "RETIRED_PROVENANCE_ONLY", "Status line marks document RETIRED."
    if any(marker in status_line for marker in HANDOFF_MARKERS):
        return "HANDOFF_ONLY", "Status line identifies document as a development handoff."
    if any(marker in status_line for marker in DESIGN_AUTHORITY_DECISION_MARKERS):
        return "NORMATIVE_REFINEMENT", "Status line identifies an explicit DESIGN_AUTHORITY_DECISION."
    if any(marker in status_line for marker in QA_MARKERS):
        return "QA_EVIDENCE_ONLY", "Status line identifies QA execution/status/evidence content."
    tokens = {t.strip("`* ") for t in re.split(r"[/,]", status_line.replace("Status:", "")) if t.strip()}
    if tokens and tokens <= BARE_DRAFT_MARKERS:
        return "UNREVIEWED", "Only marker is DESIGN_ONLY/DRAFT/NON_FINAL; requires content-role review before eligibility."
    return "UNREVIEWED", "No content-marker rule matched; fail-closed and ineligible."


def artifact_inventory_class(relative: str) -> str:
    if not relative.startswith("docs/master/"):
        return "FINAL_TARGET"
    if relative.startswith("docs/master/semantic-assurance/"):
        return "COMPANION"
    return "MASTER"


def review_summary(rows: list[dict[str, Any]]) -> dict[str, int]:
    eligible_rows = [r for r in rows if r["requirement_source_disposition"] in ELIGIBLE_DISPOSITIONS]
    ineligible_rows = [r for r in rows if r["requirement_source_disposition"] not in ELIGIBLE_DISPOSITIONS]
    eligible_unreviewed = sum(r["review_state"] == "UNREVIEWED" for r in eligible_rows)
    eligible_disputed = sum(r["review_state"] == "DISPUTED" for r in eligible_rows)
    total_unreviewed = sum(r["review_state"] == "UNREVIEWED" for r in rows)
    total_disputed = sum(r["review_state"] == "DISPUTED" for r in rows)
    return {
        "row_count": len(rows),
        "reviewed_count": sum(r["review_state"] == "REVIEWED" for r in rows),
        "unreviewed_count": eligible_unreviewed,
        "disputed_count": eligible_disputed,
        "total_unreviewed_count": total_unreviewed,
        "total_disputed_count": total_disputed,
        "eligible_count": len(eligible_rows),
        "ineligible_count": len(ineligible_rows),
        "eligible_unreviewed_count": eligible_unreviewed,
        "eligible_disputed_count": eligible_disputed,
        "ineligible_unreviewed_count": sum(r["review_state"] == "UNREVIEWED" for r in ineligible_rows),
    }


def main() -> int:
    seed_rows = load_seed_rows()
    docs = allowlisted_authority_docs()
    rows: list[dict[str, Any]] = []
    for doc in docs:
        relative = doc.relative_to(ROOT).as_posix()
        if relative == "docs/master/semantic-assurance/README.md":
            continue
        content = doc.read_bytes()
        content_sha256 = sha256_bytes(content)
        if relative in seed_rows:
            seed = seed_rows[relative]
            rows.append({
                "artifact_path": relative,
                "content_sha256": content_sha256,
                "artifact_inventory_authority_class": artifact_inventory_class(relative),
                "requirement_source_disposition": seed["disposition"],
                "authority_parent_refs": seed.get("authority_parent_refs", []),
                "supersedes": seed.get("supersedes", []),
                "rationale": seed.get("rationale", "Reviewed source-role seed disposition."),
                "review_state": seed.get("review_state", "REVIEWED"),
            })
            continue
        text = content.decode("utf-8", errors="replace")
        disposition, rationale = classify_by_content(relative, text)
        rows.append({
            "artifact_path": relative,
            "content_sha256": content_sha256,
            "artifact_inventory_authority_class": artifact_inventory_class(relative),
            "requirement_source_disposition": disposition,
            "authority_parent_refs": [],
            "supersedes": [],
            "rationale": rationale,
            "review_state": "REVIEWED" if disposition != "UNREVIEWED" else "UNREVIEWED",
        })
    rows.sort(key=lambda r: r["artifact_path"])
    population_digest = sha256_bytes("\n".join(
        f"{r['artifact_path']}:{r['content_sha256']}:{r['requirement_source_disposition']}" for r in rows).encode())
    summary = review_summary(rows)
    manifest = {
        "contract": "ONSURE_REQUIREMENT_AUTHORITY_SOURCE_MANIFEST_V1",
        "baseline_ref": "autopilot/design-closure-mission",
        "rows": rows,
        "population_digest": population_digest,
        "review_summary": summary,
        "gate_semantics": {
            "review_summary_unreviewed_disputed_are_eligible_blockers": True,
            "ineligible_unreviewed_is_disclosed_but_does_not_originate_requirements": True,
        },
        "final_claim_allowed": False,
    }
    out_dir = ROOT / ".onsure" / "requirement-universe"
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "requirement-authority-source-manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    eligible_paths = sorted(r["artifact_path"] for r in rows if r["requirement_source_disposition"] in ELIGIBLE_DISPOSITIONS)
    (out_dir / "eligible-authority-sources.json").write_text(
        json.dumps(eligible_paths, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({**summary, "population_digest": population_digest}))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError) as error:
        print(f"ONSURE_REQUIREMENT_AUTHORITY_MANIFEST_FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
