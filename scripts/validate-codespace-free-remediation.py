#!/usr/bin/env python3
from __future__ import annotations

import json
import pathlib
import py_compile
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
REQUIRED = [
    ".github/workflows/onsure-pr-validation.yml",
    "contracts/codespace-free-remediation-plan.v1.json",
    "contracts/atomic-requirement.v1.schema.json",
    "contracts/module-boundary.v1.json",
    "contracts/ledger-hardening.v1.json",
    "contracts/sandbox-boundary.v1.json",
    "contracts/schema-instance-registry.v1.json",
    "contracts/product-process-lineage.v1.json",
    "status/design-capability-coverage.v2.json",
    "status/omission-detection-status.v1.json",
    "status/implementation-matrix.v1.json",
    "status/verification-status.v1.json",
    "status/remaining-work-register.v1.json",
    "fixtures/contracts/program-profile.valid.json",
    "fixtures/contracts/behavior-profile.valid.json",
    "fixtures/contracts/failure-memory.valid.json",
    "fixtures/contracts/improvement-memory.valid.json",
    "fixtures/contracts/evidence-receipt.valid.json",
    "requirements-validation.txt",
    "pom-modular.xml",
    "modules/onsure-core/pom.xml",
    "modules/onsure-cli/pom.xml",
    "modules/onsure-local-api/pom.xml",
    "modules/onsure-test-fixtures/pom.xml",
    "modules/onsure-adapter-oruda/pom.xml",
    "modules/onsure-core/src/test/java/io/onsure/platform/CoreModuleSmokeTest.java",
    "modules/onsure-adapter-oruda/src/test/java/io/onsure/platform/OrudaAdapterModuleSmokeTest.java",
    "modules/onsure-local-api/src/test/java/io/onsure/platform/LocalAuthenticatedApiServerSmokeTest.java",
    "scripts/check-module-boundaries.py",
    "scripts/create-source-snapshot.py",
    "scripts/extract-atomic-requirements.py",
    "scripts/validate-atomic-requirements.py",
    "scripts/validate-structured-contracts.py",
    "scripts/validate-design-coverage.py",
    "scripts/validate-product-process-lineage.py",
    "scripts/validate-status-consistency.py",
    "scripts/validate-ci-boundary.py",
    "tests/test_ci_boundary.py",
    "scripts/run-core-modular-twice.sh",
    "scripts/fixture-sandbox-launcher.sh",
    "scripts/onsure-final-stage.sh",
    "src/main/java/io/onsure/assurance/ExclusiveFileLock.java",
    "src/main/java/io/onsure/learning/OfficialLearningLedger.java",
    "src/main/java/io/onsure/platform/Hashing.java",
    "src/main/java/io/onsure/platform/SourceReferenceBinding.java",
    "src/main/java/io/onsure/platform/FileValidationStore.java",
    "src/main/java/io/onsure/platform/ProductCatalog.java",
    "src/main/java/io/onsure/platform/FixtureHarness.java",
]

