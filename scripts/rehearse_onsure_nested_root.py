#!/usr/bin/env python3
"""Rehearse ONSure under an isolated products/onsure Git root without external repositories."""

from __future__ import annotations

import argparse
import json
import pathlib
import shutil
import subprocess
import tempfile
from typing import Iterable

from onsure_product_root import resolve_product_root
from verify_onsure_cutover import verify_tree


ROOT = resolve_product_root()
MANIFEST = ROOT / "assurance/migration/onsure-migration-manifest.v1.json"


def candidate_paths() -> list[pathlib.Path]:
    process = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        cwd=ROOT,
        capture_output=True,
        check=True,
    )
    return sorted(
        ROOT / value.decode("utf-8")
        for value in process.stdout.split(b"\0")
        if value and (ROOT / value.decode("utf-8")).is_file()
    )


def copy_product(destination: pathlib.Path) -> None:
    for source in candidate_paths():
        relative = source.relative_to(ROOT)
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target, follow_symlinks=False)


def run(command: list[str], cwd: pathlib.Path, environment: dict[str, str] | None = None) -> None:
    import os

    merged = os.environ.copy()
    if environment:
        merged.update(environment)
    process = subprocess.run(
        command, cwd=cwd, env=merged, text=True, capture_output=True, check=False
    )
    if process.returncode != 0:
        raise RuntimeError(
            "NESTED_ROOT_COMMAND_FAILED:"
            + " ".join(command)
            + "\n"
            + (process.stdout + process.stderr)[-6000:]
        )


def rehearse(mode: str) -> dict[str, object]:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    with tempfile.TemporaryDirectory(prefix="onsure-nested-root-") as temporary:
        workspace = pathlib.Path(temporary)
        monorepo = workspace / "monorepo"
        product = monorepo / "products/onsure"
        rollback = workspace / "rollback/onsure"
        copy_product(product)
        copy_product(rollback)
        cutover = verify_tree(ROOT, product, manifest)
        rollback_result = verify_tree(ROOT, rollback, manifest)
        if cutover["decision"] != "PASS_NONFINAL":
            raise RuntimeError(f"CUTOVER_REHEARSAL_FAILED:{cutover['violations']}")
        if rollback_result["decision"] != "PASS_NONFINAL":
            raise RuntimeError(f"ROLLBACK_REHEARSAL_FAILED:{rollback_result['violations']}")

        run(["git", "init", "-q", "-b", "main"], monorepo)
        run(["git", "config", "user.name", "ONSure Rehearsal"], monorepo)
        run(["git", "config", "user.email", "rehearsal@invalid.local"], monorepo)
        run(
            [
                "git",
                "remote",
                "add",
                "origin",
                "https://github.com/babyandi/ONSure.git",
            ],
            monorepo,
        )
        run(["git", "add", "products/onsure"], monorepo)
        run(["git", "commit", "-q", "-m", "rehearsal baseline"], monorepo)
        run(
            ["git", "update-ref", "refs/remotes/origin/main", "HEAD"],
            monorepo,
        )
        environment = {"ONSURE_PRODUCT_ROOT": str(product)}
        commands = [
            ["python3", "scripts/validate_onsure_build_boundary.py"],
            ["python3", "scripts/validate_onsure_product_metadata.py"],
            ["python3", "scripts/validate_monorepo_migration_readiness.py"],
        ]
        if mode == "full":
            commands.extend([
                ["mvn", "-B", "-ntp", "-q", "clean", "verify"],
                ["python3", "scripts/onsure_java_api_baseline.py", "validate"],
                ["mvn", "-B", "-ntp", "-q", "-f", "pom-modular.xml", "clean", "package"],
                ["python3", "-m", "unittest", "discover", "-s", "tests"],
                ["python3", "scripts/onsure_supply_chain.py", "validate"],
                ["bash", "scripts/onsure-local-gate.sh", "--mode", "static", "--profile", "core"],
            ])
        for command in commands:
            run(command, product, environment)
        return {
            "contract": "ONSURE_NESTED_PRODUCT_ROOT_REHEARSAL_V1",
            "decision": "PASS_NONFINAL",
            "mode": mode,
            "future_root": "products/onsure",
            "cutover_verified_file_count": cutover["verified_file_count"],
            "rollback_verified_file_count": rollback_result["verified_file_count"],
            "command_count": len(commands),
            "external_product_repository_used": False,
            "temporary_tree_removed": True,
            "final_claim_allowed": False,
        }


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=("static", "full"), default="static")
    args = parser.parse_args(argv)
    try:
        result = rehearse(args.mode)
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        print(json.dumps({
            "contract": "ONSURE_NESTED_PRODUCT_ROOT_REHEARSAL_V1",
            "decision": "FAIL",
            "error": str(error),
            "final_claim_allowed": False,
        }, indent=2, sort_keys=True))
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
