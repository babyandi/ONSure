# ONSure module source split and runtime rehearsal — 2026-08-04

Status: `PASS_NONFINAL / PRODUCTION_NOT_AUTHORIZED`

## Source ownership

- Commit under rehearsal: `979cf40a5006443033bd50ccbefb1353b6ba74b1`.
- `onsure-core` and `onsure-adapter-oruda` own separate `src/main/java` roots.
- Shared source modules, split packages, Maven artifact cycles and forbidden import edges: 0.
- The root canonical Maven build consumes both module-owned roots and retains the standalone
  `onsure-assurance-validator` artifact, `io.onsure` namespace and 240/240 public Java API.
- No source file was copied or generated; Git records the physical change as renames.

## Contract and feature verification

- Workflow surface parity: 43 operations across CLI, Local API and VS Code.
- Local API OpenAPI paths: 16, including read-only program registration/validation, management
  overview, Gateway settings request/approval and audit events.
- LLM Gateway OpenAPI paths: 4. Token/cost metrics and content-free digest-chain evidence remain
  implemented; prompt/completion persistence is false.
- ValidationEngine resume now has an explicit replay-ledger digest-tamper regression in addition to
  checkpoint tamper, pre-existing mutation/deletion and symlink rejection.

## Build, package and database rehearsal

- Canonical `clean verify`: 282/282, repeated twice. Independent clone: 282/282.
- Modular package: 37/37 locally and in the independent clone; public API baseline: 240/240.
- Python: 142/142; Node: 9/9; deterministic VSIX package pass.
- Ubuntu candidate SHA-256: `90096dd910f0092911d8de9f78f71ffe5e1d660dc136bb99c53947d753fe9236`.
- RHEL compatibility candidate SHA-256: `74ec4e2c844f4fd7570fb3e8f7de5d541de04220a0a263c0aa88659ff47685d8`.
- Disposable PostgreSQL 16.14/Flyway: first migration 1, second 0, concurrent `[0,1]`, pending 0,
  dump/restore and restored validation pass. Production migration was not run.
- Synthetic SQLite: apply 1, idempotent apply 0 and rollback 1 pass under the required `.onsure/`
  product-state boundary; an outside-state path was correctly rejected.
- Nested `products/onsure/` cutover and rollback: 796/796 files, 10 commands, no external product
  repository.
- VS Code 1.95.3 Extension Host: online Xvfb run exit 0 and cached `--network none` rerun exit 0.
- VS Code Ubuntu runtime surface: Local API/Gateway RUNNING, content-free token/cost metrics and
  receipt chain valid; runtime tokens were not written to evidence.

## Supply chain and air-gap

- CycloneDX components: 21; dependency license review required: 0.
- Trivy 0.65.0: critical/high/medium/low all 0; npm audit total 0.
- Previous Maven offline archive validation exposed missing Flyway 12.11.0 and PostgreSQL 42.7.12
  artifacts and was not reused as a pass.
- Current dependency pack: 27 entries, SHA-256
  `55444627515a986c40832ec32ce93955eef2e46e4250840816bf2a61a2c5ea62`.
- Current Maven repository pack: 5,205 entries, SHA-256
  `e9346cedaecb4fbac73a68301fcf00312ed3d1c7c34e8245c7a34bf516dde117`; network-disabled canonical
  and modular Maven rehearsals pass.
- Current npm cache pack: 442 entries, SHA-256
  `fbe3d213ab3de7dbf34e444cbf93fb66a5c4473bb493e44216022ad14aad2728`; offline `npm ci` passes.
- External signature remains `NOT_RUN`.

## Ubuntu validation runtime

- Runtime root is the operator-selected external `$ONSURE_RUNTIME_ROOT`; prior app/lib/migration/package are preserved at
  `backups/runtime-pre-979cf40-20260804T085920Z`.
- Both user-systemd services are enabled and active on loopback. Local/Gateway unauthenticated
  requests return 401 and an untrusted Gateway Origin returns 403.
- Dual health probes: 20/20. Three restart cycles recovered both services on readiness attempt 2;
  `NRestarts=0` after the rehearsal.
- LLM evidence chain is valid and prompt/completion content recorded remains false.
- Post-update PostgreSQL custom backup is mode 0600 at
  `backups/runtime-post-979cf40-20260804T090025Z.dump`, SHA-256
  `a23984bd20ed0cf706f74d251731bceb53b332c930c491c78757fc5f57854d71`; `pg_restore --list` passes.

## Environment blocker

Bubblewrap 0.9.0 still fails while configuring loopback inside the private network namespace:
`BWRAP_LOOPBACK_PERMISSION_DENIED`. The rootless user namespace limits are enabled, but this host
does not grant the required RTM_NEWADDR operation. The security fallback remains prohibited and
GitHub Actions are not required or used.

RHEL runtime installation, live OpenAI traffic, external signer, independent legal review,
independent OTester/OAudit, Production GO and Final PASS remain `NOT_RUN`.
