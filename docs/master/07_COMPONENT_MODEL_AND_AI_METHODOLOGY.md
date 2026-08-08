# ONSure Component 모델·AI Agent 방법론

## 1. 목적
ONSure는 대상 시스템과 ONSure 자신을 모두 CBD(Component-Based Development) 원칙에 따라 "독립적으로 식별·계약·검증 가능한 Component" 단위로 다룬다. 또한 ONSure 자신의 Review/Verification/Improvement 실행 주체가 AI Agent이므로, 그 Agent들의 역할·권한·재현성을 별도 방법론으로 고정한다. 이 문서는 [00_ONSURE_MASTER_DESIGN_SET.md](00_ONSURE_MASTER_DESIGN_SET.md)의 프로그램 구성을 실행하는 공통 기반이며, 특히 Claude 등 AI Agent 또는 대화형 코딩("바이브 코딩")으로 생성된 프로그램을 진단·검증·수정·개선하는 ONSure의 1차 사용 시나리오를 전제로 작성한다.

## 2. Component 모델(CBD)
ONSure 자신도 이미 이 원칙을 적용받는다: `contracts/module-boundary.v1.json`과 `contracts/core-extension-boundary.v1.json`이 `onsure-core`와 `onsure-adapter-oruda`를 Provided/Forbidden Import Prefix, Required Capability(GENERIC_TARGET_REGISTRATION, SOURCE_LOCK, FIXTURE_EXECUTION, EVIDENCE_PERSISTENCE, NONFINAL_DECISION, LEARNING_CANDIDATE_GOVERNANCE)로 구분한 실제 Component Contract다. `onsure-adapter-oruda`는 `required_for_core: false`, `may_write_onsure_final_decision: false`로 명시되어 Core가 ORUDA 없이도 동작해야 한다는 [README.md](../../README.md)의 "Standalone first" 원칙을 계약으로 강제한다. 이 절 이하는 **고객의 대상 프로그램**에 같은 원칙을 적용하는 설계이며, 아직 이를 위한 `component-contract.v1.schema.json` 계약은 없다(§2.3 참조).

### 2.1 Component 식별 규칙
- 최소 단위: 독립적으로 빌드·배포·테스트 가능한 Module, Service, Package, 또는 AI Agent/Tool/Prompt 정의 단위
- 식별 신호: Build 산출물 경계, API/Interface 경계, Repository/Directory 경계, Deployment Manifest, AI Agent/Tool 정의 파일
- Component ID는 Baseline에 결속되며, Revision마다 ComponentSignature(코드 Hash + Interface Hash)로 버전을 추적한다
- OLearning은 Program Profile 생성 시 ComponentGraph의 각 노드를 Component ID에 매핑한다([02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) §3)

### 2.2 Component Contract
각 Component는 다음 계약을 가지며 [04_ARCHITECTURE_DATA_API_OLICENSE.md](04_ARCHITECTURE_DATA_API_OLICENSE.md)의 ComponentContract/ComponentInterface/ComponentVersion 엔티티에 저장한다.
- Provided Interface: 이 Component가 제공하는 API/Event/Function
- Required Interface: 이 Component가 의존하는 대상
- Data Contract: 입출력 Schema
- AI Contract(해당 시): Prompt Version, Tool Permission, RAG Source, Model Binding
- Quality Contract: 필수 Test Coverage 수준, 허용 Complexity 상한, 필수 Review Domain

### 2.3 Component 단위 Review/Verify
- Finding과 TestClaim은 File/Line뿐 아니라 Component ID에도 결속한다
- Contract 위반(순환참조, Interface Breaking Change)은 Architecture Review 최상위 우선순위로 처리하며 ComponentContract 상태는 BREAKING_CHANGE_FLAGGED로 전이한다
- Component 재사용률과 Duplicate Component(동일 기능의 반복 구현, 특히 AI 생성 코드에서 빈발)를 OReview 지표로 관리하고 ReuseLink 엔티티로 추적한다
- Provided Interface 변경 시 ReuseLink로 역조회해 같은 System 내 다른 Program까지 영향을 스캔하는 Cross-Program Impact Scan을 수행한다([04_ARCHITECTURE_DATA_API_OLICENSE.md](04_ARCHITECTURE_DATA_API_OLICENSE.md) ComponentContract). 단일 Repository 관점의 CI만으로는 이 영향을 알 수 없다는 것이 CBD를 시스템 경계 전체에 적용하는 이유다

### 2.4 Component와 Knowledge Pattern 연결
[02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md)의 OMemory가 관리하는 KnowledgePattern은 ComponentSignature 단위로 매칭되어, 동일 Contract를 가진 다른 Case의 실패 이력(익명화된 공유 Pattern)을 참고 신호로 제공한다. Pattern 매치는 판정의 유일 근거가 될 수 없으며 Confidence 보조 신호로만 사용한다([02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) §7-1 수용기준).

