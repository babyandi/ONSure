#!/usr/bin/env python3
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import pathlib
import shutil
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]


def run(command: list[str], log: pathlib.Path, env: dict[str, str] | None = None) -> int:
    with log.open("w", encoding="utf-8") as output:
        process = subprocess.run(
            command,
            cwd=ROOT,
            env=env,
            stdout=output,
            stderr=subprocess.STDOUT,
            text=True,
            check=False,
        )
    return process.returncode


def digest(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require_clean_source() -> str:
    status = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=True,
    ).stdout
    if status:
        raise RuntimeError("WORKTREE_DIRTY_OR_UNTRACKED")
    return subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=True,
    ).stdout.strip()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--profile",
        choices=("core", "oruda", "full"),
        default="core",
        help="full includes the core product and the ORUDA adapter lane",
    )
    parser.add_argument(
        "--stage",
        choices=("prepare", "codespace-final", "auto", "all"),
        default="auto",
        help="auto runs Codespace final only when its complete toolchain is present",
    )
    parser.add_argument(
        "--repeat",
        type=int,
        default=2,
        help="number of identical-source Full Gate runs (1-5)",
    )
    parser.add_argument(
        "--fail-closed",
        action="store_true",
        help="return HOLD until independent verification and human approvals exist",
    )
    args = parser.parse_args()
    if not 1 <= args.repeat <= 5:
        parser.error("--repeat must be between 1 and 5")
    try:
        source_commit = require_clean_source()
    except (RuntimeError, subprocess.CalledProcessError) as failure:
        print(f"ONSURE_INTEGRATED_RUN_FAIL {failure}", file=sys.stderr)
        return 72

    required_full_tools = ("java", "javac", "mvn", "bwrap", "prlimit", "node", "npm")
    full_toolchain = all(shutil.which(tool) for tool in required_full_tools)
    effective_stage = args.stage
    if effective_stage == "all":
        effective_stage = "codespace-final"
    elif effective_stage == "auto":
        effective_stage = "codespace-final" if full_toolchain else "prepare"
    if effective_stage == "codespace-final" and not full_toolchain:
        missing = [tool for tool in required_full_tools if not shutil.which(tool)]
        print(f"ONSURE_INTEGRATED_RUN_BLOCKED MISSING_FULL_TOOLCHAIN_{'_'.join(missing)}", file=sys.stderr)
        return 69

    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    output_root = pathlib.Path(
        os.environ.get(
            "ONSURE_INTEGRATED_OUTPUT",
            str(ROOT / ".onsure" / "integrated-run" / f"{stamp}-{os.getpid()}"),
        )
    )
    output_root.mkdir(parents=True, exist_ok=False)
    steps: list[dict[str, object]] = []

    execution_profile = "oruda" if args.profile == "full" else args.profile
    commands = [
        ("design-baseline-runtime", [sys.executable, "scripts/validate-design-baseline-runtime.py"]),
        ("authority-consistency", [sys.executable, "scripts/validate-final-authority-consistency.py"]),
        ("python-regression", [sys.executable, "-m", "unittest", "discover", "-s", "tests", "-p", "test_*.py", "-v"]),
        ("shell-syntax", ["bash", "scripts/check-shell-syntax.sh"]),
    ]
    commands.extend(
        (
            f"static-repeat-{iteration}",
            ["bash", "scripts/onsure-one-shot.sh", "--profile", execution_profile, "--static-only"],
        )
        for iteration in range(1, args.repeat + 1)
    )
    if effective_stage == "codespace-final":
        commands.extend(
            (
                f"codespace-final-{iteration}",
                ["bash", "scripts/onsure-final-stage.sh", "--profile", execution_profile],
            )
            for iteration in range(1, args.repeat + 1)
        )

    decision = "PASS_NONFINAL"
    for name, command in commands:
        log = output_root / f"{name}.log"
        exit_code = run(command, log)
        steps.append(
            {
                "name": name,
                "command": command,
                "exit_code": exit_code,
                "log": log.name,
                "log_sha256": digest(log),
            }
        )
        if exit_code:
            decision = "BLOCKED" if exit_code in (69, 75, 78) else "FAIL"
            break

    end_commit = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=True,
    ).stdout.strip()
    if end_commit != source_commit:
        decision = "FAIL"
        steps.append({"name": "source-lock", "exit_code": 73, "reason": "SOURCE_COMMIT_DRIFT"})

    result = {
        "contract": "ONSURE_INTEGRATED_RUN_RESULT_V1",
        "source_commit": source_commit,
        "profile": args.profile,
        "execution_profile": execution_profile,
        "repeat": args.repeat,
        "fail_closed": args.fail_closed,
        "requested_stage": args.stage,
        "effective_stage": effective_stage,
        "github_actions": "DISABLED",
        "decision": decision,
        "steps": steps,
        "codespace_full_gate": (
            f"PASS_NONFINAL_{args.repeat}X"
            if effective_stage == "codespace-final" and decision == "PASS_NONFINAL"
            else "NOT_RUN"
        ),
        "independent_otester_two_clean": "NOT_RUN",
        "independent_oaudit_two_clean": "NOT_RUN",
        "human_approval": "NOT_RUN",
        "final_lock_allowed": False,
        "production_go": False,
        "commercial_go": False,
    }
    result_path = output_root / "result.json"
    result_path.write_text(
        json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    (output_root / "evidence.sha256").write_text(
        "\n".join(
            f"{digest(path)}  {path.name}"
            for path in sorted(output_root.iterdir())
            if path.name != "evidence.sha256"
        )
        + "\n",
        encoding="utf-8",
    )
    if decision != "PASS_NONFINAL":
        print(f"ONSURE_INTEGRATED_RUN_{decision} {output_root}", file=sys.stderr)
        return next((int(step["exit_code"]) for step in reversed(steps) if step.get("exit_code")), 1)
    if args.fail_closed:
        print(f"ONSURE_INTEGRATED_RUN_HOLD_INDEPENDENT_APPROVAL_REQUIRED {output_root}", file=sys.stderr)
        return 75
    print(f"ONSURE_INTEGRATED_RUN_PASS_NONFINAL {output_root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
