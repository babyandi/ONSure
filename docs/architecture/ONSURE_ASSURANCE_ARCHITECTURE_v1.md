# ONSURE Assurance Architecture v1

## 1. 제품 목적과 현재 단계

ONSURE은 일반인·개발자·제품팀이 AI로 만든 AI 프로그램과 일반 프로그램을 독립적으로 검증하는 상용 Software Validation Platform이다. 소스·요구사항·설계·코드·보안·정책·실행·증거를 검토하고, Failure Mode와 RCA를 만들며, Fixture·Harness·Oracle로 문제를 재현하고, 개선·리포트·재검증까지 수행한다.

ONSURE은 ORUDA 전용 검증기가 아니다. ORUDA Adaptive Validation Master의 RCA·Failure Mode·Fixture·Harness·Oracle·Receipt·Regression Lock 구조는 범용 Validator Engine 설계 재료로 흡수하며, ORUDA 자체는 ONSURE 독립 제품 완성 후 등록되는 1호 검증 대상이다.

```text
Product          INDEPENDENT_COMMERCIAL_SOFTWARE_VALIDATION_PLATFORM
Core Runtime     STANDALONE
Target Scope     AI_APPLICATION + GENERAL_SOFTWARE
First Target     ORUDA / PLANNED
Execution Gate   HOLD
Runtime Result   NOT_RUN
PR State         DRAFT
```

설계·계약·Validator·Fixture·로컬 Runner·실행 환경은 구현되었지만 실제 JDK 17/Maven 실행 증거가 없으므로 PASS를 주장하지 않는다.

## 2. 최상위 원칙

1. 특정 검증 대상은 ONSURE Final Decision Authority가 될 수 없다.
2. ORUDA를 포함한 모든 대상은 Target Adapter를 통해 연결한다.
3. 대상 제품이 없어도 ONSURE Core가 단독 실행되어야 한다.
4. 실행 증거가 없으면 PASS 금지, 미실행은 `NOT_RUN`이다.
5. 증거·정책·권한이 불명확하면 fail-closed 한다.
6. 생성·수정 주체와 독립 검증·감사 주체를 분리한다.
7. 코드 수정은 요구사항·정책·Fixture·Oracle·Regression Lock·Receipt에 결속한다.
8. Critical 또는 High 보안 결함이 존재하면 배포를 차단한다.
9. 자동 수정은 승인된 범위에서만 수행한다.
10. 업무 의미·규제·권한 모델 변경은 승인 후 수행한다.
11. 수정 후 동일 실패와 변형 실패 및 전체 회귀를 재실행한다.
12. Material report conclusion은 Evidence에 연결되어야 한다.
13. GitHub Actions와 외부 CI는 공식 판정 근거로 사용하지 않는다.
14. 공식 검증은 Standalone Runner와 서명 Receipt로 증명한다.
15. Embedded Agent/Module도 portable Receipt를 내보내 독립 재검증 가능해야 한다.

## 3. 범용 전체 구조

```text
Validation Target Registry
  -> Target Adapter / Intake
  -> Immutable Source & Artifact Lock
  -> Requirement / Policy / Architecture Reconstruction
  -> Static Code / Configuration Validator
  -> Runtime & E2E Harness
  -> Security / Privacy / Supply-chain Validator
  -> Failure Mode Registry
  -> RCA Engine
  -> Fixture Synthesizer & Registry
  -> Oracle Evaluation
  -> Remediation Planner / Approved Patch Builder
  -> Regression Lock
  -> Independent Verifier
  -> Independent Audit
  -> Validation Report
  -> Final Lock / Receipt Ledger / Final Decision
```

## 4. Core와 Target 경계

### 4.1 ONSURE Core

ONSURE Core는 Target와 무관하게 다음 범용 기능을 제공한다.

- Target Registry와 Adapter contract
- Receipt canonical serialization과 서명 검증
- SHA-256, Source Lock, Permit, Replay 검증
- Claim-Evidence binding
- 정책 카탈로그와 reason code
- Failure Mode와 RCA registry
- Fixture·Harness·Oracle interface
- SBOM·provenance·dependency evidence contract
- Remediation approval·patch·rollback contract
- Regression Lock
- Run Context, Final Lock, Ledger, Final Receipt 검증
- 일반 사용자·개발자·감사용 Validation Report

### 4.2 Target Adapter

Target Adapter는 언어·프레임워크·빌드·실행·데이터 특성을 ONSURE 표준 계약으로 변환한다.

Adapter가 할 수 있는 일:

- Source·artifact·configuration inventory 제공
- 빌드·실행 명령과 sandbox profile 제공
- Target-specific Fixture Pack 연결
- 로그·metric·trace·screen·AI tool call 수집

Adapter가 할 수 없는 일:

- ONSURE policy 또는 Oracle 변경
- Finding severity 축소
- 독립 Verifier/Audit decision 작성
- Target 자체 PASS를 ONSURE Final PASS로 승격
- Core 실행에 Target Runtime을 필수 의존성으로 삽입

## 5. 현재 내부 Provider와 범용 역할

현재 구현명은 초기 reference provider이며 제품 계약의 필수 고유명사가 아니다.

| 현재 구현명 | 범용 역할 | 제품 경계 |
|---|---|---|
| ODocument | Document / Requirement Analyzer | 교체 가능한 capability provider |
| OReport | Evidence Analyzer / Reporter | ONSURE Reporter로 일반화 |
| ODesign | Architecture / Design Analyzer | 선택적 provider |
| OUI | UI / Interaction Analyzer | Target별 provider |
| OMaker | Implementation / Remediation Planner | 승인 경계가 있는 provider |
| OBuilder | Isolated Patch Builder | 교체 가능한 builder |
| OTester | Independent Verifier | 별도 프로세스·별도 key |
| OAudit | Independent Audit | 판정 상한·Receipt 계보 감사 |

ONSURE Runtime은 독립 Verifier/Audit 판정을 대신 작성할 수 없다.

## 6. 핵심 모듈

### 6.1 Target Registration

- `ONSURE_VALIDATION_TARGET_REGISTRY_V1`에 대상 등록
- target type, source locator, adapter, policy profile, execution profile 고정
- Target 등록이 Core 의존성을 만들지 않는지 검사
- 첫 Target는 ORUDA이며 초기 관계는 `EXTERNAL_VALIDATION_TARGET`이다.

### 6.2 Source and Artifact Intake

- Git ref를 immutable commit SHA로 고정
- package·binary·container·configuration digest 수집
- tracked source inventory와 canonical path/content digest 생성
- 정책 집합 digest 생성
- dirty tracked worktree 차단
- `ONSURE_SOURCE_DIGEST_V1` Source Lock 발행

### 6.3 Requirement·Policy·Architecture Reconstruction

- 요구사항 ID, 출처, 수용 기준, 추적 관계 저장
- README·issue·RFP·code·test·runtime claim 간 충돌 탐지
- 상충 요구사항은 자동 선택하지 않고 `CONFLICT`
- 추론 내용은 `INFERRED`로 표시하고 승인 전 Authority 금지
- 신뢰 경계·권한·상태·장애·복구 검토

### 6.4 Static and Security Validator

- 설계-코드 불일치, 상태 전이, 예외, 동시성, 멱등성 검토
- 인증·인가·입력 검증·Injection·Secret·개인정보 검토
- 공급망·SBOM·provenance·서명·license 검토
- AI prompt injection·tool abuse·data exfiltration·hallucination guard 검토
- Critical/High 1건 이상이면 Publication 차단

### 6.5 Runtime Harness

- 정상·예외·장애·복구·동시성·대용량 시나리오 실행
- Target-specific sandbox와 network policy 적용
- 동일 locked input으로 clean regression 2회
- 결과·compiled artifact·runtime output 비교
- AI 프로그램은 모델 변동·tool call·authority boundary를 별도 기록

### 6.6 Failure Mode와 RCA

```text
Failure Mode
-> Trigger / Preconditions
-> Observable Symptom
-> Violated Contract
-> Evidence
-> Blast Radius
-> Root Cause
-> Minimal Fix / Durable Fix
-> Required Fixture / Oracle / Regression
```

Failure Mode Registry는 동일 원인의 변형 실패와 재발 여부를 연결한다.

### 6.7 Fixture·Harness·Oracle

- Fixture: 입력·환경·권한·장애·적대 조건
- Harness: 격리 실행·관찰·장애 주입·복구 수행체
- Oracle: expected decision·reason·tolerance·policy 기반 판정기

Target가 제공하는 예상 결과는 claim으로만 취급하고 ONSURE이 독립 재계산한다.

### 6.8 Remediation and Build

- RCA 기반 minimal/durable remediation option 생성
- 자동 수정 가능 범위와 승인 필요 범위 분리
- 승인된 plan digest·source digest·dependency lock·policy digest만 소비
- 무허가 network·미고정 dependency·미신고 file operation 차단
- patch, build artifact, SBOM, provenance, rollback receipt 생성

### 6.9 Independent Verification and Audit

Independent Verifier는 runtime result·artifact·Fixture·Oracle 결과를 별도 process/service에서 재계산한다.

Independent Audit은 Source·Policy·Target·Fixture·Harness·Oracle·Finding·RCA·Patch·Regression·Report Receipt 계보와 decision ceiling을 검증한다.

### 6.10 Report Generation

ONSURE은 다음 결과를 생성한다.