SOURCE_ASSERTIONS = {
    "src/main/java/io/onsure/assurance/ExclusiveFileLock.java": [
        "ConcurrentHashMap", "lockInterruptibly", "channel.lock()",
    ],
    "src/main/java/io/onsure/learning/OfficialLearningLedger.java": [
        "TWO_DISTINCT_VERIFIERS_REQUIRED", "VALIDATION_RECEIPT_PACK_STALE",
        "POST_APPLY_RECEIPT_MISSING", "FileChannel.open(lockFile",
        "LEDGER_HEAD_ANCHOR_MISMATCH", "requireGitObjectId",
    ],
    "src/main/java/io/onsure/platform/Hashing.java": [
        '"ls-files"', '"--full-name"', "GIT_LS_FILES_FAILED", "archiveFiles",
        "new Thread", "reader.setDaemon(true)",
    ],
    "src/main/java/io/onsure/platform/SourceReferenceBinding.java": [
        '"--untracked-files=all"', "IMMUTABLE_GIT_TREE_DIGEST_MISMATCH",
    ],
    "src/main/java/io/onsure/platform/FileValidationStore.java": [
        "ONSURE_VALIDATION_STORAGE_CONTEXT_V1", "ExclusiveFileLock",
        "ONSURE_VALIDATION_STORE_REVISION_V1",
    ],
    "src/main/java/io/onsure/platform/ProductCatalog.java": [
        "ONSURE_PRODUCT_CATALOG_REVISION_V1", "ExclusiveFileLock",
    ],
    "src/main/java/io/onsure/platform/FixtureHarness.java": [
        "ONSURE_FIXTURE_SANDBOX_MODE", "fixture-sandbox-launcher.sh", "terminateProcessTree",
    ],
    "scripts/fixture-sandbox-launcher.sh": [
        "--unshare-net", "prlimit", "--ro-bind", "timeout --signal=KILL",
    ],
    "scripts/validate-atomic-requirements.py": [
        "ATOMIC_IMPLEMENTATION_VOCABULARY_MISMATCH",
        "ATOMIC_PASS_WITHOUT_EXECUTED_ORACLE_TEST_EVIDENCE",
        "ATOMIC_SOURCE_DOCUMENT_DIGEST_MISMATCH", "self_test",
    ],
    "scripts/validate-design-coverage.py": [
        "REQUIRED_CAPABILITIES", "PROCESS_PREDECESSOR_MISSING_OR_OUT_OF_ORDER",
        "LINEAGE_PARENT_BINDING_MISSING", "FAILURE_CASE_UNDETECTED",
        "PASS_WITHOUT_EVIDENCE", "self_test", "validate-product-process-lineage.py",
    ],
    "scripts/validate-product-process-lineage.py": [
        "REQUIRED_STAGES", "REQUIRED_ARTIFACTS", "ARTIFACT_PARENT_BINDING_MISSING",
        "STAGE_CONSUMES_UNPRODUCED_ARTIFACT", "NO_FINAL_WITHOUT_INDEPENDENT_RECEIPTS",
        "self_test",
    ],
    "scripts/validate-status-consistency.py": [
        "TRACE_DESIGN_ID_SET_MISMATCH", "IMPLEMENTATION_MATRIX_CAPABILITY_MAP_MISMATCH",
        "OMISSION_FAILURE_CASE_COUNT_MISMATCH", "UNSAFE_RELEASE_FLAG",
    ],
    "scripts/validate-ci-boundary.py": [
        "CI_MUTATION_TOKEN_FORBIDDEN", "UNAPPROVED_WORKFLOW_PRESENT",
        "CI_CHECKOUT_CREDENTIALS_NOT_DISABLED", "contents: write", "git push",
    ],
}

FORBIDDEN_SOURCE_TOKENS = {
    "src/main/java/io/onsure/platform/ValidationEngine.java": [
        "new OrudaTargetAdapter", "withOrudaAdapter",
    ],
    "src/main/java/io/onsure/platform/FileValidationStore.java": [
        "io.onsure.platform.oruda", "OrudaEvidenceRegistry",
    ],
    "src/main/java/io/onsure/platform/Hashing.java": [
        "Thread.ofVirtual", "Executors.newVirtualThreadPerTaskExecutor",
    ],
}


