# ONSure Enterprise Web W12 — AutoPilot Execution Runbook

- Status: EXECUTION_RUNBOOK_NONFINAL
- Issue: #94
- ONSure Draft PR: #95
- AutoPilot Draft PR: #288
- GitHub Actions: NOT USED
- Execution authority: `/workspace/Autopilot`

## Purpose

Execute the W12 validation mission against one exact ONSure SHA using AutoPilot server-local gates. This runbook does not authorize merge, FinalLock, Production GO or Commercial GO.

## Preconditions

- `/workspace/Autopilot` is on the approved AutoPilot binding revision containing PR #288 assets.
- `/workspace/ONSure` contains the exact PR #95 source SHA requested for validation.
- Java 17 and Maven are available to the AutoPilot runner.
- ONSure Web bootstrap credentials are runtime-only values and are not persisted in receipts.
- Core read roots may be supplied with `ONSURE_CORE_CATALOG_ROOT` and `ONSURE_CORE_VALIDATION_ROOT`; absence must render `NOT_AVAILABLE`, not zero/PASS.

## Required sequence

```bash
cd /workspace/Autopilot
python3 scripts/validate_onsure_web_w12_binding.py
python3 scripts/admit_autopilot_onsure_web_w12_java_mission.py --api-url http://127.0.0.1:<java-api-port>
python3 scripts/run_autopilot_onsure_web_w12_orchestrator_v2.py --onsure-sha <EXACT_PR95_SHA>
```

The runner must HOLD if `/workspace/ONSure` HEAD differs from `<EXACT_PR95_SHA>` or if the validation subject is dirty.

## AutoPilot DAG

`G0 → {G1,G2,G3} → G4 → {G5,G6,G7} → G8 → G9 → G10`

- G0 source identity
- G1 install authoritative `onsure-core` locally + build Web package
- G2 unit/MVC/security/Core-read tests with non-zero denominator
- G3 static Web-authority contract checks
- G4 real Spring Boot runtime
- G5 health/auth
- G6 browser-security negative checks
- G7 Core read slice validation: Project/Target/Evidence facts + Assurance explicit availability
- G8 clean run A
- G9 same-SHA clean run B
- G10 per-node receipt/readback integration

## Core read expectations

The current read-only slice uses ONSure Core `ProductCatalog` and validation-store evidence. Project/Target/Evidence are authoritative Core facts. The canonical eight-stage Assurance projection is not yet implemented as a Core provider, so the Web must return `NOT_AVAILABLE` with `CORE_ASSURANCE_PROJECTION_NOT_IMPLEMENTED` rather than infer a stage from evidence or validation decisions.

## Evidence rules

The resulting receipt must identify exact ONSure SHA, AutoPilot Mission identity, environment, bounded command/result digests, per-node dependency lineage, explicit unavailable/not-run states and A/B linkage.

Java Mission admission/readback is separate from W12 execution evidence. A registered Mission without server-local execution receipts is not W12 completion.

## Result interpretation

A successful technical W12 self-validation can at most become `W12_SELF_VALIDATION_CLEAN_NONFINAL` after all required nodes and same-SHA two-clean complete. It is not independent validation and cannot by itself promote the product to `INDEPENDENTLY_VERIFIED`.

Until authoritative receipts are read back:

`W12 = NOT_RUN`
