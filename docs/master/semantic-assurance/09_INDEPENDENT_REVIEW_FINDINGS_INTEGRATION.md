# ONSure Independent Review Findings 통합설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
이 문서는 OBuilder에서 이식한 14개 Semantic Assurance Capability와 별개로, ONSure 자체를 반복적으로 독립 검토하면서 발견한 false-assurance, 자기참조, 운영·시간·사람·데이터셋·인증서 소비 문제를 14개 Capability 안에 흡수한다.

새 Capability ID를 추가하지 않는다. 기존 SA-01~SA-14로 표현할 수 없는 새로운 defect class가 입증될 때만 신규 Capability를 검토한다.

## 2. 통합 원칙
1. `기능이 있음 != 그 기능의 검증기가 신뢰 가능함`.
2. `독립이라고 선언됨 != 독립성이 증명됨`.
3. `Ground Truth 등급이 높음 != Ground Truth 생성경로가 Qualified됨`.
4. `Unknown=0 != Unknown이 존재하지 않음`.
5. `Excluded != 검증 불필요`.
6. `Retry 후 PASS != 안정 PASS`.
7. `Certificate 발급 성공 != 소비자가 올바르게 해석함`.
8. `서비스 복구 != Assurance graph 복구`.
9. `Validator 개선 != 기존 Qualification 유지`.
10. 모든 신규 통제는 기능 요구, Review Finding, Architecture/Contract, UX 표현, Test/Failure Injection, 운영·재자격까지 수직 trace를 가져야 한다.

## 3. SA-01 Evidence Reperformance & Truth Binding 추가 통제
### 3.1 Distributed Evidence Consistency
Evidence가 DB, Object Storage, Filesystem, Queue, Git, Certificate Store에 분산될 수 있으므로 단일 `COMMITTED` 문자열만으로 일관성을 주장하지 않는다.

필수 상태 후보:
- `CONSISTENT`
- `RECONCILABLE`
- `PARTIALLY_COMMITTED`
- `ORPHANED`
- `UNKNOWN`

필수 검증:
- DB commit 후 Object 누락
- Object 존재 후 Ledger append 실패
- Queue duplicate delivery
- Git/외부 effect 성공 후 local receipt 실패
- Certificate publish 성공 후 validity registry 실패

`PARTIALLY_COMMITTED|ORPHANED|UNKNOWN`은 Final positive evidence가 될 수 없다.

### 3.2 Result Selection Integrity
실패 run을 버리고 성공 retry만 선택하는 cherry-picking을 금지한다.

필수 기록:
- 모든 attempt ID
- first attempt result
- retry reason
- retry authorization
- attempt inclusion/exclusion reason
- final selection policy

`FAIL, FAIL, PASS`를 단순 PASS로 축약하지 않는다. 필요한 경우 `FLAKY_PASS/HOLD`로 승격한다.

### 3.3 Trusted Time
Approval expiry, evidence freshness, revocation, certificate validity는 신뢰 가능한 시간에 의존한다.

검증 대상:
- wall-clock rollback
- monotonic ordering
- NTP drift/skew
- timezone-independent canonical timestamp
- offline verifier time uncertainty
- signed timestamp 또는 동등한 external time evidence 적용 필요성

시간 신뢰성을 증명할 수 없으면 시간 의존 claim은 `TIME_AUTHORITY_UNPROVEN_HOLD`다.

## 4. SA-02 Denominator & Coverage Discovery 추가 통제
### 4.1 Requirement Universe Authority
Requirement denominator는 한 문서에서 가져오지 않는다. 최소 후보 source class:
- 사용자/사업 요구
- 계약·정책
- 구현 코드·설정
- 아키텍처/API/Data
- 운영·배포·Runbook
- Security/Privacy
- 법규/외부 표준
- 사용자·운영자 권리
- failure/recovery
- 실제 runtime behavior/incident

각 source class는 `DISCOVERED|PARTIAL|NOT_PROVEN|NOT_APPLICABLE_WITH_JUSTIFICATION` 상태를 가진다.

Critical source class가 `NOT_PROVEN`이면 Requirement Complete를 주장하지 않는다.

### 4.2 Authority Denominator Drift
Legacy requirement registry와 `docs/master`, contract, runtime-discovered requirement set이 다르면 어느 하나를 자동 권위로 선택하지 않는다.

