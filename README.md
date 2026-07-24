# ONSURE

ONSURE은 일반 사용자·개발자·제품팀이 AI로 만든 프로그램과 일반 프로그램을 독립적으로 검증하고, 오류·보안·정책·구조·실행 문제를 찾아내며, 근본원인분석(RCA)·개선·검증 보고서·재검증까지 수행하는 **독립 상용 소프트웨어 검증 플랫폼**입니다.

ONSURE은 ORUDA 전용 검증기가 아닙니다. ORUDA 적응형 검증 체계의 근본원인분석, 실패 유형, 시험 데이터, 실행 하네스, 판정 오라클, 검증 영수증, 회귀 잠금 구조를 범용 검증 엔진의 설계 재료로 흡수합니다. ORUDA는 ONSURE 독립 제품 완성 후 등록되는 첫 번째 공식 검증 대상입니다.

> 문서 언어 원칙: 제목·본문·표·운영 절차는 한글로 작성합니다. `PASS`, `FAIL`, `HOLD`, `NOT_RUN`, 오류 코드, JSON 키, 클래스명, 명령어와 파일 경로는 실행 계약 호환성을 위해 원문을 유지합니다.

## 2026-07-24 설계 반영

이번 기준선은 ONSURE을 하나의 독립 프로그램으로 유지하면서 학습 엔진, 검증 엔진, 실행기, 증적, 통제를 핵심 설계에 반영합니다.

```text
ONSURE 핵심 체계
→ 작업 대장 / 실행기 반복 처리
→ 실행 하네스
→ 검증 엔진
→ 검증 영수증 / 증적 / 추적 정보
→ 데이터셋 등록소 / 정책 코드화
→ 학습 피드백 엔진
→ 승격 / 롤백 관문
→ 현황판 / 변화 감지 / 사고 재현
```

반영 원칙은 다음과 같습니다.

- 제품은 하나이며 내부 엔진의 권한과 데이터는 분리합니다.
- 학습 엔진은 개선 후보를 만들지만 `PASS` 또는 관문 통과를 결정하지 않습니다.
- 검증 엔진은 독립 재계산과 골든·비공개 시험으로 통과·차단을 판단합니다.
- 실행기는 실제 작업 대장을 소비하고 `READY`, `RUNNING`, `DONE`, `RETRY`, `HOLD` 상태 전이 영수증을 남깁니다.
- 추적 정보, 데이터셋 등록소, 정책 코드화, 모델·프롬프트·도구 등록소, 사고 재현을 설계 기준에 포함합니다.
- ORUDA 어댑터는 후순위 검증 대상 팩으로 둡니다.

## 학습 적용 파이프라인 보완

기존 설계는 검증되지 않은 학습 후보의 자동 적용을 차단했지만, 검증 완료 후보가 실제 적용으로 이동하는 공식 경로가 부족했습니다. 다음 경로를 유일한 적용 경로로 사용합니다.

```text
학습 후보
→ 검증 요청
→ 검증 엔진 독립 검증
→ PASS 영수증
→ 승격 검토
→ 적용 커밋 또는 안정 등록소 활성화
→ 적용 후 검증
→ 적용 잠금
```

학습 적용 1건은 다음 조건을 모두 만족할 때만 계산합니다.

- 후보가 학습 엔진에서 생성되었습니다.
- 검증 엔진이 독립 재계산으로 `PASS` 영수증을 발행했습니다.
- 승격 영수증과 역할 분리가 존재합니다.
- 적용 커밋 또는 안정 등록소 버전이 존재합니다.
- 실제 활성 선택자가 승격된 산출물을 참조합니다.
- 적용 후 검증 영수증과 롤백 포인터가 존재합니다.

후보 큐, 검증 요청, `NON_FINAL` 실험 채택, 열린 PR은 적용 건수로 계산하지 않습니다. MVP의 첫 적용 목표는 ORUDA 대상이 아니라 ONSURE 핵심 검증 팩에 대해 `APPLIED_LOCKED` 1건을 만드는 것입니다.

## 제품 기준선

```text
제품 성격       독립 상용 소프트웨어 검증 플랫폼
검증 범위       AI 응용프로그램 + 일반 소프트웨어
핵심 실행 방식  독립 실행
첫 검증 대상    ORUDA / 계획됨
향후 내장 형태  ONSURE 에이전트 또는 ONSURE 검증 모듈
구현 상태       IMPLEMENTATION_READY
실행 상태       NOT_RUN
관문 상태       HOLD
PR 상태         DRAFT
```

## 주요 사용자

