# ONSure Runtime Currentness·Composition·Certificate·Scale 시험·운영 확장설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parent: `docs/master/06_TEST_OPERATION_IMPLEMENTATION_PLAN.md`
Requirements: `FR-META-044~060`
Architecture: `semantic-assurance/29~32`

## 1. 목적
기존 Meta-Validation 시험체계에 Deployment→Runtime Currentness, Product-level Composition, Evidence Graph, Certificate/Offline, Enterprise Authority, Distributed Work, Plugin/Adapter, AI Runtime, ONSure Meta-Assurance를 추가한다. 단순 happy-path 성공이 아니라 **잘못된 positive assurance가 생성되는 경로를 의도적으로 공격**하는 것을 기본으로 한다.

## 2. 공통 시험 원칙
- 모든 시험은 정확한 Target/Scope/Requirement/Policy/Run/Attempt identity를 가진다.
- negative fixture는 precondition proof와 positive counterpart를 함께 가진다.
- 기대 결과가 PASS가 아닌 경우 FAIL/HOLD/BLOCKED/NOT_RUN/INCONCLUSIVE/STALE/REASSESSMENT_REQUIRED/INVALIDATED/REVOKED를 구분한다.
- retry로 최초 failure를 삭제하지 않는다.
- test count가 아니라 exact case ID+digest+population epoch을 denominator authority로 사용한다.
- 테스트 Harness 오류나 Observer 불완전은 대상 PASS로 계산하지 않는다.
- operation별 receipt, attempt history, raw evidence를 보존한다.

## 3. Deployment Identity 시험군
### DT-01 Mutable Tag Substitution
동일 `image:latest` 또는 동일 branch tag가 다른 digest를 가리키게 한 후 Production Currentness 평가를 수행한다.
Expected: desired tag가 동일해도 observed digest 불일치 → `INVALIDATED` 또는 `REASSESSMENT_REQUIRED`, CURRENT 금지.

### DT-02 Verified vs Deployed Digest Mismatch
FinalLock이 artifact A를 참조하지만 실제 registry/runtime은 artifact B를 실행한다.
Expected: Verified-to-Deployed chain FAIL, Production-bound Assurance 발급 금지.

### DT-03 Running Instance Drift
DeploymentRevision은 A지만 active pod 10개 중 1개가 B를 실행한다.
Expected: runtime population closure 실패. 전체 CURRENT 금지. mismatched instance identity 명시.

### DT-04 Target Binding Escape
다른 tenant/target의 cluster/account/namespace를 DeploymentTarget으로 끼워 넣는다.
Expected: object-level tenant/target authority fail-closed.

## 4. Rollout 시험군
### RO-01 Rolling Mixed Population
old/new revision이 혼재한 중간 시점에서 전체 PASS 요청.
Expected: TRANSITIONING 또는 제한된 cohort result만 허용. Production-wide CURRENT 금지.

### RO-02 Blue/Green Traffic Authority Mismatch
Green 검증 PASS지만 traffic router는 Blue를 serving한다.
Expected: serving revision 기준 currentness 계산. Green PASS를 production current로 사용 금지.

### RO-03 Canary Scope Inflation
5% canary traffic PASS를 전체 100% production PASS로 승격 시도.
Expected: canary cohort scope로만 결과 발급. global composition 차단.

### RO-04 Multi-region Partial Stale
3개 region 중 1개가 STALE 또는 UNKNOWN.
Expected: global CURRENT 금지. region별 상태와 weakest region 표시.

## 5. Runtime Drift 시험군
각 Drift는 source SHA를 유지한 채 독립적으로 주입한다.
- CONFIG_DRIFT
- FEATURE_FLAG_DRIFT
- DEPENDENCY_DRIFT
- SECRET_REFERENCE_DRIFT
- MODEL_DRIFT
- SYSTEM_PROMPT_DRIFT
- TOOL_REGISTRY_DRIFT
- RAG_CORPUS_DRIFT
- EMBEDDING_MODEL_DRIFT
- EXTERNAL_CONTRACT_DRIFT
- INFRASTRUCTURE_DRIFT
- OBSERVER_DRIFT
- VALIDATOR_QUALIFICATION_DRIFT
- AUTHORITY_DRIFT

