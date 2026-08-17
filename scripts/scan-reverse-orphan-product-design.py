#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SNAPSHOT = ROOT / ".onsure/requirement-universe/requirement-universe-snapshot.json"
ID_PATTERN = re.compile(r"\b(?:FR-[A-Z]+-\d{2,3}|NFR-[A-Z]+(?:-\d{2,3})?|DD-\d{3})\b")
META_PREFIXES = ("scripts/", ".onsure/")
CATEGORIES = {
    "test": ("src/test/", (".java",)),
    "test_py": ("tests/", (".py",)),
    "evidence": ("status/", (".json",)),
    "contract": ("contracts/", (".json",)),
}


def tracked() -> list[str]:
    r = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True, text=True, check=False)
    if r.returncode:
        raise RuntimeError("GIT_LS_FILES_FAILED")
    return [x for x in r.stdout.splitlines() if x]


def main() -> int:
    if not SNAPSHOT.exists():
        raise RuntimeError("REQUIREMENT_UNIVERSE_SNAPSHOT_MISSING")
    known = set(json.loads(SNAPSHOT.read_text(encoding="utf-8"))["requirement_ids"])
    results = {}
    all_dangling = []
    for name, (prefix, suffixes) in CATEGORIES.items():
        scanned = real_count = 0
        no_id = []
        dangling = []
        for rel in tracked():
            if rel.startswith(META_PREFIXES) or not rel.startswith(prefix) or not rel.endswith(suffixes):
                continue
            p = ROOT / rel
            try:
                text = p.read_text(encoding="utf-8", errors="replace")
            except OSError:
                continue
            scanned += 1
            found = set(ID_PATTERN.findall(text))
            real = found & known
            stale = sorted(found - known)
            if real:
                real_count += 1
            elif not found:
                no_id.append(rel)
            if stale:
                row = {"file": rel, "stale_ids": stale}
                dangling.append(row)
                all_dangling.append(row)
        results[name] = {
            "scanned": scanned,
            "cites_current_requirement": real_count,
            "cites_no_requirement_id": len(no_id),
            "cites_stale_nonexistent_id": len(dangling),
            "dangling_citations": dangling,
            "no_citation_sample": no_id[:30],
        }
    report = {
        "contract": "ONSURE_PRODUCT_DESIGN_REVERSE_ORPHAN_SCAN_REPORT_V1",
        "known_requirement_id_count": len(known),
        "dd_requirement_count": len([x for x in known if x.startswith("DD-")]),
        "categories": results,
        "stale_reference_count": len(all_dangling),
        "disposition": "Disclosure scan. Zero-citation shared infrastructure is not automatically an orphan; stale references are actionable.",
        "final_claim_allowed": False,
    }
    out = ROOT / ".onsure/requirement-universe/product-design-reverse-orphan-scan-report.json"
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError, KeyError) as e:
        print(f"ONSURE_PRODUCT_DESIGN_REVERSE_ORPHAN_FAIL {e}", file=sys.stderr)
        raise SystemExit(1)
