# ONSure Semantic Assurance 시험·운영·구현 계획 확장

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parent authority: `../06_TEST_OPERATION_IMPLEMENTATION_PLAN.md`

## 1. 목적
본 문서는 기존 Unit/Contract/Integration/E2E/Mutation/Recovery/AI/OMemory 시험체계에 Semantic Assurance Capability를 검증하기 위한 **전용 negative/adversarial fixture, execution identity, failure injection, 운영 재검증**을 추가한다.

기존 시험이 존재한다는 사실을 그대로 승격하지 않는다. 각 신규 Capability는 current target revision에서 실제 실행 evidence가 있어야 한다.

## 2. 공통 실행 규칙
모든 Semantic Assurance 시험은 최소 다음을 기록한다.
- test_case_id
- fixture_id + content hash
- exact target source/artifact revision
- contract/policy/validator/oracle revision
- environment/toolchain
- executor identity
- precondition proof
- action/fault injection
- expected state/effect
- actual state/effect
- read-back result
- raw log/evidence refs
- final decision

`Fixture 존재`, `Test 정의`, `Execution PASS`는 서로 다른 상태다.

## 3. SA-01 Evidence Reperformance 시험 Pack
### 정상
- upstream PASS receipt를 읽고 target bytes를 독립 read-back 후 동일 digest 재계산
- 필수 Oracle 재실행 결과 일치

### 부정
1. upstream receipt의 PASS만 존재하고 raw execution 없음
2. expected digest 문자열은 맞지만 실제 bytes 변조
3. old target receipt를 current target에 재사용
4. command/exit code는 있으나 checkout revision 불명
5. upstream summary는 PASS인데 하위 receipt는 HOLD
6. raw log가 없고 digest만 존재
7. 필수 Oracle 하나를 skip했는데 overall PASS

### 기대판정
- REPERFORMED_BOUND
- EXECUTED_BUT_PARTIALLY_BOUND
- DECLARED_RESULT_ONLY
- EVIDENCE_CONTRADICTION_HOLD

## 4. SA-02 Denominator & Coverage 시험 Pack
1. 기존 Requirement 하나를 의도적으로 제거하고 기존 trace는 100% 유지 → denominator challenge가 신규 gap 발견
2. lifecycle EXIT/POST_EXIT를 생략한 CRUD 시스템
3. retry/replay/reconcile을 blanket N/A 처리
4. 동일 semantic Function을 두 ID로 중복 등록해 coverage 부풀리기
5. critical component를 excluded 처리 후 denominator에서도 삭제
6. 새로운 operator recovery need가 발견됐는데 기존 FR에 억지로 합치기
7. 기존 문서를 숨기고 Basic Information/Program Profile에서 독립 reconstruction 후 denominator 비교

### 성공조건
- 새 HIGH candidate를 발견하면 기존 Coverage 100%를 stale 처리
- excluded/unknown을 denominator에서 silent drop하지 않음

## 5. SA-03 Obligation Closure 시험 Pack
### Composite Requirement 예
`Secure deletion`:
ALL_OF(
- delete command
- authorization/privacy policy
- retention/legal-hold invariant
- physical deletion/read-back
- deletion receipt
- retry/idempotency
- negative legal-hold fixture
)

### 부정
- Function만 존재하고 invariant/test 없음
- IDENTIFIED/ROUTED 상태를 SATISFIED로 승격
- ANY_OF로 safety evidence 우회
- mandatory downstream owner 미지정
- schema 존재하지만 validator가 해당 member를 읽지 않음

## 6. SA-04 Authority Lifecycle 시험 Pack
- first owner bootstrap 시 nonexistent role 요구
- self-bootstrap으로 ADMIN 무제한 승격
- grantor보다 넓은 delegation
- A→B→C→A delegation cycle
- last owner 탈퇴 후 zero-owner
- revoke 직후 queue worker가 stale approval로 실행
- policy revision이 바뀐 scheduled action
- current ADMIN role로 과거 event-time 권한을 재구성
- compromise recovery 후 공격자가 만든 API key/role 자동 복원

