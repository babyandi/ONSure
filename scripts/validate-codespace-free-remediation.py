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
    "requirements-validation.txt",
    "pom-modular.xml",
    "modules/onsure-core/pom.xml",
    "modules/onsure-cli/pom.xml",
    "modules/onsure-local-api/pom.xml",
    "modules/onsure-test-fixtures/pom.xml",
    "modules/onsure-adapter-oruda/pom.xml",
    "scripts/onsure-local-gate.sh",
    "scripts/onsure-one-shot.sh",
    "scripts/onsure-final-stage.sh",
    "scripts/check-module-boundaries.py",
    "scripts/create-source-snapshot.py",
    "scripts/extract-atomic-requirements.py",
    "scripts/validate-atomic-requirements.py",
    "scripts/validate-structured-contracts.py",
    "scripts/validate-design-coverage.py",
    "scripts/validate-product-process-lineage.py",
    "scripts/validate-status-consistency.py",
    "scripts/validate-ci-boundary.py",
    "scripts/validate-verification-claims.py",
    "tests/test_ci_boundary.py",
    "tests/test_verification_claims.py",
    "scripts/fixture-sandbox-launcher.sh",
    "scripts/test-fixture-sandbox-boundary.sh",
    "src/main/java/io/onsure/assurance/ExclusiveFileLock.java",
    "src/main/java/io/onsure/learning/OfficialLearningLedger.java",
    "src/main/java/io/onsure/platform/Hashing.java",
    "src/main/java/io/onsure/platform/SourceReferenceBinding.java",
    "src/main/java/io/onsure/platform/FileValidationStore.java",
    "src/main/java/io/onsure/platform/ProductCatalog.java",
    "src/main/java/io/onsure/platform/FixtureHarness.java",
    "src/test/java/io/onsure/platform/AdversarialConcurrencyAndOutputTest.java",
]

SOURCE_ASSERTIONS = {
    "scripts/onsure-local-gate.sh": [
        "ONSURE_LOCAL_GATE_PASS_NONFINAL",
        "scripts/validate-verification-claims.py",
        "scripts/test-fixture-sandbox-boundary.sh",
        '"github_actions": "DISABLED"',
        "mvn -B -ntp -q test",
        "pom-modular.xml",
    ],
    "scripts/validate-ci-boundary.py": [
        "GITHUB_ACTIONS_WORKFLOW_FORBIDDEN",
        "LOCAL_VALIDATION_RUNNER_MISSING",
        "DISABLED_AND_FORBIDDEN",
    ],
    "scripts/validate-verification-claims.py": [
        "GITHUB_ACTIONS_POLICY_NOT_DISABLED",
        "LOCAL_RECEIPT_REQUIRED",
        "GITHUB_ACTIONS_WORKFLOW_FORBIDDEN",
        "SANDBOX_PARTIAL_SCOPE_OVERCLAIMED_AS_PASS",
    ],
    "scripts/fixture-sandbox-launcher.sh": [
        "ROOTLESS_BWRAP",
        "NON_LOCAL_BACKEND_FORBIDDEN",
        "--unshare-net",
        "--ro-bind",
        "prlimit",
        "timeout --signal=KILL",
    ],
    "scripts/test-fixture-sandbox-boundary.sh": [
        "FILESYSTEM_ESCAPE_BLOCKED",
        "SYMLINK_ESCAPE_BLOCKED",
        "CHILD_PROCESS_TERMINATED",
        "CPU_LIMIT_ENFORCED",
        "MEMORY_LIMIT_ENFORCED",
        "ONSURE_FIXTURE_SANDBOX_BOUNDARY_PASS 12",
    ],
    "src/main/java/io/onsure/assurance/ExclusiveFileLock.java": [
        "ConcurrentHashMap", "lockInterruptibly", "channel.lock()",
    ],
    "src/main/java/io/onsure/learning/OfficialLearningLedger.java": [
        "TWO_DISTINCT_VERIFIERS_REQUIRED",
        "VALIDATION_RECEIPT_PACK_STALE",
        "POST_APPLY_RECEIPT_MISSING",
        "ExclusiveFileLock.call(lockFile",
        "LEDGER_HEAD_ANCHOR_MISMATCH",
    ],
    "src/main/java/io/onsure/platform/Hashing.java": [
        '"ls-files"', '"--full-name"', "GIT_LS_FILES_FAILED", "archiveFiles",
    ],
    "src/main/java/io/onsure/platform/SourceReferenceBinding.java": [
        '"--untracked-files=all"', "IMMUTABLE_GIT_TREE_DIGEST_MISMATCH",
    ],
    "src/main/java/io/onsure/platform/FileValidationStore.java": [
        "ONSURE_VALIDATION_STORAGE_CONTEXT_V1", "ExclusiveFileLock",
    ],
    "src/main/java/io/onsure/platform/ProductCatalog.java": [
        "ONSURE_PRODUCT_CATALOG_REVISION_V1", "ExclusiveFileLock",
    ],
    "src/main/java/io/onsure/platform/FixtureHarness.java": [
        "ONSURE_FIXTURE_SANDBOX_MODE", "fixture-sandbox-launcher.sh",
        "fixture output limit exceeded",
    ],
    "src/test/java/io/onsure/platform/AdversarialConcurrencyAndOutputTest.java": [
        "fixtureOutputFloodIsRejectedBeforeEvidenceCanBeAccepted",
        "concurrentCatalogUpdatesDoNotLoseProjectsOrRevision",
        "concurrentLearningLedgerAppendsPreserveHashChainAndEveryCandidate",
    ],
}

