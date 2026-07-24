# ONSURE 범용 검증 하네스 v1 실행서

## 기준선

- 제품 핵심 기준선: `main`
- 범용 하네스: `harness/universal-v1/**`
- 실행 코드: `io.onsure.harness`
- 상태 파일: `harness/universal-v1/status/current-status.v1.json`
- 공식 실행 환경: JDK 17, Maven, Git, Bash

## 목적

ONSURE의 문서상 검증 기준을 실제 실행 가능한 범용 하네스로 전환하고, 일반 프로그램·AI 프로그램·ORUDA 대상에 동일한 증적·오라클·근본원인·회귀검증 규칙을 적용한다.

## 실행 전 조건

```text
JDK 17
Maven
Git
Bash
sha256sum
추적 파일 변경이 없는 작업공간
고정된 대상 소스
고정된 시험 데이터·오라클
```

필수 조건이 없으면 `PASS`가 아니라 `BLOCKED` 또는 `NOT_RUN`으로 기록한다.

## 사전 점검

```bash
bash scripts/preflight-universal-harness.sh
```

사전 점검은 다음을 확인한다.

- 필수 파일·스키마·검증 축 존재
- 7개 시험 데이터 유형 존재
- 시험 데이터와 오라클 연결
- 실행 명령 허용 목록
- 대상 루트 이탈·절대경로·인라인 셸 차단
- JDK 17·Maven 사용 가능 여부
- 작업공간 변경 여부

## 단일 실행

```bash
bash scripts/run-universal-harness.sh <운영자-ID> <환경-표시>
```

예:

```bash
bash scripts/run-universal-harness.sh operator-1 local-jdk17
```

단일 실행은 다음을 생성한다.

- 실행 디렉터리
- 환경 해시
- 시험 데이터별 명령·출력·종료 코드
- 시험 데이터 영수증
- 전체 실행 영수증
- 증적 SHA-256 목록
- 실패 시 근본원인분석 초안

## 독립 실행 2회

```bash
bash scripts/run-universal-harness-twice.sh \
  operator-independent-1 \
  operator-independent-2 \
  local-jdk17
```

두 운영자 ID는 서로 달라야 한다. 동일 운영자를 재사용하면 독립 실행으로 인정하지 않는다.

비교 항목:

- 소스 해시
- 정책 해시
- 시험 데이터·오라클 해시
- 환경 해시
- 정규화 결과 해시
- 발견사항 수와 심각도
- `NOT_RUN`·`BLOCKED` 수
- 근본원인분석·회귀검증 상태

## 판정

```text
PASS      실제 실행 및 모든 필수 오라클 충족
FAIL      실제 실행됐으나 기대 판정 불일치
BLOCKED   필수 환경·권한·도구·증적 부족
NOT_RUN   실행하지 않음
```

최종 후보 조건:

```text
독립 운영자 2명
두 실행 모두 PASS
NOT_RUN = 0
BLOCKED = 0
Critical/Major 미해결 = 0
정규화 결과 해시 동일
필요한 근본원인분석·회귀검증 완료
```

최종 후보가 되어도 최종 잠금은 자동 허용되지 않는다.

## 실패 처리

실패가 발생하면 다음 순서를 따른다.

```text
실패 영수증 생성
→ 근본원인분석 상태 RCA_PENDING
→ 원인·영향·수정안 기록
→ 코드·정책·시험 데이터 수정
→ 집중 재시험
→ 독립 회귀검증 2회
→ 전체 범용 하네스 재실행
```

금지 사항:

- 실패 증적 삭제
- 기대값 완화로 실패 은폐
- 부분 성공을 전체 통과로 승격
- 미실행 항목을 통과로 변경
- 자동 최종 잠금

## 읽기 전용 재검증

검증 영수증은 다음을 다시 확인해야 한다.

- 자기 해시
- 상위·하위 영수증 연결
- 증적 파일 존재와 SHA-256
- 실행 명령·종료 코드·출력 해시
- 대상 소스·정책·시험 데이터·오라클 버전
- 판정 상한

## 개발 관문 연결

```bash
bash scripts/run-onsure-development-gate.sh
```

개발 관문은 다음을 순서대로 실행한다.

```text
제품 플랫폼 종단간 검증 2회
→ 범용 하네스 독립 2회
→ ONSURE 자체 보증
→ Issue #4 최종 증적 확인
→ 개발 관문 판정
```

성공 표식:

```text
ONSURE_PRODUCT_PLATFORM_E2E_PASS
ONSURE_UNIVERSAL_TWO_RUN_PASS
ISSUE4_FINAL_GATE_EVIDENCE_READY
ONSURE_DEVELOPMENT_GATE_PASS
```

## 현재 정확한 상태

```text
하네스 코드              구현됨
시험 데이터·오라클        구현됨
Maven 컴파일             NOT_RUN
JUnit                    NOT_RUN
단일 하네스              NOT_RUN
독립 실행 1·2회          NOT_RUN
개발 관문                HOLD
최종 후보                BLOCKED
최종 잠금                NOT_ALLOWED
```
