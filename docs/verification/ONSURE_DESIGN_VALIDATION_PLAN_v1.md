# ONSURE Design Validation Plan v1

## 1. 목적

ONSURE이 특정 프로젝트 전용 도구가 아니라 AI 프로그램과 일반 프로그램을 검증하는 독립 상용 Validation Platform으로 동작하는지 검증한다. 문서 존재가 아니라 제품 독립성, Target Adapter 경계, 상태·권한·Receipt·Hash·Failure Mode·RCA·Fixture·Harness·Oracle·Regression Lock·리포트·재검증의 모순을 반박 중심으로 찾는다.

## 2. 검증 대상 계층

### Layer A — ONSURE 자체 검증

- Generic Validator Core
- Product Scope·Target Registry·Target Adapter 계약
- Receipt·Source Lock·Final Lock·Ledger
- Failure Mode·RCA·Fixture·Harness·Oracle·Regression Lock 구조
- Independent Verifier/Audit 경계
- Report·Remediation·Revalidation 계약

### Layer B — 범용 Target SDK 검증

- 서로 다른 언어·프레임워크·실행 방식의 샘플 Target
- Target 제거 후 Core 독립 실행
- Target Adapter가 Policy·Oracle·Final Decision을 침범하지 않는지 검증
- Embedded Agent/Module과 Standalone 결과의 등가성 검증

### Layer C — ORUDA 1호 Target 검증

- ORUDA를 `EXTERNAL_VALIDATION_TARGET`으로 등록
- ORUDA Target Adapter와 Target Fixture Pack 사용
- ORUDA 내부 claim을 독립 재계산
- ORUDA 장애·제거가 ONSURE Core에 영향을 주지 않는지 확인
- 장기 Embedded Agent/Module은 별도 단계에서 검증

Layer A가 PASS하기 전에 Layer C 결과를 ONSURE 제품 완성 근거로 사용할 수 없다.

## 3. 공식 환경

- GitHub Actions와 외부 CI는 공식 판정에 사용하지 않는다.
- GitHub는 immutable source와 승인 계약 저장소다.
- JDK 17과 Maven을 사용하는 clean local worktree에서 실행한다.
- Product Scope, Target Registry, Adapter contract, commit SHA, tracked tree, policy set, Fixture 계약, Security Finding Register를 고정한다.
- 실행 결과는 서명 Receipt·Final Lock·append-only Ledger·Final Receipt로 남긴다.

## 4. Layer A 공식 검증 순서

```text
Preflight
-> Product Scope / Target Registry / Adapter Contract Consistency
-> Source Lock
-> Fixture Contract Snapshot
-> Security Findings Snapshot / blocking gate
-> Compile / Unit / A01~A20 regression-1
-> Clean target
-> Compile / Unit / A01~A20 regression-2
-> Summary·Class Hash·Fixture Report equivalence
-> Evidence Manifest validation
-> Independent Verifier separate JVM
-> Independent Audit separate JVM
-> Final Lock
-> Receipt Ledger
-> Final Receipt
-> Current repository source re-verification
```

단일 Runner 내부 회귀 2회와 별개로 전체 Runner 자체도 연속 2회 실행한다. 두 전체 실행은 별도 Run Context·키·Receipt를 사용하면서 Source Lock·Snapshot·결정적 Evidence가 같아야 한다.

## 5. 정상 시나리오

| ID | 시나리오 | 기대 결과 |
|---|---|---|
| N01 | Product Scope 계약 확인 | 독립 상용 플랫폼·AI/일반 프로그램 범위 확인 |
| N02 | ORUDA 설계 재료 관계 확인 | `DESIGN_INPUT_ONLY`, Runtime·Authority dependency false |
| N03 | ORUDA 1호 Target Registry 확인 | `PLANNED_FIRST_VALIDATION_TARGET`·External 관계 |
| N04 | Target Adapter 권한 확인 | Policy·Oracle·Independent Decision override 금지 |
| N05 | immutable commit의 clean source | Source Lock과 policy digest 생성 |
| N06 | 일반 Receipt 계약 직렬화 | JSON Schema와 Java record 필드 정확히 일치 |
| N07 | Implementation Planner와 독립 승인 | 자기승인 차단, plan digest 결속 |
| N08 | Isolated Builder 재현 빌드 | plan·source·artifact·SBOM·provenance 결속 |
| N09 | 보안 Finding Register 완료 | open Critical/High 0건일 때만 통과 |
| N10 | A01~A20 Snapshot 실행 | 예상 Decision·Reason과 실제 결과 일치 |
| N11 | Independent Verifier 별도 JVM | regression Evidence Manifest digest에 직접 결속 |
| N12 | Independent Audit 별도 JVM | Verifier Receipt digest에 직접 결속 |
| N13 | 후속 Ledger append | 과거 Final Receipt의 per-run head 검증 유지 |
| N14 | 전체 Runner 연속 2회 | Source Lock·Snapshot·Summary·Class·Fixture Report 동일 |
| N15 | Target Adapter 제거 | ONSURE Core 실행·계약 검증 유지 |
| N16 | Embedded result export | portable Receipt로 Standalone 재검증 가능 |
| N17 | Validation Report 생성 | 중대한 결론이 Evidence·Finding·RCA·Receipt에 연결 |

## 6. 적대 시나리오

기존 A01~A20과 함께 범용 제품 적대 시나리오를 확장한다.

### 기존 실행·증거 적대 시나리오

