# ONSURE 제품 구현 상태 v1

## 판정

ONSURE 제품 개발의 권위 구현은 `io.onsure.platform`이다. `io.onsure.assurance`는 독립 검증·영수증·최종 관문을 담당하며, `io.onsure.harness`는 범용 실행 하네스를 담당한다.

```text
제품 핵심 구현              완료
범용 검증 엔진              완료
일반 프로그램 대상          완료
AI 프로그램 대상            완료
ORUDA 대상 어댑터           완료
실패 유형·근본원인분석       완료
개선·재검증                 완료
범용 하네스                 완료
Maven 컴파일               NOT_RUN
JUnit                      NOT_RUN
실제 제품 종단간 시험        NOT_RUN
개발 관문                   HOLD
```

## 제품 핵심부

구현된 기능:

- 작업공간·프로젝트·검증 대상 목록
- 검증 작업 생성과 상태 전이
- 실패 시 부분 증적 보존
- 대상 어댑터 등록소
- 검증 단계 파이프라인
- 판정 상한
- 발견사항·실패 유형·근본원인분석 저장 모델
- 개선 계획과 승인 필요 분류
- JSON·Markdown·HTML 검증 보고서
- 수정 전후 재검증 차이

## 범용 검증 엔진

```text
대상 입력
→ 소스 목록
→ 정적 분석
→ AI 동작 검증
→ 시험 데이터·오라클 등록소 봉인
→ 제한된 프로세스 하네스
→ 오라클 판정
→ 실패 유형·근본원인분석
→ 개선 계획
→ 회귀 잠금
→ 독립 제품 검증
→ 독립 제품 감사
→ 보고서·영수증·증적 목록
```

핵심 통제:

- 실제 프로세스 출력·종료 코드·시간 제한 증적
- 인라인 셸·절대경로·대상 루트 이탈 차단
- 전역 실패 유형 등록소
- 해시 봉인된 검증·감사 영수증
- 대상 자체 최종 판정 작성 차단

## 일반 프로그램 종단간 대상

결함본:

- 비밀정보 노출
- 미완성 표시
- 권한 오류

수정본:

- 비밀정보 제거
- 권한 판정 수정
- 회귀검증 기대값 고정

실제 Java 컴파일과 프로세스 시험 코드는 구현돼 있으나 공식 실행 결과는 `NOT_RUN`이다.

## AI 프로그램 종단간 대상

검증 항목:

- 신뢰하지 않은 도구 실행
- 에이전트 자기 승인
- 프롬프트 주입 우회
- 전체 문맥 유출
- 안전·적대 도구 호출
- 정책과 실제 행동 불일치

시험 대상 실행 스크립트는 존재하지만 ONSURE 전체 엔진의 공식 `PASS` 증거는 아직 없다.

## ORUDA 대상

구현 범위:

- 독립 외부 대상 어댑터
- ORUDA의 ONSURE 최종 판정 작성 차단
- 실행 감사 위조·에이전트 자기 승인 시험
- 실행 패키지 목록
- 증적 등록소
- 실행 결과 분류기
- 영수증 계보 검증기
- MVF-001 실행 시험 데이터

ORUDA는 ONSURE 핵심 의존성이 아니며 후순위 대상 팩으로 유지한다.

## 범용 하네스

구현 범위:

- 필수 검증 축 30개
- 시험 데이터 유형 7개
- 제한된 프로세스 실행
- 시간 제한·출력 제한
- 증적·시험·실행 영수증
- SHA-256 증적 목록
- 실패 시 `RCA_PENDING`
- 독립 회귀검증 2회
- 서로 다른 운영자의 독립 실행 2회
- 최종 후보 차단 규칙

## 실행 명령

제품 플랫폼 종단간 시험:

```bash
bash scripts/run-product-platform-e2e.sh
```

범용 하네스 독립 2회:

```bash
bash scripts/run-universal-harness-twice.sh \
  operator-independent-1 operator-independent-2 local-jdk17
```

전체 개발 관문:

```bash
bash scripts/run-onsure-development-gate.sh
```

## 성공 조건

```text
ONSURE_PRODUCT_PLATFORM_E2E_PASS
ONSURE_UNIVERSAL_TWO_RUN_PASS
ISSUE4_FINAL_GATE_EVIDENCE_READY
ONSURE_DEVELOPMENT_GATE_PASS
```

성공 표식만으로 통과할 수 없다. 종료 코드, 영수증, 증적 SHA-256, 읽기 전용 재검증 결과가 모두 필요하다.

## 남은 작업

- JDK 17·Maven 실행 환경 준비
- Maven 컴파일·JUnit 실행
- 제품 종단간 시험 2회
- 범용 하네스 독립 2회
- ONSURE 자체 보증
- 실패 시 근본원인분석·수정·전체 회귀검증
- 개발 관문 성공
- 증적 고정과 독립 검토

## 정확한 현재 상태

```text
IMPLEMENTATION        COMPLETE
STATIC_INTEGRATION    COMPLETE
FORMAL_EXECUTION      NOT_RUN
DEVELOPMENT_GATE      HOLD
FINAL_CANDIDATE       BLOCKED
FINAL_LOCK_ALLOWED    false
```
