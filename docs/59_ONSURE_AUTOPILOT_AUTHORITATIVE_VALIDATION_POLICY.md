# ONSure AutoPilot Authoritative Validation Policy

- Status: NORMATIVE_EXECUTION_POLICY_NONFINAL
- Issue: #94
- Branch: `feature/onsure-enterprise-web-springboot`
- Applies to: Enterprise Web W12 validation and later implementation validation gates
- GitHub Actions: PROHIBITED AS AUTHORITATIVE VALIDATION PATH
- AutoPilot: REQUIRED AUTHORITATIVE EXECUTION PATH

## 1. Decision

ONSure validation MUST NOT use GitHub Actions as the authoritative execution or evidence path.

Authoritative validation is executed through ONSure AutoPilot in an authorized execution environment. GitHub remains source/PR/issue/evidence-index infrastructure only; it is not the validation runner.

## 2. Current binding status

Repository search on the current Enterprise Web branch did not identify a concrete AutoPilot runner entrypoint or binding by the literal names `AutoPilot`, `autopilot`, or `Manual Run`.

Therefore the current execution state is:

`AUTOPILOT_BINDING_NOT_CONFIRMED`

This MUST NOT be promoted to RUN, PASS, CLEAN, EVIDENCED or INDEPENDENTLY_VERIFIED merely because this policy document exists.

## 3. W12 execution contract

W12 remains `NOT_RUN` until AutoPilot is concretely bound to an authorized runner and executes the required checks against one exact source SHA.

Minimum AutoPilot W12 sequence for `apps/onsure-web`:

1. resolve and record exact immutable source SHA
2. establish authorized Java 17 + Maven runtime identity
3. compile/package
4. unit/MVC/security tests
5. real Spring Boot runtime startup
6. `/healthz` readback
7. authenticated browser/session behavior
8. CSP and frame-denial negative verification
9. Core read-model path validation once implemented
10. PostgreSQL/Flyway validation only when the implementation actually depends on that persistence path
11. same-SHA second execution
12. evidence receipt/readback comparison
13. independent review gate before any higher assurance claim

## 4. Anti-False-PASS

AutoPilot execution MUST preserve the following distinctions:

- NOT_RUN is not PASS
- tool/environment failure is not security success
- 0 cases is not PASS
- UNKNOWN is not PASS
- INCONCLUSIVE is not PASS
- stale evidence is not current evidence
- one successful run is not same-SHA two-run reproducibility
- self-validation is not independent verification

## 5. Evidence requirements

Each AutoPilot execution must record enough identity to reproduce and independently read back the result, including at least:

- source SHA
- AutoPilot run identity
- execution environment identity
- commands/check identifiers
- start/end timestamps
- exit/result status by bounded check
- generated evidence/receipt identities
- failure/skip/not-run reasons
- second-run linkage when applicable

The exact receipt schema is governed by ONSure Core/Evidence authority and MUST NOT be invented by the Web layer.

## 6. GitHub boundary

GitHub may contain:

- source code
- Draft PR
- issue/PR progress ledger
- links or references to AutoPilot evidence
- immutable source SHA references

GitHub Actions MUST NOT be reintroduced as:

- the W12 runner
- authoritative compile/test/runtime evidence
- CLEAN counter source
- independent verification source
- FinalLock/Production GO/Commercial GO evidence

Historical exploratory Actions results, if any, are debugging intelligence only and have zero authoritative weight.

## 7. UI/UX validation relation

Static UI rendering and design review can produce `VISUAL_BASELINE_CANDIDATE_NONFINAL`, but implementation validation after the Visual baseline is accepted must use AutoPilot for runtime/test/evidence gates.

Visual or design approval never upgrades W12.

## 8. Promotion gate

No W12 promotion is allowed until all of the following are true:

- concrete AutoPilot runner/binding identified
- authorized execution environment identified
- exact SHA captured
- required bounded checks executed
- results recorded without state collapse
- same-SHA second run executed where required
- evidence independently readable

Until then:

`W12 = NOT_RUN`

`FinalLock = false`

`Production GO = false`

`Commercial GO = false`
