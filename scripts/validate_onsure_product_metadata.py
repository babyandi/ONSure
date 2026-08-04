#!/usr/bin/env python3
"""Validate standalone ONSure metadata prepared for a future products/onsure root."""

from __future__ import annotations

import json
import sys

import yaml

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()


def validate_documents(
        product: dict[str, object], build: dict[str, object], obuilder: dict[str, object]
) -> list[str]:
    violations: list[str] = []
    if product.get("schema_version") != "onsure-product-candidate/v1":
        violations.append("PRODUCT_SCHEMA_VERSION")
    if product.get("product", {}).get("id") != "onsure":
        violations.append("PRODUCT_ID")
    if product.get("product", {}).get("future_root_candidate") != "products/onsure":
        violations.append("FUTURE_PRODUCT_ROOT")
    namespace = product.get("namespace", {})
    if namespace.get("current_java") != "io.onsure":
        violations.append("CURRENT_JAVA_NAMESPACE")
    if namespace.get("future_java_candidate") != "kr.co.oruda.products.onsure":
        violations.append("FUTURE_JAVA_NAMESPACE_CANDIDATE")
    if namespace.get("rename_authorized") is not False:
        violations.append("NAMESPACE_RENAME_AUTHORITY")
    ownership = product.get("ownership", {})
    license_body = product.get("license", {})
    if ownership.get("copyright_holder") != "ORUDA Labs":
        violations.append("COPYRIGHT_HOLDER")
    if ownership.get("inbound_rights_declaration") != "contracts/onsure-rights-declaration.v1.json":
        violations.append("INBOUND_RIGHTS_DECLARATION")
    if license_body.get("identifier") != "LicenseRef-ORUDA-Labs-Proprietary":
        violations.append("ROOT_LICENSE_IDENTIFIER")
    if license_body.get("distribution_policy") != "PROPRIETARY_WRITTEN_AGREEMENT_REQUIRED":
        violations.append("ROOT_DISTRIBUTION_POLICY")

    canonical = build["canonical_build"]
    compatibility = build["compatibility_build"]
    product_build = product.get("build", {})
    if product_build.get("canonical", {}).get("command") != canonical["clean_verify"]:
        violations.append("CANONICAL_BUILD_COMMAND_DRIFT")
    if product_build.get("modular_compatibility", {}).get("command") != compatibility["package"]:
        violations.append("COMPATIBILITY_BUILD_COMMAND_DRIFT")
    if product_build.get("modular_compatibility", {}).get("release_authority") is not False:
        violations.append("PRODUCT_COMPATIBILITY_RELEASE_AUTHORITY")
    if obuilder.get("canonical_build", {}).get("command") != canonical["clean_verify"]:
        violations.append("OBUILDER_CANONICAL_COMMAND_DRIFT")
    if obuilder.get("compatibility_build", {}).get("command") != compatibility["package"]:
        violations.append("OBUILDER_COMPATIBILITY_COMMAND_DRIFT")
    if obuilder.get("compatibility_build", {}).get("release_authority") is not False:
        violations.append("OBUILDER_COMPATIBILITY_RELEASE_AUTHORITY")
    if product_build.get("public_api_validation", {}).get("command") != build["public_api"]["command"]:
        violations.append("PUBLIC_API_VALIDATION_COMMAND_DRIFT")
    if product_build.get("supply_chain_validation", {}).get("cyclonedx_schema") != build["supply_chain"]["cyclonedx_schema"]:
        violations.append("CYCLONEDX_SCHEMA_DRIFT")
    if product_build.get("nested_root_rehearsal", {}).get("command") != build["cutover_rehearsal"]["command"]:
        violations.append("NESTED_ROOT_REHEARSAL_COMMAND_DRIFT")
    operational_command = "python3 scripts/validate_onsure_operational_boundary.py"
    if product_build.get("operational_boundary_validation", {}).get("command") != operational_command:
        violations.append("OPERATIONAL_BOUNDARY_COMMAND_DRIFT")
    if operational_command not in obuilder.get("required_nonfinal_gates", []):
        violations.append("OBUILDER_OPERATIONAL_BOUNDARY_GATE")
    diagnostic = product_build.get("bubblewrap_environment_diagnostic", {})
    if diagnostic.get("command") != "python3 scripts/onsure_bubblewrap_diagnostics.py":
        violations.append("BUBBLEWRAP_DIAGNOSTIC_COMMAND_DRIFT")
    if diagnostic.get("github_actions_required") is not False:
        violations.append("BUBBLEWRAP_GITHUB_ACTIONS_BOUNDARY")
    sandbox_diagnostic = product_build.get("sandbox_backend_diagnostic", {})
    if sandbox_diagnostic.get("command") != "python3 scripts/onsure_sandbox_diagnostics.py":
        violations.append("SANDBOX_BACKEND_DIAGNOSTIC_COMMAND_DRIFT")
    if sandbox_diagnostic.get("local_oci_only") is not True \
            or sandbox_diagnostic.get("changes_deployment_topology") is not False \
            or sandbox_diagnostic.get("github_actions_required") is not False:
        violations.append("SANDBOX_BACKEND_DIAGNOSTIC_BOUNDARY")

    release = product.get("release", {})
    for key in ("production_go", "commercial_go", "final_pass"):
        if release.get(key) is not False:
            violations.append(f"PROHIBITED_RELEASE_AUTHORITY:{key}")
    if obuilder.get("final_claim_allowed") is not False:
        violations.append("OBUILDER_FINAL_CLAIM")
    return violations