Expected: drift class별 affected_claims와 revalidation scope가 생성되고 material drift를 CURRENT로 유지하지 않는다.

## 6. Revocation / Recovery 시험군
### RV-01 Revoked Key Historical Reuse
Final Approval 당시 key가 이후 compromise/revocation 됐고 revocation policy가 해당 effect에 영향을 주는 경우.
Expected: policy에 따른 impact analysis. 단순 historical PASS 유지 금지.

### RV-02 MissedFinding Historical Impact
새 Critical MissedFinding을 과거 Certificate 대상과 매칭한다.
Expected: affected Certificate를 SAFE/REASSESSMENT_REQUIRED/STALE/INVALIDATED 중 정책에 맞게 재분류.

### RV-03 Rollback Auto-Restore Attack
이전 정상 artifact로 rollback한 직후 과거 CURRENT를 자동 복원하려 한다.
Expected: 현재 policy/authority/validator qualification 재검증 없이는 CURRENT 금지.

### RV-04 DR Environment Substitution
Production FinalLock을 다른 OS/runtime/DB/network를 가진 DR environment에 그대로 적용.
Expected: environment materiality 평가와 재qualification/revalidation.

### RV-05 Evidence Store Restore / Ledger Rollback
백업 복원으로 Evidence store는 과거 시점, authority/policy는 최신 상태.
Expected: chain head/epoch mismatch 검출, Assurance recovery HOLD.

## 7. Product Composition 시험군
### CP-01 Duplicate Child Inflation
동일 child result ID/digest를 여러 번 넣어 population count를 부풀린다.
Expected: duplicate reject.

### CP-02 HARD→SOFT Spoof
Critical dependency를 self-declared soft로 변경.
Expected: registered dependency authority/impact proof 없이 propagation class 변경 금지.

### CP-03 N/A Without Applicability Proof
Critical child를 N/A로 제외하되 rationale/evidence/reviewer 없음.
Expected: denominator 제외 금지, HOLD.

### CP-04 Critical HOLD Averaging
9 PASS + 1 Critical HOLD를 평균해 PASS 생성 시도.
Expected: HARD dependency propagation으로 Product PASS 금지.

### CP-05 Stale Child / Current Product
필수 LLM Provider가 STALE인데 Product CURRENT 요청.
Expected: Product currentness ceiling ≤ REASSESSMENT_REQUIRED.

### CP-06 Conflicting PASS/FAIL Latest-wins
동일 claim/context에서 supersession proof 없이 최신 PASS만 선택.
Expected: CONFLICT_HOLD.

### CP-07 Self-validation Level Inflation
`PASS@AL1`을 independent evidence 없이 AL4로 승격.
Expected: assurance strength monotonic ceiling violation.

### CP-08 Missing Region/Target Population
Product topology에는 존재하지만 composition population에서 일부 target를 누락.
Expected: population closure failure.

## 8. Evidence Graph 시험군
- DERIVED_FROM cycle 생성
- SUPERSEDES cycle 생성
- dangling edge
- cross-tenant material edge
- CONTRADICTS edge가 있는데 conflict 무시
- INVALIDATES edge가 있는데 Certificate CURRENT 유지
- retry PASS가 failure node를 삭제
- FinalLock material parent가 PRIMARY evidence까지 연결되지 않음
- Certificate가 CompositionSnapshot 없이 생성됨

Expected: graph invariant fail-closed, explainable ceiling path 생성.

## 9. Certificate 시험군
### CE-01 Expired Certificate Presented as Current
Expected: EXPIRED가 issued PASS보다 우선.

