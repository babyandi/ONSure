# ONSure for VS Code

ONSure for VS Code is the loopback-only user surface for the ONSure Java Local API. It keeps
workspace identity, execution plans, approval receipts and validation artifacts bound to the
active workspace. All results are `SELF_VALIDATION_NONFINAL`.

## Prerequisites

1. Start the Java Local API with `ONSURE_WORKSPACE_ROOT`, `ONSURE_LOCAL_API_TOKEN` and an explicit
   loopback port. The bearer token must contain at least 32 characters.
2. Configure the same loopback URL and token with `ONSure: Configure Local API`.
3. Add an `onsure-target.json` conforming to `ONSURE_TARGET_MANIFEST_V1` to the target root.

The extension rejects non-loopback URLs, browser-origin API access, paths outside the active
workspace and a registered target whose persisted source root differs from the active workspace.

## Controlled validation flow

Run these commands in order:

1. `ONSure: Register Workspace & Target`
2. `ONSure: Learn Program`
3. `ONSure: Generate Execution Plan`
4. Obtain a signed `ONSURE_EXECUTION_PLAN_APPROVAL_V1` receipt from the configured external
   approval authority.
5. `ONSure: Verify Signed Plan Approval`
6. `ONSure: Run Validation`

Validation does not accept source, target-policy or product-state path overrides. A registered
target using the reviewed execution profile cannot execute fixtures without the original plan,
derived approved plan and consumed signed approval receipt.

## Dedicated assurance surfaces and recovery

The 14 Activity Bar views use one identity-bound `/v1/workspace-snapshot` read model. Profile,
plan, run, finding, evidence, approval, runtime and Git delivery rows are no longer duplicate
status lists. Restarting the Extension Host recovers fixed workspace artifacts for the active
registered target; a source-root or identity mismatch fails closed. Run artifacts and snapshot
documents are openable only when they remain inside the active workspace.

The controlled improvement and delivery sequence is:

1. Use `ONSure: Review Patch Hunk Diff` to open a source-digest-verified diff.
2. Select explicit hunks with `ONSure: Create Hunk Signing Request`. This writes an unsigned
   `ONSURE_HUNK_APPROVAL_REQUEST_V1`; ONSure never self-signs it.
   Alternatively, `ONSure: Create Whole-file Signing Request` expands each selected file to all
   of its declared hunks. Both request forms include bounded risk, impact and rollback previews.
3. Obtain the matching signed receipt from the external trusted approver, then run
   `ONSure: Apply Approved Patch`.
4. Validate the isolated approved worktree and select baseline/current reports with
   `ONSure: Prove Improvement`.
5. Select a signed delivery approval and run `ONSure: Commit Approved Worktree`.
6. Select the reviewed PR body and run `ONSure: Open Approved Draft PR`.

The Java services revalidate signed receipts, digests, expiry, branch, changed-file set and
replay state. Direct main writes, force push and merge remain prohibited. The extension never
claims that a self-validation result is final.

## Restart-safe Autopilot control

Start the repository-owned controller from a trusted terminal with
`python3 scripts/onsure-autopilot.py run`. Its CLI, Local API and Runtime view share the fixed
`.onsure/autopilot/checkpoint.json` and `control.json` journal. The Runtime view can request
pause, resume and cancellation. The controller applies those requests to the full subprocess
group and never reports client-side HTTP abort as execution cancellation.

Completed stages are not rerun after restart. An interrupted stage whose process is gone is
recovered explicitly; a still-running orphan PID fails closed because safe process reattachment
is not yet implemented.

## Current limit

Dedicated read views, digest-bound Hunk diff preview, Hunk/whole-file external signing requests
and CLI/Local API/VS Code Autopilot controls are implemented. Core-stage cooperative checkpoints,
orphan-process reattachment, semantic Ask/Plan/Act agent modes, and
installed VS Code Extension Host end-to-end automation remain partial or `NOT_RUN`.
Independent OTester/OAudit are still required before any release claim.

`npm run package` invokes the repository-owned deterministic VSIX wrapper. It normalizes ZIP entry
order, timestamps and compression so clean clones of the same source produce the same package
SHA-256. The generated `.vsix` remains ignored build output and is not a migration input.
