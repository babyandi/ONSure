# ONSure dirty worktree read-only reconciliation

## Scope and invariant

- Inspected repository: operator-supplied standalone ONSure worktree
- Inspected HEAD: `94a5d21fc01032957fd3fd0d1424c817bb230cef`
- Inspection mode: read-only; no checkout, reset, clean, add, commit, or file write was run there.
- Reproduction mode: tracked staged/unstaged binary diff plus the three untracked files were
  materialized in a disposable clone under `/tmp`.
- Reproducible classifier:
  `python3 scripts/analyze_onsure_worktree.py --repository "$INSPECTED_REPOSITORY"`
- NUL-delimited status digest:
  `608bf4d513749af9f0f85511c9659c13e21377edddcc5c9792d60bffd17a1154`

## Exact classification

| Classification | Count | Result |
|---|---:|---|
| Source/product input | 270 | REVIEW_REQUIRED |
| Generated output | 0 | CLEAR |
| Backup/reject/editor file | 0 | CLEAR |
| Unmerged/conflict marker | 0 | CLEAR |
| Secret/customer-data candidate | 0 | CLEAR_AUTOMATED |

The source/product inputs comprise 136 code files, 80 tests or fixtures, 19 scripts, 10 contracts,
11 status/evidence documents, 8 build configuration files, 4 documents, 1 product asset, and 1 other
source file. Automated scanning never emits matched values. Human ownership, license, and customer-data
review remain required before a cutover claim.

Git status distribution is 208 `RM`, 38 unstaged modifications, 12 staged modifications, 7 staged and
unstaged modifications, 3 untracked files, 1 staged deletion, and 1 added-then-modified file.

## Primary incompatibility

All 208 renames migrate Java paths and declarations from `io.onsure` to
`kr.co.oruda.onsure`. That namespace is neither the compatible current namespace nor the recorded future
candidate `kr.co.oruda.products.onsure`. Applying these renames would violate `AGENTS.md`, change public
binary descriptors, and break the required 240/240 public API baseline. The renames are therefore not
integrated into the remediation branch.

The three untracked files contain a server-authenticated identity and tenant RBAC implementation/test.
Their useful behavior was reviewed and reimplemented under the current `io.onsure` namespace as an
internal, additive boundary. It is wired only into authenticated Local API workflow dispatch so the
existing public constructor and all 240 public descriptors remain unchanged.

## Failure reproduction

The exact dirty file state did not reproduce a canonical build failure on this host:

| Command | Result |
|---|---|
| `mvn -B -ntp clean verify` | PASS; 254 tests, 0 failures/errors/skips |
| `python3 -m unittest discover -s tests -p 'test_*.py'` | PASS; 70 tests |

Earlier failure evidence is not attributable to the current 270-file state without an immutable command
receipt, environment inventory, and captured output. It must not be reported as a current source failure.

## Module split decision

`onsure-core` and `onsure-adapter-oruda` still select disjoint packages from the same canonical
`src/main/java` root. Split packages, artifact cycles, forbidden imports, and direct target-source
dependencies are already zero. Reducing the shared source module count from 2 to 0 requires moving the
canonical files into module-owned roots. That would conflict with the active path-freeze requirement, so
the physical move is deliberately not performed in this change. Copying or generating duplicate source
trees would only hide the coupling and is prohibited as a false remediation.

## Disposition

- Preserve the original 270-file worktree for its owner to reconcile.
- Do not commit the incompatible namespace rename.
- Integrate only independently reviewed semantic fixes under `io.onsure`.
- Keep physical module source relocation as `BLOCKED_BY_PATH_FREEZE`, not `PASS` or `NOT_RUN`.
- Re-run canonical, modular, public API, independent clone, package, and 772-file nested migration gates
  after every accepted remediation commit.
