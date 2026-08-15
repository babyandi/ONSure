# 160 Final Target Product Authority Reconciliation

Status: `DESIGN_AUTHORITY_DECISION / NORMATIVE_REFINEMENT / NON_FINAL`

## 1. Problem
The repository contains two independent normative trees:
- `docs/05_PRODUCT_REQUIREMENTS_AND_ACCEPTANCE.md` + `docs/40~44`, which declare a final financial-sector target product and preserve `FR-FIN-01~22`.
- `docs/master/00_ONSURE_MASTER_DESIGN_SET.md` + `docs/master/01~08` + `docs/master/semantic-assurance`, which declare the development/design baseline.

Neither tree currently declares supersession over the other. Therefore omission of the `docs/05 + 40~44` tree from the Requirement Authority Manifest is an authority-population defect, not evidence that the final-target tree is obsolete.

## 2. Authority hierarchy
The two trees are not mutually exclusive and MUST NOT be resolved by deleting one.

### A. FINAL_TARGET_PRODUCT_AUTHORITY
The final product goal-state authority set is:
1. `docs/05_PRODUCT_REQUIREMENTS_AND_ACCEPTANCE.md` — canonical final-target Requirement-ID anchor and acceptance principles. `FR-FIN-01~22` MUST retain identity.
2. `docs/41_ONSURE_FINAL_TARGET_ARCHITECTURE.md` — final-target architecture refinement.
3. `docs/42_VSCODE_AGENT_AND_GIT_FULL_CHAIN_DESIGN.md` — VS Code/Git full-chain detailed refinement.
4. `docs/43_FINANCIAL_CONTROL_TRACE_AND_ACCEPTANCE.md` — financial-control trace and acceptance refinement.
5. `docs/44_UNIFIED_AI_WORK_DEVELOPER_ASSURANCE_DESIGN.md` — unified work/developer/assurance refinement.
6. `docs/40_FINAL_PRODUCT_RESEARCH_AND_ROLE_MODELS.md` — normative input/source basis; it may originate source-derived requirements only through the Requirement Authority process and may not override explicit `FR-FIN` requirements by implication.

Within this set, explicit requirement text in `docs/05` has precedence for `FR-FIN-*` identity/text unless another document explicitly states `REFINES` or `SUPERSEDES` that exact Requirement ID. Architecture/control/workbench documents refine implementation meaning; research input does not silently override an explicit product requirement.

### B. DEVELOPMENT_DESIGN_AUTHORITY
`docs/master/00_ONSURE_MASTER_DESIGN_SET.md`, `docs/master/01~08`, and eligible `docs/master/semantic-assurance` documents are the development/design realization authority.

This authority MAY decompose, refine, constrain, implement, test, or evidence final-target requirements. It MUST NOT silently omit, downgrade, rename, or supersede an `FR-FIN-*` requirement merely because it is outside `docs/master`.

## 3. Requirement Universe disposition
`FR-FIN-01~22` are explicit Product Design Requirement Universe requirements and MUST be included in the active Requirement Authority Manifest.

They are top-level goal requirements. Existing granular requirements (`FR-COM`, `FR-META`, `FR-FRESH`, `FR-LEARN`, NFRs and non-ID materialized requirements) are not deleted when they overlap. Instead explicit semantic relations MUST be created, such as `REFINES`, `DECOMPOSES`, `SATISFIES`, `OVERLAPS`, or `CONFLICTS`.

Coverage MUST NOT be double-counted. A top-level `FR-FIN` requirement may be closed by verified closure of all mandatory child/refinement requirements plus any acceptance criteria unique to the parent. Mere textual similarity does not close either node.

## 4. Generator and manifest rule
Requirement authority discovery MUST support repository-relative allowlisted paths outside `docs/master`. The generator MUST NOT infer authority from directory location.

Required behavior:
- Requirement Authority Manifest explicitly lists `docs/05` and `docs/40~44` rows.
- Manifest row disposition controls eligibility.
- A manifest-eligible source outside `docs/master` is scanned exactly like an eligible source inside it.
- An unlisted source remains fail-closed.
- `docs/master/**/*.md` is not an implicit allowlist.

## 5. Epoch impact
The current `EPOCH::REQUIREMENT::0002` was generated without `FR-FIN-01~22` and therefore remains valid historical evidence of that authority epoch but is not sufficient as the post-reconciliation active denominator.

Create a new requirement denominator epoch (next monotonic epoch, expected `EPOCH::REQUIREMENT::0003`) after:
1. manifest population includes the final-target authority set;
2. `FR-FIN-01~22` extraction is gap-free;
3. semantic relation mapping to granular requirements is materialized;
4. applicability and global trace are regenerated;
5. old->new epoch reconciliation is recorded;
6. deterministic rerun digest is stable.

Do NOT delete or rewrite epoch 0002 evidence.

## 6. Conflict handling
If an `FR-FIN-*` requirement conflicts with a `docs/master` requirement:
- do not choose based on path, recency, text length, or implementation convenience;
- create an explicit authority/conflict record;
- preserve both source statements;
- HOLD the affected positive claim until an explicit `REFINES/SUPERSEDES/PRECEDENCE` decision is made.

If there is no semantic conflict and the master requirement is a decomposition/refinement, both remain current with an explicit relation.

## 7. Development impact
This decision reopens Requirement-authority/denominator qualification only. It does NOT invalidate proven implementation/test evidence for unrelated contracts.

Claude should continue autonomously by:
1. extending the authority manifest/materializer to repository-relative allowlisted paths;
2. adding the six final-target authority documents;
3. materializing FR-FIN-01~22;
4. generating semantic relations to existing requirements;
5. creating the next requirement epoch and reconciliation;
6. rerunning applicability, trace/orphan and coverage closure;
7. continuing coverage/integrity work without waiting for approval unless a true unresolved semantic authority conflict is found.

## 8. Current decision
`FINAL_TARGET_TREE_STATUS = CURRENT_NORMATIVE_PRODUCT_TARGET`
`MASTER_TREE_STATUS = CURRENT_NORMATIVE_DESIGN_REALIZATION`
`FR_FIN_01_22_STATUS = MUST_INCLUDE_IN_PRODUCT_DESIGN_RU`
`EPOCH_0002_STATUS = HISTORICAL_VALID_BUT_PRE_RECONCILIATION`
`GLOBAL_LOCK = HOLD / NON_FINAL`
