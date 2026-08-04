#!/usr/bin/env python3
"""Validate sanitized owner-provided Ubuntu privileged policy observations."""

from __future__ import annotations

import json
import sys

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
EVIDENCE = ROOT / "assurance/runtime/onsure-ubuntu-privileged-policy-observation.v1.json"


def validate(evidence: dict[str, object]) -> dict[str, object]:
    violations: list[str] = []
    if evidence.get("contract") != "ONSURE_UBUNTU_PRIVILEGED_POLICY_OBSERVATION_V1":
        violations.append("PRIVILEGED_POLICY_CONTRACT")
    if evidence.get("decision") != "REVIEW_REQUIRED":
        violations.append("UNSAFE_PRODUCTION_DECISION")
    if evidence.get("observation_source") != "OWNER_PROVIDED_READ_ONLY_COMMAND_OUTPUT":
        violations.append("OBSERVATION_SOURCE")
    if evidence.get("secret_values_recorded") is not False:
        violations.append("SECRET_RECORDING_BOUNDARY")
    if evidence.get("final_claim_allowed") is not False:
        violations.append("FINAL_AUTHORITY_BOUNDARY")

    apparmor = evidence.get("apparmor", {})
    if not isinstance(apparmor, dict):
        violations.append("APPARMOR_OBSERVATION")
    else:
        for field in ("loaded_profile_count", "enforce_profile_count", "complain_profile_count"):
            value = apparmor.get(field)
            if not isinstance(value, int) or isinstance(value, bool) or value < 0:
                violations.append("APPARMOR_COUNT:" + field)
        if apparmor.get("dedicated_onsure_profile_enforced") is not False \
                or apparmor.get("onsure_java_effective_profile") != "unprivileged_userns":
            violations.append("APPARMOR_ONSURE_GAP_NOT_RECORDED")

    ufw = evidence.get("ufw", {})
    if not isinstance(ufw, dict) or ufw.get("active") is not True \
            or ufw.get("default_incoming") != "deny" \
            or ufw.get("postgresql_public_allow") is not True:
        violations.append("UFW_POLICY_OBSERVATION")
    else:
        for family in ("public_allow_tcp_ports_ipv4", "public_allow_tcp_ports_ipv6"):
            ports = ufw.get(family, [])
            if not isinstance(ports, list) or 5432 not in ports:
                violations.append("UFW_POSTGRESQL_RULE_BINDING:" + family)

    postgresql = evidence.get("postgresql", {})
    if not isinstance(postgresql, dict) \
            or postgresql.get("listen_addresses") != "localhost" \
            or postgresql.get("port") != 5432 \
            or postgresql.get("ssl") != "on" \
            or postgresql.get("loopback_only") is not True:
        violations.append("POSTGRESQL_NETWORK_OBSERVATION")

    blockers = evidence.get("production_blockers", [])
    expected_blockers = {
        "APPARMOR_DEDICATED_ONSURE_PROFILE_NOT_ENFORCED",
        "UFW_POSTGRESQL_PUBLIC_ALLOW_RULE",
    }
    if not isinstance(blockers, list) or set(blockers) != expected_blockers:
        violations.append("PRODUCTION_BLOCKER_SET")
    return {
        "contract": "ONSURE_UBUNTU_PRIVILEGED_POLICY_VALIDATION_V1",
        "decision": "PASS_NONFINAL" if not violations else "FAIL",
        "violations": violations,
        "observed_production_ready": False,
        "remediation_required": sorted(expected_blockers),
        "final_claim_allowed": False,
    }


def main() -> int:
    result = validate(json.loads(EVIDENCE.read_text(encoding="utf-8")))
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["decision"] == "PASS_NONFINAL" else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print("ONSURE_UBUNTU_PRIVILEGED_POLICY_FAIL " + str(error), file=sys.stderr)
        raise SystemExit(1)
