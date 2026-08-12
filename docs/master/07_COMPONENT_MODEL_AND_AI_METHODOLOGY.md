# ONSure Component 모델·AI Agent 방법론

## 1. 목적
ONSure는 대상 시스템과 ONSure 자신을 모두 CBD(Component-Based Development) 원칙에 따라 "독립적으로 식별·계약·검증 가능한 Component" 단위로 다룬다. 또한 ONSure 자신의 Review/Verification/Improvement 실행 주체가 AI Agent이므로, 그 Agent들의 역할·권한·재현성을 별도 방법론으로 고정한다. 이 문서는 [00_ONSURE_MASTER_DESIGN_SET.md](00_ONSURE_MASTER_DESIGN_SET.md)의 프로그램 구성을 실행하는 공통 기반이며, 특히 Claude 등 AI Agent 또는 대화형 코딩("바이브 코딩")으로 생성된 프로그램을 진단·검증·수정·개선하는 ONSure의 1차 사용 시나리오를 전제로 작성한다.

## 2. Component 모델(CBD)
ONSure 자신도 이미 이 원칙을 적용받는다: `contracts/module-boundary.v1.json`과 `contracts/core-extension-boundary.v1.json`이 `onsure-core`와 `onsure-adapter-oruda`를 Provided/Forbidden Import Prefix, Required Capability(GENERIC_TARGET_REGISTRATION, SOURCE_LOCK, FIXTURE_EXECUTION, EVIDENCE_PERSISTENCE, NONFINAL_DECISION, LEARNING_CANDIDATE_GOVERNANCE)로 구분한 실제 Component Contract다. `onsure-adapter-oruda`는 `required_for_core: false`, `may_write_onsure_final_decision: false`로 명시되어 Core가 ORUDA 없이도 동작해야 한다는 [README.md](../../README.md)의 "Standalone first" 원칙을 계약으로 강제한다. 이 절 이하는 고객의 대상 프로그램에 같은 원칙을 적용하는 설계다.

### 2.1 Component 식별 규칙
- 최소 단위: 독립적으로 빌드·배포·테스트 가능한 Module, Service, Package, 또는 AI Agent/Tool/Prompt 정의 단위
- 식별 신호: Build 산출물 경계, API/Interface 경계, Repository/Directory 경계, Deployment Manifest, AI Agent/Tool 정의 파일
- Component ID는 Baseline에 결속되며, Revision마다 ComponentSignature(코드 Hash + Interface Hash)로 버전을 추적한다
- OLearning은 Program Profile 생성 시 ComponentGraph의 각 노드를 Component ID에 매핑한다

### 2.2 Component Contract
각 Component는 Provided Interface, Required Interface, Data Contract, AI Contract(해당 시), Quality Contract를 가진다.

### 2.3 Component 단위 Review/Verify
Finding과 TestClaim은 File/Line뿐 아니라 Component ID에도 결속하며 Contract 위반, Breaking Change, Cross-Program Impact를 검사한다.

### 2.4 Component와 Knowledge Pattern 연결
KnowledgePattern은 ComponentSignature 단위로 매칭하며 Pattern match는 보조신호이지 판정의 유일 근거가 될 수 없다.

## 3. AI Agent 방법론
### 3.1 Agent 역할 분리와 최소권한
| Agent | 담당 | Tool 권한 |
|---|---|---|
| Learner Agent | OLearning, Program Profile 생성 | 읽기 전용 |
| Reviewer Agent | OReview Finding 생성 | 읽기 + 정적분석 도구 실행 |
| Verifier Agent | OVerification 실행 | Sandbox 내 실행 권한 |
| Improver Agent | OImprovement Patch 생성 | Worktree 내 쓰기만 가능, Main/Push 권한 없음 |

각 Agent는 서로 다른 Credential을 가지며 한 Agent가 자신 또는 동일 계열 Agent의 산출물을 무비판 승인하지 않는다. `contracts/public-sdk-boundary.v1.json`의 FINAL_CLAIM/MERGE/PRODUCTION_GO Authority 공개 금지 원칙을 Agent 역할에도 적용한다.

