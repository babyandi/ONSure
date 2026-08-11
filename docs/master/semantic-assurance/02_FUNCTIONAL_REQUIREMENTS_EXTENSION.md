# ONSure Semantic Assurance 기능 요구사항 확장

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parent authority: `../02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md`

## 1. 목적
본 문서는 기존 ONSure 기능 요구사항을 대체하지 않고, OBuilder에서 도출된 검증 메커니즘 중 ONSure의 **실제 검출력과 false-assurance 방지 능력**을 확장하는 14개 Semantic Assurance Capability를 기능 수준으로 상세화한다.

기존 02 문서의 OLearning/OPlanning/OReview/OVerification/OImprovement/OMemory/OTraining/OEvidence/ODelivery 책임은 유지한다. 신규 기능은 가능한 한 기존 프로그램에 흡수하며, 불필요한 신규 서비스 분할을 피한다.

## 2. SA-01 Evidence Reperformance & Truth Binding
### 책임
상위 도구·Agent·Receipt가 선언한 PASS, count, digest, 독립성, 실행상태를 독립 증거로 그대로 소비하지 않고 실제 대상 bytes/object와 필요한 Oracle을 재검증한다.

### 입력
- upstream receipt/report/dashboard/self-claim
- 대상 artifact path/ref/revision
- 필수 Oracle 집합
- target/scope/policy/environment identity

### 기능
- 대상 artifact를 실제 read-back하고 Git blob/SHA-256 등 immutable identity 재계산
- upstream이 선언한 digest와 실제 source bytes에서 계산한 digest 비교
- 필수 Oracle을 현재 검증 주체가 직접 재수행
- 실행 command, checkout/revision, exit code, raw log 또는 content-addressed log 보존
- 재수행하지 않은 upstream claim은 claim별 허용 이유·제한·freshness를 명시
- 상위 narrative와 하위 evidence가 불일치하면 `NARRATIVE_EVIDENCE_DRIFT` 생성

### 산출물
`ReperformanceReport`, `SubjectReadback`, `OracleReexecutionRecord`, `EvidenceStrengthAssessment`

### Evidence Strength
- `REPERFORMED_BOUND`
- `EXECUTED_BUT_PARTIALLY_BOUND`
- `AUTHORIZED_LIMITED_ACCEPTANCE`
- `DECLARED_RESULT_ONLY`

### 수용기준
- `DECLARED_RESULT_ONLY`만으로 Independent PASS 불가
- 필수 Oracle 미재수행 시 HOLD
- read-back 대상 revision과 upstream claim target 불일치 시 FAIL/HOLD
- upstream PASS와 실제 재수행 결과 충돌 시 `EVIDENCE_CONTRADICTION_HOLD`

## 3. SA-02 Denominator & Coverage Discovery
### 책임
기존 Requirement/Function/API/Table/Test 개수를 정답으로 고정하지 않고, 제품의 실제 actor·goal·state·effect·failure·rights에서 denominator를 역도출한다.

### Discovery 축
- Actor/Persona → Goal/Job
- Business Process normal/alternate/failure/recovery
- Lifecycle: ENTRY/CREATE/ACTIVE/CHANGE/SUSPEND/REVOCATION/EXIT/RECOVERY/RETENTION
- Canonical State → owner/transition
- Side Effect → command/API/event
- Persistence Need → table/object
- User/Operator Task → UX route/action
- Rights/Remedy → exercising path
- Negative-space: cancel/revoke/expire/block/report/quarantine/retry/replay/legal-hold/restore/reconcile/fallback

### 기능
- 기존 denominator를 working baseline으로만 취급
- 누락/중복/과도 분해/잘못된 병합 후보를 `DENOMINATOR_CHANGE_CANDIDATE`로 기록
- CoverageReport 생성 전에 denominator epoch을 잠금
- 새 candidate가 승인되면 Scope/Coverage/Test denominator를 stale 처리하고 재계산
- candidate를 기존 항목에 억지로 포함해 denominator를 보존하는 행위 금지

