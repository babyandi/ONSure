# ONSURE

ONSURE는 등록된 AI 프로그램과 일반 소프트웨어를 학습·검증하고, 검증된 Finding에 한정해 승인형 개선과 Git 전달을 수행하도록 설계된 독립형 제품입니다.

## 제품 원칙

- **Evidence over assertion** — 실행 증거 없는 PASS를 금지합니다.
- **Fail closed** — 누락·충돌·NOT_RUN·HOLD·독립성 부족을 완료로 바꾸지 않습니다.
- **Standalone first** — ORUDA 없이 Core가 빌드·시험·실행돼야 합니다.
- **Fixed trust root** — 승인 검증의 Key Registry와 Replay Ledger를 요청자가 선택할 수 없습니다.
- **Transition revalidation** — Commit 때 유효했던 승인도 Push·Draft PR 직전에 다시 검증합니다.
- **Bounded execution** — 자식 프로세스는 출력 Drain·크기 제한·Wall-clock Timeout·Process-tree 종료를 함께 적용합니다.

## 검증 실행 정책

ONSURE 저장소는 **GitHub Actions를 사용하지 않습니다.**

- `.github/workflows/*.yml`과 `.yaml`은 금지합니다.
- 검증은 저장소 내부 로컬 실행기로만 수행합니다.
- 실행 결과는 `.onsure/` 아래 Receipt·로그·Hash로 보관합니다.
- 과거 원격 자동화 결과는 감사 이력일 뿐 현재 소스의 PASS 증적이 아닙니다.
- 로컬 자체검증은 `SELF_VALIDATION_NONFINAL` 상한을 넘을 수 없습니다.

## 현재 구현된 주요 경계

- Core·CLI·Loopback Local API·Optional ORUDA Adapter 모듈 경계
- Workspace·Project·Target 등록 Workflow
- Program/Behavior Profile 후보와 Observation Receipt
- 위험 기반 Plan, 전체·부분 서명 승인, 승인되지 않은 Stage 실행 차단
- 원본 Plan·승인 Plan·서명 Receipt·고정 Trust Root·소비 Ledger의 Approval Bundle 검증
- OReview와 Evidence-based RCA
- 승인형 Patch·Worktree·Rollback·Improvement Proof
- Commit 승인과 Push·Draft PR 전 승인 만료·Identity·서명·소비 상태 재검증
- Source Identity·Receipt·Ledger·Replay·Cross-process File Lock
- Rootless Bubblewrap Sandbox와 적대 Fixture
- Bounded child-process runner
- VS Code Extension·VSIX 및 OLicense·Service Case 상태 코어

## 이번 메타감사에서 확인된 검출기 사각지대

이전 검증은 28개 대분류 기능군, 파일·클래스·테스트 존재, 후보 Requirement 형식을 주로 확인했습니다. 그 결과 다음 결함을 놓쳤습니다.

- 대분류 `PARTIAL` 안에 숨은 증분 학습·필수 View·Pause/Resume·Public SDK 누락
- 설계가 요구한 부분 승인을 전체 승인으로만 제한한 구현
- 승인 Plan JSON만으로 Engine에 진입할 수 있던 서명 Bundle 우회
- 요청자가 Trusted Key Registry·Replay Ledger 경로를 바꿀 수 있던 Trust-root substitution
- `waitFor(timeout)`가 있어도 출력 읽기 순서 때문에 Timeout에 도달하지 못하는 Process hang
- Commit 때 검증한 승인을 Push 시점에 재검증하지 않는 상태전이 누락
- Core 기능이 존재하지만 CLI·Local API·VS Code 제품 표면에 연결되지 않은 경로

이를 방지하기 위해 다음 권위 검사를 추가했습니다.

```text
28개 설계·프로세스·데이터 실패주입
10개 원자 Requirement 실패주입
6개 Actions 금지·로컬 자동화 실패주입
10개 Verification Claim 실패주입
10개 제품 하위 Requirement 실패주입
6개 Workflow Surface 실패주입
24개 Critical Callpath 실패주입
합계 94개
```

권위 파일:

- `status/product-subrequirement-coverage.v1.json` — 38개 제품 하위 요구
- `scripts/validate-product-subrequirements.py`
- `scripts/validate-workflow-surface-parity.py` — 39개 Workflow·3개 제품 표면
- `scripts/validate-critical-callpaths.py`
- `contracts/validation-case-registry.v1.json` — 성공·실패·공격 사례 단일 권위 목록
- `scripts/validate-validation-case-registry.py` — 0건 실행·누락·Skip·Failure/Error 차단 및 실행 Receipt 생성
- `status/omission-detection-status.v1.json`
- `status/verification-status.v1.json`
- `status/remaining-work-register.v1.json`

## 명시적으로 미완료인 주요 기능

- 변경분 기반 증분 Program Learning
- Tool Contract 내용 분석과 실행 로그 인벤토리
- VS Code의 Chat·Profile·Learning·Verification·Findings·Improvement·Evidence·Git/PR 개별 View
- Ask·Plan·Act·Autopilot 모드
- VS Code 부분 Plan 승인 및 파일·Hunk 승인 UX
- 장기 작업 Checkpoint·Cancel·Pause·Resume·재시작 복구
- Provider·Model 교체성
- Token·비용·데이터 전송 범위 가시화
- 외부 제품용 Public SDK
- Identity·RBAC·Cross-tenant 격리
- Approval Replay Ledger의 외부 Anchor
- 제품 SBOM·취약점·라이선스 Pack
- 성능·장애·복구·운영·배포 Pack
- 실제 Payment Provider와 Production Model Telemetry
- 독립 OTester·OAudit와 Human Acceptance

## 로컬 단일 검증

정적 비최종 Gate:

```bash
bash scripts/onsure-local-gate.sh --mode static --profile core
```

Java 17·Maven·Sandbox·VSIX 포함 전체 로컬 비최종 Gate:

```bash
bash scripts/onsure-local-gate.sh --mode full --profile core
```

최종 단계 Source-bound One-Shot:

```bash
bash scripts/onsure-final-stage.sh --profile core
```

## 현재 판정 상한

현재 브랜치의 새 Python 실패주입과 독립형 Java 17 호환 Smoke 일부는 수행됐지만, **현재 브랜치 전체 Java 17 Maven/JUnit·Modular·Sandbox·VSIX Local Gate는 아직 실행되지 않았습니다.**

```text
Assurance      SELF_VALIDATION_NONFINAL
FinalLock      false
Production GO  false
Commercial GO  false
```
