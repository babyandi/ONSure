# ONSURE 범용 하네스 실행 전환

작성일: 2026-07-23

## 목적

설계·문서 중심의 검토 결과를 실제 실행 가능한 범용 검증 하네스로 전환한다. 이 문서는 구현 완료를 주장하지 않으며, 실행 증거가 생성되기 전까지 관문 상태를 `HOLD`로 유지한다.

## 전환 전 상태

```text
문서·계약              존재
검증 축                정의됨
시험 데이터 유형        정의됨
오라클                  정의됨
실행 하네스             구현됨
Maven/JUnit             NOT_RUN
독립 실행 2회           NOT_RUN
최종 후보               BLOCKED
최종 잠금               NOT_ALLOWED
```

## 전환 대상

- 30개 범용 검증 축
- 7개 시험 데이터 유형
- 시험 데이터·오라클 스키마
- 제한된 프로세스 실행
- 종료 코드·시간 제한·출력 제한
- 증적·영수증·SHA-256 목록
- 실패 유형·근본원인분석
- 독립 회귀검증 2회
- 서로 다른 운영자의 독립 실행 2회
- 개발 관문 결속

## 상태 전환 규칙

```text
DESIGN_ONLY
→ IMPLEMENTED_NOT_RUN
→ EXECUTION_RUNNING
→ PASS / FAIL / BLOCKED
→ RCA_PENDING
→ REGRESSION_RUNNING
→ CANDIDATE_NONFINAL
```

다음 전이는 금지한다.

```text
DESIGN_ONLY → PASS
NOT_RUN → PASS
FAIL → PASS_WITHOUT_RCA
CANDIDATE_NONFINAL → FINAL_LOCK
```

## 실행 순서

```bash
bash scripts/preflight-universal-harness.sh
mvn -B -ntp test
bash scripts/run-universal-harness.sh operator-1 local-jdk17
bash scripts/run-universal-harness-twice.sh \
  operator-independent-1 operator-independent-2 local-jdk17
bash scripts/run-onsure-development-gate.sh
```

## 증적 요구사항

각 실행은 다음을 보존한다.

- 실행 HEAD와 소스 해시
- 환경·도구 버전과 해시
- 시험 데이터·오라클·정책 해시
- 명령, 종료 코드, 표준출력·표준오류 해시
- 시험 데이터별 판정
- 발견사항과 심각도
- 근본원인분석 상태
- 회귀검증 상태
- 전체 실행 영수증

## 실패 처리

실패 시 즉시 완료 상태를 만들지 않는다.

```text
실패 확인
→ 증적 보존
→ RCA_PENDING
→ 원인·영향·수정안 기록
→ 수정
→ 집중 재시험
→ 독립 회귀검증 2회
→ 전체 실행 재개
```

## 독립성 요구사항

- 실행 운영자 ID 분리
- 검증·감사 역할 분리
- 대상이 최종 판정을 작성하지 못하도록 차단
- 실행 결과를 호출자 입력으로 신뢰하지 않음
- 영수증과 증적을 읽기 전용으로 재검증

## 완료 조건

```text
Maven compile PASS
JUnit PASS
필수 검증 축 NOT_RUN 0
BLOCKED 0
Critical/Major 미해결 0
독립 실행 2회 PASS
정규화 결과 동일
근본원인분석·회귀검증 완료
개발 관문 PASS
```

완료 조건을 충족해도 최종 잠금은 별도 승인 대상으로 유지한다.
