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
- ONSure Web bootstrap credentials are provided as runtime-only environment values and are not written to receipts.
- Any required PostgreSQL DSN is local secret/config, never committed.

## Required sequence

```bash
cd /workspace/Autopilot
python3 scripts/validate_onsure_web_w12_binding.py
python3 scripts/run_autopilot_onsure_web_w12_local_gate.py --onsure-sha <EXACT_PR95_SHA>
```

The runner must HOLD if `/workspace/ONSure` HEAD differs from `<EXACT_PR95_SHA>` or if the workspace is dirty in a way that changes the validation subject.

## AutoPilot DAG

`G0 → {G1,G2,G3} → G4 → {G5,G6,G7} → G8 → G9 → G10`

- G0 source identity
- G1 build/package
- G2 unit/MVC/security tests
- G3 static authority-contract checks
- G4 runtime boot
- G5 health/auth
- G6 browser-security negative checks
- G7 Core read contract
- G8 clean run A
- G9 same-SHA clean run B
- G10 receipt/readback integration

## Evidence rules

The resulting receipt must identify exact ONSure SHA, AutoPilot mission identity, environment, bounded command/result digests, dependency lineage, explicit unavailable/not-run states and A/B linkage.

If G7 is not implemented, the runner must report `NOT_IMPLEMENTED`/`HOLD_NONFINAL`; it must not infer success from the shell or mockup.

## Result interpretation

A successful technical W12 self-validation can at most become `W12_SELF_VALIDATION_CLEAN_NONFINAL` after all required applicable nodes and same-SHA two-clean complete. It is not independent validation and cannot by itself promote the product to `INDEPENDENTLY_VERIFIED`.

Until authoritative receipts are read back:

`W12 = NOT_RUN`
