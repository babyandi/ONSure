#!/usr/bin/env python3
"""Validate tracked JSON Schema instances, YAML documents, and JSONL records."""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import subprocess
import sys
from typing import Any

ROOT = pathlib.Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "contracts/schema-instance-registry.v1.json"

try:
    import jsonschema
    import yaml
except ImportError as exc:
    print(f"ONSURE_STRUCTURED_CONTRACTS_DEPENDENCY_MISSING {exc}", file=sys.stderr)
    raise SystemExit(69)


def tracked_files() -> list[pathlib.Path]:
    result = subprocess.run(
        ["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, check=False
    )
    if result.returncode:
        raise RuntimeError("GIT_LS_FILES_FAILED")
    return sorted(
        (ROOT / raw.decode("utf-8")).resolve()
        for raw in result.stdout.split(b"\0")
        if raw
    )


def rel(path: pathlib.Path) -> str:
    return path.relative_to(ROOT).as_posix()


def digest(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_json(path: pathlib.Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def validate_yaml(files: list[pathlib.Path], errors: list[str]) -> dict[str, str]:
    values: dict[str, str] = {}
    for path in files:
        if path.suffix.lower() not in {".yaml", ".yml"}:
            continue
        relative = rel(path)
        try:
            documents = list(yaml.safe_load_all(path.read_text(encoding="utf-8")))
            if not documents:
                errors.append(f"YAML_EMPTY:{relative}")
            values[relative] = digest(path)
        except Exception as exc:
            errors.append(f"YAML_INVALID:{relative}:{type(exc).__name__}:{exc}")
    return values


def validate_jsonl(files: list[pathlib.Path], errors: list[str]) -> dict[str, str]:
    values: dict[str, str] = {}
    for path in files:
        if path.suffix.lower() != ".jsonl":
            continue
        relative = rel(path)
        try:
            lines = path.read_text(encoding="utf-8").splitlines()
            if not lines:
                errors.append(f"JSONL_EMPTY:{relative}")
                continue
            for line_number, line in enumerate(lines, start=1):
                if not line.strip():
                    errors.append(f"JSONL_BLANK:{relative}:{line_number}")
                    continue
                if not isinstance(json.loads(line), dict):
                    errors.append(f"JSONL_RECORD_NOT_OBJECT:{relative}:{line_number}")
            values[relative] = digest(path)
        except Exception as exc:
            errors.append(f"JSONL_INVALID:{relative}:{type(exc).__name__}:{exc}")
    return values


def validate_schemas(files: list[pathlib.Path], errors: list[str]) -> dict[str, Any]:
    schemas: dict[str, Any] = {}
    for path in files:
        relative = rel(path)
        if not relative.endswith(".schema.json"):
            continue
        try:
            schema = load_json(path)
            jsonschema.Draft202012Validator.check_schema(schema)
            schemas[relative] = schema
        except Exception as exc:
            errors.append(f"JSON_SCHEMA_INVALID:{relative}:{type(exc).__name__}:{exc}")
    return schemas


def validate_instances(schemas: dict[str, Any], errors: list[str]) -> list[dict[str, Any]]:
    registry = load_json(REGISTRY)
    if registry.get("contract") != "ONSURE_SCHEMA_INSTANCE_REGISTRY_V1":
        errors.append("SCHEMA_INSTANCE_REGISTRY_CONTRACT_INVALID")
        return []
    results: list[dict[str, Any]] = []
    seen: set[str] = set()
    for binding in registry.get("bindings", []):
        schema_path = binding.get("schema")
        if not isinstance(schema_path, str) or schema_path in seen:
            errors.append(f"SCHEMA_BINDING_INVALID_OR_DUPLICATE:{schema_path}")
            continue
        seen.add(schema_path)
        schema = schemas.get(schema_path)
        if schema is None:
            errors.append(f"SCHEMA_BINDING_UNKNOWN_SCHEMA:{schema_path}")
            continue
        instances = binding.get("instances", [])
        if not isinstance(instances, list):
            errors.append(f"SCHEMA_BINDING_INSTANCES_NOT_LIST:{schema_path}")
            continue
        if binding.get("required") is True and not instances:
            errors.append(f"REQUIRED_SCHEMA_INSTANCE_MISSING:{schema_path}")
        validator = jsonschema.Draft202012Validator(
            schema, format_checker=jsonschema.FormatChecker()
        )
        valid_count = 0
        for relative in instances:
            path = ROOT / relative
            if not path.is_file():
                errors.append(f"SCHEMA_INSTANCE_FILE_MISSING:{schema_path}:{relative}")
                continue
            instance = load_json(path)
            violations = sorted(
                validator.iter_errors(instance), key=lambda value: list(value.absolute_path)
            )
            if violations:
                for violation in violations:
                    pointer = "/".join(str(item) for item in violation.absolute_path)
                    errors.append(
                        f"SCHEMA_INSTANCE_INVALID:{schema_path}:{relative}:{pointer}:{violation.message}"
                    )
            else:
                valid_count += 1
        state = "PASS" if instances and valid_count == len(instances) else binding.get("state", "NOT_RUN")
        results.append(
            {
                "schema": schema_path,
                "scope": binding.get("scope"),
                "required": bool(binding.get("required")),
                "instance_count": len(instances),
                "valid_count": valid_count,
                "state": state,
            }
        )
    return results


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=pathlib.Path)
    args = parser.parse_args()

    errors: list[str] = []
    files = tracked_files()
    yaml_digests = validate_yaml(files, errors)
    jsonl_digests = validate_jsonl(files, errors)
    schemas = validate_schemas(files, errors)
    bindings = validate_instances(schemas, errors)
    report = {
        "contract": "ONSURE_STRUCTURED_CONTRACT_VALIDATION_REPORT_V1",
        "decision": "PASS_NONFINAL" if not errors else "FAIL",
        "errors": errors,
        "schema_count": len(schemas),
        "schema_bindings": bindings,
        "yaml_digests": yaml_digests,
        "jsonl_digests": jsonl_digests,
        "runtime_generated_instances": "NOT_RUN",
        "final_claim_allowed": False,
    }
    serialized = json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if args.output:
        output = args.output.resolve()
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(serialized, encoding="utf-8")
    print(serialized, end="")
    if errors:
        print("ONSURE_STRUCTURED_CONTRACTS_FAIL", file=sys.stderr)
        return 1
    print("ONSURE_STRUCTURED_CONTRACTS_PASS_NONFINAL")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