## 3. AI Agent 방법론
### 3.1 Agent 역할 분리와 최소권한
| Agent | 담당 | Tool 권한 |
|---|---|---|
| Learner Agent | OLearning, Program Profile 생성 | 읽기 전용 |
| Reviewer Agent | OReview Finding 생성 | 읽기 + 정적분석 도구 실행 |
| Verifier Agent | OVerification 실행 | Sandbox 내 실행 권한(격리, [04_ARCHITECTURE_DATA_API_OLICENSE.md](04_ARCHITECTURE_DATA_API_OLICENSE.md) §3 Sandbox) |
| Improver Agent | OImprovement Patch 생성 | Worktree 내 쓰기만 가능, Main/Push 권한 없음([02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) FR-COM-007/008) |

각 Agent는 서로 다른 Credential을 가지며 한 Agent가 자신 또는 동일 계열 Agent의 산출물을 무비판 승인하지 않는다. 이는 [03_OREVIEW_CODE_REVIEW_SPECIFICATION.md](03_OREVIEW_CODE_REVIEW_SPECIFICATION.md)의 Independent Pass 원칙, 그리고 OMemory 재귀학습의 "자기 참조 승인 금지" 원칙과 동일한 근거를 공유한다.

실제 `contracts/public-sdk-boundary.v1.json`이 이미 이 원칙을 외부 SDK 경계에 적용한 사례다: 공개 SDK는 7개 Operation(project.register-workspace/register/register-target, program.learn, plan.generate/approve, validation.run)만 노출하고, `FINAL_CLAIM_AUTHORITY`, `MERGE_AUTHORITY`, `PRODUCTION_GO_AUTHORITY`, Trusted Key Registry 경로, Approval Replay Ledger 경로를 공개 입력으로 받는 것 자체를 계약으로 금지한다(Raw JSON/Map Request도 금지, Typed Immutable Record만 허용). 위 표의 Agent 최소권한 설계는 이 경계를 Agent 역할 단위로 더 세분화한 것이며, Improver Agent가 Main/Push 권한이 없다는 것도 이 공개 SDK 경계와 같은 방향이다.

### 3.2 Plan-Act-Observe 루프
모든 Agent 실행은 다음 루프를 따른다.
1. Plan: ExecutionPlan/PatchPlan 범위 내에서 다음 행동을 결정한다
2. Act: 부여된 권한 범위 내에서 Tool을 호출한다(읽기/분석/실행/쓰기)
3. Observe: 결과를 Evidence로 기록하고 Stop Condition을 재평가한다
4. 반복 상한(Max Turn)과 비용 상한(Token/Credit)에 도달하면 강제 종료 후 INCONCLUSIVE로 표시한다

### 3.3 모델 라우팅과 버전 고정
- Task 유형별 모델 등급을 사전 매핑한다(예: 대규모 구조 학습=고성능 모델, VS Code Fast Review=경량 모델)
- 프로덕션 판정에 사용하는 모델 조합은 Rule Pack Digest에 고정하며 임의 시점 자동 업그레이드를 금지한다([03_OREVIEW_CODE_REVIEW_SPECIFICATION.md](03_OREVIEW_CODE_REVIEW_SPECIFICATION.md) §9-1)
- Fallback Provider 장애로 대체 모델을 사용한 경우 전환 사실을 Evidence에 표시하고 해당 실행 결과는 재현성 참고용으로 별도 표기한다

## 3.4 모델 Provider 벤더 거버넌스
[01_BUSINESS_PRODUCT_SERVICE_PLAN.md](01_BUSINESS_PRODUCT_SERVICE_PLAN.md) §12의 "AI 원가 급증" 위험에 대응하기 위해 다음을 관리한다.
- Provider당 최소 2개 이상 대체 가능한 모델을 사전 검증해 Primary/Fallback으로 등록한다(§3.3의 Fallback 전환과 연동)
- Provider 가격 변경은 원가 Meter에 자동 반영하며, Credit 교환비율에 영향을 주는 변경은 사전 고지 기간(최소 30일)을 두고 고객에게 통지한다(ONotify, [02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) §10-1)
- Provider 장애·Rate Limit 초과 시 Hard Stop보다 Fallback 전환을 우선하되, 규제산업 Enterprise 계약은 계약된 Provider 외 전환을 금지하는 옵션을 둔다
- Provider별 데이터 처리 위치와 학습 재사용 정책(고객 데이터가 Provider 자체 모델 학습에 사용되지 않음)을 계약으로 고정하고 [04_ARCHITECTURE_DATA_API_OLICENSE.md](04_ARCHITECTURE_DATA_API_OLICENSE.md) §12-1 규제산업 컴플라이언스 설계와 연결한다

