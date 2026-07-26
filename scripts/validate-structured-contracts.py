#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import pathlib
import subprocess
import sys
from typing import Any

ROOT = pathlib.Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "contracts/schema-instance-registry.v1.json"


def tracked_files(suffixes: tuple[str, ...]) -> list[pathlib.Path]:
    result = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, check=False)
    if result.returncode != 0:
        raise RuntimeError("GIT_LS_FILES_FAILED")
    return sorted(
        ROOT / value.decode("utf-8")
        for value in result.stdout.split(b"\0")
        if value and value.decode("utf-8").endswith(suffixes)
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--require-full", action="store_true")
    args = parser.parse_args()
    errors: list[str] = []
    limitations: list[str] = []

    try:
        import jsonschema  # type: ignore
    except ImportError:
        jsonschema = None
        limitations.append("JSONSCHEMA_PACKAGE_NOT_INSTALLED")
    try:
        import yaml  # type: ignore
    except ImportError:
        yaml = None
        limitations.append("PYYAML_PACKAGE_NOT_INSTALLED")

    registry: dict[str, Any] = json.loads(REGISTRY.read_text(encoding="utf-8"))
    for pair in registry.get("pairs", []):
        schema_path = ROOT / pair["schema"]
        try:
            schema = json.loads(schema_path.read_text(encoding="utf-8"))
            if jsonschema is not None:
                jsonschema.Draft202012Validator.check_schema(schema)
        except Exception as exc:  # noqa: BLE001
            errors.append(f"SCHEMA_INVALID:{pair['schema']}:{type(exc).__name__}:{exc}")
            continue
        for instance_name in pair.get("instances", []):
            try:
                instance = json.loads((ROOT / instance_name).read_text(encoding="utf-8"))
                if jsonschema is not None:
                    jsonschema.Draft202012Validator(schema).validate(instance)
            except Exception as exc:  # noqa: BLE001
                errors.append(f"INSTANCE_INVALID:{instance_name}:{type(exc).__name__}:{exc}")

    for path in tracked_files((".yaml", ".yml")):
        relative = path.relative_to(ROOT).as_posix()
        if yaml is None:
            continue
        try:
            documents = list(yaml.safe_load_all(path.read_text(encoding="utf-8")))
            if not documents:
                errors.append(f"YAML_EMPTY:{relative}")
        except Exception as exc:  # noqa: BLE001
            errors.append(f"YAML_INVALID:{relative}:{type(exc).__name__}:{exc}")

    if args.require_full and limitations:
        errors.extend(f"REQUIRED_DEPENDENCY_MISSING:{item}" for item in limitations)

    report = {
        "contract": "ONSURE_STRUCTURED_CONTRACT_VALIDATION_V1",
        "decision": "PASS" if not errors else "FAIL",
        "errors": errors,
        "limitations": limitations,
        "full_validation": not limitations,
        "runtime_execution": "NOT_RUN",
        "final_claim_allowed": False,
    }
    print(json.dumps(report, indent=2, sort_keys=True))
    if errors:
        print("ONSURE_STRUCTURED_CONTRACTS_FAIL", file=sys.stderr)
        return 1
    if limitations:
        print("ONSURE_STRUCTURED_CONTRACTS_SYNTAX_NONFINAL")
    else:
        print("ONSURE_STRUCTURED_CONTRACTS_PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError, json.JSONDecodeError) as error:
        print(f"ONSURE_STRUCTURED_CONTRACTS_FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
