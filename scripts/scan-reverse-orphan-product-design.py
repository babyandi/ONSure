#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_UNIVERSE_DIR = ROOT / ".onsure/requirement-universe"
ID_PATTERN = re.compile(r"\b(?:FR-[A-Z]+-\d{2,3}|NFR-[A-Z]+(?:-\d{2,3})?|DD-\d{3})\b")
META_PREFIXES = ("scripts/", ".onsure/")
CATEGORIES = {
    "test": ("src/test/", (".java",)),
    "module_test": ("modules/", (".java",)),
    "test_py": ("tests/", (".py",)),
    "evidence": ("status/", (".json",)),
    "tracked_evidence": ("evidence/", (".json",)),
    "contract": ("contracts/", (".json",)),
}


def tracked() -> list[str]:
    r = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True, text=True, check=False)
    if r.returncode:
        raise RuntimeError("GIT_LS_FILES_FAILED")
    return [x for x in r.stdout.splitlines() if x]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--universe-dir", type=Path, default=DEFAULT_UNIVERSE_DIR)
    parser.add_argument("--requirements", type=Path,
                        help="Optional requirement-records.json override; known ids are read from its rows.")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    universe_dir = args.universe_dir if args.universe_dir.is_absolute() else ROOT / args.universe_dir
    universe_dir = universe_dir.resolve()
    snapshot_path = universe_dir / "requirement-universe-snapshot.json"
    if args.requirements:
        requirements_path = args.requirements if args.requirements.is_absolute() else ROOT / args.requirements
        rows = json.loads(requirements_path.read_text(encoding="utf-8"))
        known = {row["requirement_id"] for row in rows}
        universe_digest = None
    else:
        if not snapshot_path.is_file():
            raise RuntimeError(f"REQUIREMENT_UNIVERSE_SNAPSHOT_MISSING:{snapshot_path}")
        snapshot = json.loads(snapshot_path.read_text(encoding="utf-8"))
        known = set(snapshot["requirement_ids"])
        universe_digest = snapshot.get("requirement_manifest_digest")

    results = {}
    all_dangling: list[dict] = []
    tracked_files = tracked()
    for name, (prefix, suffixes) in CATEGORIES.items():
        scanned = real_count = 0
        no_id: list[str] = []
        dangling: list[dict] = []
        for rel in tracked_files:
            if rel.startswith(META_PREFIXES) or not rel.startswith(prefix) or not rel.endswith(suffixes):
                continue
            if name == "module_test" and "/src/test/" not in rel:
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

    # Deduplicate files that can appear in more than one reporting category.
    distinct_dangling = {(row["file"], tuple(row["stale_ids"])) for row in all_dangling}
    report = {
        "contract": "ONSURE_PRODUCT_DESIGN_REVERSE_ORPHAN_SCAN_REPORT_V1",
        "universe_directory": universe_dir.relative_to(ROOT).as_posix() if universe_dir.is_relative_to(ROOT) else str(universe_dir),
        "universe_digest": universe_digest,
        "known_requirement_id_count": len(known),
        "dd_requirement_count": len([x for x in known if x.startswith("DD-")]),
        "categories": results,
        "stale_reference_count": len(distinct_dangling),
        "disposition": "Disclosure scan. Zero-citation shared infrastructure is not automatically an orphan; stale references are actionable.",
        "final_claim_allowed": False,
    }
    out = args.output or (universe_dir / "product-design-reverse-orphan-scan-report.json")
    out = out if out.is_absolute() else ROOT / out
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError, KeyError) as e:
        print(f"ONSURE_PRODUCT_DESIGN_REVERSE_ORPHAN_FAIL {e}", file=sys.stderr)
        raise SystemExit(1)