필수 산출물:
- denominator source inventory
- source precedence/authority decision
- divergence list
- supersession/migration rule
- stale downstream inventory

### 4.3 Unknown Discovery Coverage
`unknown_count=0`은 `no unknown discovered`일 뿐이다. Unknown discovery method coverage를 별도 계산한다.

최소 discovery method:
- static structural discovery
- runtime/dynamic observation
- failure/adversarial discovery
- independent reconstruction
- external normative comparison where applicable

### 4.4 Exclusion Abuse Control
제외 항목에는 authority, reason, expiry/review, criticality, compensating control을 기록한다.

Hard HOLD:
- Critical surface를 단순 budget/time 이유로 제외하고 전체 PASS
- 반복적으로 같은 어려운 영역을 제외
- exclusion decision owner 불명
- exclusion이 denominator에서 삭제되어 coverage 상승

## 5. SA-03 Obligation Closure 추가 통제
### 5.1 Assurance-Level Ceiling
Assurance Level은 평균점수로 계산하지 않는다. Critical dimension 중 가장 낮은 충족수준이 상한을 결정한다.

Critical dimension 후보:
- Scope
- Validator Capability
- Observability
- Oracle/Ground Truth
- Evidence Binding
- Independence
- Freshness/Temporal

### 5.2 Design-Omission Mutation
코드 mutation뿐 아니라 설계의무 자체를 제거하는 구조적 mutation을 지원한다.

Mutant 예:
- Function 전체 제거
- Right/Remedy 제거
- Recovery path 제거
- Observer/Collector 제거
- denominator 항목 삭제
- state owner 중복 허용
- contract field는 남기고 runtime consumer 무시
- Final blocker를 warning으로 약화

Critical design-omission mutant가 Meta-Suite를 통과하면 해당 Capability는 Qualified될 수 없다.

## 6. SA-04 / SA-09 Authority·Principal 추가 통제
### 6.1 Independence Proof Recursion
서로 다른 run/key/model이라는 사실만으로 독립성을 인정하지 않는다.

독립성 attestation은 최소:
- actual principal ownership
- credential/KMS administrative ownership
- implementation lineage
- oracle implementation lineage
- discovery method lineage
- shared knowledge/input manifest
을 포함한다.

독립성 판정기 자체도 별도 Qualification 대상이다.

### 6.2 Reviewer Common-Mode Bias / Collusion
여러 reviewer가 같은 prior verdict, 같은 summary, 같은 draft를 보고 결정한 경우 완전 독립으로 세지 않는다.

High-risk review 후보 규칙:
- decision-before-discussion
- independent timestamp
- prior verdict hidden 여부
- shared evidence는 허용하되 shared conclusion 금지
- organizational/conflict-of-interest 기록

### 6.3 Accepted Risk Accumulation
Accepted Risk를 Finding 단위로만 보지 않는다.

추가 지표:
- cumulative accepted critical/high count
- repeated waiver count
- same finding recurrence
- approver concentration
- expired waiver count
- compensating-control execution state

반복 waiver가 사실상의 영구 우회가 되면 HOLD한다.

## 7. SA-08 Freshness & Invalidation 추가 통제
### 7.1 Offline Revocation / Maximum Offline Freshness
오프라인 Certificate/Trust Bundle은 최신 revocation을 모를 수 있다.

필수 개념:
- revocation epoch
- trust bundle generated_at
- maximum offline freshness
- offline verification uncertainty
- emergency key compromise invalidation package

maximum freshness를 초과한 offline verification은 `VALID`가 아니라 `STALE_OR_STATUS_UNKNOWN`으로 표시한다.

### 7.2 Revocation Propagation Assurance
Certificate 상태를 DB에서 바꾼 것만으로 revocation 완료로 보지 않는다.

전파 대상 후보:
- API read model/cache
- dashboard cache
- report portal/CDN
- downstream webhook/event consumers
- local/offline verification bundle

전파 지연과 미전파 대상은 Evidence로 남긴다.

### 7.3 Historical Revalidation Scale
새 MissedFinding/Rule/CVE가 과거 Validation에 미치는 영향을 대량으로 찾을 수 있어야 한다.

