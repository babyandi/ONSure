# ONSURE General Validation Platform v1

## 1. 제품 정의

ONSURE은 특정 프로젝트의 전용 시험기가 아니다. 일반인·개발자·제품팀이 AI로 만든 AI 프로그램과 일반 프로그램을 검증하고, 오류·보안·정책·구조·실행 문제를 찾아 RCA를 수행하며, 검증 리포트·개선안·패치·재검증 증거까지 제공하는 독립 상용 검증 플랫폼이다.

```text
Product          INDEPENDENT_COMMERCIAL_SOFTWARE_VALIDATION_PLATFORM
Core Runtime     STANDALONE
Target Scope     AI_APPLICATION + GENERAL_SOFTWARE
First Target     ORUDA
Target Relation  EXTERNAL_VALIDATION_TARGET
Future Embed     ONSURE_AGENT or ONSURE_VALIDATION_MODULE
Product Rule     ONSURE standalone line remains independent
```

## 2. 제품 사용자와 사용 시나리오

### 일반 사용자

- AI 코딩 도구로 만든 프로그램이 실제로 안전하고 정상적인지 확인
- 실행은 되지만 누락된 예외·권한·보안·복구 경로 탐지
- 무엇이 잘못됐고 어떻게 고쳐야 하는지 이해 가능한 리포트 수신
- 허용된 범위에서 자동 개선 후 동일 조건 재검증

### 개발자와 제품팀

- 요구사항·설계·코드·테스트·보안·운영 계약 간 불일치 탐지
- Fixture·Harness·Oracle 기반 반복 실패 재현
- Source·Policy·Dependency·Artifact·Receipt 계보 고정
- 회귀 잠금과 독립 재검증으로 수정 안전성 입증

### 기업 검증 조직

- 여러 프로젝트를 Target Registry에 등록해 동일 검증 체계 적용
- 팀·제품별 Policy Profile과 승인 경계 적용
- 감사 가능한 Finding·RCA·Receipt·Final Report 보존
- 사내망·로컬·서버·내장 Module 배포 선택

## 3. 범용 Validator Engine

```text
Target Registration
  -> Target Adapter / Intake
  -> Immutable Source & Artifact Lock
  -> Requirement / Policy / Architecture Reconstruction
  -> Static Validator
  -> Runtime & E2E Harness
  -> Security / Privacy / Supply-chain Validator
  -> Failure Mode Registry
  -> RCA Engine
  -> Fixture Synthesizer & Registry
  -> Oracle Evaluation
  -> Remediation Planner / Approved Patch Builder
  -> Regression Lock
  -> Independent Revalidation
  -> Validation Report
  -> Receipt Ledger / Final Decision
```

### 3.1 Target Registration

모든 검증 대상은 Target Registry에 등록한다. 대상은 저장소, 패키지, 바이너리, 실행 환경, 정책 문서, 데이터 계약을 포함할 수 있다.

Target Adapter는 대상의 언어·프레임워크·빌드·실행 특성을 ONSURE 표준 Evidence 계약으로 변환한다. Adapter는 검증 정책이나 Final Decision을 바꿀 권한이 없다.

### 3.2 Evidence Graph

ONSURE은 다음 관계를 하나의 Evidence Graph로 구성한다.

```text
Requirement -> Design -> Code -> Configuration -> Dependency
-> Build Artifact -> Runtime Behaviour -> Test Evidence
-> Finding -> RCA -> Patch -> Regression -> Final Decision
```

모든 주장에는 source anchor, digest, authority, time, policy, execution ID가 결속되어야 한다.

### 3.3 Static Validator

- 언어·프레임워크별 구문·타입·품질·취약점 검사
- 인증·인가·입력 검증·비밀정보·개인정보 검사
- AI 프로그램의 prompt injection·tool abuse·data exfiltration 검사
- 설정·배포·의존성·라이선스·SBOM·provenance 검사
- 요구사항·설계·코드·테스트 간 누락과 충돌 검사

### 3.4 Runtime Harness

- 정상·예외·장애·복구·동시성·대용량 시나리오 실행
- 외부 서비스 실패·시간 초과·부분 성공·재시도·중복 실행 검증
- AI 응답 변동·도구 오용·권한 경계·환각·근거 누락 검증
- 동일 locked input으로 반복 실행해 재현성 비교

### 3.5 Failure Mode와 RCA

Failure Mode Registry는 증상만 저장하지 않는다.

```text
Failure Mode
-> Trigger / Preconditions
-> Observable Symptom
-> Violated Contract
-> Blast Radius
-> Root Cause
-> Minimal Fix
-> Durable Fix
-> Required Fixture / Oracle / Regression
```

동일 원인의 변형 실패를 묶고, 수정 후 재발 여부를 Regression Lock으로 추적한다.

### 3.6 Fixture·Harness·Oracle

- Fixture: 정상·경계·적대·장애 입력과 환경 상태
- Harness: 대상 실행·격리·관찰·복구를 수행하는 실행체
- Oracle: 기대 결과·허용 오차·정책·증거를 이용한 판정기

Fixture와 Oracle은 대상 제품의 주장만 신뢰하지 않고 ONSURE이 독립 계산 가능한 형태로 저장한다.

### 3.7 Receipt와 Regression Lock

Receipt는 Source·Policy·Target·Fixture·Harness·Oracle·Finding·Patch·Result를 서명된 증거 체인으로 묶는다.

