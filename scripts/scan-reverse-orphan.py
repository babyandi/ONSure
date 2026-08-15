#!/usr/bin/env python3
"""Autonomous Development Mode standing-policy execution queue item 7: reverse orphan closure.

scan-global-trace-closure.py checks the FORWARD direction: for every requirement, does a
test/evidence/contract/design reference exist? This script checks the REVERSE direction doc 159
SS1 names explicitly: Evidence -> Test -> Implementation -> Contract -> Design -> Requirement ->
Authority. For every downstream artifact that exists (a test file, an evidence file, a contract
schema), does it trace back to a REAL, currently-known requirement ID -- or was it built without a
clear requirement justification (a genuine reverse orphan), or does it cite a requirement ID that
no longer exists in the current Requirement Universe (a dangling/stale reference)?

This is a disclosure tool, not a gate: a file citing zero requirement IDs is not automatically a
problem (some code is legitimate shared infrastructure -- e.g. HashChainRecordStore.java has no
single owning requirement because it supports many ledgers each satisfying their own requirement).
The report distinguishes "no citation found" from "cites a requirement ID that does not exist",
the latter being the more actionable finding (a stale reference that must be fixed or removed).
"""
from __future__ import annotations

import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]

NUMBERED_ID_PATTERN = re.compile(r"\b(?:FR|NFR)-[A-Z]+-\d{2,3}\b")
# Only NFR has bare (no numeric suffix) IDs in the current catalog (NFR-SESSION, NFR-REL, ...).
# Every real FR-* ID carries a numeric suffix -- a bare "FR-LEARN" mention in prose is category
# shorthand ("this relates to the Learning capability area"), not a citation of a specific,
# nonexistent requirement, and must NOT be treated as one (caught as a false positive: this
# script's own first draft flagged contracts/official-learning-ledger-entry.v1.schema.json's
# description "FR-LEARN: one hash-chained entry..." as a stale citation of a requirement called
# "FR-LEARN", which was never a real ID in the first place).
BARE_NFR_PATTERN = re.compile(r"\bNFR-[A-Z]+\b")

# Files whose entire purpose is meta-tooling over the requirement universe itself (generators,
# scanners, this script, and their own tests) legitimately cite many/most requirement IDs or none
# at all as part of exercising the mechanism -- excluded from reverse-orphan flagging the same way
# scan-global-trace-closure.py excludes META_TEST_FILES from its own forward scan.
META_FILES = {
    "scripts/generate-requirement-universe.py",
    "scripts/materialize-requirement-authority-manifest.py",
    "scripts/scan-global-trace-closure.py",
    "scripts/recalculate-orphan-severity.py",
    "scripts/scan-reverse-orphan.py",
    "tests/test_requirement_universe.py",
    "tests/test_target_assurance_wave1.py",
    "tests/test_repository_contracts.py",
    "tests/test_orphan_severity_recalculation.py",
}

DOWNSTREAM_CATEGORIES = {
    "test": (
        [".java"],
        ["src/test"],
    ),
    "test_py": (
        [".py"],
        ["tests"],
    ),
    "evidence": (
        [".json"],
        ["status"],
    ),
    "contract_schema": (
        [".schema.json"],
        ["contracts"],
    ),
}


def git_tracked_files() -> list[str]:
    result = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True, text=True, check=True)
    return [line for line in result.stdout.splitlines() if line]


def known_requirement_ids() -> set[str]:
    snapshot = json.loads((ROOT / ".onsure/requirement-universe/requirement-universe-snapshot.json").read_text(encoding="utf-8"))
    return set(snapshot["requirement_ids"])


def cited_ids(text: str) -> set[str]:
    return set(NUMBERED_ID_PATTERN.findall(text)) | set(BARE_NFR_PATTERN.findall(text))


def main() -> int:
    known_ids = known_requirement_ids()
    tracked = git_tracked_files()

    results: dict[str, dict] = {}
    for category, (suffixes, prefixes) in DOWNSTREAM_CATEGORIES.items():
        no_citation: list[str] = []
        dangling_citation: list[dict] = []
        cited_real: int = 0
        scanned: int = 0
        for relative in tracked:
            if relative in META_FILES:
                continue
            if not any(relative.startswith(prefix + "/") for prefix in prefixes):
                continue
            if category == "contract_schema":
                if not relative.endswith(".schema.json"):
                    continue
            elif not any(relative.endswith(suffix) for suffix in suffixes):
                continue
            path = ROOT / relative
            try:
                text = path.read_text(encoding="utf-8")
            except (UnicodeDecodeError, FileNotFoundError):
                continue
            scanned += 1
            found = cited_ids(text)
            real = found & known_ids
            stale = found - known_ids
            if real:
                cited_real += 1
            elif not found:
                no_citation.append(relative)
            if stale:
                dangling_citation.append({"file": relative, "stale_ids": sorted(stale)})

        results[category] = {
            "scanned": scanned,
            "cites_a_real_requirement_id": cited_real,
            "cites_no_requirement_id_at_all": len(no_citation),
            "cites_a_stale_nonexistent_id": len(dangling_citation),
            "no_citation_sample": no_citation[:30],
            "dangling_citations": dangling_citation,
        }

    report = {
        "contract": "ONSURE_REVERSE_ORPHAN_SCAN_REPORT_V1",
        "authority_ref": "docs/master/semantic-assurance/159_REVERSE_ALIGNMENT_AND_GLOBAL_LOCK_GATE_PREPARATION.md SS1; Autonomous Development Mode standing policy execution queue item 7",
        "known_requirement_id_count": len(known_ids),
        "categories": results,
        "disposition": (
            "DISCLOSURE_ONLY -- a file citing zero requirement IDs is not automatically a defect "
            "(shared infrastructure legitimately supports many requirements indirectly without "
            "citing any one by ID). cites_a_stale_nonexistent_id is the actionable finding: a "
            "file citing a requirement ID that no longer exists in the current Requirement "
            "Universe, which should be investigated (renamed/superseded requirement? typo? "
            "genuinely removed scope?)."
        ),
        "final_claim_allowed": False,
    }
    print(json.dumps(report, indent=2, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
