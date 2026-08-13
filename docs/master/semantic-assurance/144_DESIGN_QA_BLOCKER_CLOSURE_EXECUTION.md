# 144 Design QA Blocker Closure Execution

Status: `QA_DEFECT_CLOSURE_IN_PROGRESS / NON_FINAL`

본 문서는 143 이후 새 제품 설계를 추가하지 않고, 남은 Design QA defect를 실제 증거 기준으로 줄이기 위한 closure 실행 기록이다.

## 1. FR-COM-008 P0 trace orphan 재분류

기존 Batch 0 mechanical scan은 `FR-COM-008`에 대해 `test_refs=0`, `evidence_refs=0`으로 보고하여 P0 orphan으로 분류했다. 관련 repository contract는 `contracts/main-branch-protection.v1.json`이며 다음 운영 통제를 요구한다.

- pull request required
- minimum approvals = 1
- stale approval dismissal
- conversation resolution
- direct push blocked
- force push blocked
- branch deletion blocked
- administrators enforced
- independent status checks before merge

QA에서 실제 GitHub `main` branch protection API를 조회하여 운영 evidence로 닫으려 했으나 연결된 GitHub integration 권한이 branch-protection read를 허용하지 않아 HTTP 403 `Resource not accessible by integration`이 발생했다.

따라서 이 항목을 PASS나 CLOSED로 승격하지 않는다. 다만 결함의 성격을 다음처럼 정교화한다.

`P0_REQUIREMENT_TRACE_ORPHAN` → `P0_EXTERNAL_CONTROL_EVIDENCE_BLOCKED`

이는 requirement가 없어졌거나 중요도가 낮아졌다는 뜻이 아니다. 오히려 실제 GitHub control-plane evidence가 필요하다는 의미다.

### FR-COM-008 authoritative closure evidence
다음이 모두 필요하다.
1. `main-branch-protection.v1.json`의 declared control population
2. GitHub main branch 실제 branch-protection snapshot
3. declared-vs-observed control comparison result
4. mismatch가 있으면 FAIL/HOLD
5. 모든 required control이 observed=true일 때만 evidence PASS
6. snapshot의 repository/branch/observed_at/content digest 또는 equivalent immutable identity
7. Global Trace의 `test_refs[]`와 `evidence_refs[]`에 해당 verifier/evidence를 결속

현재 상태: `BLOCKED_BY_GITHUB_CONTROL_PLANE_READ_PERMISSION`.

PR merge 이력만으로 direct push/force push/admin enforcement 등을 증명할 수 없으므로 PR 이력은 대체 evidence로 사용하지 않는다.

## 2. 31 explicit semantic variants

Batch 0은 31개 explicit ID에서 cross-document normalized text variant를 발견했지만, 상세 review input은 `.onsure/requirement-universe/explicit-id-cross-document-variants.json`에 local/gitignored evidence로 남아 있다. QA branch에는 31개 각각의 source document/anchor/text/digest population이 materialize되어 있지 않다.

따라서 31개를 임의로 canonicalize하지 않는다.

Canonical disposition에 필요한 최소 입력:
- requirement_id
- 모든 source occurrence의 authority_document
- source_anchor
- exact text 또는 normalized text digest
- authority/supersession ordering
- relation candidate: SAME/REFINES/SUPERSEDES/CONFLICTS

각 ID는 다음 중 하나로 닫아야 한다.
- `SAME_SEMANTICS_VARIANT_TEXT`
- `CANONICAL_WITH_REFINEMENT`
- `SUPERSEDED_TEXT`
- `REAL_SEMANTIC_CONFLICT`

P0 semantic conflict가 하나라도 남으면 Design Lock 금지.

현재 상태: `31_REVIEW_INPUT_NOT_MATERIALIZED / HOLD`.

## 3. 16 duplicate semantic groups

88의 원칙에 따라 source-anchored occurrence 자체를 삭제하지 않는다. 그러나 `DUPLICATES` 관계로 판정된 동일 의미 requirement는 active denominator에 중복 산입하지 않는다.

현재 Batch 0 local evidence에는 16 duplicate group count가 있으나 QA branch에는 member population과 canonical representative decision이 materialize되어 있지 않다.

각 group closure에 필요한 값:
- group_id
- member requirement_ids
- member source digests
- canonical representative
- relation per member
- denominator_contribution: canonical=1, duplicate=0
- authority rationale

현재 상태: `16_GROUP_MEMBER_POPULATION_NOT_MATERIALIZED / HOLD`.

## 4. Denominator 승격 금지

현재 899는 계속 `record_population_candidate`다. 다음 조건 전에는 exact active denominator로 승격하지 않는다.
- 31 semantic variant disposition complete
- 16 duplicate group disposition complete
- unresolved P0 semantic conflict = 0
- resulting ACTIVE/SUPERSEDED/RETIRED/OPEN_POLICY population materialized
- deterministic population digest generated

## 5. 다음 QA closure 순서

1. FR-COM-008 control-plane evidence 획득 가능 권한/독립 snapshot 확보
2. 31 semantic variant review input을 committed review evidence로 materialize
3. 16 duplicate-group member/canonical decision input을 committed review evidence로 materialize
4. canonical disposition 수행
5. exact active denominator/digest 재계산
6. applicability 1:1 population 재생성
7. Global Trace 재실행
8. 이후 naming/artifact SHA/registry digest/baseline reconstructability로 이동

## 6. 현재 판정

- FR-COM-008: `P0_EXTERNAL_CONTROL_EVIDENCE_BLOCKED`
- semantic variants: `31_PENDING_MATERIALIZED_REVIEW_INPUT`
- duplicate groups: `16_PENDING_MATERIALIZED_REVIEW_INPUT`
- exact active denominator: `NOT_YET_PROVEN`
- Design Lock: `HOLD`

본 문서는 scanner의 과거 관측값을 삭제하거나 PASS로 덮지 않는다. 143의 `P0=1`은 마지막 실제 scan result로 보존되며, 144는 해당 P0의 closure state를 더 정확히 설명한다.