def main() -> int:
    errors: list[str] = []
    for relative in REQUIRED:
        if not (ROOT / relative).is_file():
            errors.append(f"MISSING:{relative}")

    for relative in REQUIRED:
        path = ROOT / relative
        if not path.is_file():
            continue
        try:
            if path.suffix == ".json":
                json.loads(path.read_text(encoding="utf-8"))
            elif path.suffix == ".xml" or path.name == "pom.xml":
                ET.parse(path)
            elif path.suffix == ".py":
                py_compile.compile(str(path), doraise=True)
        except Exception as exc:  # noqa: BLE001
            errors.append(f"INVALID:{relative}:{type(exc).__name__}:{exc}")

    for relative, tokens in SOURCE_ASSERTIONS.items():
        path = ROOT / relative
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for token in tokens:
            if token not in text:
                errors.append(f"SOURCE_ASSERTION_MISSING:{relative}:{token}")

    for relative, tokens in FORBIDDEN_SOURCE_TOKENS.items():
        path = ROOT / relative
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for token in tokens:
            if token in text:
                errors.append(f"FORBIDDEN_SOURCE_TOKEN:{relative}:{token}")

    commands = [
        ([sys.executable, "scripts/check-module-boundaries.py"], "ONSURE_MODULE_BOUNDARY_STATIC_PASS"),
        ([sys.executable, "scripts/validate-repository-contracts.py"], "ONSURE_REPOSITORY_CONTRACTS_PASS"),
        ([sys.executable, "scripts/validate-structured-contracts.py"], "ONSURE_STRUCTURED_CONTRACTS_"),
        ([sys.executable, "scripts/validate-atomic-requirements.py", "--self-test"],
         '"decision": "PASS"'),
        ([sys.executable, "scripts/validate-design-coverage.py", "--matrix",
          "status/design-capability-coverage.v2.json", "--root", ".", "--self-test"],
         '"decision": "PASS"'),
        ([sys.executable, "scripts/validate-status-consistency.py"],
         "ONSURE_STATUS_CONSISTENCY_PASS"),
        ([sys.executable, "scripts/validate-ci-boundary.py"], "ONSURE_CI_BOUNDARY_PASS"),
        ([sys.executable, "-m", "unittest", "tests.test_ci_boundary", "-v"], "OK"),
        (["bash", "scripts/check-shell-syntax.sh"], "ONSURE_SHELL_SYNTAX_PASS"),
    ]
    for command, marker in commands:
        result = subprocess.run(command, cwd=ROOT, text=True, capture_output=True, check=False)
        combined = result.stdout + result.stderr
        if result.returncode != 0 or marker not in combined:
            errors.append(
                f"COMMAND_FAIL:{' '.join(command)}:{result.returncode}:"
                f"{result.stdout[-2000:]}:{result.stderr[-1000:]}"
            )

    plan = json.loads((ROOT / "contracts/codespace-free-remediation-plan.v1.json").read_text(encoding="utf-8"))
    if plan.get("final_single_command") != "bash scripts/onsure-final-stage.sh --profile core":
        errors.append("FINAL_SINGLE_COMMAND_MISMATCH")
    if plan.get("assurance_ceiling") != "SELF_VALIDATION_NONFINAL":
        errors.append("ASSURANCE_CEILING_UNSAFE")
    if any(plan.get(field) is not False for field in ("final_lock_allowed", "production_go", "commercial_go")):
        errors.append("UNSAFE_GO_FLAG")

    report = {
        "contract": "ONSURE_CODESPACE_FREE_STATIC_GATE_V9",
        "decision": "PASS" if not errors else "FAIL",
        "errors": errors,
        "source_boundary_assertions": "PASS" if not errors else "FAIL",
        "status_cross_consistency": "PASS" if not errors else "FAIL",
        "ci_mutation_boundary": "PASS" if not errors else "FAIL",
        "design_capability_count": 28,
        "product_process_stage_count": 20,
        "product_lineage_artifact_count": 20,
        "failure_injection_count": 43,
        "atomic_requirement_failure_injections": 10,
        "design_and_lineage_failure_injections": 28,
        "ci_boundary_failure_injections": 5,
        "design_and_lineage_detection": "PASS" if not errors else "FAIL",
        "atomic_requirement_detection": "PASS" if not errors else "FAIL",
        "structured_contract_validation": "SYNTAX_OR_FULL_DEPENDING_ON_PINNED_PACKAGES",
        "runtime_execution": "NOT_RUN",
        "modular_compile": "NOT_RUN",
        "independent_otester": "NOT_RUN",
        "independent_oaudit": "NOT_RUN",
        "final_claim_allowed": False,
    }
    print(json.dumps(report, indent=2, sort_keys=True))
    if errors:
        print("ONSURE_CODESPACE_FREE_STATIC_GATE_FAIL", file=sys.stderr)
        return 1
    print("ONSURE_CODESPACE_FREE_STATIC_GATE_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
