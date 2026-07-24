# ONSURE 로컬 실행서 v1

## 1. 목적과 현재 상태

ONSURE 로컬 검증을 동일한 명령·증거·판정 순서로 수행하는 공식 절차다. GitHub Actions와 외부 CI는 `PASS` 근거로 사용하지 않는다.

```text
구현 상태       완료
공식 로컬 실행  NOT_RUN
개발 관문       HOLD
최종 후보       BLOCKED
최종 잠금       NOT_ALLOWED
```

## 2. 실행 원칙

- 승인된 변경 불가 커밋에서 실행한다.
- 추적 파일 변경이 있으면 중단한다.
- 모든 실행은 고유 증적 디렉터리를 사용한다.
- 실패 증적을 삭제하지 않는다.
- 종료 코드와 출력 해시를 함께 기록한다.
- 미실행은 `NOT_RUN`, 환경 부족은 `BLOCKED`다.
- 단일 성공 실행만으로 최종 후보가 될 수 없다.
- 독립 운영자 2회 실행과 읽기 전용 재검증이 필요하다.

## 3. 환경 준비

```bash
bash scripts/prepare-assurance-environment.sh
```

수동 확인:

```bash
git status --short
git rev-parse HEAD
java -version
javac -version
mvn -version
python3 --version
```

필수 조건:

```text
JDK 17
Maven
Git
Bash
Python 3
sha256sum
추적 파일 변경 없음
```

## 4. 실행 전 점검

```bash
bash scripts/preflight-local-assurance.sh
bash scripts/preflight-universal-harness.sh
```

점검 실패는 `PASS`가 아니라 `BLOCKED`다.

## 5. Maven·JUnit 실행

```bash
mvn -B -ntp test
```

기록 항목:

- 명령과 종료 코드
- 전체 시험 수
- 성공·실패·오류·건너뜀 수
- 표준출력·표준오류 SHA-256
- 생성 산출물 SHA-256

## 6. 제품 플랫폼 종단간 실행

```bash
bash scripts/run-product-platform-e2e.sh
```

검증 범위:

- 일반 프로그램 결함본·수정본
- AI 프로그램 정상·적대 동작
- ORUDA 외부 대상 어댑터
- 발견사항·근본원인·개선·재검증
- 독립 검증·감사 영수증

같은 조건으로 2회 실행하고 정규화 결과를 비교한다.

## 7. 범용 하네스 독립 실행 2회

```bash
bash scripts/run-universal-harness-twice.sh \
  operator-independent-1 \
  operator-independent-2 \
  local-jdk17
```

필수 조건:

- 운영자 ID 서로 다름
- 동일 소스·정책·시험 데이터·오라클
- 동일 환경 분류
- 두 실행 모두 `PASS`
- `NOT_RUN=0`
- `BLOCKED=0`
- 미해결 `Critical/Major=0`
- 정규화 결과 해시 동일

## 8. 로컬 자체 보증 2회

```bash
bash scripts/run-local-assurance-twice.sh
```

각 전체 실행기는 내부 회귀검증 2회를 수행한다.

```text
실행 전 점검
→ 실행 문맥
→ 소스 잠금
→ 시험 데이터·보안 스냅샷
→ 회귀검증 1회차
→ 빌드 산출물 정리
→ 회귀검증 2회차
→ 결과 비교
→ 독립 검증 영수증
→ 독립 감사 영수증
→ 최종 영수증
```

## 9. Issue #4 최종 관문

```bash
bash scripts/execute-issue-4-final-gate.sh
```

성공 표식:

```text
ISSUE4_FINAL_GATE_EVIDENCE_READY
```

증적 파일·영수증·종료 코드가 없으면 표식만으로 성공 처리하지 않는다.

## 10. 개발 관문

```bash
bash scripts/run-onsure-development-gate.sh
```

개발 관문은 제품 종단간 시험, 범용 하네스, 자체 보증, Issue #4 증적을 모두 확인한다.

성공 표식:

```text
ONSURE_DEVELOPMENT_GATE_PASS
```

## 11. 읽기 전용 재검증

```bash
bash scripts/verify-local-assurance.sh receipts/local/<run-directory>
```

재검증 항목:

- 영수증 자기 해시
- 증적 파일 존재·SHA-256
- 소스 잠금·정책·실행 문맥
- 독립 검증·감사 계보
- 판정 상한
- 최종 영수증

## 12. 실행 결과 요약

```bash
bash scripts/summarize-local-assurance.sh \
  --verify receipts/local/<run-directory>
```

보고서에는 다음을 포함한다.

- 실행 HEAD와 환경
- 시험 수와 결과
- 제품 종단간·범용 하네스 결과
- 발견사항과 심각도
- 근본원인분석·회귀검증 상태
- 증적 루트와 SHA-256
- 개발 관문 판정
- 잔여 `NOT_RUN/BLOCKED`

## 13. 실패 처리

```text
실패 발생
→ 명령·출력·종료 코드·증적 보존
→ RCA_PENDING
→ 근본원인 확정
→ 최소 수정·지속 수정 구분
→ 집중 재시험
→ 전체 회귀검증 2회
→ 독립 검증·감사
→ 전체 관문 재실행
```

시험 기대값을 완화하거나 실패 항목을 제외하여 통과시키는 행위는 금지한다.

## 14. 최종 판정

```text
PASS      모든 필수 실행·검증·증적 완료
FAIL      실행 결함 또는 판정 불일치
BLOCKED   환경·도구·권한·증적 부족
NOT_RUN   미실행
HOLD      최종 승격 조건 미충족
```

최종 후보가 되어도 최종 잠금은 별도 승인·감사 절차를 요구한다.
