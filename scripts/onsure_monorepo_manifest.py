#!/usr/bin/env python3
"""Generate a nonfinal ONSure monorepo migration manifest candidate."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import pathlib
import pwd
import grp
import re
import stat as stat_module
import subprocess
from collections import Counter
from typing import Iterable

from onsure_product_root import resolve_product_root

ROOT = resolve_product_root()
DEFAULT_OUTPUT = ROOT / "assurance/migration/onsure-migration-manifest.v1.json"
CURRENT_NAMESPACE = "io.onsure"
FUTURE_NAMESPACE = "kr.co.oruda.products.onsure"
FUTURE_ROOT = "products/onsure"
ROOT_LICENSE = "LicenseRef-ORUDA-Labs-Proprietary"
COPYRIGHT_HOLDER = "ORUDA Labs"
RIGHTS_DECLARATION = "contracts/onsure-rights-declaration.v1.json"

HIGH_RISK_PATTERNS: tuple[tuple[str, re.Pattern[bytes]], ...] = (
    ("PRIVATE_KEY_PEM", re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")),
    ("GITHUB_TOKEN", re.compile(rb"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})\b")),
    ("AWS_ACCESS_KEY", re.compile(rb"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b")),
    ("SLACK_TOKEN", re.compile(rb"\bxox[baprs]-[A-Za-z0-9-]{20,}\b")),
)
REVIEW_PATTERNS: tuple[tuple[str, re.Pattern[bytes]], ...] = (
    (
        "LONG_SECRET_ASSIGNMENT",
        re.compile(
            rb"(?i)(?:password|secret|api[_-]?key|access[_-]?token)\s*[:=]\s*"
            rb"['\"]?[A-Za-z0-9/+_.=-]{20,}"
        ),
    ),
)
CUSTOMER_DATA_PATH_PARTS = {
    "customer-data",
    "customer_data",
    "production-data",
    "production_data",
    "validation-data",
    "receipts",
}


def run_git(*args: str) -> bytes:
    return subprocess.check_output(("git", *args), cwd=ROOT)


def candidate_paths(output: pathlib.Path = DEFAULT_OUTPUT) -> list[pathlib.Path]:
    raw = run_git("ls-files", "--cached", "--others", "--exclude-standard", "-z")
    output_relative = output.resolve().relative_to(ROOT).as_posix()
    paths: list[pathlib.Path] = []
    for value in raw.decode("utf-8").split("\0"):
        if not value or value == output_relative:
            continue
        path = ROOT / value
        if path.is_file() or path.is_symlink():
            paths.append(path)
    return sorted(paths, key=lambda item: item.relative_to(ROOT).as_posix())


def git_modes() -> dict[str, str]:
    modes: dict[str, str] = {}
    for line in run_git("ls-files", "-s", "-z").decode("utf-8").split("\0"):
        if not line:
            continue
        metadata, path = line.split("\t", 1)
        modes[path] = metadata.split(" ", 1)[0]
    return modes


def content_bytes(path: pathlib.Path) -> bytes:
    if path.is_symlink():
        return os.readlink(path).encode("utf-8")
    return path.read_bytes()


def detect_license(raw: bytes) -> str:
    head = raw[:8192].decode("utf-8", errors="ignore")
    match = re.search(
        r"(?m)^[#/*\s-]*SPDX-License-Identifier:\s*([A-Za-z0-9.+-]+)\s*(?:\*/)?$",
        head,
    )
    return match.group(1) if match else "UNDECLARED"


def sensitivity(path: pathlib.Path, raw: bytes) -> dict[str, object]:
    reasons = [name for name, pattern in HIGH_RISK_PATTERNS if pattern.search(raw)]
    review = [name for name, pattern in REVIEW_PATTERNS if pattern.search(raw)]
    path_parts = {part.lower() for part in path.parts}
    if path_parts & CUSTOMER_DATA_PATH_PARTS:
        review.append("CUSTOMER_OR_OPERATIONAL_DATA_PATH")
    if reasons:
        status = "HIGH_RISK_PATTERN_MATCH"
    elif review:
        status = "REVIEW_REQUIRED"
    else:
        status = "NO_PATTERN_MATCH"
    return {"status": status, "reasons": sorted(set(reasons + review))}


def future_path(relative_path: str) -> str:
    return f"{FUTURE_ROOT}/{relative_path}"


def normalized_git_mode(filesystem_mode: int) -> str:
    """Return the Git index mode an untracked filesystem entry will receive."""
    if stat_module.S_ISLNK(filesystem_mode):
        return "120000"
    return "100755" if filesystem_mode & 0o111 else "100644"


def file_entry(path: pathlib.Path, modes: dict[str, str]) -> dict[str, object]:
    relative = path.relative_to(ROOT).as_posix()
    raw = content_bytes(path)
    stat = path.lstat()
    try:
        owner = pwd.getpwuid(stat.st_uid).pw_name
    except KeyError:
        owner = str(stat.st_uid)
    try:
        group = grp.getgrgid(stat.st_gid).gr_name
    except KeyError:
        group = str(stat.st_gid)
    detected_license = detect_license(raw)
    return {
        "current_path": relative,
        "future_path_candidate": future_path(relative),
        "sha256": hashlib.sha256(raw).hexdigest(),
        "size_bytes": len(raw),
        "git_mode": modes.get(relative, normalized_git_mode(stat.st_mode)),
        "filesystem_owner": owner,
        "filesystem_group": group,
        "repository_owner": "babyandi/ONSure",
        "license": ROOT_LICENSE if detected_license == "UNDECLARED" else detected_license,
        "copyright_owner": COPYRIGHT_HOLDER,
        "sensitive_information": sensitivity(path, raw),
    }


def build_manifest(output: pathlib.Path = DEFAULT_OUTPUT) -> dict[str, object]:
    modes = git_modes()
    files = [file_entry(path, modes) for path in candidate_paths(output)]
    sensitivity_counts = Counter(
        str(item["sensitive_information"]["status"]) for item in files
    )
    license_counts = Counter(str(item["license"]) for item in files)
    return {
        "contract": "ONSURE_MONOREPO_MIGRATION_MANIFEST_CANDIDATE_V1",
        "decision": "CANDIDATE_NONFINAL",
        "source_repository": "babyandi/ONSure",
        "source_basis": {
            "scope": "GIT_TRACKED_PLUS_UNTRACKED_NONIGNORED_FILES",
            "commit_binding": "OMITTED_TO_AVOID_MANIFEST_COMMIT_SELF_REFERENCE",
            "immutable_cutover_commit_required": True,
        },
        "current_java_namespace": CURRENT_NAMESPACE,
        "future_java_namespace_candidate": FUTURE_NAMESPACE,
        "future_product_root_candidate": FUTURE_ROOT,
        "manifest_self_reference": {
            "path": output.resolve().relative_to(ROOT).as_posix(),
            "included": False,
            "reason": "SELF_DIGEST_RECURSION_PROHIBITED",
        },
        "ownership_interpretation": {
            "repository_owner": "GitHub repository owner, not a copyright conclusion",
            "filesystem_owner": "inspection-host metadata, to be normalized during migration",
            "copyright_owner": COPYRIGHT_HOLDER,
            "rights_declaration": RIGHTS_DECLARATION,
            "attestation_class": "OWNER_ATTESTED_NONFINAL",
            "copyright_basis": "OWNER_ATTESTATION_2026-08-04",
            "external_contributors_or_contractors": "NONE_ATTESTED",
            "copied_external_repository_code_or_assets": "NONE_ATTESTED",
        },
        "license_interpretation": {
            "default": ROOT_LICENSE,
            "root_license_file_present": any(
                item["current_path"].upper().startswith(("LICENSE", "COPYING", "NOTICE"))
                for item in files
            ),
        },
        "sensitivity_scan": {
            "method": "HIGH_CONFIDENCE_SECRET_PATTERNS_AND_PATH_HEURISTICS",
            "limitations": [
                "NOT_A_REPLACEMENT_FOR_HUMAN_DATA_CLASSIFICATION",
                "ENCRYPTED_OR_ENCODED_SECRETS_MAY_NOT_BE_DETECTED",
                "CUSTOMER_OPERATIONAL_DATA_REQUIRES_OWNER_ATTESTATION",
            ],
        },
        "summary": {
            "file_count": len(files),
            "total_size_bytes": sum(int(item["size_bytes"]) for item in files),
            "license_counts": dict(sorted(license_counts.items())),
            "sensitivity_counts": dict(sorted(sensitivity_counts.items())),
        },
        "files": files,
        "final_claim_allowed": False,
    }


def write_manifest(body: dict[str, object], output: pathlib.Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(body, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=pathlib.Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args(argv)
    output = args.output if args.output.is_absolute() else ROOT / args.output
    body = build_manifest(output)
    write_manifest(body, output)
    print(
        "ONSURE_MONOREPO_MANIFEST_GENERATED "
        f"files={body['summary']['file_count']} output={output.relative_to(ROOT)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
