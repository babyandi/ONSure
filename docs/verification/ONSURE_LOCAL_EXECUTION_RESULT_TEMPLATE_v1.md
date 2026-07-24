# ONSURE 로컬 실행 결과 서식 v1

실제 실행 증거가 없는 항목은 `PASS`로 기록하지 않는다.

## 1. 실행 식별

```text
실행 ID:
실행 일시:
운영자 ID:
저장소:
브랜치:
실행 HEAD:
실행 환경:
증적 루트:
```

## 2. 도구 환경

| 항목 | 경로·버전 | 판정 |
|---|---|---|
| Git |  | NOT_RUN |
| Bash |  | NOT_RUN |
| Java |  | NOT_RUN |
| javac |  | NOT_RUN |
| Maven |  | NOT_RUN |
| Python |  | NOT_RUN |
| sha256sum |  | NOT_RUN |

## 3. 작업공간

```text
추적 파일 변경:
실행 시작 HEAD:
실행 종료 HEAD:
원격 HEAD 일치:
소스 잠금 SHA-256:
정책 SHA-256:
시험 데이터 SHA-256:
오라클 SHA-256:
```

## 4. 실행 전 점검

| 점검 | 결과 | 증적 |
|---|---|---|
| 로컬 보증 점검 | NOT_RUN |  |
| 범용 하네스 점검 | NOT_RUN |  |
| 제품 범위 계약 | NOT_RUN |  |
| 대상 등록소 계약 | NOT_RUN |  |
| 상태·영수증 계약 | NOT_RUN |  |
| 보안 발견사항 계약 | NOT_RUN |  |

## 5. Maven·JUnit

```text
명령:
종료 코드:
시험 수:
성공:
실패:
오류:
건너뜀:
표준출력 SHA-256:
표준오류 SHA-256:
판정: NOT_RUN
```

## 6. 제품 플랫폼 종단간 시험

| 실행 | 운영자 | 결과 | 정규화 결과 SHA-256 | 증적 위치 |
|---|---|---|---|---|
| 1회차 |  | NOT_RUN |  |  |
| 2회차 |  | NOT_RUN |  |  |

```text
두 실행 결과 동일:
ONSURE_PRODUCT_PLATFORM_E2E_PASS 확인:
```

## 7. 범용 하네스 독립 실행

| 실행 | 운영자 | 환경 | 결과 | NOT_RUN | BLOCKED | Critical/Major |
|---|---|---|---|---:|---:|---:|
| 1회차 |  |  | NOT_RUN |  |  |  |
| 2회차 |  |  | NOT_RUN |  |  |  |

```text
운영자 분리:
환경 해시 동일:
정규화 결과 해시 동일:
ONSURE_UNIVERSAL_TWO_RUN_PASS 확인:
```

## 8. 로컬 자체 보증

| 항목 | 1회차 | 2회차 | 증적 |
|---|---|---|---|
| 소스 잠금 | NOT_RUN | NOT_RUN |  |
| 시험·보안 스냅샷 | NOT_RUN | NOT_RUN |  |
| 회귀검증 | NOT_RUN | NOT_RUN |  |
| 독립 검증 영수증 | NOT_RUN | NOT_RUN |  |
| 독립 감사 영수증 | NOT_RUN | NOT_RUN |  |
| 최종 영수증 | NOT_RUN | NOT_RUN |  |

## 9. 발견사항

| ID | 심각도 | 설명 | 상태 | 증적 |
|---|---|---|---|---|
|  |  |  |  |  |

```text
Critical 미해결:
High 미해결:
Medium 미해결:
Low 미해결:
```

## 10. 근본원인분석과 회귀검증

| 실패 ID | 근본원인 상태 | 수정 커밋 | 집중 재시험 | 전체 회귀 1 | 전체 회귀 2 |
|---|---|---|---|---|---|
|  |  |  | NOT_RUN | NOT_RUN | NOT_RUN |

## 11. 증적 무결성

```text
증적 목록 파일:
증적 목록 SHA-256:
영수증 자기 해시 검증:
계보 검증:
읽기 전용 재검증:
```

## 12. 개발 관문

```text
ONSURE_PRODUCT_PLATFORM_E2E_PASS:
ONSURE_UNIVERSAL_TWO_RUN_PASS:
LOCAL_ASSURANCE_TWICE_PASS:
ISSUE4_FINAL_GATE_EVIDENCE_READY:
ONSURE_DEVELOPMENT_GATE_PASS:
```

## 13. 최종 판정

```text
MAVEN_COMPILE       = NOT_RUN
JUNIT               = NOT_RUN
PRODUCT_E2E_TWICE   = NOT_RUN
UNIVERSAL_TWO_RUN   = NOT_RUN
SELF_ASSURANCE      = NOT_RUN
CRITICAL_OPEN       = UNKNOWN
HIGH_OPEN           = UNKNOWN
DEVELOPMENT_GATE    = HOLD
FINAL_CANDIDATE     = BLOCKED
FINAL_LOCK_ALLOWED  = false
```

## 14. 판정 사유

실행된 항목, 미실행 항목, 실패 원인, 증적 누락, 잔여 위험을 서술한다.
