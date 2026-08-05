#!/usr/bin/env python3
"""Classify a Git worktree without modifying it or disclosing matched secret values."""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import subprocess
from collections import Counter
from typing import Any


GENERATED_PARTS = {
    "target", "build", "dist", "coverage", ".coverage", "node_modules",
    "__pycache__", ".pytest_cache", ".mypy_cache", ".ruff_cache",
}
GENERATED_SUFFIXES = {".class", ".jar", ".war", ".pyc", ".pyo", ".coverage"}
BACKUP_SUFFIXES = {".bak", ".backup", ".orig", ".rej", ".swp", ".swo", ".tmp"}
SENSITIVE_NAMES = {
    ".env", ".env.local", "id_rsa", "id_ed25519", "credentials.json",
    "secrets.json", "secrets.yaml", "secrets.yml",
}
SECRET_PATTERNS = {
    "private_key": re.compile(rb"BEGIN\s+(?:RSA |EC |OPENSSH )?PRIVATE KEY"),
    "aws_access_key": re.compile(rb"AKIA[0-9A-Z]{16}"),
    "github_token": re.compile(rb"gh[opsu]_[A-Za-z0-9_]{20,}"),
    "api_key": re.compile(rb"sk-[A-Za-z0-9]{20,}"),
}
CUSTOMER_PATTERNS = {
    "resident_id_candidate": re.compile(rb"(?<![0-9])[0-9]{6}-[1-4][0-9]{6}(?![0-9])"),
    "payment_card_candidate": re.compile(
        rb"(?<![0-9])[0-9]{4}[- ][0-9]{4}[- ][0-9]{4}[- ][0-9]{4}(?![0-9])"
    ),
}
CONFLICT_MARKER = re.compile(rb"(?m)^(?:<<<<<<< |=======\s*$|>>>>>>> )")
MAX_SCAN_BYTES = 5 * 1024 * 1024


def git(root: pathlib.Path, *args: str) -> bytes:
    return subprocess.check_output(["git", "-C", str(root), *args], stderr=subprocess.DEVNULL)


def status_entries(root: pathlib.Path) -> tuple[bytes, list[dict[str, Any]]]:
    raw = git(root, "status", "--porcelain=v1", "-z", "--untracked-files=all")
    records = raw.split(b"\0")
    entries: list[dict[str, Any]] = []
    index = 0
    while index < len(records):
        record = records[index]
        index += 1
        if not record:
            continue
        status = record[:2].decode("ascii", "replace")
        path = record[3:].decode("utf-8", "surrogateescape")
        original = None
        if status[0] in "RC" or status[1] in "RC":
            if index >= len(records) or not records[index]:
                raise ValueError("GIT_RENAME_RECORD_INVALID")
            original = records[index].decode("utf-8", "surrogateescape")
            index += 1
        entries.append({"status": status, "path": path, "original_path": original})
    return raw, entries


def source_kind(path: pathlib.PurePosixPath) -> str:
    value = path.as_posix()
    if "/test/" in f"/{value}/" or value.startswith("tests/") or value.startswith("fixtures/"):
        return "test_or_fixture"
    if value.startswith("docs/") or path.name.lower() in {"readme.md", "changelog.md", "agents.md"}:
        return "documentation"
    if value.startswith("contracts/"):
        return "contract"
    if value.startswith("status/") or value.startswith("evidence/"):
        return "status_or_evidence"
    if value.startswith("assets/") or value.startswith("vscode-extension/"):
        return "product_asset"
    if value.startswith("scripts/"):
        return "script"
    if path.name in {"pom.xml", "pom-modular.xml", "package.json", "package-lock.json", "pyproject.toml"}:
        return "build_configuration"
    if path.suffix.lower() in {".java", ".py", ".js", ".ts", ".css", ".html", ".sh"}:
        return "source_code"
    return "other_source"


def path_category(path: pathlib.PurePosixPath) -> str:
    lower_parts = {part.lower() for part in path.parts}
    suffix = path.suffix.lower()
    name = path.name.lower()
    if lower_parts & GENERATED_PARTS or suffix in GENERATED_SUFFIXES:
        return "generated"
    if suffix in BACKUP_SUFFIXES or name.endswith("~"):
        return "backup"
    if name in SENSITIVE_NAMES or name.startswith(".env.") or suffix in {".pem", ".key", ".p12", ".pfx"}:
        return "sensitive_candidate"
    return "source"


def scan_file(root: pathlib.Path, relative: str) -> dict[str, Any]:
    path = root / relative
    result: dict[str, Any] = {
        "secret_pattern_ids": [],
        "customer_data_pattern_ids": [],
        "conflict_markers": False,
        "scan_state": "NOT_PRESENT",
    }
    try:
        if not path.is_file() or path.is_symlink():
            return result
        size = path.stat().st_size
        if size > MAX_SCAN_BYTES:
            result["scan_state"] = "SKIPPED_TOO_LARGE"
            return result
        content = path.read_bytes()
    except OSError:
        result["scan_state"] = "READ_FAILED"
        return result
    result["scan_state"] = "SCANNED"
    result["secret_pattern_ids"] = sorted(
        name for name, pattern in SECRET_PATTERNS.items() if pattern.search(content)
    )
    result["customer_data_pattern_ids"] = sorted(
        name for name, pattern in CUSTOMER_PATTERNS.items() if pattern.search(content)
    )
    result["conflict_markers"] = bool(CONFLICT_MARKER.search(content))
    return result


def analyze(root: pathlib.Path) -> dict[str, Any]:
    root = root.resolve()
    raw, entries = status_entries(root)
    primary_counts: Counter[str] = Counter()
    source_counts: Counter[str] = Counter()
    status_counts: Counter[str] = Counter()
    classified: list[dict[str, Any]] = []
    for entry in entries:
        relative = entry["path"]
        path = pathlib.PurePosixPath(relative)
        scan = scan_file(root, relative)
        unmerged = "U" in entry["status"] or entry["status"] in {"AA", "DD"}
        category = path_category(path)
        if unmerged or scan["conflict_markers"]:
            category = "conflict"
        elif scan["secret_pattern_ids"] or scan["customer_data_pattern_ids"]:
            category = "sensitive_candidate"
        primary_counts[category] += 1
        status_counts[entry["status"]] += 1
        kind = source_kind(path) if category == "source" else None
        if kind:
            source_counts[kind] += 1
        classified.append({
            **entry,
            "category": category,
            "source_kind": kind,
            **scan,
        })

    head = git(root, "rev-parse", "HEAD").decode().strip()
    return {
        "contract": "ONSURE_READ_ONLY_WORKTREE_ANALYSIS_V1",
        "repository_name": root.name,
        "head": head,
        "entry_count": len(entries),
        "status_digest_sha256": hashlib.sha256(raw).hexdigest(),
        "primary_category_counts": dict(sorted(primary_counts.items())),
        "source_kind_counts": dict(sorted(source_counts.items())),
        "git_status_counts": dict(sorted(status_counts.items())),
        "secret_value_disclosure": False,
        "read_only": True,
        "entries": classified,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", type=pathlib.Path, default=pathlib.Path.cwd())
    parser.add_argument("--summary-only", action="store_true")
    args = parser.parse_args()
    result = analyze(args.repository)
    if args.summary_only:
        result.pop("entries", None)
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