필수:
- reverse claim/dependency index
- affected-certificate query
- scan completeness receipt
- partial scan 구분
- backlog/lag monitoring

### 7.4 Queue Replay / Old Authority Resurrection
Async message는 `authority_epoch`, `target_digest`, `policy_epoch`, `nonce`, `expiry`에 결속한다.

오래된 queue message가 revoked approval, stale certificate, superseded remediation을 되살리면 실패다.

### 7.5 Assurance Recovery
Service restart/DB restore가 성공해도 Assurance state 복구를 별도 검증한다.

복구 후 비교:
- authority state
- evidence/claim graph
- receipt consumption/replay state
- pending approval
- target/scope/policy binding
- revocation state

## 8. SA-10 Privacy Disclosure & Observer 추가 통제
### 8.1 Assurance Communication Fidelity
기술 상태가 UI/API/CLI/PDF/Certificate에서 의미 상승되지 않아야 한다.

예:
- `SELF_VALIDATION_NONFINAL PASS` → 단순 `PASS` 금지
- `0 Critical Found` → `No Critical Defects` 금지
- `NOT_PROVEN` capability를 숨긴 “전체 검증 완료” 금지

### 8.2 Assurance Surface Semantic Parity
동일 run에 대해 다음 projection은 동일 assurance ontology를 사용한다.
- Web
- VS Code
- CLI exit/result
- API
- webhook/event
- PDF/Technical Report
- Acceptance Certificate

### 8.3 Human Misinterpretation Test
문자열만 검증하지 않고 실제 인지 실패를 시험한다.

시험 후보:
- 3초 perception test
- 색/배지/크기 hierarchy
- mobile/narrow layout
- collapsed section
- localization
- accessibility tree
- screenshot/export/PDF

Critical limitation이 존재하는데 대표 사용자가 Final PASS로 오인하면 UX FAIL이다.

### 8.4 Certificate Consumer Misuse
Certificate에는 scope, assurance level, validity, target digest, verification timestamp, current-status lookup requirement를 machine-readable하게 포함한다.

Consumer가 다른 target에 재사용하거나 stale/expired 상태를 무시할 수 있는 integration은 negative fixture 대상이다.

## 9. SA-11 AI Lifecycle & Human Assurance 추가 통제
### 9.1 Ground Truth Qualification
GT0~GT5 같은 등급만으로 충분하지 않다. Ground Truth producer/oracle도 Qualification을 가진다.

필수:
- producer identity
- oracle/tool implementation digest
- target-code coupling
- calibration history
- known failure modes
- review provenance
- validity scope/epoch

`GT3_EXECUTABLE_ORACLE`라도 oracle qualification이 NOT_PROVEN이면 Critical PROVEN claim의 단독 근거가 될 수 없다.

### 9.2 Memory-Blind Proof
`memory_blind=true` 선언만 인정하지 않는다.

차단 증거 후보:
- previous findings denied
- prior score/verdict denied
- KnowledgePattern denied
- shared vector/RAG source denied
- cached prompt/conversation context denied
- scratch state reset
- denied-source access audit

산출물: `BlindContextManifest`, `DeniedSourceAccessReceipt`.

### 9.3 Human Reviewer Qualification
Expert Reviewer도 oracle로 취급한다.

최소 qualification:
- domain/role
- conflict of interest
- recency
- blind mode capability
- Golden/calibration history
- overturn/error rate
- workload/fatigue signal where measurable

Reviewer qualification 만료 또는 기준 미달이면 GT4/Independent authority 상한을 낮춘다.

## 10. SA-14 Validator Requalification 추가 통제
### 10.1 Meta-Validator Qualification
CrossContractInvariantEngine, FinalClaimReconstructor, Independence Verifier, Contamination Classifier도 검증 대상이다.

검증해야 할 failure:
- mandatory invariant 하나 누락
- 신규 enum/field 무시
- parse exception을 warning 처리 후 PASS
- unknown field silently ignored
- partial execution을 complete로 집계

### 10.2 Hidden/Golden Corpus Governance
Hidden이라는 label만으로 충분하지 않다.

필수 관리:
- corpus owner
- authorized readers
- access log
- first disclosure time
- semantic/implementation family
- repeated query count
- rotation/retirement
- leakage incident
- leakage 시 qualification invalidation scope