### 반드시 포함할 race
`APPROVE -> REVOKE -> EFFECT`
`CANCEL -> WORKER CLAIM`
`POLICY CHANGE -> RETRY`

## 7. SA-05 Canonical State Authority 시험 Pack
- callback과 internal worker가 같은 final state 동시 write
- ops/debug endpoint가 owner command 우회
- migration script가 invariant bypass
- stale read-model에서 effect command 발행
- AI tool이 owner adapter를 우회해 DB/API 직접 effect
- DB commit 직후 process kill
- external effect 직후 local commit 전 process kill
- unknown provider result를 FAILED로 단정하고 duplicate retry

### 성공조건
canonical authoritative writer uniqueness와 actual effect read-back 모두 확인.

## 8. SA-06 Rights & Remedy 시험 Pack
- 문서에는 appeal 가능, 실제 command/UX 없음
- user revoke right는 있지만 현재 state에서 route unreachable
- operator recovery가 direct DB 수정만 가능
- right 실행 후 새 dispute right 생성되지만 exercising path 없음
- generic `manage rights` command 하나로 unrelated right를 모두 닫음
- restore 전후 right holder/resource binding 비교
- temporarily blocked right에 remedy가 없음

### Fixed-point 시험
closure iteration 중 새 right가 계속 발견될 때 `new_actionable_right_count==0`까지 반복되는지 확인.

## 9. SA-07 Distributed Effect 시험 Pack
### Handoff
- source commit 후 queue write 실패
- duplicate message delivery
- cancelled source outcome이 target에서 실행
- superseded revision work 실행
- target effect 후 receipt write 전 crash

### Batch
- N번째 item effect 후 process kill
- partial success 후 전체 batch retry
- 중간에 일부 item authority revoke
- subset policy revision 변경
- ambiguous external effect item
- batch summary count와 item receipts 불일치

### Irreversibility
- external payment success/local failure 후 blind retry
- sealed historical original을 correction으로 overwrite
- compensation이 원 effect와 correlation 없이 기록
- irreversible point 이후 cancel을 rollback로 표현

### Terminal
- account close 사이에 legal hold 추가
- pending payment/publication 존재
- last owner close
- residual session/token/delegation
- new dependency가 preflight 이후 commit 전에 생성

## 10. SA-08 Freshness / Invalidation 시험 Pack
- source 변경 후 old report가 current PASS 표시
- denominator 확장 후 old Coverage 100% 유지
- Machine Contract 변경 후 old OTester/OAudit receipt 재사용
- report regenerated but render/read-back가 old binary hash 참조
- validator 변경 후 qualification이 fresh로 유지
- MissedFinding 추가 후 affected certificate가 VALID 유지

### 성공조건
모든 child artifact가 stale graph에 따라 invalidation되고 historical PASS와 current disposition이 분리됨.

## 11. SA-09 Principal / Policy / SoD 시험 Pack
- 이메일 재할당 후 이전 권한 승계
- shared mailbox가 individual identity로 승격
- 누구나 유명 기관 namespace를 만들고 verified representation 획득
- allow와 deny 동시 match, precedence 미정
- membership leave 후 cached principal expansion이 old allow 사용
- 한 principal의 APPROVER+VERIFIER로 quorum 2명 충족
- emergency override가 무기한 유지

## 12. SA-10 Observer / Disclosure 시험 Pack
동일 observer/input shape에서 protected internal state만 바꾸어 반복 실행한다.

Observable vector:
`status, schema keys, content class, length bucket, retry/backoff, notification, cache/header, localized message, accessibility text, latency distribution`

### 공격
- blocked sender가 상대 block 여부 추론
- report 대상이 reporter/category 추론
- legal hold 여부를 delete error로 추론
- external token 오류로 resource existence 추론
- email/push title이 private review state 노출
- webhook raw internal reason 노출
- localized error만 더 구체적
- accessibility text가 hidden state 노출

