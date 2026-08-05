# ONSURE 제품 구현 상태 v1

## 판정

```text
DESIGN_BASELINE                 AVAILABLE
VALIDATOR_FIXTURE_SLICE         PARTIAL
FILE_EVIDENCE_AND_RECEIPTS      PARTIAL
LEARNING_GOVERNANCE             PARTIAL
PROGRAM_LEARNING                STUB
BEHAVIOR_LEARNING               STUB
OPLANNING                       DESIGN_ONLY
OREVIEW                         STUB
OIMPROVEMENT_PATCH              DESIGN_ONLY
VSCODE_EXTENSION                PARTIAL
GIT_FULL_CHAIN                  DESIGN_ONLY
WEB_COMMERCE_OLICENSE           DESIGN_ONLY
CURRENT_SOURCE_FORMAL_RUN       NOT_RUN
INDEPENDENT_OTESTER             NOT_RUN
INDEPENDENT_OAUDIT              NOT_RUN
STANDALONE_PRODUCT_FULL_CHAIN   BLOCKED
FINAL_LOCK_ALLOWED              false
```

## 권위 상태

구현 상태의 기계 판정 권위는 다음 파일이다.

- `contracts/requirements-traceability.v1.json`
- `status/implementation-matrix.v1.json`
- `status/design-conflict-register.v1.json`
- `status/missing-capability-register.v1.json`
- `status/verification-status.v1.json`

## 현재 존재하는 구현

- Generic과 선택형 ORUDA Target Adapter 구조
- Source Tree와 Immutable Reference 일부 검증
- Fixture Registry, 제한된 프로세스 Harness와 Oracle 비교
- 일부 정적 Marker와 AI Policy Marker 검사
- Finding, Failure Mode, RCA 후보와 Remediation Plan 저장
- Regression Digest와 보고서 Export
- 파일 기반 Evidence와 Manifest
- Learning Candidate부터 Applied Lock까지의 Ledger 골격
- RAG 준비 Candidate와 Target Learning 요청·사후 기록
- 중단·재개 가능한 Repository Autopilot 보조 Runner
- stage-bound validation context snapshot과 명시적 typed replay
- process birth identity에 결속된 orphan pause/resume/cancel 복구
- 결정론적 local ASK/PLAN, Provider SPI와 loopback Public SDK 후보 모듈
- 승인 exchange 결속, 프로젝트 지식 익명화·공통 후보 분리
- runtime assurance, deploy/migration preflight, Maven air-gap 및 강화된 공급망 gate
- OpenAPI 3.1 Local API·LLM Gateway와 content-free token/cost receipt chain
- 등록 프로그램 검증 결과·개선 후보·Gateway 상태를 표시하는 loopback 관리화면

## 축소·모의 구현으로 분류하는 이유

- AI Behavior 단계는 실제 모델·Agent·Tool 실행 대신 Marker를 검색한다.
- RCA는 Trace와 인과 실험 대신 Category Template을 사용한다.
- Remediation은 Patch를 만들지 않고 계획 문구만 기록한다.
- Before/After는 Finding Fingerprint 집합의 차이를 비교한다.
- 실제 VS Code Extension과 Loopback Local API가 존재하고 등록·학습·Plan·서명 승인·Validation
  경로를 연결한다. 다만 개별 Chat·Profile·Finding·Diff/Hunk·Evidence·Git UX와 Extension Host
  Full-Chain은 아직 완료되지 않았다.
- ProductPlatformE2E는 제품 Full-Chain이 아니라 Validator Fixture E2E다.
- VS Code 1.95.3 Extension Host E2E는 Node 22/Xvfb 컨테이너에서 온라인 준비 실행과
  `--network none` 오프라인 재실행 모두 종료 코드 0으로 검증했다. 이는 비최종 개발 증적이며
  설치 사용자 전체 여정 또는 독립 OTester 승인을 대체하지 않는다.
- Maven/npm vulnerability totals는 0이고 `ORUDA Labs` proprietary root license와
  third-party notice가 반영됐다. 독립 법률 검토와 고객 배포계약은 `NOT_RUN`이다.

## 이번 기준선에서 추가된 Codespace-free 보완

- 설계 권위와 문서 우선순위
- 통일된 상태 용어
- Core와 Optional Adapter 경계
- Program, Behavior, Failure, Improvement, Evidence Schema
- 전체 요구사항 추적성
- 구현·충돌·누락 대장
- Core/ORUDA Profile Preflight
- 단일 실행기
- Repository Contract Validator
- 과장된 구현 완료 표현과 오래된 상태 기준선 교정

## 남은 P0 구현

1. 실제 Repository Understanding과 Program Profile
2. 반복 실행 기반 Behavior Profile
3. Risk-based OPlanning과 승인
4. 요구사항·Architecture·Policy·Code·AI 전체 OReview
5. 재현·최초 실패·Trace 기반 RCA
6. Patch·Hunk 승인·Worktree·Rollback
7. 동일 검증 맥락의 Before/After Proof
8. Local Authenticated API의 운영·복구·Extension Host E2E 완성
9. VS Code의 개별 Chat·Profile·Finding·Evidence·Git UX 완성
10. Git Commit·Push·Draft PR Full-Chain

## 최종 실행

Codespace 또는 동등 환경은 위 Codespace-free 변경이 병합된 뒤 마지막에 사용한다.

```bash
bash scripts/onsure-one-shot.sh --profile core
```

ORUDA Adapter까지 검증할 경우에만 다음을 실행한다.

```bash
bash scripts/onsure-one-shot.sh --profile oruda
```

실행 성공은 `SELF_VALIDATION_NONFINAL`까지만 허용한다. 독립 OTester·OAudit와 실제 제품 Full-Chain이 없는 상태에서 Final PASS, FinalLock, Production GO 또는 Commercial GO를 부여하지 않는다.
