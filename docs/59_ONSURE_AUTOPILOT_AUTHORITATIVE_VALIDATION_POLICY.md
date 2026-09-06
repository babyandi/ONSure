# ONSure AutoPilot Authoritative Validation Policy

- Status: NORMATIVE_EXECUTION_POLICY_NONFINAL
- Issue: #94
- Branch: `feature/onsure-enterprise-web-springboot`
- Applies to: Enterprise Web W12 validation and later implementation validation gates
- GitHub Actions: PROHIBITED AS AUTHORITATIVE VALIDATION PATH
- AutoPilot: REQUIRED AUTHORITATIVE EXECUTION PATH

## 1. Decision

ONSure validation MUST NOT use GitHub Actions as the authoritative execution or evidence path.

Authoritative validation is executed through the standalone `babyandi/Autopilot` runtime. GitHub remains source/PR/issue/evidence-index infrastructure only.

## 2. Current binding status

AutoPilot is confirmed and its server-local execution authority is `/workspace/Autopilot`. AutoPilot Draft PR #288 on `feature/onsure-web-w12-binding-v2` materializes the ONSure project context and W12 mission binding. ONSure counterpart contract is `docs/60_ONSURE_AUTOPILOT_W12_BINDING_CONTRACT.md`.

Current state:

`AUTOPILOT_W12_BINDING_MATERIALIZED_NONFINAL`

This is a binding state, not execution evidence. W12 remains `NOT_RUN` until server-local receipts exist for one exact ONSure SHA.

## 3. W12 execution contract

Required bounded sequence:
1. exact source SHA identity
2. Java 17 + Maven preflight
3. compile/package
4. unit/MVC/security tests with non-zero discovered test denominator
5. real Spring Boot runtime
6. `/healthz` readback without interpreting service health as assurance PASS
7. authenticated browser/session behavior
8. CSP/frame-denial negative verification
9. Core read-model path validation when implemented
10. PostgreSQL/Flyway only when the slice actually depends on that persistence path
11. same-SHA clean run A
12. same-SHA clean run B
13. AutoPilot receipt/readback integration gate
14. separate independent review before higher assurance promotion

## 4. Anti-False-PASS

- NOT_RUN is not PASS
- tool/environment failure is not security success
- zero tests is not PASS
- UNKNOWN/INCONCLUSIVE/HOLD are not PASS
- stale evidence is not current evidence
- one successful run is not two-run reproducibility
- self-validation is not independent verification
- historical GitHub Actions observations have authoritative weight 0

## 5. Evidence identity

Each AutoPilot run must preserve at least:
- AutoPilot goal/mission identity
- ONSure exact source SHA
- workspace/environment identity
- bounded operation/command identity
- timestamps
- result state and explicit failure/skip/not-run reason
- output/artifact digest or reference
- parent/dependency receipt lineage
- second-run linkage

## 6. GitHub boundary

Allowed: source, Draft PR, issue/PR ledger, AutoPilot receipt references, immutable SHA references.

Forbidden as authority: GitHub Actions workflow/run status, workflow artifacts, CLEAN counters derived from Actions, independent-verification claims derived from Actions.

## 7. Promotion gate

Before server-local AutoPilot execution receipts:

`W12 = NOT_RUN`

`FinalLock = false`

`Production GO = false`

`Commercial GO = false`