def validate() -> dict[str, object]:
    product = yaml.safe_load((ROOT / "product.yaml").read_text(encoding="utf-8"))
    build = json.loads(
        (ROOT / "contracts/onsure-build-boundary.v1.json").read_text(encoding="utf-8")
    )
    obuilder = yaml.safe_load(
        (ROOT / ".obuilder/product-build.yaml").read_text(encoding="utf-8")
    )
    violations = validate_documents(product, build, obuilder)
    required = [
        "AGENTS.md",
        "README.md",
        "CHANGELOG.md",
        "product.yaml",
        "LICENSE",
        "NOTICE",
        "THIRD_PARTY_NOTICES.md",
        "vscode-extension/LICENSE",
        "vscode-extension/THIRD_PARTY_NOTICES.md",
        ".obuilder/README.md",
        ".obuilder/product-build.yaml",
        "contracts/java-public-api-baseline.v1.json",
        "contracts/onsure-rights-declaration.v1.json",
        "assurance/dependencies/onsure.cdx.json",
        "assurance/dependencies/onsure-dependency-license-inventory.v1.json",
        "assurance/migration/onsure-open-pr-overlap.v1.json",
        "contracts/onsure-operational-boundary.v1.json",
    ]
    missing = [path for path in required if not (ROOT / path).is_file()]
    if missing:
        violations.append("REQUIRED_PRODUCT_METADATA_MISSING:" + ",".join(missing))

    agent_text = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
    for required_text in (
        "io.onsure",
        "kr.co.oruda.products.onsure",
        "ONSURE_PRODUCT_ROOT",
        "main",
        "Final PASS",
    ):
        if required_text not in agent_text:
            violations.append(f"AGENT_BOUNDARY_MISSING:{required_text}")

    return {
        "contract": "ONSURE_PRODUCT_METADATA_VALIDATION_V1",
        "decision": "PASS_NONFINAL" if not violations else "FAIL",
        "violations": violations,
        "product_id": product.get("product", {}).get("id"),
        "current_java_namespace": product.get("namespace", {}).get("current_java"),
        "future_product_root_candidate": product.get("product", {}).get(
            "future_root_candidate"
        ),
        "required_metadata_file_count": len(required),
        "canonical_build_authority": build["canonical_build"]["authority"],
        "final_claim_allowed": False,
    }


def main() -> int:
    result = validate()
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["decision"] == "PASS_NONFINAL" else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, yaml.YAMLError) as error:
        print(f"ONSURE_PRODUCT_METADATA_FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
