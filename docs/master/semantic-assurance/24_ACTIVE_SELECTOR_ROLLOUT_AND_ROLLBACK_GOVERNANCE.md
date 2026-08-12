# ONSure Active Selector Rollout & Rollback Governance

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
본 문서는 v1과 v2 Contract/Runtime이 공존하는 동안 어떤 버전이 현재 authority인지, 언제 v2를 active로 전환할 수 있는지, rollback 시 assurance history와 current validity를 어떻게 보존할지를 정의한다.

Candidate 파일 존재, branch merge, 테스트 일부 PASS만으로 active authority를 바꾸지 않는다.

## 2. Selector 모델
Active Selector는 contract family별로 독립 관리한다.

필수 필드:
- selector_id
- contract_family
- active_version
- active_contract_digest
- previous_version
- previous_contract_digest
- effective_at
- migration_receipt_sha256
- qualification_receipt_sha256
- shadow_comparison_set_digest
- blocker_set_digest
- rollback_pointer
- authority profile/epoch
- nonce/expiry
- signature

## 3. 전환 단계
상태:
- CANDIDATE_REGISTERED
- STATIC_QUALIFICATION_PENDING
- STATIC_QUALIFIED
- RUNTIME_SHADOW_PENDING
- RUNTIME_SHADOW_ACTIVE
- DISAGREEMENT_HOLD
- INDEPENDENT_QUALIFICATION_PENDING
- ACTIVATION_APPROVAL_PENDING
- ACTIVE
- ACTIVE_STALE
- ROLLBACK_PENDING
- ROLLED_BACK
- REVOKED

단계 생략 금지.

## 4. Activation Prerequisite
v2 activation 전 최소:
1. Schema/Contract static validation PASS
2. runtime compile/test PASS
3. exact migration population 생성
4. v1→v2 reconstruction 실행
5. shadow comparison 실행
6. all blocking disagreement disposition
7. independent OTester/OAudit qualification
8. open P0 blocker 0
9. selector authority current
10. rollback path tested

## 5. Shadow 기준
Shadow 단계에서는 v1이 authority를 유지한다. v2는 같은 입력을 읽고 별도 decision을 생성한다.

필수 비교:
- v1 decision
- v2 decision
- evidence strength
- missing v2 evidence
- independence/qualification/freshness 차이
- denominator 차이

`v1 PASS / v2 HOLD|FAIL|BLOCKED`는 activation blocker다.

## 6. Disagreement Disposition
허용:
- LEGACY_FALSE_PASS_CONFIRMED
- V2_FALSE_BLOCK_CONFIRMED
- EVIDENCE_MISSING
- MIGRATION_GAP
- POLICY_DIFFERENCE_APPROVED
- UNKNOWN_HOLD

설명문만으로 닫지 않고 authority + evidence + expiry를 요구한다.

## 7. Activation Approval
Activation approval은 일반 merge approval과 분리한다.
- purpose=`CONTRACT_ACTIVATION`
- exact selector payload digest
- exact candidate contract/runtime digest set
- qualification receipts
- shadow results
- blocker set
- nonce/expiry
- dual-control 또는 정책상 필요한 SoD

## 8. Atomic Selector Change
selector change는 다음을 atomic하게 기록한다.
- old selector
- new selector
- effective timestamp
- transition receipt
- rollback pointer

부분적으로 consumer마다 다른 selector를 보는 split-brain을 금지한다.

## 9. Consumer Compatibility
모든 consumer는 active selector version을 명시적으로 확인한다.
- API
- Web
- VS Code
- report generator
- Final reconstruction
- background jobs

unknown/newer version을 읽지 못하면 fail-closed한다.

## 10. Mixed-version Run 금지
한 validation run 내부에 서로 다른 active contract epoch을 섞지 않는다. Run 시작 시 selector snapshot을 고정한다.

중간 selector change가 발생하면:
- current run continue under frozen snapshot, 또는
- stale/cancel/restart
정책을 명시한다.

## 11. Rollback
Rollback은 단순 selector pointer 복귀가 아니다.

필수:
1. rollback authority
2. rollback reason/finding
3. previous contract/runtime digest 재검증
4. compatibility check
5. state/data migration reversibility
6. selector transition
7. post-rollback validation
8. active assurance reconstruction

## 12. Assurance History
과거 v2 ACTIVE/PASS event는 immutable history로 보존한다. rollback 후 현재 validity는 별도 상태로 표시한다.

`historically active`와 `currently active`를 분리한다.

## 13. Emergency Rollback
보안/운영 Critical 상황의 emergency rollback도 single-person unlogged override를 허용하지 않는다.
- emergency authority profile
- explicit scope
- expiry
- post-event review
- mandatory requalification

## 14. Multi-node / Region Consistency
selector는 global/region scope를 명시한다.

필수:
- selector epoch
- replication watermark
- region acknowledgement
- stale node count
- quorum/leader semantics

모든 required region이 current selector를 반영하기 전 global ACTIVE를 주장하지 않는다.

## 15. Rollout 전략
지원 후보:
- SHADOW_ONLY
- INTERNAL_CANARY
- TENANT_CANARY
- PERCENTAGE_CANARY
- REGION_CANARY
- FULL

Canary PASS는 FULL qualification이 아니다.

## 16. 자동 승격 금지
다음 조건으로 자동 ACTIVE 금지:
- CI green
- fixture count 충족
- candidate file 존재
- PR merged
- developer approval
- 일정 기간 장애 없음

## 17. Negative Fixture
1. unsigned selector
2. expired activation approval
3. stale qualification
4. open P0 blocker
5. shadow disagreement hidden
6. consumer old-version incompatibility
7. mixed selector epochs in one run
8. one region stale
9. rollback target bytes changed
10. rollback without post-validation
11. candidate filename auto-discovery activation
12. same principal candidate author+activation authority where SoD required

## 18. UI/Operation
운영 화면은:
- active selector
- candidate selectors
- shadow status
- disagreement count
- qualification status
- blocker count
- activation authority
- rollback target
- region propagation
을 보여준다.

## 19. Claude 개발 경계
구현 순서:
1. selector store/read API
2. run-time selector snapshot
3. shadow comparison binding
4. activation prerequisite checker
5. signed transition receipt
6. rollback transaction
7. multi-node propagation

현재 selector는 v1 active / v2 HOLD를 유지한다.

## 20. 현재 상태
- selector schema candidate: 존재
- rollout state: HOLD
- v2 active: false
- shadow execution: NOT_RUN
- activation approval: NOT_RUN
- rollback qualification: NOT_RUN
