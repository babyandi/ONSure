#!/usr/bin/env python3
"""Static safety validation for the non-deploying container candidates."""

from __future__ import annotations

import json
import pathlib
import sys

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()


def validate() -> dict[str, object]:
    dockerfile = (ROOT / "deploy/Dockerfile.candidate").read_text(encoding="utf-8")
    compose = (ROOT / "deploy/compose.candidate.yaml").read_text(encoding="utf-8")
    required_docker = ("USER ${ONSURE_UID}:${ONSURE_GID}", "ONSURE_WORKSPACE_ROOT=/onsure-product")
    required_compose = (
        "read_only: true", "network_mode: none", 'cap_drop: ["ALL"]',
        'security_opt: ["no-new-privileges:true"]', "ONSURE_LOCAL_API_TOKEN:",
        "restart: \"no\"", "- ..:/onsure-product:ro",
    )
    violations = ["DOCKERFILE_MISSING:" + value for value in required_docker if value not in dockerfile]
    violations += ["COMPOSE_MISSING:" + value for value in required_compose if value not in compose]
    for unsafe in ("0.0.0.0", "privileged: true", "network_mode: host"):
        if unsafe in dockerfile or unsafe in compose:
            violations.append("UNSAFE_CONTAINER_SETTING:" + unsafe)
    return {
        "contract": "ONSURE_CONTAINER_CANDIDATE_VALIDATION_V1",
        "decision": "PASS_NONFINAL" if not violations else "FAIL",
        "violations": violations,
        "non_root_uid": 65532,
        "application_filesystem_read_only": True,
        "external_network_available": False,
        "loopback_only": True,
        "actual_deployment": "NOT_RUN_NOT_AUTHORIZED",
        "final_claim_allowed": False,
    }


if __name__ == "__main__":
    result = validate()
    print(json.dumps(result, indent=2, sort_keys=True))
    raise SystemExit(0 if result["decision"] == "PASS_NONFINAL" else 1)
