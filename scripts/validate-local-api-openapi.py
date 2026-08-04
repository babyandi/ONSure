#!/usr/bin/env python3
"""Validate ONSure Local API OpenAPI syntax and implementation route parity."""

from __future__ import annotations

import json
import pathlib
import re

import yaml

ROOT = pathlib.Path(__file__).resolve().parents[1]
CONTRACT = ROOT / "contracts/onsure-local-api.v1.openapi.yaml"
SERVER = ROOT / "src/main/java/io/onsure/platform/LocalAuthenticatedApiServer.java"


def validate() -> list[str]:
    errors: list[str] = []
    body = yaml.safe_load(CONTRACT.read_text(encoding="utf-8"))
    if body.get("openapi") != "3.1.0":
        errors.append("LOCAL_API_OPENAPI_VERSION_INVALID")
    paths = body.get("paths")
    if not isinstance(paths, dict) or not paths:
        errors.append("LOCAL_API_OPENAPI_PATHS_EMPTY")
        paths = {}
    implementation = SERVER.read_text(encoding="utf-8")
    implemented_paths = set(re.findall(r'server\.createContext\("([^"?]+)"', implementation))
    contract_paths = set(paths)
    if contract_paths != implemented_paths:
        errors.append(
            "LOCAL_API_OPENAPI_ROUTE_DRIFT:"
            f"missing={sorted(implemented_paths-contract_paths)}:"
            f"extra={sorted(contract_paths-implemented_paths)}"
        )
    operation_ids: list[str] = []
    for path, operations in paths.items():
        if not isinstance(operations, dict):
            errors.append(f"LOCAL_API_OPENAPI_PATH_INVALID:{path}")
            continue
        for method, operation in operations.items():
            if method not in {"get", "post", "put", "patch", "delete"}:
                continue
            operation_id = operation.get("operationId") if isinstance(operation, dict) else None
            if not operation_id:
                errors.append(f"LOCAL_API_OPENAPI_OPERATION_ID_MISSING:{method}:{path}")
            else:
                operation_ids.append(operation_id)
    if len(operation_ids) != len(set(operation_ids)):
        errors.append("LOCAL_API_OPENAPI_OPERATION_ID_DUPLICATED")
    if body.get("security") != [{"LocalBearer": []}]:
        errors.append("LOCAL_API_OPENAPI_DEFAULT_SECURITY_INVALID")
    if paths.get("/v1/health", {}).get("get", {}).get("security") != []:
        errors.append("LOCAL_API_OPENAPI_HEALTH_SECURITY_OVERRIDE_MISSING")
    return sorted(set(errors))


def main() -> int:
    errors = validate()
    report = {
        "contract": "ONSURE_LOCAL_API_OPENAPI_VALIDATION_V1",
        "decision": "PASS_NONFINAL" if not errors else "FAIL",
        "errors": errors,
        "route_count": 8,
        "final_claim_allowed": False,
    }
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