### 산출물
`DenominatorDiscoveryReport`, `DenominatorChangeCandidate`, `CoverageUniverseSnapshot`

### 수용기준
- open HIGH denominator candidate가 남으면 Full Coverage 주장 금지
- 기존 요구사항 100% trace만으로 completeness PASS 금지
- 기존 숫자와 동일하다는 이유로 PASS 금지
- CoverageReport는 반드시 최신 denominator epoch을 참조

## 4. SA-03 Obligation Closure Engine
### 책임
하나의 설계·검증 의무가 단일 Function 또는 단일 artifact 존재로 닫히는 false closure를 방지한다.

### Resolution 표현
각 obligation은 하나 이상의 `resolution_groups`를 가진다.
- `ALL_OF`
- `ANY_OF`
- `EXACTLY_ONE_OF`

Member 유형 예:
- BUSINESS_FUNCTION
- CONTROL_REQUIREMENT
- STATE_INVARIANT
- DESIGN_MECHANISM
- EVIDENCE_REQUIREMENT
- TEST_ORACLE
- OPERATIONAL_CONTROL
- HUMAN_OR_EXTERNAL_DECISION

### 상태
Discovery: `IDENTIFIED | MISSING | INPUT_REQUIRED | N_A_JUSTIFIED`
Downstream: `NOT_STARTED | ROUTED | IN_PROGRESS | SATISFIED | STALE_HOLD`

### 기능
- Function 존재만으로 invariant/test/evidence 존재를 추론하지 않음
- obligation별 target stage/owner/source rationale 라우팅
- mandatory member 중 GAP/INPUT_REQUIRED가 있으면 전체 obligation HOLD
- downstream artifact가 변경되면 관련 closure stale

### 산출물
`ObligationClosureRecord`, `ObligationRoutingMap`, `ObligationGapReport`

## 5. SA-04 Authority Lifecycle Validator
### 책임
현재 권한 체크만이 아니라 권한의 생성·이전·위임·철회·종료·복구와 decision/effect 사이의 시점 변화를 검증한다.

### Lifecycle
`PRE_CREATION -> FIRST_AUTHORITY -> ACTIVE -> TRANSFER/DELEGATION -> REVOCATION -> LAST_AUTHORITY_EXIT -> DISSOLUTION/SUCCESSION -> POST_EXIT_EVIDENCE`

### 기능
- first owner/admin materialization 검증
- delegation이 grantor authority보다 넓어지는지 검증
- delegation cycle/redelegation depth 검증
- offboarding/revoke 시 delegate authority invalidation
- last owner exit 시 successor/dissolution/HOLD
- compromise recovery 시 공격자가 만든 role/key/delegation 재검증
- decision 시점과 effect 시점의 authority를 분리하고 high-risk async effect는 필요 시 재검증
- retry가 stale authorization receipt를 authority로 갱신하지 못하게 함

### 산출물
`AuthorityLifecycleAssessment`, `DecisionEffectAuthorityRecord`, `AuthorityRevalidationReceipt`

## 6. SA-05 Canonical State Authority Validator
### 책임
canonical business state의 authoritative writer와 transition owner가 중복·우회되지 않도록 검증한다.

### 기능
- state별 authoritative owner 정확히 1개인지 검증
- command boundary, precondition, concurrency/version rule, actor/tenant/purpose authorization 결속
- callback/webhook이 owner 검증 없이 final state를 직접 설정하는지 탐지
- ops/debug/migration/repair가 정상 command를 우회하는지 탐지
- AI/tool agent가 proposal을 넘어 canonical effect authority를 갖는지 탐지
- cache/search/read model이 write/effect eligibility의 sole truth가 되는지 탐지
- timeout/unknown external outcome을 무조건 FAIL로 단정하고 blind retry하는지 탐지

### 산출물
`CanonicalStateAuthorityMap`, `StateWriterConflictFinding`, `AuthorityBypassFinding`

## 7. SA-06 Rights & Remedy Executability
### 책임
`RIGHT DECLARED != RIGHT EXECUTABLE`을 강제한다.

