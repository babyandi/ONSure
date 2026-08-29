# ONSure Enterprise Web W12 — AutoPilot Binding Contract

- Status: `BINDING_MATERIALIZED_NONFINAL`
- ONSure issue: #94
- ONSure draft PR: #95
- Validation executor: `babyandi/Autopilot`
- GitHub Actions: NOT USED FOR EXECUTION OR VALIDATION
- W12: NOT_RUN until server-local AutoPilot receipts exist

## 1. Authority boundary

GitHub stores source, Draft PR, issues and evidence references. It is not the W12 execution authority.

The authoritative execution environment is the AutoPilot server-local workspace documented by `babyandi/Autopilot`: `/workspace/Autopilot`.

ONSure Web must be validated through an AutoPilot Root Goal / GoalNode / Mission DAG. Manual shell observations or historical GitHub Actions results have zero authoritative W12 weight.

## 2. Root Goal

`ONSURE_WEB_W12_VALIDATE_PR95`

Objective: validate one exact ONSure source SHA for the Enterprise Web vertical slice without creating Web-side business authority and without promoting UNKNOWN/NOT_RUN to success.

## 3. Required immutable inputs

- repository: `babyandi/ONSure`
- target branch: `feature/onsure-enterprise-web-springboot`
- exact source SHA: captured at AutoPilot mission start and immutable for the run
- module: `apps/onsure-web`
- Java: 17
- execution authority: `/workspace/Autopilot`
- product workspace: `/workspace/ONSure`

If the checked-out ONSure SHA differs from the mission-bound SHA, the run is HOLD and must not continue as the same evidence lineage.

## 4. Goal DAG

```text
G0 SOURCE_IDENTITY
  ↓
  ├─ G1 BUILD_AND_PACKAGE
  ├─ G2 UNIT_MVC_SECURITY_TESTS
  └─ G3 STATIC_CONTRACT_CHECKS
          ↓ join ALL
      G4 RUNTIME_BOOT
          ↓
      ├─ G5 HEALTH_AND_AUTH
      ├─ G6 BROWSER_SECURITY_NEGATIVE
      └─ G7 CORE_READ_CONTRACT
          ↓ join ALL
      G8 CLEAN_RUN_A
          ↓
      G9 CLEAN_RUN_B_SAME_SHA
          ↓
      G10 RECEIPT_READBACK_AND_INTEGRATION_GATE
```

Parallelism is allowed only where dependencies permit. `G8/G9` are sequential reproducibility gates and must use the same immutable ONSure SHA and equivalent declared environment identity.

## 5. GoalNode acceptance

### G0 SOURCE_IDENTITY
- repository and exact SHA resolved
- dirty/uncontrolled workspace state recorded
- mismatch => HOLD

### G1 BUILD_AND_PACKAGE
- Java 17 preflight
- Maven executable/version evidence
- clean compile/package for `apps/onsure-web`
- non-zero exit => FAIL

### G2 UNIT_MVC_SECURITY_TESTS
- unit/MVC/security test execution
- zero discovered tests may not be treated as PASS
- skipped/disabled/NOT_RUN must remain explicit

### G3 STATIC_CONTRACT_CHECKS
- no Web-side calculation of assurance state, blockers, approval eligibility or final readiness
- Core → UI field mapping conformance against `docs/52_ONSURE_ENTERPRISE_WEB_CORE_UI_FIELD_MAPPING.md`
- forbidden direct state mutation controls absent in the bounded slice

### G4 RUNTIME_BOOT
- real Spring Boot process starts from the built artifact
- startup and shutdown receipts recorded

### G5 HEALTH_AND_AUTH
- `/healthz` reachable as designed
- protected UI requires authentication
- health success must not be interpreted as assurance PASS

### G6 BROWSER_SECURITY_NEGATIVE
- CSP present
- frame denial present
- unauthenticated protected-resource access denied/redirected as designed
- negative result interpretation is fail-closed

### G7 CORE_READ_CONTRACT
- Project / Target / AssuranceSnapshot / BlockingCondition / Evidence read contract exercised when implementation exists
- unavailable functionality remains NOT_IMPLEMENTED/NOT_RUN rather than fabricated success

### G8 / G9 TWO CLEAN
Each run must repeat the complete applicable W12 execution against the same ONSure SHA. Environment differences must be materialized in receipts. A prior run from another SHA cannot satisfy either run.

### G10 INTEGRATION GATE
- all required GoalNodes have terminal evidence
- no UNKNOWN/NOT_RUN/INCONCLUSIVE is silently dropped
- receipt lineage includes ONSure SHA and AutoPilot mission identity
- result remains NONFINAL until independent validation requirements are separately met

## 6. Receipt minimum fields

Each mission/goal receipt must allow readback of:
- AutoPilot goal/mission identity
- ONSure repository and exact SHA
- execution timestamp
- execution host/workspace identity
- command or operation identity
- exit/result state
- stdout/stderr or bounded artifact references where applicable
- dependency parent receipts
- environment identity
- explicit NOT_RUN/SKIPPED/UNKNOWN states

## 7. Promotion rules

Creation of this binding document is not validation evidence.

Allowed current state after materialization:
`AUTOPILOT_W12_BINDING_MATERIALIZED_NONFINAL`

Forbidden before server-local execution receipts:
- W12 PASS
- EVIDENCED
- INDEPENDENTLY_VERIFIED
- FinalLock
- Production GO
- Commercial GO

## 8. Cross-repository binding

AutoPilot counterpart branch/document must define the same Root Goal identity and ONSure project context. If the two contracts drift materially, execution is HOLD until reconciled.