### 10.3 Benchmark Precommitment
Evaluation 시작 전에 benchmark set/selection policy/denominator를 freeze한다.

결과를 본 뒤 잘 나온 corpus만 선택하거나 failed corpus를 제외하면 Qualification FAIL이다.

### 10.4 Semantic Contamination Classifier Qualification
semantic family/near-duplicate classifier 자체의 model/version/threshold/calibration을 기록한다.

classifier disagreement 또는 low confidence는 overlap 없음으로 자동 판정하지 않는다.

### 10.5 Mutation Diversity
단순 statement/operator mutation만으로 validator quality를 주장하지 않는다.

최소 mutation family:
- code mutation
- contract mutation
- authority mutation
- denominator mutation
- evidence mutation
- design-omission mutation
- cross-artifact mapping mutation
- observer/collector mutation

### 10.6 Validator Self-Improvement Governance
Detector/Rule/Oracle/Scenario Generator 변경은 일반 feature release가 아니라 Requalification Event다.

변경 시:
- prior qualification invalidation impact
- critical recall regression
- hidden/OOD benchmark
- historical certificate impact
- TCB/independence impact
을 평가한다.

## 11. Benchmark Shopping / Result Laundering 공통 금지
다음은 전체 Capability 공통 hard finding이다.
- 여러 benchmark 중 성공한 subset만 보고
- 실패 run 삭제
- failed fixture를 denominator에서 제외
- retry PASS만 report
- stale evidence를 current summary로 재포장
- human approval을 factual oracle로 대체
- high agreement를 truth로 간주

## 12. Downstream 문서 반영 규칙
### 02 Functional
본 문서의 각 통제는 해당 SA 기능의 입력·기능·산출물·수용기준으로 반영한다.

### 03 Review
최소 Finding family:
- `REQUIREMENT_UNIVERSE_NOT_PROVEN`
- `DENOMINATOR_AUTHORITY_DRIFT`
- `RESULT_SELECTION_CHERRY_PICKING`
- `TIME_AUTHORITY_UNPROVEN`
- `INDEPENDENCE_SELF_ATTESTED`
- `GROUND_TRUTH_UNQUALIFIED`
- `BENCHMARK_SELECTION_AFTER_RESULT`
- `ASSURANCE_SURFACE_SEMANTIC_DRIFT`
- `REVOCATION_PROPAGATION_INCOMPLETE`
- `ASSURANCE_RECOVERY_INCOMPLETE`

### 04 Architecture/Data/API
신규 logical records 후보:
- EvidenceConsistencyTransaction
- AttemptSelectionLedger
- TrustedTimeEvidence
- RequirementUniverseSource
- IndependenceAttestation
- BlindContextManifest
- RevocationPropagationReceipt
- HistoricalImpactScanReceipt
- ReviewerQualification
- BenchmarkPrecommitment
- MetaValidatorQualification

### 05 UI/UX
- finality/assurance/unknown/exclusion/revocation을 첫 화면에서 분리 표시
- offline/stale certificate 명시
- retry/flaky history 숨김 금지
- accepted risk 누적 표시
- reviewer/validator qualification limitation 노출

### 06 Test/Operation
각 항목은 dedicated failure injection을 가진다. 특히 clock rollback, partial evidence commit, queue replay, stale offline certificate, hidden leakage, benchmark shopping, memory leakage, reviewer common-mode, result cherry-picking을 필수 fixture 후보로 둔다.

### 07 AI/Agent
Memory-blind와 hidden qualification은 선언이 아니라 technical isolation/evidence로 판정한다. Human/Agent/Oracle 모두 qualification 대상이다.

### 08 Tracker
Contract, threshold, authority, operational SLA가 미정인 항목은 OPEN/DESIGN_ONLY로 추적한다.

## 13. 비권위 경계
이 통합설계는 ONSure가 이미 해당 기능을 구현·실행·검증했다는 주장이 아니다. Contract/Runtime/Failure Injection/Execution Evidence/Independent Qualification이 완료되기 전까지 관련 Capability는 `DESIGN_ONLY` 또는 그 이하의 실제 상태를 유지한다. Merge/Deployment/Production GO/Commercial GO/FinalLock 권위는 부여하지 않는다.
