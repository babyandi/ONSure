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

## Current limit

The Activity Bar contribution and controlled Local API path are implemented. Dedicated Chat,
Profile, Findings, Diff/Hunk Approval, Evidence and Git/PR interaction models remain partial.
An installed VS Code Extension Host end-to-end run and independent OTester/OAudit are still
required before any release claim.

`npm run package` invokes the repository-owned deterministic VSIX wrapper. It normalizes ZIP entry
order, timestamps and compression so clean clones of the same source produce the same package
SHA-256. The generated `.vsix` remains ignored build output and is not a migration input.
