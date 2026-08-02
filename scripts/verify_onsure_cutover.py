#!/usr/bin/env python3
"""Verify source-to-products/onsure and rollback trees against the migration Manifest."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import pathlib
import stat
import sys
from typing import Iterable

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
DEFAULT_MANIFEST = ROOT / "assurance/migration/onsure-migration-manifest.v1.json"


def content(path: pathlib.Path) -> bytes:
    if path.is_symlink():
        return os.readlink(path).encode("utf-8")
    return path.read_bytes()


def git_mode(path: pathlib.Path) -> str:
    mode = path.lstat().st_mode
    if stat.S_ISLNK(mode):
        return "120000"
    return "100755" if mode & 0o111 else "100644"


def verify_tree(
        source_root: pathlib.Path,
        candidate_root: pathlib.Path,
        manifest: dict[str, object],
) -> dict[str, object]:
    violations: list[str] = []
    expected_paths: set[str] = set()
    for entry in manifest.get("files", []):
        relative = str(entry["current_path"])
        expected_paths.add(relative)
        expected_future = f"products/onsure/{relative}"
        if entry.get("future_path_candidate") != expected_future:
            violations.append(f"FUTURE_PATH_DRIFT:{relative}")
        for label, root in (("SOURCE", source_root), ("CANDIDATE", candidate_root)):
            path = root / relative
            if not path.is_file() and not path.is_symlink():
                violations.append(f"{label}_FILE_MISSING:{relative}")
                continue
            raw = content(path)
            if hashlib.sha256(raw).hexdigest() != entry.get("sha256"):
                violations.append(f"{label}_DIGEST_MISMATCH:{relative}")
            if len(raw) != entry.get("size_bytes"):
                violations.append(f"{label}_SIZE_MISMATCH:{relative}")
            if git_mode(path) != entry.get("git_mode"):
                violations.append(f"{label}_MODE_MISMATCH:{relative}")

    manifest_path = str(manifest["manifest_self_reference"]["path"])
    allowed = expected_paths | {manifest_path}
    actual = {
        path.relative_to(candidate_root).as_posix()
        for path in candidate_root.rglob("*")
        if path.is_file() or path.is_symlink()
    }
    extras = sorted(actual - allowed)
    missing = sorted(expected_paths - actual)
    if extras:
        violations.append("CANDIDATE_EXTRA_FILES:" + ",".join(extras))
    if missing:
        violations.append("CANDIDATE_MANIFEST_FILES_MISSING:" + ",".join(missing))
    return {
        "contract": "ONSURE_CUTOVER_TREE_VALIDATION_V1",
        "decision": "PASS_NONFINAL" if not violations else "FAIL",
        "violations": violations,
        "verified_file_count": len(expected_paths),
        "extra_file_count": len(extras),
        "missing_file_count": len(missing),
        "final_claim_allowed": False,
    }


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", type=pathlib.Path, default=ROOT)
    parser.add_argument("--candidate-root", type=pathlib.Path, required=True)
    parser.add_argument("--rollback-root", type=pathlib.Path)
    parser.add_argument("--manifest", type=pathlib.Path, default=DEFAULT_MANIFEST)
    args = parser.parse_args(argv)
    source = args.source_root.resolve()
    candidate = args.candidate_root.resolve()
    body = json.loads(args.manifest.read_text(encoding="utf-8"))
    result = verify_tree(source, candidate, body)
    if args.rollback_root is not None:
        rollback = verify_tree(source, args.rollback_root.resolve(), body)
        result["rollback_decision"] = rollback["decision"]
        result["rollback_violations"] = rollback["violations"]
        if rollback["decision"] != "PASS_NONFINAL":
            result["decision"] = "FAIL"
            result["violations"].append("ROLLBACK_TREE_VALIDATION_FAILED")
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["decision"] == "PASS_NONFINAL" else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"ONSURE_CUTOVER_TREE_FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