### 3.2 Plan-Act-Observe 루프
1. Plan
2. Act
3. Observe
4. 반복/비용 상한 도달 시 INCONCLUSIVE

### 3.3 모델 라우팅과 버전 고정
- Task 유형별 모델 등급을 사전 매핑한다.
- 프로덕션 판정 모델 조합은 Rule Pack Digest에 고정한다.
- Fallback 사용은 Evidence에 명시한다.

### 3.4 모델 Provider 벤더 거버넌스
Provider별 Primary/Fallback, 가격/원가, 데이터 처리 위치, 학습 재사용 정책, 규제산업 전환 제한을 관리한다.

## 4. 재귀학습과의 관계
MissedFinding RCA는 Agent/model/rule-pack context를 식별하고, 개정 Rule/Pattern은 독립 회귀검증을 통과해야 한다.

## 5. Claude/바이브 코딩 산출물 특화 적용
AI 생성 추정 신호, 대량 신규 파일, 구조 불일치, hallucinated dependency, 과도한 Tool 권한 등을 우선 검증한다.

## 6. 청중별 빠른 참조
AI는 역할/권한/고정 모델/RulePack을, 개발자는 UI 위험승인 흐름을, 운영자는 Sandbox/MissedFinding/Rule regression을 우선 참조한다.

## 7. 문서 간 참조 지도
| 주제 | 근거 문서 |
|---|---|
| 사업·상품 정의 | 01_BUSINESS_PRODUCT_SERVICE_PLAN.md |
| 프로그램 기능 | 02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md |
| 리뷰 규칙 | 03_OREVIEW_CODE_REVIEW_SPECIFICATION.md |
| 아키텍처·API | 04_ARCHITECTURE_DATA_API_OLICENSE.md |
| 화면·흐름 | 05_UI_UX_WORKFLOW_SPECIFICATION.md |
| 시험·운영 | 06_TEST_OPERATION_IMPLEMENTATION_PLAN.md |

## 8. AI·Agent 메타검증 및 Truth Assurance 방법론
### 8.1 독립성 6축
Execution / Principal / Implementation / Oracle / Discovery / Knowledge Independence를 별도 기록한다. `different_run` 또는 `different_model` 하나로 Independent PASS를 발급하지 않는다.

### 8.2 Ground Truth 등급
GT0_UNKNOWN, GT1_CORROBORATED, GT2_INDEPENDENT_TOOL, GT3_EXECUTABLE_ORACLE, GT4_EXPERT_VERIFIED, GT5_REAL_WORLD_OBSERVED를 사용한다. Critical Claim은 GT0/GT1만으로 PROVEN 불가다.

### 8.3 Ground Truth Provenance와 Epoch
ground_truth_id, source_type, requirement_digest, oracle_digest, created_by/reviewed_by, validity, evidence_refs, scope, epoch를 가진다.

### 8.4 Memory-Blind Independent Review
memory_blind=true lane에서는 이전 Finding, RiskScore, previous verdict 접근을 기술적으로 차단한다. 충돌은 DISAGREEMENT_HOLD다.

### 8.5 Semantic Dataset Separation
byte hash뿐 아니라 defect_class, semantic_family_id, implementation_family_id, source_project_family_id, generation_method를 분리한다.

### 8.6 Oracle Coupling Gate
Oracle의 target_code_dependency를 NONE|PARTIAL|SHARED로 기록한다. Critical Final Oracle은 원칙적으로 NONE이다.

### 8.7 Rule/Detector 변경 위험등급
NEW_DETECTOR, DETECTOR_TIGHTENING, DETECTOR_WEAKENING, DETECTOR_REMOVAL, ORACLE_CHANGE, SEVERITY_POLICY_CHANGE, COVERAGE_POLICY_CHANGE를 구분하고 weakening/removal/oracle change에 최고 위험 Gate를 적용한다.

