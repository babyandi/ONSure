# ONSure Composition·Evidence·Invalidation·Recovery·Certificate Final Specification

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
30/38/39/40/41/43/51/64의 설계를 하나의 실행 가능한 의미 체계로 묶는다. Product-level Assurance는 child results, dependency topology, evidence graph, currentness, recovery state, certificate verification을 한 Context에서 계산한다.

## 2. Composition Algebra
Assurance result는 단일 PASS가 아니라 다음 tuple이다.
`A=(Decision, Strength, Currentness, Qualification, Independence, Uncertainty)`

### Decision ordering 후보
FAIL/INVALIDATED/REVOKED > BLOCKED > HOLD/CONFLICT_HOLD > INCONCLUSIVE/UNKNOWN/NOT_RUN > PASS.

단순 numeric ordering이 아니라 각 축별 ceiling을 적용한다.

### HARD dependency
- FAIL/INVALIDATED/REVOKED → parent PASS 금지
- BLOCKED → parent BLOCKED/HOLD ceiling
- HOLD/UNKNOWN/NOT_RUN/INCONCLUSIVE → parent positive strong claim 금지
- STALE/REASSESSMENT_REQUIRED → parent CURRENT 금지

### SOFT dependency
SOFT는 self-declaration이 아니다. 해당 dependency 실패가 claim에 material하지 않다는 impact proof와 policy rule이 필요하다.

### N/A
N/A는 `applicability_rule + subject_digest + requirement_epoch + rationale + evidence + reviewer`를 요구한다.

## 3. Evidence Graph
### Node classes
SOURCE, REQUIREMENT, POLICY, ORACLE, FIXTURE, EXECUTION, OBSERVATION, FINDING, RCA, PATCH, APPROVAL, QUALIFICATION, RECEIPT, FINAL_LOCK, DEPLOYMENT, RUNTIME_OBSERVATION, CERTIFICATE.

### Edge classes
DERIVED_FROM, REPERFORMED_FROM, INDEPENDENTLY_CONFIRMS, CONTRADICTS, SUPERSEDES, INVALIDATES, REVOKES, SATISFIES, VIOLATES, DEPENDS_ON, DEPLOYMENT_OF, OBSERVATION_OF, QUALIFIES, APPROVES.

### Graph rules
- immutable node content
- graph head/generation
- exact node/edge populations
- tenant namespace isolation
- dangling edge 금지
- derivation/supersession cycle 금지
- independent evidence origin은 source lineage가 독립이어야 함

## 4. Invalidation Algorithm
Input: `MaterialChangeEvent`.
1. 변경 object identity/digest/epoch 확인
2. reverse dependency index로 directly affected nodes 탐색
3. propagation rule별 graph traversal
4. claim materiality 분류
5. currentness state 후보 계산
6. FinalLock/Composition/Certificate impacted set 생성
7. signed impact receipt 생성
8. 필요한 reperformance/requalification/reacceptance action 생성

### Trigger examples
- deployed artifact mismatch → INVALIDATED
- validator defect affecting claim → INVALIDATED 또는 REASSESSMENT_REQUIRED
- qualification expiry → STALE/REASSESSMENT_REQUIRED
- policy/regulatory change → impact-dependent REASSESSMENT_REQUIRED
- new MissedFinding → historical impact scan
- key compromise → authority/signed artifact impact scan

## 5. Revocation
REVOKED는 자동 drift detector가 직접 발행하지 않는다. 권한 있는 revocation authority의 signed receipt가 필요하다. 자동 엔진은 INVALIDATED/REASSESSMENT_REQUIRED를 만들고 revoke proposal을 생성할 수 있다.

## 6. Recovery
### 원칙
`SERVICE_RESTORED != ASSURANCE_RESTORED`.

Recovery flow:
1. service availability restore
2. restored data/evidence/ledger/key inventory
3. backup manifest verification
4. ledger head reconciliation
5. missing/corrupt object detection
6. authority/key currentness revalidation
7. RecoveryQualificationReceipt
8. impacted Final/Certificate re-evaluation

과거 PASS/FinalLock은 historical fact로 남지만 current state 자동복원 금지.

### Partial recovery
authoritative Evidence 일부 미복구 → affected Claim UNKNOWN/REASSESSMENT_REQUIRED ceiling.

## 7. Rollback
rollback artifact는 historical Final이 있어도 current policy/qualification/authority와 다시 비교한다. rollback revision의 actual bytes/read-back, config, dependencies, runtime population을 확인한 후 new CurrentnessSnapshot을 생성한다.

## 8. Certificate Issuance
필수 입력:
- product composition snapshot
- final lock
- target/scope/requirement/policy epochs
- independent receipts
- qualification state
- currentness snapshot
- limitation/exclusion set
- issuer authority/key

Certificate bytes는 issuance fact를 보존하며 현재 validity는 별도 online/offline verification으로 계산한다.

## 9. Certificate Verification Modes
### ONLINE_CURRENT
현재 issuer/verifier key, revocation, currentness, policy compatibility를 조회한다.

### OFFLINE_BUNDLE_BOUND
signed offline trust bundle의 root/key/policy/revocation snapshot/time validity에 결속한다. bundle max-age 초과 시 uncertainty ceiling.

### HISTORICAL_SIGNATURE_ONLY
signature/issuance integrity만 증명하며 현재 safety/currentness를 주장하지 않는다.

## 10. Supersession
새 Certificate/Final generation이 이전 것을 supersede할 수 있으나 이전 bytes/issuance history를 삭제하지 않는다. supersession relation과 reason을 graph에 추가한다.

## 11. Multi-region / Canary
Composition population은 실제 serving cohort/region을 포함한다. canary 5% 결과는 전체 product 결과로 승격하지 않는다. mixed rollout 동안 cohort-scoped assurance만 발행한다.

## 12. Conflict Resolution
동일 claim/context에서 PASS와 FAIL이 존재하면:
- valid supersession chain → superseding result 사용
- context/epoch가 다르면 별도 generation
- 그 외 → CONFLICT_HOLD

## 13. Performance / Storage
Evidence Graph는 immutable object store + relational index/projection을 사용한다. cache/index는 authority가 아니다. graph head/population commitment를 기준으로 paginated query를 수행한다.

## 14. Final 수용기준
- Product result는 exact input population에서 deterministic recompute 가능
- 모든 ceiling reason은 graph path로 설명 가능
- invalidation은 affected Certificate까지 추적 가능
- recovery는 missing authoritative objects를 숨기지 않음
- Certificate verify는 issuance와 current validity를 분리
- stale/revoked/unknown child/result를 current full PASS로 세탁하지 않음
