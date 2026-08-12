#!/usr/bin/env python3
"""Validate ONSure Semantic Assurance v2 candidate schemas and fixtures.

This validator is NON_FINAL. It validates schema/fixture conformance only and does
not grant Runtime, Independent, FinalLock, Production, or Commercial authority.
"""
from __future__ import annotations

import hashlib
import json
import sys
from dataclasses import dataclass, asdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

try:
    import jsonschema
except ImportError as exc:  # fail closed
    raise SystemExit("jsonschema is required; validation was NOT_RUN") from exc

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "contracts/semantic-assurance-v2-schema-instance-registry.candidate.v1.json"
OUTPUT = ROOT / ".onsure/semantic-assurance-v2-static-validation.json"


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def load_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as fh:
        return json.load(fh)


@dataclass
class InstanceResult:
    schema: str
    instance: str
    expected: str
    observed: str
    schema_sha256: str
    instance_sha256: str
    error: str | None = None


def validate_instance(schema_path: Path, instance_path: Path, expected_valid: bool) -> InstanceResult:
    schema = load_json(schema_path)
    instance = load_json(instance_path)
    observed_valid = True
    error = None
    try:
        jsonschema.Draft202012Validator.check_schema(schema)
        jsonschema.Draft202012Validator(schema).validate(instance)
    except Exception as exc:  # semantic invalid fixtures are expected to land here
        observed_valid = False
        error = f"{type(exc).__name__}: {exc}"

    expected = "VALID" if expected_valid else "INVALID"
    observed = "VALID" if observed_valid else "INVALID"
    return InstanceResult(
        schema=str(schema_path.relative_to(ROOT)),
        instance=str(instance_path.relative_to(ROOT)),
        expected=expected,
        observed=observed,
        schema_sha256=sha256_file(schema_path),
        instance_sha256=sha256_file(instance_path),
        error=error,
    )


def main() -> int:
    if not REGISTRY.exists():
        print(f"registry missing: {REGISTRY}", file=sys.stderr)
        return 2

    registry = load_json(REGISTRY)
    results: list[InstanceResult] = []
    missing: list[str] = []

    for pair in registry.get("pairs", []):
        schema_path = ROOT / pair["schema"]
        if not schema_path.exists():
            missing.append(pair["schema"])
            continue
        for rel in pair.get("valid_instances", []):
            p = ROOT / rel
            if not p.exists():
                missing.append(rel)
                continue
            results.append(validate_instance(schema_path, p, True))
        for rel in pair.get("invalid_instances", []):
            p = ROOT / rel
            if not p.exists():
                missing.append(rel)
                continue
            results.append(validate_instance(schema_path, p, False))

    mismatches = [r for r in results if r.expected != r.observed]
    decision = "PASS_NONFINAL" if not missing and not mismatches else "FAIL"
    receipt = {
        "contract": "ONSURE_SEMANTIC_ASSURANCE_V2_STATIC_VALIDATION_RECEIPT_V1",
        "assurance_class": "SELF_VALIDATION_NONFINAL",
        "independent": False,
        "registry": str(REGISTRY.relative_to(ROOT)),
        "registry_sha256": sha256_file(REGISTRY),
        "executed_at": datetime.now(timezone.utc).isoformat(),
        "result_count": len(results),
        "missing": missing,
        "mismatch_count": len(mismatches),
        "results": [asdict(r) for r in results],
        "decision": decision,
        "final_claim_allowed": False,
    }
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(receipt, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    print(json.dumps({"decision": decision, "results": len(results), "missing": len(missing), "mismatches": len(mismatches)}))
    return 0 if decision == "PASS_NONFINAL" else 1


if __name__ == "__main__":
    raise SystemExit(main())