- AI 코딩 도구로 프로그램을 만든 일반 사용자
- 소프트웨어 개발자와 제품팀
- 보안·품질·감사·내부통제 조직
- 여러 프로젝트를 동일 기준으로 검증하려는 기업

## 검증 대상

- AI 응용프로그램과 에이전트형 시스템
- 웹·API·데스크톱·모바일 응용프로그램
- 일반 업무 프로그램과 자동화 작업 흐름
- 소스 저장소, 패키지, 실행 파일, 컨테이너, 설정
- 프롬프트·도구 권한·모델 응답을 포함한 AI 실행 구조

## 범용 검증 엔진

```text
검증 대상 등록
→ 대상 어댑터 / 입력 접수
→ 변경 불가 소스·산출물 잠금
→ 요구사항·정책·아키텍처 재구성
→ 정적 코드·설정 검증
→ 실행·종단간 하네스
→ 보안·개인정보·공급망 검증
→ 실패 유형 등록소
→ 근본원인분석 엔진
→ 시험 데이터 합성·등록
→ 판정 오라클 평가
→ 개선안 / 승인된 패치
→ 회귀 잠금
→ 독립 재검증
→ 검증 보고서
→ 최종 잠금 / 영수증 대장 / 최종 판정
```

## 핵심 산출물

- 오류·위험·누락 발견사항
- 재현 가능한 실패 유형
- 기술적 근본원인분석
- 정상·경계·적대·장애 시험 데이터
- 실행 하네스와 독립 오라클 판정
- 최소 수정안·지속 가능한 개선안·승인 필요사항
- 패치 전후 비교와 회귀 잠금
- 일반 사용자용 요약 보고서
- 개발자용 재현·근본원인·개선 보고서
- 감사 가능한 영수증·증적·최종 검증 보고서

## 제품 독립성

- ORUDA와 별도 저장소·별도 제품·별도 실행 환경을 유지합니다.
- ORUDA가 없어도 ONSURE 독립 핵심 체계가 실행되어야 합니다.
- 모든 대상은 대상 어댑터로 연결합니다.
- 검증 대상은 ONSURE 정책·오라클·최종 판정을 변경할 수 없습니다.
- 검증 대상 내부 결과는 주장으로만 취급하며 독립 재계산 전에는 신뢰하지 않습니다.
- 내장 에이전트·모듈도 이식 가능한 영수증을 내보내야 합니다.
- ORUDA 제거·장애가 다른 검증 대상의 실행을 중단시키지 않아야 합니다.

## ORUDA 첫 공식 검증 대상

ONSURE 독립 제품을 먼저 보강한 뒤 `contracts/validation-target-registry.v1.json`에 ORUDA를 첫 검증 대상으로 등록합니다.

```text
ONSURE 독립 실행
→ ORUDA 대상 어댑터
→ ORUDA 소스 / 정책 / 실행 증적
→ 범용 검증 엔진
→ ORUDA 전용 시험 팩
→ 독립 하네스 / 오라클 / 영수증 / 보고서
```

장기적으로 ORUDA 내부에 ONSURE 에이전트 또는 검증 모듈을 이식할 수 있지만, ONSURE 자체는 단독 판매·배포·실행 가능한 제품 구조를 유지합니다.

## ORUDA 설계 재료 흡수

다음 구조는 ORUDA 전용 구현이 아니라 ONSURE 소유의 범용 계약으로 일반화합니다.

- 근본원인분석
- 실패 유형
- 시험 데이터
- 실행 하네스
- 판정 오라클
- 검증 영수증
- 회귀 잠금

ORUDA 경로·에이전트·실행 환경·정책 권위는 ONSURE 핵심 체계의 필수 의존성이 아닙니다.

## 현재 구현 상태

설계·계약·검증기·A01~A20 시험 데이터·보안 발견사항 관문·독립 실행기·독립 검증/감사 참조 구현·서명 영수증·최종 잠금·대장·최종 영수증·재검증 명령행 도구·개발 컨테이너 실행 환경이 구현되었습니다.

실제 JDK 17과 Maven 실행 증거가 없으므로 `PASS`를 주장할 수 없습니다.

## 실행 환경

```text
JDK 17
Maven
Git
sha256sum
cmp
추적 파일 변경이 없는 작업공간
```

Codespace 또는 개발 컨테이너 준비:

```bash
bash scripts/prepare-assurance-environment.sh
```

Issue #4 최종 실행:

```bash
bash scripts/execute-issue-4-final-gate.sh
```

성공 표식:

```text
LOCAL_ASSURANCE_TWICE_PASS
ISSUE4_FINAL_GATE_EVIDENCE_READY
```

