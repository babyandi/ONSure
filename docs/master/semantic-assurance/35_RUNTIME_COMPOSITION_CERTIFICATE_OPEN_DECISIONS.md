# ONSure Runtime Currentness·Composition·Certificate·Scale Open Decisions

Status: `DESIGN_ONLY / OPEN_DECISIONS / NON_FINAL`
Parent: `docs/master/08_REVIEW_CHECKLIST_OPEN_DECISIONS.md`
Sources: `FR-META-044~060`, `semantic-assurance/29~34`

## 1. 목적
새 설계에서 구조는 확정했지만 수치·명명·권한·임계치·운영정책을 아직 확정해서는 안 되는 항목을 분리한다. 아래 항목은 실제 정책/계약/시장 요구에 따라 결정되기 전까지 `OPEN`이며, 개발자가 임의 기본값을 정본으로 만들면 안 된다.

## 2. Assurance Level / Strength 통합
### OD-A01 Level Ontology 통합
현재 기존 문서는 L0~L5를 `STATIC_REVIEWED→QUALIFIED_HIGH_ASSURANCE`로 사용하고, 신규 Composition 설계는 AL0~AL5를 `UNASSESSED→PRODUCTION_BOUND_CURRENT`로 제안한다.

결정 필요:
- 하나의 6단계로 통합할지
- Verification Strength와 Production Currentness를 별도 축으로 유지할지
- Customer Certificate에 어느 축을 노출할지

권고: 별도 축 유지. `Decision`, `Verification Strength`, `Currentness`를 단일 등급으로 합치면 의미손실 위험이 높다.
상태: OPEN.

### OD-A02 Product Composition Ceiling Policy
Critical HARD child의 최소 Strength가 Product ceiling을 결정한다는 기본원칙은 유지하되, CONDITIONAL/SOFT child의 정확한 downgrade 규칙과 exception approval 여부를 결정해야 한다.
상태: OPEN.

## 3. Currentness / Freshness
### OD-C01 Evidence TTL
Evidence 유형별 TTL을 결정한다.
후보 차원:
- source/build identity
- dependency/CVE snapshot
- policy/regulation
- external API contract
- runtime observation
- validator qualification
- authority/key status

모든 Evidence에 동일 TTL을 쓰지 않는다.
상태: OPEN.

### OD-C02 Runtime Observation Window
AL5/Production-bound Currentness에 필요한 runtime population 관찰시간과 최소 observation completeness를 target archetype별로 결정한다.
상태: OPEN.

### OD-C03 Invalidation vs Reassessment Threshold
어떤 drift를 즉시 INVALIDATED로 만들고 어떤 drift를 REASSESSMENT_REQUIRED 또는 STALE로 둘지 policy owner/decision matrix가 필요하다.
상태: OPEN.

### OD-C04 Revocation Authority
Certificate/Assurance REVOKED를 발행할 수 있는 principal/role과 required multi-party approval을 확정한다. 자동 detector는 REVOKED authority를 갖지 않는 원칙을 유지한다.
상태: OPEN.

## 4. Deployment / Rollout
### OD-D01 Rolling Convergence Threshold
Production CURRENT를 위해 active instance 100% expected digest를 요구할지, 명시적 excluded cohort를 허용할지 결정한다.
권고: 기본 100%, 예외는 exact excluded cohort + expiry + approval.
상태: OPEN.

### OD-D02 Canary Promotion Threshold
Canary 비율, observation window, 최소 traffic/event population, allowed failure rate를 제품 위험등급별로 결정한다.
상태: OPEN.

### OD-D03 Multi-region Composition
region별 required/optional 분류와 DR region의 denominator 포함시점을 결정한다.
상태: OPEN.

### OD-D04 Environment Materiality
Validation vs Production environment 차이를 MATCH/NON_MATERIAL/MATERIAL/UNKNOWN으로 분류하는 rule owner와 policy pack을 결정한다.
상태: OPEN.

## 5. Product Composition
### OD-P01 HARD/SOFT Dependency Authority
누가 dependency propagation class를 지정/변경할 수 있는지 결정한다. 개발자의 self-declaration만으로 HARD→SOFT 변경 금지.
상태: OPEN.

### OD-P02 N/A Approval Strength
Critical requirement/target를 NOT_APPLICABLE로 제외할 때 필요한 reviewer/authority 수준을 결정한다.
상태: OPEN.

### OD-P03 Composition Rule Version Owner
Composition algebra/rule version을 누가 제정·승인·배포·rollback하는지 결정한다. Rule 변경은 historical re-evaluation trigger가 되어야 한다.
상태: OPEN.

### OD-P04 Conflict Resolution
PASS/FAIL conflicting evidence를 언제 supersession으로 해결하고 언제 third-party oracle/HOLD로 보낼지 정책화한다.
상태: OPEN.

## 6. Certificate
### OD-CE01 Certificate Product Tiers
어떤 상품/Plan에서 Certificate를 제공할지, Web 1회/Enterprise/Continuous의 Certificate 차이를 결정한다.
상태: OPEN.

### OD-CE02 Certificate Expiry vs Revalidation Due
고정 expiry를 쓸지, event-driven currentness + revalidation_due_at을 병행할지 결정한다.
상태: OPEN.

### OD-CE03 Public Disclosure Minimum
Public verification API가 공개할 필드의 최소/최대 범위를 확정한다.
후보: issuer, subject digest, product version, level, currentness, issued/expiry, limitation count, revocation state.
상태: OPEN.

### OD-CE04 Limitation/Exclusion Disclosure
민감정보를 노출하지 않으면서 material exclusion을 숨기지 않는 disclosure template/분류를 확정한다.
상태: OPEN.

