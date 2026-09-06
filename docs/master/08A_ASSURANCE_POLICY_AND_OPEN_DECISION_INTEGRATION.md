# ONSure Assurance Policy·Open Decision 통합 부속 정본

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parent: `08_REVIEW_CHECKLIST_OPEN_DECISIONS.md`

## 1. 목적
기존 08의 재무/법무/엔지니어링/규제/계약 결정 이력을 보존하면서, 35·42·61·66~69에서 새로 생긴 Assurance 정책 결정을 한 곳에서 추적한다. 기존 08을 대체하지 않는다.

## 2. P0 configurable policy로 전환할 항목
- evidence TTL
- currentness evaluation interval/TTL
- validator qualification validity
- certificate validity/revalidation period
- offline grace 및 revocation snapshot max-age
- critical HARD dependency propagation
- N/A proof requirement
- four-eyes required operation set
- delegation maximum depth
- break-glass maximum TTL
- statistical confidence level / minimum sample / zero-failure claim rule
- retry/flakiness classification threshold
- canary/rolling population closure rule
- multi-region currentness aggregation rule
- plugin qualification expiry/requalification trigger
- AI model/prompt/RAG/provider drift trigger
- ONSure release qualification validity/requalification trigger

## 3. 결정 유형
각 항목은 `FIXED_POLICY | TENANT_CONFIGURABLE_WITH_FLOOR | INDUSTRY_PROFILE | PRODUCT_TIER | CONTRACT_OVERRIDE_WITH_CEILING | OPEN` 중 하나로 분류한다.

### 고정해야 하는 안전 불변식
다음은 configurable value가 아니라 고정 invariant다.
- NOT_RUN/HOLD/BLOCKED/INCONCLUSIVE/UNKNOWN을 PASS로 승격 금지
- revoked/invalid signature를 CURRENT로 사용 금지
- Critical HARD child FAIL/INVALIDATED/REVOKED를 parent PASS가 숨기지 못함
- self-validation을 independent verification으로 승격 금지
- expired/revoked authority를 effect-time authority로 사용 금지
- exact denominator/population commitment 없이 product/final certificate 발급 금지
- break-glass로 Assurance strength 상승 금지
- offline uncertainty를 online currentness처럼 표시 금지

## 4. 초기 안전 기본값 후보
값은 구현 편의를 위한 확정값이 아니라 `BASELINE_CANDIDATE`이며 실제 운영 데이터/산업 profile로 조정한다.
- unknown/missing/unverifiable → HOLD 또는 UNKNOWN
- stale observer/currentness data → REASSESSMENT_REQUIRED
- deployment/runtime identity mismatch → INVALIDATED
- certificate revocation service unreachable: online mandatory profile에서는 verification UNKNOWN/HOLD
- same-principal two-key approval → four-eyes 불충족
- unsupported target feature → PARTIAL/NOT_PROVEN
- retry after failure → prior attempt history 보존, stable PASS 자동부여 금지

## 5. Industry Profile 결정 연결
- 금융: 폐쇄망/강한 SoD/감사·보존/독립검증/currentness 강화 후보
- 공공: data residency, offline, 공급망·artifact provenance 강화 후보
- 의료: 개인정보/데이터 provenance/human acceptance 강화 후보
- 일반 Enterprise: 조직 policy override는 허용하되 hard invariant 완화 금지

## 6. Product Assurance Tier 결정 연결
상품 Plan과 기술 Assurance Tier를 분리한다. 고가 Plan이라고 높은 Assurance Tier를 자동 부여하지 않는다.
- AT0 UNASSESSED
- AT1 EXECUTED
- AT2 EVIDENCE_BOUND
- AT3 INDEPENDENT
- AT4 QUALIFIED
- AT5 PRODUCTION_BOUND_CURRENT

각 Tier는 `68_PRODUCT_ASSURANCE_TIER_AND_SERVICE_PROFILE_DESIGN.md`의 증거 조건을 따른다.

## 7. 미확정값의 처리
미확정 정책이 P0 의미에 영향을 주면 구현이 임의 상수를 넣지 않는다. `policy_source=UNRESOLVED`, `decision=HOLD/NOT_AVAILABLE`로 노출하거나 안전 floor를 사용하고 해당 floor의 provenance를 기록한다.

## 8. 완료조건
- P0 의미에 영향을 주는 OPEN 값은 모두 fixed invariant 또는 configurable policy schema로 전환
- industry/product/tenant override 우선순위 명시
- 안전 floor보다 약한 override 차단
- policy epoch/digest가 Receipt/Final/Certificate에 결속

## 9. Final-target delta discovery 결정 추적
`162_FINAL_TARGET_DELTA_DESIGN_DISCOVERY_REOPENING.md`와 `163_FINAL_TARGET_DELTA_MISSING_DESIGN_CLOSURE.md`가 FR-FIN-01~22 도입 이후 Product Design scope를 다시 열었다. 아래 항목은 구현자가 임의 상수나 관행으로 정할 수 없다.

