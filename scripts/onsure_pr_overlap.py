#!/usr/bin/env python3
"""Generate and validate a read-only overlap matrix for concurrent ONSure Draft PRs."""

from __future__ import annotations

import argparse
import json
import pathlib
import subprocess
import sys
from typing import Iterable

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
DEFAULT_OUTPUT = ROOT / "assurance/migration/onsure-open-pr-overlap.v1.json"
TARGET_PRS = (27, 28)
SUPERSEDED_BY = {27: 28}
REVALIDATION_PATHS = {
    "pom.xml",
    "pom-modular.xml",
    "requirements-validation.txt",
    "scripts/create-source-snapshot.py",
    "scripts/onsure-local-gate.sh",
    "scripts/onsure-one-shot.sh",
}


def command(*arguments: str) -> str:
    process = subprocess.run(
        list(arguments), cwd=ROOT, text=True, capture_output=True, check=False
    )
    if process.returncode != 0:
        raise ValueError(f"COMMAND_FAILED:{' '.join(arguments)}:{process.stderr.strip()}")
    return process.stdout


def local_changed_paths(output: pathlib.Path = DEFAULT_OUTPUT) -> set[str]:
    values: set[str] = set()
    for arguments in (
        ("git", "diff", "--name-only", "origin/main...HEAD"),
        ("git", "diff", "--name-only"),
        ("git", "ls-files", "--others", "--exclude-standard"),
    ):
        values.update(line for line in command(*arguments).splitlines() if line)
    values.discard(output.relative_to(ROOT).as_posix())
    return values


def fetch_pr(number: int) -> dict[str, object]:
    body = json.loads(command(
        "gh", "pr", "view", str(number), "--repo", "babyandi/ONSure",
        "--json", "number,title,isDraft,state,headRefName,headRefOid,baseRefName,url",
    ))
    filenames = command(
        "gh", "api", "--paginate",
        f"repos/babyandi/ONSure/pulls/{number}/files?per_page=100",
        "--jq", ".[].filename",
    ).splitlines()
    body["files"] = [{"path": value} for value in filenames if value]
    return body


def integrated_pr_numbers(prs: list[dict[str, object]]) -> set[int]:
    integrated: set[int] = set()
    for pr in prs:
        process = subprocess.run(
            ("git", "merge-base", "--is-ancestor", str(pr["headRefOid"]), "HEAD"),
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )
        if process.returncode == 0:
            integrated.add(int(pr["number"]))
        elif process.returncode != 1:
            raise ValueError(
                f"ANCESTRY_CHECK_FAILED:{pr['number']}:{process.stderr.strip()}"
            )
    return integrated


def build_matrix(
        local_paths: set[str],
        prs: list[dict[str, object]],
        pairwise_relationship: dict[str, object] | None = None,
        integrated: set[int] | None = None,
) -> dict[str, object]:
    integrated = integrated or set()
    entries: list[dict[str, object]] = []
    for pr in prs:
        number = int(pr["number"])
        paths = {str(item["path"]) for item in pr.get("files", [])}
        exact = sorted(local_paths & paths)
        revalidation = sorted(paths & REVALIDATION_PATHS)
        successor = SUPERSEDED_BY.get(number)
        if number in integrated:
            decision = "INTEGRATED_IN_CURRENT_BRANCH"
        elif successor in integrated:
            decision = f"SUPERSEDED_BY_INTEGRATED_PR_{successor}"
        elif exact:
            decision = "CONFLICT_RECONCILIATION_REQUIRED"
        elif revalidation:
            decision = "POST_MERGE_REVALIDATION_REQUIRED"
        else:
            decision = "NO_PATH_OVERLAP"
        entries.append({
            "number": pr["number"],
            "title": pr["title"],
            "url": pr.get("url"),
            "state": pr["state"],
            "draft": pr["isDraft"],
            "base": pr["baseRefName"],
            "head_branch": pr["headRefName"],
            "head_sha": pr["headRefOid"],
            "changed_file_count": len(paths),
            "exact_path_overlap": exact,
            "migration_gate_revalidation_paths": revalidation,
            "decision": decision,
        })
    blocking_decisions = {
        "CONFLICT_RECONCILIATION_REQUIRED",
        "POST_MERGE_REVALIDATION_REQUIRED",
    }
    merge_hold = any(
        entry["decision"] in blocking_decisions and entry["state"] == "OPEN"
        for entry in entries
    )
    return {
        "contract": "ONSURE_OPEN_PR_OVERLAP_V1",
        "source": "GITHUB_READ_ONLY_CURRENT_PR_HEADS",
        "repository": "babyandi/ONSure",
        "base": "origin/main",
        "local_changed_path_count": len(local_paths),
        "pull_requests": entries,
        "pairwise_relationship": pairwise_relationship or {"status": "NOT_EVALUATED"},
        "merge_decision": (
            "HOLD_MERGE_ORDER_REQUIRED" if merge_hold else "INTEGRATION_ORDER_RESOLVED"
        ),
        "required_actions": [
            "CHOOSE_PR_MERGE_ORDER",
            "REBASE_REMAINING_PRS_ON_CHOSEN_BASE",
            "REGENERATE_API_SBOM_MANIFEST_AND_OVERLAP_BASELINES",
            "RERUN_CANONICAL_MODULAR_NESTED_ROOT_GATES",
        ] if merge_hold else [],
        "automatic_merge_allowed": False,
        "final_claim_allowed": False,
    }


def current_matrix(output: pathlib.Path = DEFAULT_OUTPUT) -> dict[str, object]:
    prs = [fetch_pr(number) for number in TARGET_PRS]
    comparison = json.loads(command(
        "gh", "api",
        f"repos/babyandi/ONSure/compare/{prs[0]['headRefOid']}...{prs[1]['headRefOid']}",
    ))
    relationship = {
        "base_pr": prs[0]["number"],
        "head_pr": prs[1]["number"],
        "status": comparison["status"].upper(),
        "ahead_by": comparison["ahead_by"],
        "behind_by": comparison["behind_by"],
        "merge_base_sha": comparison["merge_base_commit"]["sha"],
    }
    return build_matrix(
        local_changed_paths(output),
        prs,
        relationship,
        integrated_pr_numbers(prs),
    )


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("generate", "validate"))
    parser.add_argument("--output", type=pathlib.Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args(argv)
    output = args.output if args.output.is_absolute() else ROOT / args.output
    actual = current_matrix(output)
    if args.mode == "generate":
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(
            json.dumps(actual, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        print(
            "ONSURE_PR_OVERLAP_GENERATED "
            f"prs={len(actual['pull_requests'])} decision={actual['merge_decision']}"
        )
        return 0
    if not output.is_file():
        raise ValueError("PR_OVERLAP_BASELINE_MISSING")
    expected = json.loads(output.read_text(encoding="utf-8"))
    violations = [] if expected == actual else ["OPEN_PR_OVERLAP_BASELINE_DRIFT"]
    result = {
        "contract": "ONSURE_OPEN_PR_OVERLAP_VALIDATION_V1",
        "decision": "PASS_NONFINAL" if not violations else "FAIL",
        "violations": violations,
        "merge_decision": actual["merge_decision"],
        "pull_request_count": len(actual["pull_requests"]),
        "final_claim_allowed": False,
    }
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if not violations else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"ONSURE_PR_OVERLAP_FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
