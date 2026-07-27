# ONSURE

ONSURE는 등록된 AI 프로그램과 일반 소프트웨어의 목적·구조·행동을 학습하고, 실제 수행 결과를 검증하며, 확인된 Finding에서 제한적 개선과 재검증을 수행하도록 설계된 독립형 제품입니다.

## 제품 원칙

- **Learn before judging** — 대상 프로그램을 이해한 뒤 검증한다.
- **Evidence over assertion** — 실행 증거 없는 PASS를 금지한다.
- **Improve from verified findings** — 임의 기능 개발이 아니라 확인된 Finding에서 개선을 시작한다.
- **Preserve intent** — 승인된 제품 목적과 정상 동작을 훼손하지 않는다.
- **Standalone first** — ORUDA 또는 특정 대상 제품 없이 Core가 빌드·시험·실행돼야 한다.
- **Fail closed** — 누락, 충돌, NOT_RUN, PENDING과 독립성 부족을 완료로 바꾸지 않는다.

## 검증 실행 정책

ONSURE 저장소는 **GitHub Actions를 사용하지 않습니다.**

- `.github/workflows/*.yml`과 `.yaml`은 금지됩니다.
- 검증은 저장소 내부의 로컬 실행기로만 수행합니다.
- 실행 결과는 `.onsure/` 아래 Receipt·로그·Hash로 보관합니다.
- 과거 원격 자동화 결과는 감사 이력일 뿐 현재 소스의 PASS 증적이 아닙니다.
- 로컬 자체검증은 `SELF_VALIDATION_NONFINAL` 상한을 넘을 수 없습니다.

## 제품 목표 흐름

```text
Project registration
→ Program and Behavior Learning
→ Program Profile
→ Risk-based Plan and Approval
→ Review and Verification
→ Finding and evidence-based RCA
→ Approved Patch in isolated Worktree
→ Regression and Before/After Proof
→ Commit, Push and Draft PR
→ Evidence and Memory
→ Restart-safe restoration
```

## 현재 구현 경계

현재 기준선에는 다음이 포함됩니다.

- Core·CLI·Local API·Optional ORUDA Adapter 모듈 경계
- Program/Behavior Profile 후보와 Observation Receipt
- 위험 기반 Plan과 서명 승인
- OReview와 Evidence-based RCA
- 승인형 Patch·Worktree·Rollback·Improvement Proof·Git 경계
- Source Identity·Receipt·Ledger·Replay·File Lock
- Rootless Bubblewrap Sandbox와 적대 Fixture
- Loopback Local API와 VS Code Extension·VSIX
- OLicense와 Service Case 상태 코어
- 설계·프로세스·데이터·검증 Claim 누락 감지

다음은 계속 미완료 또는 비최종입니다.

- 원자 Requirement 권위 화해 100%
- 실제 VS Code Extension Host 사용자 Full-Chain
- 실제 원격 Push·Draft PR 제품 Delivery
- 실제 Payment·Refund Provider 연동
- Production Model·Prompt·Tool·RAG 직접 Telemetry
- Tenant Identity·RBAC·Cross-tenant 제품 적대시험
- SBOM·취약점·라이선스 Pack
- 성능·부하·장애·복구·운영·배포
- 독립 OTester·OAudit와 Human Acceptance

정확한 상태는 다음 파일을 권위로 합니다.

- [설계 권위와 적용 범위](docs/architecture/ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md)
- [전체 상세설계 Gap 검증](docs/verification/ONSURE_FULL_DESIGN_GAP_ASSESSMENT_v1.md)
- [병합 후 자기검증](docs/verification/ONSURE_POST_MERGE_SELF_AUDIT_v1.md)
- [요구사항 추적성 계약](contracts/requirements-traceability.v1.json)
- [구현 상태 Matrix](status/implementation-matrix.v1.json)
- [검증 상태](status/verification-status.v1.json)
- [남은 작업 대장](status/remaining-work-register.v1.json)

## 독립 제품 정책

```text
Default: ONSure Core + Generic Target Adapter
Optional: ONSure Core + ORUDA Adapter Module
```

ONSURE Core 요구사항과 기본 실행은 ORUDA의 경로, 정책, 실행기, 저장소, 데이터 또는 프로그램 구성에 의존해서는 안 됩니다.

## 로컬 단일 실행 명령

일상 정적 검증:

```bash
bash scripts/onsure-local-gate.sh --mode static --profile core
```

Java 17·모듈·Sandbox·VSIX를 포함한 전체 로컬 비최종 검증:

```bash
bash scripts/onsure-local-gate.sh --mode full --profile core
```

Optional ORUDA Adapter까지 포함:

```bash
bash scripts/onsure-local-gate.sh --mode full --profile oruda
```

최종 단계 One-Shot과 증적 고정:

```bash
bash scripts/onsure-final-stage.sh --profile core
```

로컬 Gate는 `.onsure/local-gate/<UTC timestamp>-<pid>/`에 결과를 저장합니다. 최종 단계는 `.onsure/final-stage/`에 별도 증적을 생성합니다.

## 판정 상한

현재 허용 상태는 `SELF_VALIDATION_NONFINAL / BLOCKED`입니다.

원자 Traceability, 실제 제품 Full-Chain, 현재 Source 기준 Final One-Shot, 독립 OTester·OAudit와 사용자 승인 전에는 Final PASS, FinalLock, Production GO 또는 Commercial GO를 선언하지 않습니다.
