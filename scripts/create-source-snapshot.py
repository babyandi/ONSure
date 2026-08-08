#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
GIT_TIMEOUT_SECONDS = 20
GIT_MAX_OUTPUT_BYTES = 32 * 1024 * 1024


def git(*args: str, binary: bool = False):
    try:
        result = subprocess.run(
            ["git", *args], cwd=ROOT, capture_output=True, check=False,
            timeout=GIT_TIMEOUT_SECONDS,
        )
    except subprocess.TimeoutExpired as exc:
        raise RuntimeError("GIT_TIMEOUT:" + " ".join(args)) from exc
    if result.returncode != 0:
        raise RuntimeError("GIT_FAILED:" + " ".join(args))
    if len(result.stdout) > GIT_MAX_OUTPUT_BYTES or len(result.stderr) > GIT_MAX_OUTPUT_BYTES:
        raise RuntimeError("GIT_OUTPUT_LIMIT:" + " ".join(args))
    return result.stdout if binary else result.stdout.decode("utf-8").strip()


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()

    status = git("status", "--porcelain", "--untracked-files=all")
    if status:
        raise RuntimeError("WORKTREE_DIRTY_OR_UNTRACKED")

    commit = git("rev-parse", "HEAD")
    tree = git("rev-parse", "HEAD^{tree}")
    branch = git("branch", "--show-current")
    repository_root = git("rev-parse", "--show-toplevel")
    remote_url = git("config", "--get", "remote.origin.url")
    remote_main = git("rev-parse", "refs/remotes/origin/main")
    merge_base = git("merge-base", "HEAD", "refs/remotes/origin/main")
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
        "contract": "ONSURE_TRACKED_SOURCE_SNAPSHOT_V3",
        "repository_identity": pathlib.Path(repository_root).name,
        "remote_origin_url_sha256": sha256(remote_url.encode("utf-8")),
        "branch": branch,
        "commit_sha": commit,
        "git_tree_sha": tree,
        "remote_main_sha": remote_main,
        "merge_base_with_remote_main_sha": merge_base,
        "head_relation_to_remote_main": {
            "ahead": int(git("rev-list", "--count", "refs/remotes/origin/main..HEAD")),
            "behind": int(git("rev-list", "--count", "HEAD..refs/remotes/origin/main")),
        },
        "tracked_index_sha256": sha256(index),
        "tracked_content_sha256": aggregate.hexdigest(),
        "tracked_file_count": len(tracked),
        "worktree_clean_including_untracked": True,
        "git_timeout_seconds": GIT_TIMEOUT_SECONDS,
        "git_max_output_bytes": GIT_MAX_OUTPUT_BYTES,
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