- 일반 사용자용 위험·오류·개선 요약
- 개발자용 재현 절차와 기술 RCA
- 보안·정책·공급망 Finding 보고서
- 개선 우선순위와 승인 필요사항
- 패치 전후 비교
- 재검증·Regression Lock 결과
- 배포 가능 여부와 잔여 위험
- Evidence/Receipt가 결속된 Final Validation Report

## 7. ORUDA 자산 흡수와 1호 Target

ORUDA Adaptive Validation Master에서 다음 구조를 일반화하여 흡수한다.

- RCA
- Failure Mode
- Fixture
- Harness
- Oracle
- Receipt
- Regression Lock

흡수된 구조는 ONSURE 소유의 범용 계약으로 재작성한다. ORUDA 경로·Agent·Runtime·정책 Authority를 Core dependency로 가져오지 않는다.

초기 ORUDA 검증:

```text
ONSURE Standalone
  -> ORUDA Target Adapter
  -> ORUDA Source / Policy / Runtime Evidence
  -> Generic Validator Engine
  -> ORUDA Target Fixture Pack
  -> Independent Harness / Oracle / Receipt / Report
```

## 8. 장기 Embedded 구조

장기적으로 ORUDA 내부에 ONSURE Agent 또는 ONSURE Validation Module을 이식할 수 있다.

- Target 내부에서 상태·로그·Receipt 수집 및 사전 차단
- portable evidence를 Standalone ONSURE으로 전달
- 외부 ONSURE이 독립 재계산 가능
- embedded result와 standalone result equivalence 검증

이식 후에도 ONSURE 별도 저장소·배포판·Runtime·Final Decision Authority는 유지한다.

## 9. Receipt와 무결성

### 9.1 일반 Receipt

`contracts/receipt-envelope.v1.schema.json`은 Java `ReceiptEnvelope`의 정확한 구조를 정의한다.

### 9.2 Agent Receipt

현재 Independent Verifier/Audit reference provider는 `ONSURE_LOCAL_AGENT_RECEIPT_V1`을 사용한다.

필수 결속:

- authority와 role policy/scope
- agent run ID와 assurance run ID
- target ID와 source/policy context
- run start와 creation time
- input digest
- Ed25519 key ID와 signature
- 별도 execution boundary

### 9.3 Regression Lock

- target ID와 immutable source/artifact
- toolchain/dependency/execution profile
- Fixture set과 Oracle version
- Policy profile
- Finding/RCA/patch chain
- 반복 실행 결과와 허용 차이

### 9.4 Ledger와 Final Receipt

Ledger는 append-only hash chain이다. Final Receipt는 자신의 Run ID와 정확한 independent verifier/audit entry 및 per-run head에 결속된다. 후속 실행이 추가되어도 과거 Receipt 재검증은 유지된다.

## 10. 상태 모델

현재 구현 호환 상태명은 유지한다.

```text
UNINITIALIZED
-> SOURCE_LOCKED
-> REQUIREMENTS_VALIDATED
-> ARCHITECTURE_REVIEWED
-> DESIGN_REVIEWED
-> OMAKER_PLAN_APPROVED
-> CODE_REVIEWED
-> SECURITY_REVIEWED
-> REMEDIATION_READY
-> PATCHED
-> OBUILDER_BUILT
-> TESTED
-> OTESTER_VERIFIED
-> OAUDIT_VERIFIED
-> PUBLICATION_ELIGIBLE
```

OMAKER/OBUILDER/OTESTER/OAUDIT 상태명은 현재 reference implementation 호환 이름이며, 외부 제품 계약에서는 implementation plan, build, independent verification, independent audit 역할로 해석한다.

## 11. 판정

- `PASS`: 실행·증거·독립 검증 완료
- `FAIL`: 검증 기준 위반 재현
- `HOLD`: 승인·정책·실행 Gate 미충족
- `BLOCKED`: 필수 환경 또는 의존성 부재
- `NOT_RUN`: 아직 실행되지 않음
- `INCONCLUSIVE`: 증거 상충 또는 재현 불가

`NOT_RUN`, `HOLD`, `BLOCKED`, `INCONCLUSIVE`는 PASS로 승격할 수 없다.

## 12. 실행 Gate

다음이 모두 실제 증거로 확인되어야 한다.

- Product Scope·Target Registry 계약 일치
- Preflight PASS
- JDK 17 compile PASS
- JUnit 전체 PASS
- A01~A20 예상 판정 일치
- 한 Runner 내부 회귀 2회 동일
- 전체 Runner 연속 2회 PASS
- 두 실행 Source Lock·Snapshot·결정적 Evidence 동일
- Independent Verifier/Audit Receipt PASS
- Final Lock·Ledger·Final Receipt PASS
- 두 실행 읽기 전용 재검증 PASS
- Critical/High 미해결 0건

현재는 독립 제품 설계·실행 환경 기준선까지 준비되었으며 실제 실행 Gate는 `HOLD`다.
