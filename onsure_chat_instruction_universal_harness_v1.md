# ONSURE 채팅 작업 인수 지시서 — Universal Verification Harness v1

## 1. 현재 상태 고정

```text
DOCUMENT_ENRICHMENT_DONE = true
PRODUCT_CORE_IMPLEMENTED = true
UNIVERSAL_HARNESS_INTEGRATED = true
MAVEN_COMPILE = NOT_RUN
JUNIT = NOT_RUN
SINGLE_HARNESS_RUN = NOT_RUN
INDEPENDENT_RUN_1 = NOT_RUN
INDEPENDENT_RUN_2 = NOT_RUN
EXECUTION_REQUIRED_NEXT = true
HARNESS_STOP_REASON_FOUND = false
FINAL_CANDIDATE = BLOCKED
FINAL_LOCK_ALLOWED = false
CURRENT_PHASE = EXECUTION_HARNESS_REQUIRED
CODESPACE_STATUS = CONFIGURING
```

권위 개발선:

- Product Core·Validator Engine: PR #2, `design/assurance-architecture-v1`
- 검토 문서 기준선: PR #6, `docs/onsure/review/`
- 원 Universal Harness 구현: PR #7
- 통합 브랜치: `integration/product-core-universal-harness-v1`

`ORUDA-Master-Queue`는 사용하지 않는다.

## 2. ONSURE 제품 범위

ONSURE은 AI 프로그램에만 한정되지 않는다. 일반 프로그램, AI 애플리케이션, Agent 기반 프로그램, API, 배치, 웹·데스크톱 시스템을 검증하는 독립 범용 검증 플랫폼이다.

```text
대상 등록
→ 원본·환경 수집
→ 검증 축 선택
→ Fixture 실행
→ Harness 실행
→ Oracle 판정
→ Evidence·Receipt 저장
→ Failure Mode·RCA 연결
→ 개선
→ 재검증
→ Regression 2회
→ Final Candidate 판정
```

ORUDA는 ONSURE의 내부 필수 모듈이 아니라 첫 공식 외부 검증 대상이다. ORUDA의 자체 PASS·Audit 주장은 ONSURE이 독립 재계산하기 전까지 신뢰하지 않는다.

## 3. 범용 검증 축 30개

1. 요구사항 추적성
2. 설계 완전성
3. 설계-코드 일치성
4. 기능 정확성
5. 단위 테스트
6. 통합 테스트
7. E2E 테스트
8. 인수 테스트
9. 예외·오류 처리
10. 잘못된 데이터 처리
11. 보안 취약점
12. 인증·인가·권한 우회
13. 개인정보·민감정보 보호
14. 데이터 무결성
15. 성능·부하·스트레스
16. 메모리 누수
17. DB 커넥션·파일 핸들 누수
18. 동시성·Race Condition
19. 장애 복구·롤백
20. 로그·모니터링·운영성
21. 배포·환경 구성
22. 라이선스·의존성 취약점
23. 코드 품질
24. 코드리뷰 품질
25. 정적분석
26. UI·UX 품질
27. 접근성
28. 브라우저·디바이스 호환성
29. 산출물 품질
30. 증적·재현성

권위 파일:

```text
harness/universal-v1/axes/verification-axes.v1.json
```

필수 축은 하나라도 `NOT_RUN` 또는 `BLOCKED`이면 Final Candidate를 차단한다.

## 4. Fixture / Oracle / Evidence / Receipt 구조

Fixture 종류:

```text
NORMAL
ERROR
AUTHORIZATION
LARGE_DATA
CONCURRENCY
FAILURE_RECOVERY
ADVERSARIAL
```

필수 계약:

```text
harness/universal-v1/schemas/fixture.v1.schema.json
harness/universal-v1/schemas/oracle.v1.schema.json
harness/universal-v1/schemas/evidence.v1.schema.json
harness/universal-v1/schemas/receipt.v1.schema.json
```

Oracle 판정은 다음 네 상태만 사용한다.

```text
PASS
FAIL
BLOCKED
NOT_RUN
```

판정 우선순위:

```text
FAIL > BLOCKED > NOT_RUN > PASS
```

non-zero exit code, timeout, 증적 누락, 알 수 없는 Oracle은 정상 PASS로 승격하지 않는다.

