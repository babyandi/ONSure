# ONSure Claude Semantic Change Intake·Design Lock Candidate

Status: `DESIGN_ONLY / CANDIDATE / NON_FINAL`
Covers tasks: **14~15**
Parents: `81`, `91`, `100`, `104`, `105`

## 1. Claude implementation semantic intake
Claude 개발 중 발견되는 설계 의미 변경은 다음 queue로 들어온다.
- NEW_REQUIREMENT
- DESIGN_AMBIGUITY
- CONTRACT_GAP
- STATE_SEMANTIC_CONFLICT
- AUTHORITY_GAP
- POLICY_VALUE_REQUIRED
- MIGRATION_EXCEPTION
- IMPLEMENTATION_CONSTRAINT
- SECURITY_BOUNDARY_CHANGE
- NEW_FAILURE_MODE

각 item:
- change_id
- discovered_at_commit
- discovered_by
- affected_requirement_ids[]
- affected_design_docs[]
- observed_implementation_paths[]
- description
- proposed_semantic_change
- change_class
- assurance_impact
- baseline_impact
- disposition

## 2. Change Queue disposition
- ACCEPT_DESIGN_CHANGE
- IMPLEMENTATION_MUST_CONFORM_TO_DESIGN
- NEEDS_NEW_OPEN_DECISION
- DEFERRED_NON_BLOCKING
- REJECTED
- HOLD_FOR_INDEPENDENT_REVIEW

Claude가 구현 편의를 이유로 assurance ceiling, fail-closed default, independence/authority semantics를 약화시키면 `IMPLEMENTATION_MUST_CONFORM_TO_DESIGN`이 기본이다.

## 3. Drift classes
- DOCUMENT_ONLY_DRIFT
- CONTRACT_DRIFT
- OPERATION_DRIFT
- STATE_DRIFT
- AUTHORITY_DRIFT
- POLICY_DRIFT
- PERSISTENCE_DRIFT
- API_DRIFT
- TEST_ORACLE_DRIFT

P0-impact drift가 unresolved이면 Design Baseline Candidate 승격 금지.

## 4. Task 15 Final Design Baseline Candidate 판정
설계상의 최종 후보 판정 함수:
`candidate_ready = requirement_population_locked && trace_closed && p0_orphan_zero && p0_conflict_zero && exact_artifact_inventory_locked && implementation_alignment_scanned && unresolved_p0_change_queue_zero`

현재 값:
- requirement_population_locked = false
- trace_closed = false (global scan not run)
- p0_orphan_zero = unproven
- p0_conflict_zero = design-doc candidate only, repository-wide unproven
- exact_artifact_inventory_locked = false
- implementation_alignment_scanned = partial inventory only
- unresolved_p0_change_queue_zero = not yet materialized

따라서 현재 최종 판정은:
`DESIGN_BASELINE_CANDIDATE_HOLD`

## 5. Candidate가 READY가 된 이후에도 하지 않는 것
- Production GO
- Commercial GO
- FinalLock authority activation
- v2 Active Selector activation
- ONSure qualification 주장

Design Baseline Candidate는 개발 의미 기준선을 뜻할 뿐 제품 Assurance PASS가 아니다.

## 6. 14~15 완료 의미
Task 14는 change intake/semantic drift governance 설계로 완료한다.
Task 15는 **현재 HOLD라는 진실한 후보 판정**으로 완료한다. READY 조건을 충족하지 않았는데 READY/LOCKED를 선언하지 않는다.