### 8.8 Learning Regression Guard
평균 성능 증가로 Critical Recall 감소를 숨기지 않는다.

### 8.9 Counterexample Search
positive reproduction 외에 counterexample search, domain transfer, negative control, reproduction diversity를 요구한다.

### 8.10 AI 금지규칙
이전 PASS를 Ground Truth로 사용, Memory Pattern을 Expected Result로 승격, Cross-Model Agreement를 사실증명으로 표현, Hidden answer 반복 피드백, Human approval을 사실검증 대체, 자기 Patch/Rule/Oracle self-approval, 다수결로 낮은 confidence 세탁을 금지한다.

### 8.11 개발 구현규칙
Independent Lane은 동일 service method 단순 재호출 금지, verifier/oracle implementation digest 결속, memory-blind 기술 차단, dataset semantic family 보관, weakening API 권한 강화, Oracle coupling 자동표기를 요구한다.

### 8.12 운영체크
principal/key/environment 독립성, hidden set 접근통제, 과거 인증 영향분석, Validator RCA, Critical Recall regression, memory-blind disagreement HOLD를 확인한다.

### 8.13 수용기준
Critical Final Claim은 독립성 축과 GT 등급을 명시해야 하며 hidden/training separation 위반 시 Qualification 전체를 무효화한다.

## 9. AI Runtime Identity 및 Behavior Population (2026-08-12 정본 흡수)
본 절은 `semantic-assurance/34_AI_RUNTIME_MULTI_AGENT_AND_ONSURE_META_ASSURANCE_EXTENSION.md`와 32/37/38의 AI 설계를 정본에 흡수한다.

### 9.1 AI Runtime Identity
AI Component는 최소 다음 identity를 가진다.
- provider/model/version/deployment identity
- model weights digest 가능 시 또는 provider attestation digest
- system/developer prompt bundle digest
- dynamic prompt assembler/version
- tool registry digest + tool schema/version + effect class
- memory store identity, tenant scope, retention epoch
- RAG corpus/index/embedding/chunking/retrieval-policy digest
- safety/policy profile digest
- external provider contract/version

위 identity 중 Critical claim에 영향을 주는 값이 바뀌면 해당 AI Assurance는 자동 currentness 재평가 대상이다.

### 9.2 Behavior Population
비결정 AI 검증 결과는 단일 run이 아니라 population 객체로 다룬다.
필수 항목:
- exact scenario population digest
- sample_size
- seeds 또는 seed-generation profile
- temperature/top_p/other sampling config
- pass/fail/critical-failure counts
- excluded run ids와 exclusion reason
- confidence method/version
- confidence interval/bound
- target/runtime/prompt/RAG/tool/model epochs

sample selection 또는 exclusion을 결과 관찰 후 바꾸면 population은 INVALIDATED다.

### 9.3 Statistical Claim
Critical failure 확률/상한 같은 통계 Claim은 `sample_size + failure count + confidence method + confidence level + population identity` 없이 표시하지 않는다. `0 observed failures`를 `failure probability=0`으로 표현하지 않는다.

### 9.4 AI Drift
- MODEL_DRIFT
- SYSTEM_PROMPT_DRIFT
- TOOL_REGISTRY_DRIFT
- MEMORY_POLICY_DRIFT
- RAG_CORPUS_DRIFT
- EMBEDDING_MODEL_DRIFT
- RETRIEVAL_POLICY_DRIFT
- PROVIDER_POLICY_DRIFT
각 drift는 affected_claims와 currentness consequence를 가진다.

## 10. Tool Calling / External Effect Assurance
- 모든 Tool은 effect class(READ_ONLY|LOCAL_MUTATION|EXTERNAL_REVERSIBLE|EXTERNAL_IRREVERSIBLE)와 required authority를 가진다.
- Tool schema/version/purpose/resource/parameter digest를 action intent에 결속한다.
- AI가 tool name만 승인받고 parameter/resource를 바꾸는 것을 금지한다.
- irreversible effect는 human/enterprise policy approval과 read-back 또는 external receipt를 요구한다.
- tool output은 untrusted input으로 취급하고 prompt injection/authority hallucination을 검증한다.