### 기능
- right/remedy를 INFORMATIONAL, USER_ACTIONABLE, OPERATOR_ACTIONABLE, POLICY_ONLY, INPUT_REQUIRED로 분류
- actionable right에 exercising Function 존재 여부 검증
- `Right -> Function -> Command/API -> UX -> State/Effect -> Test -> Evidence` 양방향 trace
- Function이 legitimate right/policy authority 없이 effect를 발생시키는지 역검증
- right 실행이 새 actionable remedy를 생성하면 fixed-point closure 반복
- recovery/restore 전후 권리의 silent regression 검증

### 산출물
`RightsReachabilityReport`, `ActionableRightsClosure`, `RightsFixedPointReceipt`

### 수용기준
- actionable right without exercising path = HOLD
- operator remedy가 direct DB/manual hidden procedure에 의존 = HOLD
- generic catch-all function 하나로 서로 다른 권리를 닫는 경우 HOLD
- restore 후 원래 권리·resource binding·replay state가 복원되지 않으면 FAIL

## 8. SA-07 Distributed Effect Integrity
### 책임
비동기·분산·batch·외부 effect에서 요청/dispatch/success summary와 실제 item/business effect를 분리한다.

### 기능군
#### Handoff Continuity
`SOURCE_COMMITTED -> HANDOFF_DURABLE -> TARGET_CLAIMED -> TARGET_EFFECTED -> TARGET_READBACK_VERIFIED`

#### Batch Semantics
- batch status와 item receipt 분리
- atomicity class: ATOMIC/BEST_EFFORT/BOUNDED_PARTIAL/SAGA_STYLE
- retry candidate를 authoritative item state에서 재계산

#### Effect Reversibility
- REVERSIBLE
- COMPENSATABLE
- IRREVERSIBLE
- EXTERNALLY_AMBIGUOUS

#### Terminal Impact
account/resource/org close/delete 시 현재 dependency graph를 다시 읽고 모든 dependency를 TRANSFER/REVOKE/RETAIN/HOLD/COMPLETE/CANCEL/RECONCILE 등으로 disposition

### 산출물
`HandoffContinuityReceipt`, `BatchEffectReport`, `EffectReversibilityAssessment`, `TerminalImpactClosureReport`

## 9. SA-08 Freshness & Invalidation Graph
### 책임
현재 Source/Contract/Policy/Oracle/Detector 변경 후 과거 PASS를 current PASS로 재사용하지 못하게 한다.

### 기능
- Source -> Contract -> Fixture/Test -> Execution -> Audit -> Report/Certificate 계보 invalidation
- denominator change가 trace/coverage/test/report에 미치는 영향 전파
- generated report/dashboard/binary/render도 materialized view로 취급
- historical decision과 current disposition 분리
- 영향 없음 예외는 machine-readable impact proof 요구

### 산출물
`FreshnessGraph`, `InvalidationEvent`, `CurrentDispositionReceipt`

## 10. SA-09 Principal / Policy / SoD
### 책임
사용자 ID 단일 매칭을 넘어 identity, representation, role, organization, delegation과 여러 정책의 우선순위를 검증한다.

### 기능
- locator possession과 durable subject identity 분리
- organization namespace와 verified representation 분리
- identifier reassignment 시 이전 authority 자동 승계 방지
- allow/deny 다중 match precedence/specificity 검증
- alias/membership/delegation freshness와 effect-time resolution
- requester/approver/executor/verifier/overrider principal uniqueness와 SoD 검증
- emergency override expiry/evidence/post-review

### 산출물
`PrincipalResolutionAssessment`, `PolicyPrecedenceDecision`, `SoDAssessment`

## 11. SA-10 Privacy Disclosure & Observer
### 책임
민감 field를 body에서 제거했다는 사실만으로 정보누출이 없다고 판단하지 않는다.

### Observable Vector
- status code
- response schema/key
- content class
- length bucket
- retry/backoff
- notification side effect
- cache/header
- localization/accessibility projection
- latency distribution

