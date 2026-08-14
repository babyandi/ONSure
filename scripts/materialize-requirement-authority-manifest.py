#!/usr/bin/env python3
"""Materialize the full Requirement Authority Source Manifest (145/DCQ-0002 follow-up).

Per 145_REQUIREMENT_AUTHORITY_POPULATION_CLOSURE_CHECKPOINT.md: DesignArtifactInventory
membership (a doc exists under docs/master) is NOT the same as eligibility to originate
a CURRENT Product Design Requirement. This script classifies every docs/master/**/*.md
file's requirement_source_disposition using content_marker_rules from
contracts/requirement-authority-classification-policy.candidate.v1.json, seeded by the
human-reviewed rows in the requirement-authority-source-review.seed*.json files pulled
from the qa/onsure-design-baseline-lock design-authority branch.

FAIL_CLOSED_ON_AMBIGUITY: anything not positively classifiable stays UNREVIEWED, which
is ineligible for the requirement-universe denominator (per
denominator_contribution_rules in requirement-authority-source-manifest.candidate.v1.json).
No LONGEST_TEXT_WINS, NUMERIC_PREFIX_ONLY, or FILENAME_ONLY classification (all explicitly
prohibited by the classification policy).
"""
from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]

SEED_FILES = [
    "contracts/requirement-authority-source-review.seed.v1.json",
    "contracts/requirement-authority-source-review.seed-semantic.v1.json",
    "contracts/requirement-authority-source-review.seed-learning-026-077.compact.v1.json",
    "contracts/requirement-authority-source-review.seed-learning-gates.v1.json",
]

# priority-ordered: first match wins
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


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    return sha256_bytes(path.read_bytes())


def git_tracked_master_docs() -> list[Path]:
    result = subprocess.run(
        ["git", "ls-files", "-z", "--", "docs/master"], cwd=ROOT, capture_output=True, check=False
    )
    if result.returncode != 0:
        raise RuntimeError("GIT_LS_FILES_FAILED")
    return sorted(
        ROOT / item.decode("utf-8") for item in result.stdout.split(b"\0") if item.endswith(b".md")
    )


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

    tokens = {t.strip() for t in re.split(r"[/,]", status_line.replace("Status:", "")) if t.strip()}
    tokens = {t.strip("`* ") for t in tokens}
    if tokens and tokens <= BARE_DRAFT_MARKERS:
        return "UNREVIEWED", "Only marker is DESIGN_ONLY/DRAFT/NON_FINAL; requires manual content-role review."

    return "UNREVIEWED", "No content-marker rule matched; fail-closed."


def artifact_inventory_class(relative: str) -> str:
    if relative.startswith("docs/master/semantic-assurance/"):
        return "COMPANION"
    return "MASTER"


def main() -> int:
    seed_rows = load_seed_rows()
    docs = git_tracked_master_docs()

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
                "rationale": seed.get("rationale", "Human-reviewed seed disposition."),
                "review_state": seed.get("review_state", "REVIEWED"),
            })
            continue

        text = content.decode("utf-8", errors="replace")
        disposition, rationale = classify_by_content(relative, text)
        review_state = "REVIEWED" if disposition != "UNREVIEWED" else "UNREVIEWED"
        rows.append({
            "artifact_path": relative,
            "content_sha256": content_sha256,
            "artifact_inventory_authority_class": artifact_inventory_class(relative),
            "requirement_source_disposition": disposition,
            "authority_parent_refs": [],
            "supersedes": [],
            "rationale": rationale,
            "review_state": review_state,
        })

    rows.sort(key=lambda r: r["artifact_path"])
    manifest_source = "\n".join(f"{r['artifact_path']}:{r['content_sha256']}:{r['requirement_source_disposition']}" for r in rows).encode()
    population_digest = sha256_bytes(manifest_source)

    reviewed = sum(1 for r in rows if r["review_state"] == "REVIEWED")
    unreviewed = sum(1 for r in rows if r["review_state"] == "UNREVIEWED")
    disputed = sum(1 for r in rows if r["review_state"] == "DISPUTED")
    eligible_dispositions = {"NORMATIVE_CURRENT", "NORMATIVE_REFINEMENT"}
    eligible = sum(1 for r in rows if r["requirement_source_disposition"] in eligible_dispositions)
    ineligible = len(rows) - eligible

    manifest = {
        "contract": "ONSURE_REQUIREMENT_AUTHORITY_SOURCE_MANIFEST_V1",
        "baseline_ref": "claude/onsure-development",
        "rows": rows,
        "population_digest": population_digest,
        "review_summary": {
            "row_count": len(rows),
            "reviewed_count": reviewed,
            "unreviewed_count": unreviewed,
            "disputed_count": disputed,
            "eligible_count": eligible,
            "ineligible_count": ineligible,
        },
        "final_claim_allowed": False,
    }

    out_dir = ROOT / ".onsure" / "requirement-universe"
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "requirement-authority-source-manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )

    eligible_paths = sorted(r["artifact_path"] for r in rows if r["requirement_source_disposition"] in eligible_dispositions)
    (out_dir / "eligible-authority-sources.json").write_text(
        json.dumps(eligible_paths, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    print(json.dumps({
        "row_count": len(rows),
        "eligible_count": eligible,
        "ineligible_count": ineligible,
        "unreviewed_count": unreviewed,
        "population_digest": population_digest,
    }))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError) as error:
        print(f"ONSURE_REQUIREMENT_AUTHORITY_MANIFEST_FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
