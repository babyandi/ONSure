# ONSure Enterprise Web W12 — AutoPilot Binding Contract

- Status: `BINDING_MATERIALIZED_NONFINAL`
- ONSure issue: #94
- ONSure draft PR: #95
- Validation executor: `babyandi/Autopilot`
- AutoPilot counterpart branch: `feature/onsure-web-w12-binding-v2`
- AutoPilot counterpart draft PR: #288
- AutoPilot counterpart root goal: `ONSURE_WEB_W12_VALIDATE_PR95`
- GitHub Actions: NOT USED FOR EXECUTION OR VALIDATION
- W12: NOT_RUN until server-local AutoPilot receipts exist

## 1. Authority boundary

GitHub stores source, Draft PR, issues and evidence references. It is not the W12 execution authority. The authoritative execution environment is `/workspace/Autopilot`.

ONSure Web is validated through an AutoPilot Root Goal / GoalNode / Mission DAG. Manual shell observations or historical GitHub Actions results have zero authoritative W12 weight.

## 2. Root Goal

`ONSURE_WEB_W12_VALIDATE_PR95`

Objective: validate one exact ONSure source SHA without creating Web-side business authority and without promoting UNKNOWN/NOT_RUN to success.

## 3. Required immutable inputs

- repository: `babyandi/ONSure`
- target branch: `feature/onsure-enterprise-web-springboot`
- exact source SHA: mission-bound and immutable for the run
- module: `apps/onsure-web`
- Java: 17
- execution authority: `/workspace/Autopilot`
- product workspace: `/workspace/ONSure`

SHA mismatch or dirty validation subject => HOLD.

## 4. Goal DAG

```text
G0 SOURCE_IDENTITY
  ↓
  ├─ G1 BUILD_AND_PACKAGE
  │      ↓
  │   G2 UNIT_MVC_SECURITY_TESTS
  └─ G3 STATIC_CONTRACT_CHECKS
          ↓ join G2 + G3
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

G1 installs authoritative `onsure-core` into the server-local Maven repository before packaging Web. Therefore G2 depends on G1 and MUST NOT race the Core install. G1 and G3 may run in parallel. G5/G6/G7 may run in parallel after G4 where process isolation permits. G8/G9 are sequential reproducibility gates.

## 5. GoalNode acceptance

### G0 SOURCE_IDENTITY
- repository and exact SHA resolved
- dirty/uncontrolled workspace state recorded
- mismatch => HOLD

### G1 BUILD_AND_PACKAGE
- Java 17/Maven preflight
- `onsure-core` local install
- clean Web package
- non-zero exit => FAIL/HOLD

### G2 UNIT_MVC_SECURITY_TESTS
- depends on G1
- unit/MVC/security/Core-read tests
- zero discovered tests not PASS
- skipped required test remains explicit

### G3 STATIC_CONTRACT_CHECKS
- no Web-side calculation of assurance state/blockers/approval/final readiness
- Core→UI mapping conformance
- forbidden direct state mutation controls absent

### G4 RUNTIME_BOOT
- real Spring Boot artifact starts/stops with receipts

### G5 HEALTH_AND_AUTH
- `/healthz` readback
- protected UI requires authentication
- service health is not assurance PASS

### G6 BROWSER_SECURITY_NEGATIVE
- CSP and frame denial present
- unauthenticated protected resource blocked/redirected as designed

### G7 CORE_READ_CONTRACT
- Project/Target/persisted Evidence read from Core
- unavailable Core roots remain NOT_AVAILABLE, not empty/zero
- eight-stage Assurance remains `NOT_AVAILABLE` until authoritative Core projection exists

### G8 / G9 TWO CLEAN
Repeat complete applicable W12 against the same SHA. Another SHA cannot satisfy either run.

### G10 INTEGRATION GATE
- all required GoalNodes have terminal evidence
- no UNKNOWN/NOT_RUN/INCONCLUSIVE silently dropped
- receipt lineage includes ONSure SHA and AutoPilot Mission identity
- result remains NONFINAL pending independent validation

## 6. Promotion rules

Binding/config/code presence is not validation evidence. Before server-local receipts:

`W12 = NOT_RUN`

Forbidden promotion: EVIDENCED, INDEPENDENTLY_VERIFIED, FinalLock, Production GO, Commercial GO.

## 7. Cross-repository binding

AutoPilot Draft PR #288 carries the matching project context, orchestration manifest, Java MissionSpec, Manual Run intent, validators and server-local runners. Material drift between the two repositories => HOLD.
