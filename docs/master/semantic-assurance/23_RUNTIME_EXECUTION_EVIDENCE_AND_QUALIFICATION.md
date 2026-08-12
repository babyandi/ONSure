# ONSure Runtime Execution Evidence & Qualification 설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
본 문서는 `Contract Candidate 있음`, `코드 있음`, `Fixture 있음`을 실제 구현·실행·검증으로 오인하지 않도록 Runtime 실행 증적과 Qualification 계보를 정의한다.

목표 상태 체인:

`DESIGN_ACCEPTED -> CONTRACTED -> IMPLEMENTED -> EXECUTION_READY -> EXECUTED -> EVIDENCE_BOUND -> INDEPENDENTLY_VERIFIED -> QUALIFIED`

어느 단계도 자동 승격하지 않는다.

## 2. RuntimeExecutionReceipt
모든 실제 실행은 공통 RuntimeExecutionReceipt를 남긴다.

필수 필드:
- execution_receipt_id
- operation_id
- operation_version
- target_id
- source_tree_sha256
- artifact/input digest set
- request/payload digest
- contract/schema/validator digest
- fixture set digest
- oracle set digest
- policy/scope/requirement/denominator epoch
- executor principal/profile
- environment/toolchain digest
- started_at/completed_at
- timeout/budget
- attempt number
- raw stdout/stderr/result digest
- decision
- reason codes
- side effect summary
- parent receipt refs
- signature

## 3. Execution Identity
실행 identity는 최소 다음의 조합이다.

`Target + Input + Command/Operation + Fixture + Oracle + Validator + Policy + Environment + Toolchain + Principal + Attempt`

하나라도 달라지면 동일 실행으로 취급하지 않는다.

## 4. Attempt History
Retry는 append-only다.
- 실패 attempt 삭제 금지
- 성공 attempt만 대표값으로 선택 금지
- retry reason/trigger 기록
- retry policy는 결과 보기 전 고정

최종 decision은 attempt history 전체를 소비한다.

## 5. Execution Readiness
`EXECUTION_READY`는 다음을 요구한다.
- active/candidate contract version 명시
- validator available
- fixtures materialized
- oracle materialized
- target/source identity fixed
- authority current
- resource budget 확보
- environment isolation ready

Fixture 파일이 repository에 존재하는 것만으로 ready가 아니다.

## 6. Evidence Binding
실행 후 result를 raw evidence와 결속한다.
- file/log/output exact digest
- process exit code
- execution environment
- temporal evidence
- provider/readback evidence

Report/summary만 있고 raw evidence가 없으면 EVIDENCE_BOUND가 아니다.

## 7. Qualification Record
Qualification 대상:
- schema validator
- cross-contract validator
- semantic capability validator
- oracle engine
- denominator discovery engine
- propagation/invalidation engine
- independent execution runner
- reviewer/human calibration process
- active selector transition engine

Qualification record는 대상 build/version digest를 정확히 고정한다.

## 8. Qualification Benchmark
Benchmark는 결과 전에 precommit한다.
- benchmark id/epoch
- case/fixture exact set
- hidden/golden classification
- expected result
- severity/criticality
- exclusion rules
- scoring rules

결과 확인 후 fixture 제외/재분류를 금지한다.

## 9. Qualification Metrics
최소:
- critical recall
- high recall
- false-positive rate
- false-negative count
- mutation kill rate
- unknown detection rate
- stale/revocation detection
- cross-contract conflict detection
- resource exhaustion behavior

Critical seeded miss가 있으면 Qualified 금지.

## 10. Meta-validation
Validator를 검증하는 validator도 변경 이력을 가진다. 재귀는 TCB boundary에서 멈춘다.

필수 산출물:
- validator build provenance
- TCB manifest
- qualification benchmark
- mutation suite
- qualification receipt

## 11. Failure 상태
- NOT_RUN
- INPUT_REQUIRED
- BLOCKED_ENVIRONMENT
- BLOCKED_AUTHORITY
- BLOCKED_DEPENDENCY
- FAILED
- INCONCLUSIVE
- EVIDENCE_INCOMPLETE
- QUALIFICATION_FAILED
- STALE

실행환경 문제를 PASS나 일반 FAIL로 숨기지 않는다.

## 12. Environment Blocker Evidence
실행이 막혀도 blocker 자체를 증적화한다.
- attempted command/operation
- environment identity
- failure code/message
- network/filesystem/process capability
- retry count
- fallback 사용 여부

`못 돌렸다`는 설명문만 남기지 않는다.

## 13. Execution Evidence Store
저장 구조 후보:
`evidence/runtime/<target>/<run>/<operation>/<attempt>/`

최소:
- request.json
- execution-identity.json
- stdout.raw
- stderr.raw
- result.raw
- receipt.json
- signature

## 14. Cross-run Mixing 금지
Final reconstruction은 다른 epoch/run의 좋은 결과를 cherry-pick하지 않는다. 필요한 경우 Composite Snapshot receipt를 생성해 동일 generation임을 증명한다.

## 15. Staleness
다음 변경은 execution/qualification을 stale 처리한다.
- source/artifact
- contract/schema
- validator
- oracle
- fixture
- policy
- denominator
- authority
- environment/toolchain
- benchmark

## 16. Independent Reperformance
Qualification 전에 독립 주체가 동일 execution identity를 재수행한다. 결과 차이는 자동 평균화하지 않고 HOLD한다.

## 17. Finding Closure
Finding closure는 최소:
1. active requirement
2. active contract/enforcement
3. implementation
4. negative fixture
5. runtime execution
6. expected failure verified
7. regression clean
8. evidence bound
9. independent reperformance
10. qualification current

## 18. Claude 개발 경계
Claude는 다음을 구현한다.
- RuntimeExecutionReceipt writer
- attempt history store
- execution identity builder
- evidence directory/materialization
- qualification runner
- benchmark precommit check
- stale invalidation

실행 안 했으면 receipt decision은 NOT_RUN/BLOCKED를 유지한다.

## 19. 현재 상태
- 설계: PRESENT
- static execution: BLOCKED_NOT_RUN
- Java compile/JUnit: NOT_RUN
- qualification: NOT_RUN
- VERIFIED_CLOSED: 0