Regression Lock은 다음을 고정한다.

- 검증 대상 immutable source와 artifact
- 실행 환경과 dependency/toolchain
- Fixture set과 Oracle version
- Policy profile
- Finding/RCA/patch 연결
- 반복 실행 결과와 허용 차이

### 3.8 Report와 개선·재검증

ONSURE의 최종 산출물은 단순 오류 목록이 아니다.

- 경영·일반 사용자용 위험 요약
- 개발자용 재현 절차와 기술 RCA
- 우선순위·영향도·수정 난이도 기반 개선계획
- 자동 수정 가능 범위와 승인 필요 범위
- 패치 전후 결과 비교
- 남은 위험과 배포 가능 여부
- Receipt·Evidence 링크가 포함된 Final Validation Report

## 4. ORUDA 자산 흡수 원칙

ORUDA Adaptive Validation Master의 다음 구조는 ONSURE Validator Engine의 설계 재료로 흡수한다.

- RCA
- Failure Mode
- Fixture
- Harness
- Oracle
- Receipt
- Regression Lock

흡수 원칙:

1. 개념·계약·실패 패턴은 일반화한다.
2. ORUDA 경로·이름·Agent·Runtime을 ONSURE 필수 의존성으로 만들지 않는다.
3. ORUDA 전용 Fixture는 Target Pack으로 분리한다.
4. 범용 Engine 계약과 Target Adapter 계약을 분리한다.
5. ORUDA의 판정 결과를 ONSURE Final Decision으로 그대로 승격하지 않는다.

## 5. ORUDA 1호 검증 대상

ONSURE 독립 제품 기준선을 먼저 완성한 뒤 ORUDA를 Target Registry의 1호 검증 대상으로 등록한다.

초기 방식:

```text
ONSURE Standalone
  -> ORUDA Target Adapter
  -> ORUDA source / policy / runtime evidence intake
  -> ONSURE generic Validator Engine
  -> ORUDA Target Fixture Pack
  -> independent Harness / Oracle / Receipt / Report
```

ORUDA 검증은 ONSURE의 범용성을 입증하는 첫 사례이지 제품 존재 이유나 필수 Runtime이 아니다.

## 6. 장기 이식 구조

장기적으로 ORUDA 내부에 다음 중 하나를 이식할 수 있다.

### ONSURE Agent

- ORUDA 내부 상태·로그·Receipt를 수집
- ONSURE Standalone Engine으로 portable evidence 전달
- 로컬 사전 검사와 즉시 차단 수행

### ONSURE Validation Module

- ORUDA Runtime 안에서 일부 Fixture·Policy·Receipt 검증 수행
- 독립 OTester/OAudit 또는 외부 ONSURE에서 결과 재계산 가능
- Target 내부 결과를 portable Receipt로 내보냄

이식 후에도 다음은 유지한다.

- ONSURE 별도 제품·저장소·배포판
- ORUDA 없이 실행 가능한 Standalone Core
- Target 내부 판정과 ONSURE 공식 판정의 권한 분리
- 독립 재검증 가능한 Evidence와 Receipt
- ORUDA 제거·장애가 ONSURE Core를 중단시키지 않는 구조

## 7. 상용 제품 모듈

```text
ONSURE Studio          사용자 프로젝트 등록·검증 실행·결과 탐색
ONSURE Validator Core  정적·동적·정책·보안 Validator Engine
ONSURE Target SDK      언어·프레임워크·제품 Adapter 개발 계약
ONSURE Fixture Lab     Fixture 생성·버전·Failure Registry 관리
ONSURE Harness         격리 실행·관찰·장애 주입·재현
ONSURE Oracle          기대 결과·정책·허용 오차 판정
ONSURE Remediator      RCA 기반 개선안·승인형 패치
ONSURE Reporter        일반·기술·감사 리포트 생성
ONSURE Revalidator     수정 후 전체 재검증·Regression Lock
ONSURE Evidence Vault  Receipt·Lock·Ledger·Final Report 보존
```

## 8. 제품 불변조건

- 특정 Target가 ONSURE의 Final Decision Authority가 될 수 없다.
- ORUDA 구성요소가 없어도 ONSURE Core가 실행되어야 한다.
- Embedded Agent/Module은 독립 재검증 가능한 Receipt를 내보내야 한다.
- 실행하지 않은 검사는 PASS가 될 수 없다.
- 리포트의 모든 중대한 결론은 Evidence에 연결되어야 한다.
- 개선 후 동일 Fixture와 변형 Fixture로 재검증해야 한다.
- Target Adapter 제거가 Core의 다른 Target 검증을 손상시키면 안 된다.

## 9. 단계별 제품화

```text
Phase 1  ONSURE standalone generic core 강화
Phase 2  Generic Target SDK와 Report/Remediation UX 완성
Phase 3  ORUDA를 1호 Target으로 등록·전체 검증
Phase 4  ORUDA Target Pack과 상용 Reference Case 확정
Phase 5  ORUDA 내부 ONSURE Agent/Module 선택 이식
Phase 6  다언어·다프레임워크·다제품 Target Marketplace 확장
```

ONSURE의 제품 기준선은 Phase 1에서 독립적으로 성립해야 하며, ORUDA 연계 여부와 무관하게 판매·배포·운영 가능한 구조를 유지한다.
