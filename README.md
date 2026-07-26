# ONSURE

ONSURE는 등록된 AI 프로그램과 일반 소프트웨어의 목적·구조·행동을 학습하고, 실제 수행 결과를 검증하며, 확인된 Finding에서 제한적 개선과 재검증을 수행하도록 설계된 독립형 제품입니다.

## 제품 원칙

- **Learn before judging** — 대상 프로그램을 이해한 뒤 검증한다.
- **Evidence over assertion** — 실행 증거 없는 PASS를 금지한다.
- **Improve from verified findings** — 임의 기능 개발이 아니라 확인된 Finding에서 개선을 시작한다.
- **Preserve intent** — 승인된 제품 목적과 정상 동작을 훼손하지 않는다.
- **Standalone first** — ORUDA 또는 특정 대상 제품 없이 Core가 실행돼야 한다.
- **Fail closed** — 누락, 충돌, NOT_RUN, PENDING과 독립성 부족을 완료로 바꾸지 않는다.

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

현재 저장소에는 범용 Fixture 실행, 일부 정적·AI Marker 검사, 파일 기반 Evidence, 검증 보고서, Learning Ledger, RAG 준비 통제와 무인 실행 보조 기능이 포함돼 있습니다.

다음은 아직 Standalone 제품 Full-Chain으로 구현되지 않았습니다.

- 실제 Program Profile과 Behavior Profile 생성
- 위험 기반 OPlanning
- 요구사항·설계·정책·코드·AI 전체 OReview
- Trace 기반 RCA 확정
- Patch·Hunk 승인·Worktree·Rollback
- 실제 VS Code Extension과 Local Authenticated API
- Commit·Push·Draft PR Git Full-Chain
- Web·Commerce·OLicense·Tenant·Sandbox 운영 기능

정확한 상태는 다음 파일을 권위로 합니다.

- [설계 권위와 적용 범위](docs/architecture/ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md)
- [전체 상세설계 Gap 검증](docs/verification/ONSURE_FULL_DESIGN_GAP_ASSESSMENT_v1.md)
- [요구사항 추적성 계약](contracts/requirements-traceability.v1.json)
- [구현 상태 Matrix](status/implementation-matrix.v1.json)
- [설계 충돌 대장](status/design-conflict-register.v1.json)
- [누락 기능 대장](status/missing-capability-register.v1.json)

## 독립 제품 정책

ONSURE Core 요구사항과 기본 실행은 ORUDA의 경로, 정책, 실행기, 저장소, 데이터 또는 프로그램 구성에 의존해서는 안 됩니다.

ORUDA 관련 코드는 선택형 Target Adapter입니다.

```text
Default: ONSure Core + Generic Target Adapter
Optional: ONSure Core + ORUDA Adapter Profile
```

## 단일 실행 명령

Codespace 이전 정적 검증:

```bash
bash scripts/onsure-one-shot.sh --static-only
```

최종 Core 실행환경 검증:

```bash
bash scripts/onsure-one-shot.sh --profile core
```

선택형 ORUDA Adapter까지 포함한 검증:

```bash
bash scripts/onsure-one-shot.sh --profile oruda
```

각 실행은 `.onsure/one-shot/<UTC timestamp>/`에 단계별 로그, Receipt, Hash와 결과를 저장합니다.

## 기준 문서

- [제품 기준선](docs/00_PRODUCT_BASELINE.md)
- [사업계획서](docs/01_BUSINESS_PLAN.md)
- [Claude형 작업환경](docs/02_CLAUDE_LIKE_WORK_ENVIRONMENT.md)
- [Git·변경관리](docs/03_GIT_AND_CHANGE_GOVERNANCE.md)
- [VS Code·Agent·Git 구현 로드맵](docs/04_IMPLEMENTATION_ROADMAP_VSCODE_AGENT_GIT.md)
- [제품 요구사항 및 수용 기준](docs/05_PRODUCT_REQUIREMENTS_AND_ACCEPTANCE.md)
- [전체 산출물 대장](docs/06_DELIVERABLES_INDEX.md)
- [핵심 아키텍처 및 상태 모델](docs/07_CORE_ARCHITECTURE_AND_STATE_MODEL.md)
- [내부 책임 분리](docs/08_INTERNAL_RESPONSIBILITY_SEPARATION.md)
- [시험·상용화·출시계획](docs/08_TEST_COMMERCIALIZATION_AND_RELEASE_PLAN.md)
- [프로그램 학습 방법론](docs/09_PROGRAM_LEARNING_METHODOLOGY.md)
- [Master Design Set](docs/master/00_ONSURE_MASTER_DESIGN_SET.md)

## 판정 상한

현재 허용 상태는 `SELF_VALIDATION_NONFINAL / HOLD`입니다.

실제 VS Code와 Web Full-Chain, 현재 Source 기준 반복 실행, 독립 OTester·OAudit와 사용자 승인 전에는 Final PASS, FinalLock, Production GO 또는 Commercial GO를 선언하지 않습니다.
