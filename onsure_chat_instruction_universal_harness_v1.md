# ONSURE 채팅 작업 인수 지시서 — 범용 검증 하네스 v1

## 1. 현재 상태 고정

```text
저장소                    babyandi/ONSure
기준 브랜치               main
제품 핵심 구현             완료
범용 검증 하네스           통합 완료
Maven 컴파일              NOT_RUN
JUnit                     NOT_RUN
범용 하네스 1회차          NOT_RUN
범용 하네스 2회차          NOT_RUN
제품 종단간 검증 2회       NOT_RUN
자체 보증 2회              NOT_RUN
개발 관문                  HOLD
최종 후보                  BLOCKED
최종 잠금 허용             false
```

실제 실행 증거가 없으므로 `PASS`, 완료, 최종 후보, 최종 잠금을 주장하지 않는다.

## 2. 제품 정의

ONSURE은 AI 프로그램과 일반 프로그램을 등록·검증하고, 발견사항·실패 유형·근본원인분석·개선안·재검증 보고서를 만드는 독립 상용 소프트웨어 검증 플랫폼이다.

ORUDA는 ONSURE 핵심 의존성이 아니라 첫 공식 외부 검증 대상이다.

## 3. 구현 권위

- 제품 핵심·대상 어댑터·보고·재검증 권위: `io.onsure.platform`
- 범용 검증 하네스 권위: `io.onsure.harness`
- 로컬 보증·서명 영수증·최종 관문 권위: `io.onsure.assurance`
- ORUDA 전용 검증 권위: `io.onsure.platform.oruda`

중복 구현이 발견되면 새 구현을 추가하지 말고 권위 구현으로 수렴한다.

## 4. 금지 사항

- `ORUDA-Master-Queue` 사용 금지
- 미실행 항목을 `PASS`로 변경 금지
- 실행 증거 없는 Ready·Merge·Final Lock 금지
- 대상 제품이 ONSURE 최종 판정을 작성하도록 허용 금지
- 인라인 임의 셸, 절대경로, 대상 루트 이탈 금지
- 실패 증거 삭제 금지
- 동일 운영자를 독립 2회 실행자로 재사용 금지
- 실패 후 근본원인분석 없이 회귀검증 통과 처리 금지

## 5. 범용 검증 축

범용 하네스는 `harness/universal-v1/axes/verification-axes.v1.json`에 고정된 필수 검증 축을 사용한다.

필수 축에는 다음이 포함된다.

- 요구사항·정책·아키텍처 추적성
- 소스·설정·의존성 무결성
- 인증·인가·입력 검증
- 비밀정보·개인정보·데이터 유출
- 공급망·라이선스·SBOM·출처 증명
- 정상·오류·권한·대용량·동시성·장애복구·적대 입력
- 결정성·멱등성·복구·롤백
- 증적·영수증·오라클 계보
- 독립 검증·감사
- 근본원인분석·개선·회귀 잠금

필수 축이 `NOT_RUN` 또는 `BLOCKED`이면 최종 후보가 될 수 없다.

## 6. 시험 데이터 유형

```text
NORMAL
ERROR
AUTHORIZATION
LARGE_DATA
CONCURRENCY
FAILURE_RECOVERY
ADVERSARIAL
```

각 시험 데이터는 입력, 실행 명령, 제한 시간, 기대 종료 코드, 기대 출력, 연결된 검증 축, 판정 오라클을 명시해야 한다.

## 7. 판정 규칙

```text
PASS      모든 필수 오라클과 실행 조건 충족
FAIL      실행됨·판정 불일치 또는 결함 발견
BLOCKED   필수 도구·권한·환경·증적 부족
NOT_RUN   실행하지 않음
```

종료 코드가 0이 아니면 출력 문자열에 성공 표식이 있더라도 통과할 수 없다.

## 8. 증적과 영수증

하네스는 최소한 다음을 남긴다.

- 실행 환경 해시
- 대상 소스·정책·시험 데이터·오라클 해시
- 명령·종료 코드·표준출력·표준오류 해시
- 시험 데이터별 판정 영수증
- 실행 전체 영수증
- SHA-256 증적 목록
- 실패 시 근본원인분석 초안

생성된 영수증은 읽기 전용 재검증에서 자기 해시와 계보를 다시 확인해야 한다.

## 9. 실행 순서

```bash
bash scripts/preflight-local-assurance.sh
bash scripts/preflight-universal-harness.sh
mvn -B -ntp test
bash scripts/run-product-platform-e2e.sh
bash scripts/run-universal-harness-twice.sh \
  operator-independent-1 \
  operator-independent-2 \
  local-jdk17
bash scripts/run-onsure-development-gate.sh
```

## 10. 실패 처리

```text
실패 발견
→ 실패 증적 보존
→ 근본원인분석 상태 RCA_PENDING
→ 최소 수정·지속 수정안 구분
→ 집중 재시험
→ 전체 회귀검증 2회
→ 독립 검증
→ 개발 관문 재실행
```

실패를 수정하지 않고 시험 기대값을 완화하는 행위는 금지한다.

## 11. 독립 실행 2회 조건

- 서로 다른 운영자 ID
- 동일한 소스·정책·시험 데이터·오라클 버전
- 동일한 실행 환경 분류
- 정규화 결과 해시 동일
- `NOT_RUN=0`
- `BLOCKED=0`
- 미해결 `Critical/Major=0`
- 필요한 근본원인분석과 회귀검증 완료

위 조건을 충족해도 `final_lock_allowed=false`를 유지한다. 최종 잠금은 별도 승인·감사 절차다.

## 12. 성공 표식

```text
ONSURE_PRODUCT_PLATFORM_E2E_PASS
ONSURE_UNIVERSAL_TWO_RUN_PASS
ISSUE4_FINAL_GATE_EVIDENCE_READY
ONSURE_DEVELOPMENT_GATE_PASS
```

표식만으로 통과하지 않는다. 대응하는 영수증·증적 해시·종료 코드가 모두 존재해야 한다.

## 13. 완료 보고 형식

```text
실행 HEAD
실행 환경
Maven/JUnit 결과
제품 종단간 검증 2회 결과
범용 하네스 독립 2회 결과
실패·근본원인분석·회귀검증 결과
증적 루트와 SHA-256 목록
개발 관문 판정
잔여 NOT_RUN/BLOCKED
Final Lock 허용 여부
```

## 14. 현재 다음 작업

현재 다음 작업은 새 문서 작성이 아니라 실제 JDK 17·Maven 환경에서 전체 실행기를 돌리고, 실패를 수정한 뒤 동일 조건 회귀검증을 완료하는 것이다.