## 4. 재귀학습과의 관계
Component 모델과 AI Agent 방법론은 [02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) §7-1 OMemory의 재귀학습 루프가 동작하는 전제 조건이다. ComponentSignature가 없으면 Pattern을 매칭할 대상이 없고, Agent 역할과 모델 버전이 고정되지 않으면 어떤 개정이 실제로 탐지력을 개선했는지 판정할 수 없다. 따라서:
- MissedFinding의 RCA는 항상 어떤 Agent, 어떤 모델 버전, 어떤 Rule Pack Digest에서 발생했는지 식별하는 것에서 시작한다
- 개정된 Rule/Pattern은 Golden Fixture 전체 Regression을 통과해야 프로덕션에 반영되며, 이 Regression 실행 주체도 Verifier Agent이지 개정을 제안한 Agent 자신이 아니다

## 5. Claude/바이브 코딩 산출물 특화 적용
ONSure의 1차 사용 시나리오는 Claude 등 AI Agent 또는 바이브 코딩으로 만들어진 프로그램의 진단·검증·수정·개선이다.
- OLearning은 AIProfile에 "AI 생성 여부/추정 도구" 신호를 포함한다(Commit 메시지 패턴, 코드 스타일 급변, 대량 신규 파일 생성 이력)
- OPlanning은 AI 생성 비중이 높은 Component에 검증 우선순위를 자동 상향한다
- OReview는 [03_OREVIEW_CODE_REVIEW_SPECIFICATION.md](03_OREVIEW_CODE_REVIEW_SPECIFICATION.md) §4-1의 AI/바이브 코딩 특화 진단표를 표준 점검 세트로 적용한다
- 반복 발견되는 진단 항목은 KnowledgePattern으로 승격되어 이후 Case의 Preflight 단계부터 우선순위 신호로 제공된다

## 6. 청중별 빠른 참조
### AI(Agent)가 참조해야 하는 것
- 자신의 역할별 Tool 권한표(§3.1), Plan-Act-Observe 루프(§3.2), 고정된 Rule Pack Digest와 모델 버전(§3.3)
- Finding/TestClaim 스키마는 [03_OREVIEW_CODE_REVIEW_SPECIFICATION.md](03_OREVIEW_CODE_REVIEW_SPECIFICATION.md) §5, 상태 enum은 [04_ARCHITECTURE_DATA_API_OLICENSE.md](04_ARCHITECTURE_DATA_API_OLICENSE.md) §5

### 개발자가 참조해야 하는 것
- VS Code Plan/Review/Verification/Improvement/Knowledge 화면 흐름은 [05_UI_UX_WORKFLOW_SPECIFICATION.md](05_UI_UX_WORKFLOW_SPECIFICATION.md) §6
- 위험행위 2단계 확인 목록은 [05_UI_UX_WORKFLOW_SPECIFICATION.md](05_UI_UX_WORKFLOW_SPECIFICATION.md) §7

### 운영자가 참조해야 하는 것
- Sandbox 격리·Lifecycle·Network Policy는 [04_ARCHITECTURE_DATA_API_OLICENSE.md](04_ARCHITECTURE_DATA_API_OLICENSE.md) §3 Sandbox
- MissedFinding 처리와 탐지 능력 변경 승인은 [02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) §7-1과 [06_TEST_OPERATION_IMPLEMENTATION_PLAN.md](06_TEST_OPERATION_IMPLEMENTATION_PLAN.md) 운영 Runbook
- 모델/Rule Pack 개정에 따른 회귀 실패는 인시던트 절차로 취급한다

## 7. 문서 간 참조 지도
| 주제 | 근거 문서 |
|---|---|
| 사업·상품 정의 | [01_BUSINESS_PRODUCT_SERVICE_PLAN.md](01_BUSINESS_PRODUCT_SERVICE_PLAN.md) |
| 프로그램 기능·수용기준(OMemory 포함) | [02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) |
| 리뷰 규칙·AI/바이브 코딩 진단 | [03_OREVIEW_CODE_REVIEW_SPECIFICATION.md](03_OREVIEW_CODE_REVIEW_SPECIFICATION.md) |
| 아키텍처·데이터·API·Sandbox·OLicense | [04_ARCHITECTURE_DATA_API_OLICENSE.md](04_ARCHITECTURE_DATA_API_OLICENSE.md) |
| 화면·흐름 | [05_UI_UX_WORKFLOW_SPECIFICATION.md](05_UI_UX_WORKFLOW_SPECIFICATION.md) |
| 시험·운영·구현 순서 | [06_TEST_OPERATION_IMPLEMENTATION_PLAN.md](06_TEST_OPERATION_IMPLEMENTATION_PLAN.md) |
| Component 모델·AI Agent 방법론(본 문서) | 07_COMPONENT_MODEL_AND_AI_METHODOLOGY.md |
