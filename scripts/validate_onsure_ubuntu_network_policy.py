#!/usr/bin/env python3
"""Validate the exact non-executed Ubuntu UFW remediation boundary."""

from __future__ import annotations

import json
import sys

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
PLAN = ROOT / "deploy/ubuntu/ufw-remediation-plan.v1.json"
OBSERVATION = ROOT / "assurance/runtime/onsure-ubuntu-privileged-policy-observation.v1.json"


def validate(plan: dict[str, object], observation: dict[str, object]) -> dict[str, object]:
    violations: list[str] = []
    if plan.get("contract") != "ONSURE_UBUNTU_UFW_REMEDIATION_PLAN_V1":
        violations.append("UFW_PLAN_CONTRACT")
    if plan.get("execution_authorized") is not False \
            or plan.get("execution_state") != "NOT_RUN" \
            or plan.get("production_go") is not False \
            or plan.get("final_claim_allowed") is not False:
        violations.append("UFW_EXECUTION_AUTHORITY")
    if plan.get("commands_proposed_not_executed") != [
        "sudo ufw --force delete 6", "sudo ufw --force delete 3"
    ]:
        violations.append("UFW_DESCENDING_DELETE_SEQUENCE")
    if plan.get("out_of_scope_rules_must_remain_unchanged") != ["22/tcp", "80/tcp"]:
        violations.append("UFW_OUT_OF_SCOPE_MUTATION")
    postconditions = plan.get("postconditions", {})
    if not isinstance(postconditions, dict) \
            or postconditions.get("forbidden_public_tcp_ports") != [47311, 47312, 5432] \
            or postconditions.get("postgresql_listen_addresses") != "localhost" \
            or postconditions.get("postgresql_hba_non_loopback_rule_present") is not False \
            or postconditions.get("ufw_active") is not True:
        violations.append("UFW_POSTCONDITION_BOUNDARY")

    ufw = observation.get("ufw", {})
    postgresql = observation.get("postgresql", {})
    if not isinstance(ufw, dict) \
            or ufw.get("postgresql_public_allow") is not True \
            or ufw.get("postgresql_public_rule_ids_delete_order") != [6, 3]:
        violations.append("UFW_OBSERVATION_BINDING")
    if not isinstance(postgresql, dict) \
            or postgresql.get("loopback_only") is not True \
            or postgresql.get("hba_non_loopback_host_rule_present") is not False:
        violations.append("POSTGRESQL_LOOPBACK_PRECONDITION")
    return {
        "contract": "ONSURE_UBUNTU_NETWORK_POLICY_VALIDATION_V1",
        "decision": "PASS_NONFINAL" if not violations else "FAIL",
        "violations": violations,
        "remediation_ready": not violations,
        "execution_state": "NOT_RUN",
        "observed_production_ready": False,
        "final_claim_allowed": False,
    }


def main() -> int:
    result = validate(
        json.loads(PLAN.read_text(encoding="utf-8")),
        json.loads(OBSERVATION.read_text(encoding="utf-8")),
    )
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["decision"] == "PASS_NONFINAL" else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print("ONSURE_UBUNTU_NETWORK_POLICY_FAIL " + str(error), file=sys.stderr)
        raise SystemExit(1)