### 9.1 P0 — policy/authority 결정 필요
- DD-001 visibility mode별 minimum evidence floor
- DD-002 Oracle owner/change/dispute authority
- DD-003 Assurance Tier별 independence dimension 최소조건
- DD-004 opaque provider/model drift 판정 및 fallback equivalence ceiling
- DD-005 adapter/parser skipped/unsupported/failed 허용 ceiling
- DD-006 critical TCB health/requalification 기준과 fault-injection mandatory set
- DD-007 trusted-time/key compromise 영향 범위와 offline grace ceiling
- DD-008 budget/credit/token/time exhaustion 시 rescope authority와 positive-claim ceiling
- DD-009 waiver max duration/renewal/compensating-control minimum 및 invalidation trigger
- DD-010 systemic dependency criticality/fan-out and portfolio requalification floor
- DD-011 shared-corpus withdrawal/poisoning/rights-invalid derived-impact policy
- DD-012 reviewer qualification validity/conflict/rotation/calibration floor

### 9.2 P1 — policy/profile 결정 필요
- DD-013 purpose/rights/consent evidence level
- DD-014 external-effect engagement authorization profile
- DD-015 termination export/retention/legal-hold/shared-corpus settlement policy
- DD-016 locale/accessibility target profile without canonical-state weakening
- DD-017 connector retry/dead-letter/replay window
- DD-018 checkpoint/effect dedupe retention and replay ceiling
- DD-019 competing evidence-head reconciliation authority
- DD-020 vendor/subprocessor exit evidence retention/continuity threshold
- DD-021 redaction proof level and limitation disclosure
- DD-022 break-glass maximum TTL/post-review SLA/quorum
- DD-023 standard/regulatory currentness interval/supersession grace
- DD-024 benchmark contamination threshold/rotation/invalidation scope

### 9.3 Fail-closed rule
위 값이 미확정이어도 설계가 사라지는 것은 아니다. 안전 불변식은 즉시 적용한다:
- visibility/evidence insufficiency, disputed oracle, degraded independence, provider drift, parser loss, TCB failure, trust uncertainty, budget exhaustion, expired waiver는 positive claim strength를 올릴 수 없다.
- policy 값이 없어서 판단을 못 하면 `HOLD/UNKNOWN/REASSESSMENT_REQUIRED`를 사용한다.
- P0 값을 코드가 임의 기본값으로 고정해 Design Closure를 통과시키는 것을 금지한다.

## 10. Delta completion boundary
DD-001~024는 companion 설계 수준에서 owner/state/evidence/failure/oracle가 정의되었지만, 위 policy decision과 machine contract/API/schema가 materialize되기 전까지 `CONTRACTED` 또는 `QUALIFIED`로 승격하지 않는다.

## 11. Blind Discovery Waves 2~3 결정 추적
`165_BLIND_DESIGN_DISCOVERY_WAVES_2_3.md`와 `166_WAVES_2_3_MISSING_DESIGN_CLOSURE.md`에서 DD-025~040을 추가했다.

### 11.1 P0 policy/authority 결정 필요
- DD-025 regulation effective-date/transition grace/supersession authority
- DD-026 applicability-context high-impact delta threshold 및 requalification authority
- DD-028 Black-box 필수검증 미관측 dimension별 claim ceiling
- DD-029 third-party target authorization delegation/expiry/revocation floor
- DD-030 M&A/split/merge tenant transfer dual-control 및 authority rebinding rule
- DD-031 residency/sovereignty failover 허용 region/key/subprocessor policy
- DD-032 vendor/model/service EOL replacement semantic-equivalence qualification floor
- DD-033 crypto algorithm deprecation/migration/renewed-anchor policy
- DD-035 privileged support/admin intervention materiality와 automatic requalification trigger
- DD-036 independence common-control/common-knowledge disqualifier set
- DD-038 production financial-effect test 허용 operation set, ceiling, abort/reconciliation authority
- DD-040 discovery saturation novelty ceiling, blind-wave independence, invalidation trigger

### 11.2 P1 policy/profile 결정 필요
- DD-027 business calendar source/override/cutoff governance
- DD-034 external audit/regulator disclosure/redaction/custody profile
- DD-037 long-horizon evidence parser/schema support horizon and migration policy
- DD-039 observation coverage minimum/sampling-loss ceiling for OPERATING_EFFECTIVELY

### 11.3 추가 고정 불변식
- 규제/적용성 context가 material하게 바뀌면 기존 CURRENT claim을 자동 승계하지 않는다.
- Black-box 접근제약을 위험 없음 또는 PASS 근거로 사용하지 않는다.
- 조직 이름/tenant id 변경만으로 authority/evidence lineage를 승계하지 않는다.
- 서비스 DR 성공을 residency/sovereignty PASS로 승격하지 않는다.
- provider/model EOL 후 alias/API 호환만으로 semantic equivalence를 주장하지 않는다.
- deprecated crypto profile을 strongest-current로 계속 표시하지 않는다.
- privileged manual change 후 dependent CLEAN/qualification을 자동 승계하지 않는다.
- 서로 다른 계정만으로 independence/four-eyes를 충족했다고 보지 않는다.
- bytes가 남아 있다는 이유만으로 장기 Evidence가 independently verifiable하다고 주장하지 않는다.
- telemetry gap이 있는 기간의 zero-error observation을 operating-effectiveness PASS로 승격하지 않는다.
- discovery saturation은 문서 수나 한 번의 fresh review가 아니라 `design-discovery-saturation.candidate.v1.json`의 독립 반복 게이트로만 후보화한다.

## 12. Discovery 종료 경계
현재 Wave 1에서 24건, Wave 2~3에서 16건의 신규 P0/P1 delta가 발견됐다. 따라서 `GLOBAL_DISCOVERY_EXHAUSTED=false`다. 최소 연속 2개 독립 discovery wave에서 신규 P0=0을 증명하기 전에는 Product Design Scope Complete를 재선언하지 않는다.
