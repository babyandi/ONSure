#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]


def git(*args: str, binary: bool = False):
    result = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, check=False)
    if result.returncode != 0:
        raise RuntimeError("GIT_FAILED:" + " ".join(args))
    return result.stdout if binary else result.stdout.decode("utf-8").strip()


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()

    status = git("status", "--porcelain")
    if status:
        raise RuntimeError("WORKTREE_DIRTY_OR_UNTRACKED")

    commit = git("rev-parse", "HEAD")
    tree = git("rev-parse", "HEAD^{tree}")
    index = git("ls-files", "-s", "-z", binary=True)
    names = git("ls-files", "-z", binary=True)
    tracked = [value.decode("utf-8") for value in names.split(b"\0") if value]

    aggregate = hashlib.sha256()
    for relative in sorted(tracked):
        path = ROOT / relative
        if not path.is_file():
            raise RuntimeError(f"TRACKED_FILE_MISSING:{relative}")
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        aggregate.update(relative.encode("utf-8"))
        aggregate.update(b"\0")
        aggregate.update(digest.encode("ascii"))
        aggregate.update(b"\0")

    body = {
        "contract": "ONSURE_TRACKED_SOURCE_SNAPSHOT_V1",
        "commit_sha": commit,
        "git_tree_sha": tree,
        "tracked_index_sha256": sha256(index),
        "tracked_content_sha256": aggregate.hexdigest(),
        "tracked_file_count": len(tracked),
        "worktree_clean_including_untracked": True,
        "excluded_by_authority": [".git internals", "untracked files", "ignored runtime outputs"],
    }
    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(body, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"ONSURE_SOURCE_SNAPSHOT_PASS {output}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError) as error:
        print(f"ONSURE_SOURCE_SNAPSHOT_FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