### 기능
- private internal state의 observable equivalence class 정의
- right to act와 right to know 분리
- block/report/security/legal-hold/review 상태의 최소 disclosure 규칙
- API/Web/Mobile/email/push/SMS/webhook/export/support/accessibility/localization cross-channel consistency

### 산출물
`ObserverEquivalenceProfile`, `DisclosurePolicyAssessment`, `CrossChannelDisclosureReport`

## 12. SA-11 AI Lifecycle & Authority Closure
### 책임
AI 문서·모델·TEVV 수가 많다는 사실과 AI Use Case가 실제로 안전하고 검증됐다는 사실을 분리한다.

### AI-UC Closure
`AI-UC -> Applicability -> Non-AI Baseline -> Automation/Effect Ceiling -> Data/Egress -> Model/Prompt/Knowledge/RAG/Tool Profile -> Fallback -> TEVV Cases -> Execution -> Human Disposition -> Evidence`

### 기능
- ADOPT/DEFER/REJECT/INPUT_REQUIRED/HOLD disposition 보존
- AI OFF fallback 및 provider failure fallback 검증
- tool permit/read-back/forbidden effect 검증
- per-AI-UC normal/boundary/failure/authority-negative/privacy-security/drift case 요구
- human review가 rubber-stamp인지 독립 판단인지 구분
- AI-hidden/blinded human review fixture 지원

### 산출물
`AIUseCaseAssuranceRecord`, `AILifecycleClosureReport`, `HumanReviewModeReceipt`

## 13. SA-12 Cross-Model Semantic Trace
### 책임
Function/Requirement/Context/Component/Package/State/API/Data/Test의 개수가 같다는 이유로 mapping이 닫혔다고 보지 않는다.

### 기능
- 1:1/1:N/N:1/N:M relation 기록
- orphan source/target 탐지
- n:1 merge의 responsibility loss 탐지
- 1:n split의 authority duplication 탐지
- package/deployment 분할과 domain/component 분할 혼동 탐지
- Schema -> Authored Instance -> Validator -> Receipt closure 검증
- mandatory contract field를 validator가 소비하지 않는 경우 탐지

### 산출물
`CrossModelMappingReport`, `ResponsibilityPreservationAssessment`, `ContractEnforcementClosureReport`

## 14. SA-13 Business Semantic Integrity
### 책임
금액·수량·점수·기간·비율·quota·entitlement가 시스템 간 동일 의미로 처리되는지 검증한다.

### 기능
- unit/currency, precision/minor unit, rounding mode/stage
- component equation과 sign/zero/negative/overflow
- fee/tax/discount 포함 의미
- FX source/time/revision
- adjustment/refund/delta가 원본을 덮어쓰지 않고 상관관계를 유지하는지
- authoritative ledger와 cross-system representation
- conservation/reconciliation invariant

### 산출물
`BusinessSemanticInvariantSet`, `QuantitativeIntegrityReport`

## 15. SA-14 Validator Requalification
### 책임
ONSure validator/detector/oracle/rule/scenario generator 변경 후 기존 qualification을 자동 상속하지 않는다.

### 기능
- sealed/basic input identity
- public regression과 hidden/private qualification 분리
- genuine isolated execution
- method transport fidelity
- critical denominator와 strict recall
- critical miss 0
- open design-escape/requalification finding 0
- shadow/nonblind/self-attested 결과를 qualification substitute로 사용 금지

### 산출물
`ValidatorQualificationRun`, `MethodRequalificationReceipt`, `QualificationInvalidationEvent`

## 16. 공통 수용기준
- 모든 Capability는 대상과 적용여부를 명시한다.
- `NOT_APPLICABLE`은 근거와 반례 challenge 없이 허용하지 않는다.
- `INPUT_REQUIRED`가 남은 영역은 PASS로 승격하지 않는다.
- current execution/evidence가 없는 설계는 `DESIGN_ONLY` 또는 `NOT_RUN`이다.
- 신규 Capability를 적용해 denominator/authority/evidence가 변경되면 관련 CoverageReport와 Final Claim은 stale 처리한다.
