# ONSure Enterprise Web — Negative Design Review

Status: DESIGN_REVIEW_NONFINAL
Scope: P01~P04 static mockups only

## Overall decision
The direction is usable as a design baseline, but MUST NOT be promoted directly to implementation without Core read-model integration. The principal risk is presentation gradually becoming a second authority.

## P01 Assurance Workspace
Risks:
- three top metrics can expand into KPI-card sprawl;
- Current Assurance can be mistaken for a project-wide synthetic score;
- Needs Attention can become a Web-computed recommendation engine;
- NONFINAL may become visually ignored if duplicated excessively.

Required controls:
- cap primary summary at three items;
- no percentage/score;
- Needs Attention must come from authoritative unresolved/blocking objects;
- use one prominent NONFINAL context banner, not repeated warning decoration.

## P02 Project / Target
Risks:
- project aggregate counts may be mistaken for project assurance judgment;
- target evidence counts alone can imply quality;
- table growth can trigger premature DataGrid framework work.

Required controls:
- no project-level synthesized assurance state unless Core defines one;
- counts must drill down to actual records;
- initial table supports only bounded sort/filter/pagination;
- Target remains independently addressable even when entered through Project.

## P03 Target Detail
Risks:
- Why this state is the highest-risk place for duplicated business logic;
- stale evidence and blocking reasons can be locally inferred by Web if contracts are weak;
- PASSED verification can visually resemble overall assurance PASS.

Required controls:
- satisfiedRequirements, unresolvedRequirements, blockingConditions and freshness must originate from Core contract;
- always label result type, e.g. Runtime verification: PASSED;
- never show a standalone PASS badge as target assurance;
- progression state is separate from assurance state.

## P04 Evidence Receipt
Risks:
- receipt detail can become a raw-log browser;
- hash/IDs can overwhelm non-technical users;
- trace visualization can become an unbounded graph;
- a verification receipt may be mistaken for an approval or independent-validation decision.

Required controls:
- Summary is default, Technical is secondary;
- show compact IDs with copy/full-detail behavior in implementation;
- default lineage view is bounded trace, not global graph;
- explicitly show missing Decision/Approval rather than infer one.

## Cross-screen rejection criteria
Reject a UI implementation if any of the following appears:
1. Assurance percentage or synthetic score without explicit Core authority.
2. Web-side promotion logic such as evidenceCount -> EVIDENCED.
3. Standalone green PASS representing overall product assurance.
4. Disabled actions without distinction between permission denial and policy block.
5. UNKNOWN / NOT_RUN / INCONCLUSIVE rendered with success semantics.
6. Project-wide state calculated from target averages or minimums in presentation code.
7. Evidence freshness calculated from uncontrolled client-side timestamps/SHA comparison.
8. Direct Set State / Set PASS / Set EVIDENCED controls.
9. Global lineage graph as mandatory navigation path.
10. More than six primary top-level navigation destinations in the first implementation slice.
11. Raw audit stream used as home activity feed without severity/importance filtering.
12. Any mock/sample value shipped without explicit non-authoritative status.

## Implementation gate
Before converting P01~P04 into product UI, the following must exist:
- ProjectSummary contract
- TargetSummary contract
- AssuranceSnapshot contract
- BlockingCondition contract
- EvidenceSummary/Receipt contract
- explicit authority/revision fields
- explicit absent/unavailable/unknown semantics
- tenant-scoped server-side resource resolution

## Decision
P01~P04 = VISUAL_MOCKUP_PRESENT_NONFINAL
Visual baseline = NOT_LOCKED
Implementation authorization from these mockups alone = NO
W12 impact = NONE / remains NOT_RUN