### CE-02 Revoked Certificate Offline Reuse
Expected: current revocation snapshot이 없으면 OFFLINE_STATUS_UNCERTAIN/BLOCKED; 무기한 CURRENT 금지.

### CE-03 Limitation/Exclusion Omission
Internal composition에는 exclusion이 있으나 public Certificate에서 제거.
Expected: issuance/verification 실패.

### CE-04 QR Secret Leakage
QR payload에 token/secret/raw evidence 삽입.
Expected: generation reject.

### CE-05 Wrong Subject Digest
정상 Certificate를 다른 product version에 재사용.
Expected: signature가 유효해도 subject binding mismatch reject.

### CE-06 Revoked Issuer Key
revoked/expired key로 신규 Certificate 발급.
Expected: issue operation 차단.

## 10. Offline / Trusted Time 시험군
- local clock rollback
- trust bundle expiry
- stale revocation snapshot
- offline key registry old epoch
- reconnect에서 offline approval과 online revocation 충돌
- duplicated offline receipt replay
- long-offline 상태에서 CURRENT 유지 시도

Expected: uncertainty/freshness ceiling 노출, reconnect conflict HOLD.

## 11. Enterprise Authority 시험군
### EG-01 Over-delegation
child grant가 parent보다 넓은 subject/operation/time 범위를 요청.
Expected: reject.

### EG-02 Same Principal Multi-key Four-eyes
동일 person/principal의 두 key/account로 2인 승인 충족 시도.
Expected: unique principal/admin-owner 기준 미충족.

### EG-03 Break-glass Final PASS
Emergency Authority가 Final PASS/Certificate level 상승 시도.
Expected: operation access만 가능, assurance strength 상승 금지.

### EG-04 Legal Hold Freshness Extension
Legal Hold를 근거로 expired evidence/certificate 유효기간 연장.
Expected: retention과 validity 분리, 연장 금지.

### EG-05 Delegation Revocation Propagation
parent grant revoke 후 child grant 사용.
Expected: child effective authority 재평가 및 사용 차단.

## 12. Distributed Work / Scale 시험군
### SC-01 Duplicate Delivery
동일 WorkUnit 두 worker가 동시에 처리.
Expected: logical effect/receipt commitment/nonce 1회.

### SC-02 Stale Lease Late Commit
lease를 잃은 worker가 늦게 성공 결과 commit.
Expected: stale attempt reject; history는 보존.

### SC-03 Retry PASS Hides Failure
attempt1 FAIL, attempt2 PASS.
Expected: attempt graph 보존; stable PASS로 자동 세탁 금지.

### SC-04 Partition Omission
100 partition 중 1개 누락 후 aggregate PASS.
Expected: exact denominator closure failure.

### SC-05 Aggregation Order Nondeterminism
동일 results를 임의 순서로 합성.
Expected: canonical ordering으로 동일 digest.

### SC-06 Cross-tenant Work Mix
다른 tenant WorkUnit 결과를 aggregate.
Expected: tenant-bound reject.

### SC-07 Budget Exhaustion
CPU/GPU/token budget 고갈로 일부 partition skip.
Expected: BLOCKED/RESOURCE_LIMIT + Coverage 영향, PASS 금지.

## 13. Plugin / Adapter 시험군
- unsigned plugin
- revoked publisher
- plugin version rollback
- manifest privilege understatement
- undeclared network/filesystem access
- parser가 unsupported syntax를 조용히 drop
- adapter가 UNKNOWN을 safe default로 매핑
- plugin result schema-valid but semantically incomplete
- 동일 publisher/implementation이 independent oracle plugin도 통제
- plugin update 후 old qualification 재사용

Expected: Qualification/authority ceiling 강제.

## 14. AI Runtime 시험군
### AI-RT-01 Provider Alias Swap
같은 model alias가 실제 새 model deployment를 가리킴.
Expected: Model Drift/reassessment.

