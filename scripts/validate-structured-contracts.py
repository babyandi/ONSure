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


class DuplicateKeyError(ValueError):
    pass


def strict_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateKeyError(f"duplicate key: {key}")
        result[key] = value
    return result


def strict_json(path: pathlib.Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=strict_object)


def tracked_files(suffixes: tuple[str, ...]) -> list[pathlib.Path]:
    result = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, check=False)
    if result.returncode != 0:
        raise RuntimeError("GIT_LS_FILES_FAILED")
    return sorted(
        ROOT / value.decode("utf-8")
        for value in result.stdout.split(b"\0")
        if value and value.decode("utf-8").endswith(suffixes)
    )


def duplicate_safe_yaml_loader(yaml_module):
    class Loader(yaml_module.SafeLoader):
        pass

    def construct_mapping(loader, node, deep=False):
        mapping = {}
        for key_node, value_node in node.value:
            key = loader.construct_object(key_node, deep=deep)
            if key in mapping:
                raise DuplicateKeyError(f"duplicate yaml key: {key}")
            mapping[key] = loader.construct_object(value_node, deep=deep)
        return mapping

    Loader.add_constructor(
        yaml_module.resolver.BaseResolver.DEFAULT_MAPPING_TAG,
        construct_mapping,
    )
    return Loader


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

    registry: dict[str, Any] = strict_json(REGISTRY)
    positive_count = 0
    negative_count = 0
    registered_schemas: set[str] = set()
    for pair in registry.get("pairs", []):
        schema_name = pair.get("schema")
        if not isinstance(schema_name, str) or schema_name in registered_schemas:
            errors.append(f"SCHEMA_REGISTRY_ENTRY_INVALID_OR_DUPLICATE:{schema_name}")
            continue
        registered_schemas.add(schema_name)
        schema_path = ROOT / schema_name
        try:
            schema = strict_json(schema_path)
            if jsonschema is not None:
                jsonschema.Draft202012Validator.check_schema(schema)
        except Exception as exc:  # noqa: BLE001
            errors.append(f"SCHEMA_INVALID:{schema_name}:{type(exc).__name__}:{exc}")
            continue

        for instance_name in pair.get("instances", []):
            positive_count += 1
            try:
                instance = strict_json(ROOT / instance_name)
                if jsonschema is not None:
                    jsonschema.Draft202012Validator(schema).validate(instance)
            except Exception as exc:  # noqa: BLE001
                errors.append(f"POSITIVE_INSTANCE_INVALID:{instance_name}:{type(exc).__name__}:{exc}")

        for instance_name in pair.get("invalid_instances", []):
            negative_count += 1
            try:
                instance = strict_json(ROOT / instance_name)
            except Exception as exc:  # malformed JSON is also a valid negative fixture outcome
                if isinstance(exc, (json.JSONDecodeError, DuplicateKeyError)):
                    continue
                errors.append(f"NEGATIVE_INSTANCE_UNREADABLE:{instance_name}:{type(exc).__name__}:{exc}")
                continue
            if jsonschema is None:
                continue
            try:
                jsonschema.Draft202012Validator(schema).validate(instance)
                errors.append(f"NEGATIVE_INSTANCE_UNEXPECTED_PASS:{instance_name}")
            except jsonschema.ValidationError:
                pass
            except Exception as exc:  # noqa: BLE001
                errors.append(f"NEGATIVE_INSTANCE_VALIDATOR_ERROR:{instance_name}:{type(exc).__name__}:{exc}")

    if registry.get("negative_instances_required_before_final") is not True:
        errors.append("NEGATIVE_INSTANCE_POLICY_NOT_ENABLED")
    if negative_count == 0:
        errors.append("NEGATIVE_INSTANCE_SET_EMPTY")

    for path in tracked_files((".json", ".jsonl")):
        relative = path.relative_to(ROOT).as_posix()
        try:
            if path.suffix == ".json":
                strict_json(path)
            else:
                lines = path.read_text(encoding="utf-8").splitlines()
                if not lines:
                    errors.append(f"JSONL_EMPTY:{relative}")
                for line_number, line in enumerate(lines, 1):
                    if not line.strip():
                        errors.append(f"JSONL_BLANK_LINE:{relative}:{line_number}")
                        continue
                    json.loads(line, object_pairs_hook=strict_object)
        except Exception as exc:  # noqa: BLE001
            errors.append(f"STRUCTURED_JSON_INVALID:{relative}:{type(exc).__name__}:{exc}")

    yaml_loader = duplicate_safe_yaml_loader(yaml) if yaml is not None else None
    for path in tracked_files((".yaml", ".yml")):
        relative = path.relative_to(ROOT).as_posix()
        if yaml_loader is None:
            continue
        try:
            documents = list(yaml.load_all(path.read_text(encoding="utf-8"), Loader=yaml_loader))
            if not documents:
                errors.append(f"YAML_EMPTY:{relative}")
        except Exception as exc:  # noqa: BLE001
            errors.append(f"YAML_INVALID:{relative}:{type(exc).__name__}:{exc}")

    if args.require_full and limitations:
        errors.extend(f"REQUIRED_DEPENDENCY_MISSING:{item}" for item in limitations)

    report = {
        "contract": "ONSURE_STRUCTURED_CONTRACT_VALIDATION_V2",
        "decision": "PASS" if not errors else "FAIL",
        "errors": errors,
        "limitations": limitations,
        "registered_schema_count": len(registered_schemas),
        "positive_instance_count": positive_count,
        "negative_instance_count": negative_count,
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