## 실행 전 점검

```bash
bash scripts/preflight-local-assurance.sh
```

실행 전 점검은 실행 환경, 제품 범위, 대상 등록소, 상태·검증선·영수증·실행 문맥·소스 잠금·최종 영수증·보안 발견사항 계약, A01~A20 시험 데이터, 변경 불가 커밋과 깨끗한 작업공간을 확인합니다.

## 단일 전체 실행기

```bash
bash scripts/run-local-assurance.sh
```

```text
실행 전 점검
→ 실행 문맥
→ 소스 잠금
→ 시험 데이터·보안 스냅샷
→ Maven/JUnit 회귀검증 1회차
→ 빌드 산출물 정리
→ Maven/JUnit 회귀검증 2회차
→ 요약·클래스 해시·시험 보고서 비교
→ 독립 검증 영수증
→ 독립 감사 영수증
→ 최종 잠금
→ 추가 전용 대장
→ 최종 영수증과 자기검증
```

## 전체 실행기 연속 2회

```bash
bash scripts/run-local-assurance-twice.sh
```

- 각 실행기 내부 회귀검증 2회의 동일성
- 전체 실행기 2회의 소스 잠금·스냅샷·결정적 증적 동일성
- 두 실행의 읽기 전용 재검증
- 후속 대장 추가 이후 과거 실행별 영수증 재검증

## 읽기 전용 재검증

```bash
bash scripts/verify-local-assurance.sh receipts/local/<run-directory>
```

## 실행 결과 요약

```bash
bash scripts/summarize-local-assurance.sh --verify receipts/local/<run-directory>
```

## 주요 계약

- 제품 범위: `contracts/product-scope.v1.json`
- 검증 대상 등록소: `contracts/validation-target-registry.v1.json`
- 검증선: `contracts/assurance-lanes.v1.json`
- 상태 모델: `contracts/state-machine.v1.json`
- 일반 영수증: `contracts/receipt-envelope.v1.schema.json`
- 독립 에이전트 영수증: `contracts/local-agent-receipt.v1.schema.json`
- 실행 문맥: `contracts/local-run-context.v1.schema.json`
- 소스 잠금: `contracts/source-lock.v1.schema.json`
- 최종 영수증: `contracts/local-final-receipt.v1.schema.json`
- 보안 발견사항 등록부: `contracts/security-findings.v1.schema.json`
- 핵심 운영 아키텍처: `contracts/core-operating-architecture.v1.json`
- 학습·검증 엔진: `contracts/learning-validation-engine.v1.json`

## 기준 문서

- `docs/architecture/ONSURE_GENERAL_VALIDATION_PLATFORM_v1.md`
- `docs/architecture/ONSURE_ASSURANCE_ARCHITECTURE_v1.md`
- `docs/architecture/ONSURE_CORE_OPERATING_ARCHITECTURE_v1.md`
- `docs/architecture/ONSURE_LEARNING_VALIDATION_ENGINE_DESIGN_v1.md`
- `docs/architecture/ONSURE_LEARNING_TO_APPLICATION_PIPELINE_v1.md`
- `docs/verification/ONSURE_MVP_SCOPE_AND_ENGINEERING_PLAN_v1.md`
- `docs/research/ONSURE_EXTERNAL_PRODUCT_AND_FAILURE_REVIEW_v1.md`
- `docs/security/ONSURE_SECURITY_REMEDIATION_v1.md`
- `docs/verification/ONSURE_DESIGN_VALIDATION_PLAN_v1.md`
- `docs/verification/ONSURE_EXECUTION_ENVIRONMENT_v1.md`
- `docs/verification/ONSURE_LOCAL_EXECUTION_RUNBOOK_v1.md`
- `docs/verification/ONSURE_LOCAL_EXECUTION_RESULT_TEMPLATE_v1.md`

## 최종 관문

```text
제품 범위·대상 등록소 계약 일치
실행 전 점검 PASS
JDK 17 컴파일 PASS
JUnit 전체 PASS
A01~A20 예상 판정 일치
단일 실행기 내부 회귀검증 2회 동일
전체 실행기 연속 2회 PASS
독립 검증·감사 영수증 PASS
보안 발견사항 관문 PASS
최종 잠금·대장·최종 영수증 PASS
두 실행 읽기 전용 재검증 PASS
미해결 Critical/High 0건
```

하나라도 미실행이거나 증거가 누락되면 관문은 `HOLD`입니다.

승인된 커밋 SHA와 해당 실행의 대상 프로필·소스 잠금·시험 데이터·오라클·회귀 잠금·영수증 체인이 최종 기준입니다.