### AI-RT-02 Dynamic Prompt Fragment Omission
hash 대상에서 runtime system fragment를 제거.
Expected: prompt identity incomplete/HOLD.

### AI-RT-03 Silent RAG Reindex
corpus/index가 바뀌지만 app source는 동일.
Expected: RAG epoch drift.

### AI-RT-04 Undeclared Tool
agent가 registry에 없는 tool 호출.
Expected: authorization/security failure.

### AI-RT-05 Cross-tenant Memory
다른 tenant memory가 context에 주입.
Expected: Critical tenant isolation failure.

### AI-RT-06 Favorable Seed Sampling
실패 seed를 제외하고 PASS seed만 결과 population에 포함.
Expected: precommitted/reproducible population 검증 실패.

### AI-RT-07 Judge Common-mode Blind Spot
target과 judge가 같은 model family/knowledge path.
Expected: independence strength 하향, independent proof 불가.

### AI-RT-08 Multi-agent Majority != Ground Truth
여러 agent가 동일 오답에 합의.
Expected: majority agreement는 corroboration만, GT 승격 금지.

## 15. ONSure Meta-Assurance 시험군
- Core validator 제거 mutant
- Oracle 반전 mutant
- severity weakening mutant
- NOT_RUN→PASS mutant
- Evidence binding 제거 mutant
- stale receipt 허용 mutant
- independent self-attestation mutant
- qualification stale 재사용
- Adapter parser blind spot seeded fixture
- ONSure release build digest와 qualification build mismatch
- target archetype NOT_PROVEN인데 L5/AL5 발급 시도

Expected: Critical mutant kill 100%, archetype qualification ceiling 강제.

## 16. 운영 Runbook 확장
추가 Incident class:
- DEPLOYMENT_IDENTITY_MISMATCH
- RUNTIME_POPULATION_DRIFT
- CERTIFICATE_STALE_OR_REVOKED
- OFFLINE_RECONCILIATION_CONFLICT
- AUTHORITY_DELEGATION_ABUSE
- BREAK_GLASS_USE
- PLUGIN_COMPROMISE
- VALIDATOR_QUALIFICATION_REVOKED
- COMPOSITION_CONFLICT
- EVIDENCE_GRAPH_CORRUPTION
- DISTRIBUTED_AGGREGATION_INTEGRITY

각 Incident는 affected subjects/certificates, first detected at, currentness impact, containment, revalidation requirement, customer communication을 포함한다.

## 17. 모니터링 확장
- verified/deployed/running digest mismatch count
- active runtime drift count by class
- current/stale/reassessment/invalidated/revoked certificate count
- rollout mixed population duration
- composition HOLD/conflict rate
- weakest-critical dependency distribution
- evidence graph invalid edge count
- offline revocation age/uncertainty distribution
- delegation/break-glass usage
- duplicate WorkUnit/stale attempt rejection
- deterministic aggregation mismatch count
- Plugin/Adapter qualification expiry/revoke count
- AI runtime drift/model alias change count
- ONSure target-archetype qualification coverage

## 18. 수용기준
- 모든 FR-META-044~060은 최소 1개 positive, 2개 semantic negative/adversarial case를 가진다.
- Production-bound CURRENT는 mixed/mismatched runtime population에서 발급되지 않는다.
- Product Composition은 critical dependency의 미확정/부정 상태를 숨기지 않는다.
- Certificate는 expiry/revocation/offline uncertainty를 fail-open하지 않는다.
- Distributed retry/duplicate가 denominator를 부풀리지 않는다.
- Plugin/Adapter/ONSure self-qualification을 자기선언으로 승격하지 않는다.
- AI nondeterminism과 multi-agent agreement를 단일 PASS/GT로 축소하지 않는다.
- 실제 실행 전까지 본 확장설계는 `DESIGN_ONLY / NOT_RUN / NON_FINAL`이다.