## 11. Memory Assurance
- memory namespace는 organization/tenant/subject에 결속한다.
- cross-tenant retrieval은 항상 P0다.
- memory poisoning, stale memory, unauthorized persistence, delete/retention failure를 시험한다.
- memory-aware 결과가 memory-blind 결과보다 강한 Assurance를 자동 획득하지 않는다.
- prior verdict와 customer reputation을 Ground Truth feature로 쓰지 않는다.

## 12. RAG Assurance
RAG는 corpus→document→chunk→embedding→index→retrieval→generation의 lineage를 유지한다.
필수 검증:
- ACL preservation
- corpus/index currentness
- poisoned/adversarial document
- source/citation binding
- deleted/revoked document 제거
- embedding/chunking change impact
- tenant-aware retrieval
- insufficient retrieval evidence에서 confident factual claim 억제

## 13. Multi-Agent Assurance
### 13.1 Agent Graph
각 agent identity/role/tool authority/shared memory/delegation edge/message contract를 기록한다.

### 13.2 위험
- cyclic delegation
- privilege amplification
- collusion/common-mode blind spot
- shared-memory contamination
- majority-vote false ground truth
- coordinator silently omitting dissenting agent

### 13.3 합성
Multi-agent agreement는 CORROBORATION이며 independent ground truth가 아니다. 동일 model/provider/prompt/oracle lineage를 공유하는 여러 agent는 독립 verifier 여러 명으로 세지 않는다.

## 14. Provider Drift 및 Fallback
- provider alias 동일성만으로 같은 model로 간주하지 않는다.
- fallback은 사전 qualification scope 내에서만 허용한다.
- 규제/계약 profile이 특정 provider/model을 고정하면 fallback 대신 HOLD/BLOCKED한다.
- provider drift 후 과거 BehaviorPopulation/Certificate currentness를 영향분석한다.

## 15. Plugin/Adapter AI Trust
AI 관련 Plugin/Adapter는 signed artifact, privilege manifest, qualification scope, supported archetype/version, sandbox policy를 가진다. plugin parser가 unsupported syntax/feature를 silently drop하면 PARTIAL/NOT_PROVEN으로 내려간다.

## 16. ONSure Meta-Assurance 정본
### 16.1 ONSureReleaseQualification
- onsure build/release digest
- validator set/oracle set/adapter set digest
- benchmark/fixture set digest
- hidden corpus generation
- TCB/build provenance/SBOM
- target archetype qualification map
- independent verifier identities
- known limitations
- valid_from/valid_until
- requalification triggers

### 16.2 Self-Validation Ceiling
ONSure의 unit/integration/meta tests는 qualification input일 뿐 최종 qualification authority가 아니다. 동일 implementation/knowledge chain의 여러 self-run을 independent로 세지 않는다.

### 16.3 Archetype Scope
Qualification은 Java/Spring, Web, DB, Kubernetes, AI Agent 등 target archetype 단위다. 미검증 archetype에 global QUALIFIED를 발급하지 않는다.

### 16.4 Requalification Trigger
Core validator/oracle/adapter, critical fixture/benchmark, trust root, security boundary, runtime major dependency, MissedFinding proving blind spot, canonicalization/profile 변경은 requalification trigger다.

## 17. AI Methodology 통합 수용기준
- AI runtime identity가 currentness/evidence/final에 결속된다.
- nondeterministic result가 단일 favorable run으로 축소되지 않는다.
- Tool/Memory/RAG/Provider drift가 영향을 받은 Claim에 전파된다.
- multi-agent agreement를 ground truth/independence로 오인하지 않는다.
- ONSure self-validation만으로 ONSure를 QUALIFIED로 승격하지 않는다.
- AI/Agent/Meta-Assurance 관련 `FR-META`가 본 문서 또는 machine-method registry에 trace되어야 한다.