FORBIDDEN_SOURCE_TOKENS = {
    "scripts/fixture-sandbox-launcher.sh": [
        "CI_SUDO_UNSHARE_BWRAP", "GITHUB_ACTIONS", "sudo -n unshare",
    ],
    "src/main/java/io/onsure/platform/ValidationEngine.java": [
        "new OrudaTargetAdapter", "withOrudaAdapter",
    ],
    "src/main/java/io/onsure/platform/FileValidationStore.java": [
        "io.onsure.platform.oruda", "OrudaEvidenceRegistry",
    ],
    "src/main/java/io/onsure/platform/Hashing.java": [
        "Thread.ofVirtual", "Executors.newVirtualThreadPerTaskExecutor",
    ],
    "src/main/java/io/onsure/learning/OfficialLearningLedger.java": [
        "FileChannel.open(lockFile", "OverlappingFileLockException",
    ],
}


def main() -> int:
    errors: list[str] = []
    for relative in REQUIRED:
        if not (ROOT / relative).is_file():
            errors.append(f"MISSING:{relative}")

    workflow_root = ROOT / ".github" / "workflows"
    if workflow_root.exists():
        for path in sorted(workflow_root.glob("*.yml")) + sorted(workflow_root.glob("*.yaml")):
            errors.append(f"GITHUB_ACTIONS_WORKFLOW_FORBIDDEN:{path.name}")

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
        ([sys.executable, "scripts/validate-atomic-requirements.py", "--self-test"], '"decision": "PASS"'),
        ([sys.executable, "scripts/validate-design-coverage.py", "--matrix",
          "status/design-capability-coverage.v2.json", "--root", ".", "--self-test"],
         '"decision": "PASS"'),
        ([sys.executable, "scripts/validate-status-consistency.py"], "ONSURE_STATUS_CONSISTENCY_PASS"),
        ([sys.executable, "scripts/validate-ci-boundary.py"], "ONSURE_AUTOMATION_BOUNDARY_PASS"),
        ([sys.executable, "scripts/validate-verification-claims.py"],
         "ONSURE_VERIFICATION_CLAIM_AUDIT_PASS"),
        ([sys.executable, "-m", "unittest", "tests.test_ci_boundary", "-v"], "OK"),
        ([sys.executable, "-m", "unittest", "tests.test_verification_claims", "-v"], "OK"),
        (["bash", "scripts/check-shell-syntax.sh"], "ONSURE_SHELL_SYNTAX_PASS"),
    ]
    for command, marker in commands:
        result = subprocess.run(command, cwd=ROOT, text=True, capture_output=True, check=False)
        combined = result.stdout + result.stderr
        if result.returncode != 0 or marker not in combined:
            errors.append(
                f"COMMAND_FAIL:{' '.join(command)}:{result.returncode}:"
                f"{result.stdout[-2400:]}:{result.stderr[-1400:]}"
            )

    plan = json.loads((ROOT / "contracts/codespace-free-remediation-plan.v1.json").read_text(encoding="utf-8"))
    if plan.get("final_single_command") != "bash scripts/onsure-final-stage.sh --profile core":
        errors.append("FINAL_SINGLE_COMMAND_MISMATCH")
    if plan.get("local_gate_command") != "bash scripts/onsure-local-gate.sh --mode full --profile core":
        errors.append("LOCAL_GATE_COMMAND_MISMATCH")
    if plan.get("execution_policy", {}).get("github_actions") != "DISABLED_BY_USER":
        errors.append("ACTIONS_POLICY_NOT_DISABLED")
    if plan.get("assurance_ceiling") != "SELF_VALIDATION_NONFINAL":
        errors.append("ASSURANCE_CEILING_UNSAFE")
    if any(plan.get(field) is not False for field in ("final_lock_allowed", "production_go", "commercial_go")):
        errors.append("UNSAFE_GO_FLAG")

    report = {
        "contract": "ONSURE_CODESPACE_FREE_STATIC_GATE_V11",
        "decision": "PASS" if not errors else "FAIL",
        "errors": errors,
        "github_actions": "DISABLED_BY_USER",
        "local_gate_required": True,
        "source_boundary_assertions": "PASS" if not errors else "FAIL",
        "status_cross_consistency": "PASS" if not errors else "FAIL",
        "automation_boundary": "PASS" if not errors else "FAIL",
        "verification_claim_boundary": "PASS" if not errors else "FAIL",
        "design_capability_count": 28,
        "product_process_stage_count": 20,
        "product_lineage_artifact_count": 20,
        "failure_injection_count": 52,
        "atomic_requirement_failure_injections": 10,
        "design_and_lineage_failure_injections": 28,
        "automation_boundary_failure_injections": 6,
        "verification_claim_failure_injections": 8,
        "sandbox_required_attacks": 12,
        "sandbox_verified_attacks": 10,
        "sandbox_unverified_attacks": ["CROSS_TENANT_READ", "CROSS_TENANT_WRITE"],
        "runtime_execution": "NOT_RUN_BY_STATIC_GATE",
        "modular_compile": "NOT_RUN_BY_STATIC_GATE",
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