- 단계 건너뛰기
- Mutable ref·Source hash 누락
- 자기승인
- 계획·Artifact 바꿔치기
- 미신고 파일 변경
- 미고정 의존성
- 무허가 네트워크
- Receipt·Permit replay
- Runtime/Verifier/Audit key 공유
- Runtime의 독립 판정 위조
- NOT_RUN의 PASS 위조
- 미해결 Critical/High
- 회귀 실행 재사용·결과 불일치
- Policy drift
- 만료 Permit
- 서명 없는 Update
- Rollback 증거 누락
- 외부·내장 구현 불일치

### 범용 제품·Target 적대 시나리오

- Target Adapter가 ONSURE Policy를 덮어씀
- Target self-reported PASS를 Final PASS로 승격
- Target가 Finding severity를 낮춤
- ORUDA Runtime이 없으면 ONSURE Core가 시작되지 않음
- Target-specific Fixture가 Generic Engine 계약을 변경
- Embedded Module이 portable Receipt를 내보내지 않음
- Embedded Module과 Standalone 결과가 다름
- Target 제거 후 다른 Target 검증이 실패
- 일반 프로그램 검증 경로가 AI 전용 가정 때문에 실패
- AI 프로그램 검증에서 prompt·tool·authority evidence 누락
- 리포트 결론이 Evidence보다 높은 판정을 주장
- RCA 없이 Finding을 CLOSED 처리
- Fixture·Oracle 없이 Failure Mode를 해결됨으로 처리

## 7. 독립성

### Independent Verifier

- 별도 process/service·별도 key
- Run Context와 role policy/scope 결속
- runtime result·artifact·Fixture·Oracle 결과 재계산
- Target self-report를 공식 결과로 신뢰하지 않음

### Independent Audit

- 별도 process/service·별도 key
- Target·Source·Policy·Fixture·Harness·Oracle·Finding·RCA·Patch·Regression·Report Receipt 계보 검증
- decision ceiling 적용

### Target Independence

- Target가 없어도 Core 실행
- Adapter 삭제가 Core·다른 Adapter를 손상시키지 않음
- ORUDA는 첫 Target이지만 Product Authority가 아님
- Embedded 배포에서도 Standalone 재검증 가능

## 8. ORUDA 1호 Target 검증 순서

```text
Layer A PASS
-> ORUDA Target Profile 등록
-> ORUDA immutable source / policy / runtime inventory
-> ORUDA Adapter Receipt
-> ORUDA Failure Mode Registry import and generalization check
-> ORUDA Target Fixture Pack lock
-> Harness / Oracle execution
-> Finding / RCA / Remediation Report
-> Full regression twice
-> Independent Verifier / Audit
-> ORUDA Final Validation Report
```

ORUDA Adaptive Validation Master의 RCA·Failure Mode·Fixture·Harness·Oracle·Receipt·Regression Lock는 다음 조건으로만 사용한다.

- ONSURE 범용 계약으로 변환
- Target-specific 요소는 ORUDA Target Pack으로 분리
- ORUDA 자체 판정을 독립 재계산
- Core Runtime dependency false

## 9. 장기 Embedded 검증

ONSURE Agent 또는 Validation Module을 ORUDA 내부에 이식할 때 다음을 검증한다.

- embedded component hash와 version lock
- ORUDA key와 ONSURE key 분리
- portable Receipt export
- Standalone ONSURE 재검증
- Embedded/Standalone 동일 locked input 결과 비교
- ORUDA 장애 시 fail-closed 또는 evidence-preserving degradation
- ONSURE 독립 배포판 유지

## 10. PASS 조건

### Layer A

- Product Scope·Target Registry·Target Adapter 계약 PASS
- ONSURE이 ORUDA 없이 실행 가능
- Preflight PASS
- 현재 checkout commit·tracked tree·policy가 Source Lock과 일치
- Security Finding Gate PASS, open Critical/High 0건
- JDK 17 compile과 JUnit 전체 PASS
- A01~A20 예상 Decision·Reason 일치
- 단일 Runner 내부 회귀 2회 동일
- 전체 Runner 연속 2회 PASS
- Independent Verifier/Audit Receipt PASS
- Final Lock·전체 Ledger chain·per-run binding PASS
- Final Receipt 생성·자기검증 PASS
- 두 실행 읽기 전용 재검증 PASS

### Layer B

- 최소 2개 이상의 서로 다른 Target profile 검증
- Target Adapter 권한 침범 0건
- Target 제거 후 Core 정상
- 일반 프로그램과 AI 프로그램 경로 모두 PASS
- portable Receipt와 embedded/standalone equivalence PASS

### Layer C — ORUDA

- ORUDA 1호 Target 등록 PASS
- ORUDA Target Pack 전체 실행 PASS
- ORUDA claim 독립 재계산 PASS
- ORUDA 장애와 ONSURE Core 독립성 PASS
- ORUDA Final Validation Report와 Receipt chain PASS

미실행, 증거 누락, 외부 CI 상태는 PASS 근거가 아니다.

## 11. 현재 판정

```text
Product Scope / Target Contracts  IMPLEMENTED
Generic Platform Architecture     IMPLEMENTED
Static PR Review                  COMPLETED
Layer A JDK17/Maven Execution     NOT_RUN
Layer B Multi-target Validation   NOT_RUN
Layer C ORUDA First Target        NOT_REGISTERED_FOR_EXECUTION
Final Gate                        HOLD
PR                                DRAFT
```

현재 Issue #4는 Layer A 실행 Gate를 다룬다. Layer A 실제 증거가 없으므로 PR #2를 Ready 또는 Merge로 전환하지 않는다.