Evidence에는 최소한 다음이 있어야 한다.

```text
run_id
fixture_id
axis_ids
command
cwd
started_at / completed_at
exit_code
timed_out
stdout_sha256
stderr_sha256
environment_sha256
decision
reason
```

Receipt는 Evidence SHA-256, Oracle ID, 판정, Severity, RCA 필요 여부와 자체 Digest를 포함한다.

## 5. Harness Runner 요구사항

권위 구현:

```text
src/main/java/io/onsure/harness/
```

주요 구성:

```text
HarnessModels
FixtureExecutor
OracleEngine
UniversalHarnessRunner
RunVerifier
FinalCandidateGate
RegressionGate
HarnessCli
```

명령 실행 보안:

- 허용된 실행기만 사용
- inline shell(`-c`, `--command`) 금지
- 절대 경로 Script 금지
- Target root 밖 경로 이탈 금지
- timeout 강제
- 출력 크기 제한
- 실행 환경 최소화
- 로그 문구가 아닌 Hash·구조·계보로 재검증

## 6. RCA / Regression 조건

실제 `FAIL` Fixture는 자동으로 RCA 초안을 생성한다.

```text
root_cause = PENDING_ANALYSIS
fix_reference = NOT_SET
regression_run_1 = NOT_RUN
regression_run_2 = NOT_RUN
status = RCA_PENDING
```

종료 조건:

- RCA 원인 확정
- Fix 또는 Rule 변경 Reference 등록
- 집중 재시험 PASS
- 서로 다른 운영자의 Regression 2회 PASS
- 환경 Digest 동일
- 정상화 결과 Digest 동일
- 남은 실패 Fixture 0건

## 7. Final Candidate / Final Lock 금지 조건

Final Candidate 필수 조건:

```text
RUN_1 != RUN_2
OPERATOR_1 != OPERATOR_2
ENVIRONMENT_DIGEST_1 = ENVIRONMENT_DIGEST_2
AXIS_COUNT = 30
ALL_REQUIRED_AXES = PASS
NOT_RUN = 0
BLOCKED = 0
CRITICAL_DEFECT = 0 for 2 consecutive runs
MAJOR_DEFECT = 0 for 2 consecutive runs
NORMALIZED_RESULT_DIGEST_1 = NORMALIZED_RESULT_DIGEST_2
EVIDENCE_AND_RECEIPT_REVERIFY = PASS
RCA_PENDING = 0
REGRESSION_2X = PASS when remediation occurred
```

Final Candidate가 PASS여도 자동 Final Lock은 금지한다.

```text
final_lock_allowed = false
```

실제 인간 최종 권위 승인과 별도 서명 Receipt 없이 Final Lock을 생성하지 않는다.

## 8. 실행 명령

Codespace 또는 clean JDK 17·Maven 환경이 준비되면 다음 순서로 실행한다.

```bash
bash scripts/preflight-local-assurance.sh
bash scripts/preflight-universal-harness.sh
mvn -B -ntp test
bash scripts/run-universal-harness-twice.sh \
  operator-independent-1 operator-independent-2 local-jdk17
bash scripts/run-onsure-development-gate.sh
```

최종 개발 기준선 성공 출력:

```text
ONSURE_PRODUCT_PLATFORM_E2E_PASS
ONSURE_UNIVERSAL_TWO_RUN_PASS
ISSUE4_FINAL_GATE_EVIDENCE_READY
ONSURE_DEVELOPMENT_GATE_PASS
```

실패 시:

```text
실패 Fixture 확인
→ Evidence·Receipt Hash 재검증
→ RCA 확정
→ 코드·정책·Fixture·Oracle 수정
→ 집중 재시험
→ 전체 Product E2E
→ Universal Harness 독립 2회
→ ONSURE 자체 Assurance
```

## 작업 금지

- 문서 검토만 추가하고 실행 증적 없이 종료 선언
- `NOT_RUN` 또는 `BLOCKED`를 PASS로 승격
- 일부 검증 축 생략
- 동일 운영자의 반복을 독립 실행으로 인정
- Evidence와 Receipt를 함께 조작해 우회
- RCA_PENDING 상태에서 종료
- 자동 Final Lock
- ORUDA가 ONSURE 최종 판정을 작성하도록 허용
- ORUDA-Master-Queue 사용