## 13. SA-11 AI Lifecycle 시험 Pack
각 adopted AI-UC마다 최소:
1. normal/quality
2. boundary/malformed/no-evidence
3. failure/fallback
4. authority/effect negative
5. privacy/security
6. drift/freshness

### Human Review adversarial fixture
- AI recommendation hidden vs shown 비교
- intentionally wrong high-confidence AI recommendation
- misleading AI summary vs raw evidence
- default action reversal
- time-pressure
- human manual path parity

### Hard Floor
- unauthorized canonical/tool effect 0
- cross-tenant protected leak 0
- secret/raw credential leak 0
- missing critical evidence promotion 0
- disabled/unregistered tool execution 0

## 14. SA-12 Cross-Model Semantic Trace 시험 Pack
- Function 14 / Component 14 같은 equal-count positional mapping
- N:1 merge에서 failure/recovery responsibility 유실
- 1:N split에서 canonical state writer 복제
- orphan target component
- range trace로 individual mapping 대체
- schema 신규 mandatory field를 validator가 무시
- authoring nesting이 validator expectation과 불일치

## 15. SA-13 Business Semantic Integrity 시험 Pack
필수 arithmetic boundary:
- 0
- minimum unit
- half-round boundary
- maximum
- overflow
- negative/credit
- multi-component rounding
- repeated adjustment
- refund > original
- FX rate change
- provider amount mismatch
- partial batch aggregation

금융 외 quota/score/SLA에서도 unit/precision/threshold boundary를 적용한다.

## 16. SA-14 Validator Requalification 시험 Pack
### Corpus 분리
- PUBLIC_REGRESSION
- PRIVATE_QUALIFICATION
- ROTATING_UNSEEN
- NOVEL_COMPOSITION
- OUT_OF_DISTRIBUTION

### 필수 공격
- hidden answer leakage
- nonblind replay를 qualification으로 제출
- shadow run을 final qualification으로 제출
- manual method summary로 canonical method 변형
- isolated 실행이라고 self-attest만 하고 authority proof 없음
- critical miss 1건인데 평균 recall로 통과
- detector weakening 후 old qualification 재사용

### Requalification 최소 조건 후보
- current method manifest 고정
- exact input/transport fidelity
- independent/isolation authority
- critical denominator 명시
- strict critical recall 100%
- critical miss 0
- open critical validator RCA 0

## 17. 운영 Runbook 추가
### Evidence Contradiction Incident
upstream PASS와 current re-performance가 충돌하면 일반 test failure가 아니라 evidence integrity incident로 분리한다.

### Denominator Expansion Incident
이미 발행된 high-assurance certificate의 denominator에 material gap이 발견되면 Historical Impact Scan을 실행한다.

### Authority Orphan Incident
zero-owner, uncontrolled delegation, stale effect authority가 확인되면 해당 operation을 HOLD하고 break-glass를 정상운영으로 승격하지 않는다.

### Validator Qualification Stale
rule/oracle/detector/method 변경 시 affected capability를 즉시 STALE 처리하고 high-assurance claim 발급을 중단한다.

## 18. 구현 순서
1. Fixture/Test/Execution identity와 Reperformance
2. Denominator/Coverage
3. Obligation Closure
4. Authority/State/Rights
5. Distributed Effect
6. Freshness/Invalidation
7. Policy/SoD/Identity
8. Observer/Disclosure
9. Cross-model/Machine Contract
10. Business Semantics
11. AI Lifecycle
12. Validator Requalification

각 단계는 Contract와 failure injection 없이 IMPLEMENTED로 승격하지 않는다.

## 19. 최종 수용기준
- current target에 required semantic pack이 실제 실행됨
- required fixture가 NOT_RUN/BLOCKED인데 overall PASS가 아님
- evidence strength가 claim assurance 요구수준 이상
- denominator current epoch과 coverage가 일치
- canonical authority/rights/distributed effect P0 gap 0
- stale current artifact 0 또는 명시적 HOLD
- AI adopted use case의 per-UC TEVV closure
- validator qualification이 current method revision에 대해 fresh