## 7. Offline / Air-gapped
### OD-O01 Offline Grace by Assurance Level
License Offline Grace와 Assurance Currentness grace를 분리하고 level/target risk별 최대 grace를 결정한다.
상태: OPEN.

### OD-O02 Trusted Time Source
TPM, enterprise NTP/time authority, secure hardware clock 등 지원 우선순위와 `time source unavailable` 시 ceiling을 결정한다.
상태: OPEN.

### OD-O03 Revocation Snapshot Maximum Age
Offline trust bundle 내 revocation snapshot의 최대 허용 age를 결정한다.
상태: OPEN.

### OD-O04 Reconnect Conflict Policy
Offline 승인/Certificate와 online revocation/policy 변경이 충돌할 때 자동 invalidation vs Human review 규칙을 결정한다.
상태: OPEN.

## 8. Enterprise Authority
### OD-E01 Four-eyes Mandatory Operation Set
Final Approval, Certificate issuance/revoke, Legal Hold, policy weakening, hidden corpus access, accepted critical risk 중 어떤 operation에 2인/3인 승인을 요구할지 확정한다.
상태: OPEN.

### OD-E02 Principal Independence Definition
서로 다른 principal을 사람 identity, corporate directory identity, admin-owner, device/key ownership 중 어느 기준까지 묶어 판단할지 확정한다.
상태: OPEN.

### OD-E03 Delegation Depth
일반 authority/final authority/emergency authority별 delegation depth와 maximum TTL을 결정한다.
상태: OPEN.

### OD-E04 Break-glass Post-review SLA
Emergency use 후 mandatory review/assurance re-evaluation SLA와 미수행 시 자동 suspension 정책을 결정한다.
상태: OPEN.

## 9. Distributed Work / Scale
### OD-S01 WorkUnit Lease
기본 lease duration, heartbeat, stale takeover, clock skew tolerance를 workload class별로 결정한다.
상태: OPEN.

### OD-S02 Retry Policy
operation class별 maximum attempt/backoff와 non-retryable failure taxonomy를 결정한다. 실패를 숨기기 위한 retry 사용 금지는 고정 원칙이다.
상태: OPEN.

### OD-S03 Aggregation Population Size
초대형 graph/repository에서 population snapshot을 어떤 shard/manifest tree로 표현할지 결정한다.
상태: OPEN.

### OD-S04 Resource Budget Ceilings
Plan별 CPU/GPU/token/storage/concurrency ceiling과 budget exhaustion 시 상업적 추가승인 흐름을 결정한다.
상태: OPEN.

## 10. Plugin / Adapter
### OD-PL01 Publisher Trust Model
ONSure first-party, customer-private, verified third-party plugin의 trust tier와 signing root를 결정한다.
상태: OPEN.

### OD-PL02 Qualification Minimum
Adapter/Plugin이 QUALIFIED가 되기 위한 negative fixture, supported-version coverage, seeded defect recall, sandbox test 기준을 결정한다.
상태: OPEN.

### OD-PL03 Privilege Classes
Plugin filesystem/network/tool/external-effect privilege class와 사용자 승인 필요 수준을 정의한다.
상태: OPEN.

### OD-PL04 Compatibility / Deprecation
Core version 변화 시 plugin qualification을 자동 stale 처리하는 compatibility policy를 결정한다.
상태: OPEN.

## 11. AI Assurance
### OD-AI01 Statistical Confidence Policy
AI behavioral claim의 sample size/confidence bound 방법을 risk class별로 결정한다. 고정 `2회 PASS`를 범용 기준으로 사용하지 않는다.
상태: OPEN.

### OD-AI02 Critical Failure Tolerance
Authorization/Tenant/Safety 등 Critical AI claim의 허용 critical failure count는 0을 기본 후보로 하되 통계적 absence claim의 요구 confidence를 확정한다.
상태: OPEN.

### OD-AI03 Model Identity for Opaque Provider
weights digest를 얻지 못하는 SaaS model에서 provider attestation/version/deployment/behavior fingerprint 중 어떤 조합을 identity minimum으로 할지 결정한다.
상태: OPEN.

### OD-AI04 Multi-Agent Independence
서로 다른 agent가 같은 model/provider/prompt/memory를 공유할 때 independence score/ceiling을 어떻게 계산할지 결정한다.
상태: OPEN.

## 12. ONSure Meta-Assurance
### OD-M01 Supported Archetype Matrix
상용 출시 시 공식적으로 QUALIFIED/PARTIAL/NOT_PROVEN으로 선언할 target archetype 목록을 확정한다.
상태: OPEN.

### OD-M02 Release Qualification Cadence
매 release, minor/major, validator-changing release별 full/partial qualification 정책을 결정한다.
상태: OPEN.

### OD-M03 External Independent Verifier
L4/L5 또는 Enterprise Certificate에 필요한 외부/별도 조직 independent verification requirement를 결정한다.
상태: OPEN.

### OD-M04 Qualification Expiry
target archetype별 qualification validity/expiry와 requalification trigger priority를 결정한다.
상태: OPEN.

## 13. 결정 절차
각 Open Decision은 다음을 남긴다.
1. decision_id
2. owner
3. alternatives
4. security/assurance/business impact
5. selected option
6. effective version/date
7. affected contracts/docs/tests
8. migration/revalidation requirement
9. approval receipt

숫자나 정책을 결정했다고 기존 evidence/certificate에 소급 적용하지 않는다. Material decision 변경은 affected historical assurance를 재평가한다.

## 14. 현재 경계
이 문서의 후보값/권고는 구현 상수나 Active Contract가 아니다. `CONFIRMED`로 승격되고 관련 Contract/Fixture/Runtime이 제정되기 전까지 Claude가 임의값을 정본으로 사용하지 않는다.
