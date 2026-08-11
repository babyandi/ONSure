# ONSure Master Design Set

## 1. 문서 목적
이 문서는 ONSure의 사업·제품·서비스·프로그램·라이선스·아키텍처·개발·리뷰·검증·개선·시험·운영을 하나의 개발 기준선으로 통합한다. ONSure는 ORUDA의 하위 프로그램이 아니라 독립 판매 제품이며, ORUDA/OLicense는 ONSure의 라이선스 생성·계약·Entitlement·Credit·감사 원장을 담당하는 중앙 상용 라이선스 서비스로 연계된다.

## 2. 제품 정의
ONSure는 AI가 작성하거나 AI를 포함한 소프트웨어를 대상으로 다음 전 과정을 수행하는 AI Software Engineering Assurance Platform이다. 핵심은 **학습(Learn)**과 **검증(Verify)**이며, 이 둘을 축으로 Review·Improve까지 이어진다.

Understand → Plan → Review → Verify → Improve → Prove → Remember

- Understand: 프로그램, 요구사항, 정책, 구조, AI 구성, 실행 특성을 학습한다.
- Plan: 검토·검증·개선 범위와 순서를 수립한다.
- Review: 요구사항부터 코드와 테스트까지 변경 전후의 적정성을 검토한다.
- Verify: 실제 요구사항·정책·보안·동작·성능 충족 여부를 실행 증거로 판정한다.
- Improve: 검증 Finding에서 시작해 RCA, Patch, Regression을 수행한다.
- Prove: Evidence와 Receipt로 결과를 재현 가능하게 고정한다.
- Remember: 유효했던 개선과 실패 패턴을 재사용 가능한 지식으로 축적한다.

### 2-1. 두 종류의 학습
ONSure가 말하는 "학습"은 반드시 둘을 구분한다.

- **Program Understanding Learning**(위 Understand, OLearning이 수행): ONSure가 대상 프로그램의 목적·구조·기능·행동·실행환경을 학습한다. ONSure 자신의 판정 능력을 위한 학습이다.
- **Target AI Auto-Learning**(OTraining이 수행, [02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) §7-2): 검증된 Finding과 승인된 목표에 근거해 **대상 프로그램 안의 RAG·Prompt·Agent·Model 자체**를 실제 데이터로 재학습·개선한다. 코드 Patch(Improve)를 넘어서는 별도 축이며, 목적·입력·산출물·비용·위험·라이선스 단위를 Program Understanding Learning과 분리한다.

두 학습이 모두 검증 Finding에서 시작해 독립 재검증을 거쳐야 배포된다는 원칙(Fail-closed, 자기 자신 승인 금지)은 동일하다. Target AI Auto-Learning을 포함하는 확장 흐름은 다음과 같다.

Understand → Verify → Diagnose → Decide(Improve 또는 Train) → Improve 또는 Train → Independently Re-verify → Prove → Deploy → Observe → Re-learn

- Diagnose: RCA로 원인이 코드·정책·데이터·검색(RAG)·Prompt·Agent·Model 중 어디에 있는지 판정한다([02 §7 OImprovement](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md)와 [02 §7-2 OTraining](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md)이 공유하는 RCA).
- Decide: 원인에 따라 코드 Patch(Improve)로 고칠지, AI 구성요소 재학습(Train)으로 고칠지 결정한다.
- Deploy/Observe/Re-learn: 승인된 개선·재학습을 배포한 뒤 운영 데이터를 관찰하고, 새 실패·성능저하가 확인되면 다시 Diagnose로 돌아간다. Deploy는 자동이 아니며 §6의 배포 승인 경계를 따른다.

### 2-2. 재귀학습 원칙은 ONSure 자신에게도 적용된다
OMemory(§5)의 재귀학습(자동 판정이 놓친 결함을 RCA→개정→회귀→승격으로 흡수해 ONSure 자신의 탐지 능력을 보강하는 루프, [02 §7-1](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md))과 Target AI Auto-Learning(대상 프로그램의 AI를 재학습하는 루프)은 **같은 원칙을 공유하는 두 적용 사례**다. 검증된 근거에서만 시작하고, 자기 자신이 낸 개정을 스스로 승인하지 않으며, 독립 재검증을 통과해야 승격·배포된다. ONSure는 대상 프로그램만 재귀학습시키는 도구가 아니라, 그 재귀학습 원칙을 자기 자신의 판정 능력에도 동일하게 적용하는 도구다.

이는 선언에 그치지 않고 순서를 강제하는 실제 계약(`contracts/learning-to-application-pipeline.v1.json`)이 있다: 학습결과를 대상 프로그램에 적용하는 것(`TARGET_PRODUCT_APPLY`, 곧 OTraining)은 ONSure가 자기 자신의 학습결과를 승격시키는 경로(OMemory, `VALIDATION_PACK_APPLY`)를 최소 1건 `APPLIED_LOCKED`까지 증명하기 전까지 허용되지 않는다. 대상을 재학습시키는 도구이기 전에, 먼저 스스로를 안전하게 재학습시킬 수 있어야 한다.

## 3. 독립성 원칙
- ONSure의 제품 정의, 실행 구조, 고객 데이터, 릴리스는 ORUDA에 종속되지 않는다.
- ONSure는 독립 저장소, 독립 배포, 독립 상품, 독립 SLA를 가진다.
- OLicense 연계가 중단되어도 계약된 Offline Grace 정책 범위에서 제한 실행할 수 있다.
- ONSure는 라이선스를 생성하거나 임의 변경하지 않는다. Validate, Consume, Report만 수행한다.

## 4. 상품 체계
### 4.1 Web One-time Service
- Learn
- Verify
- Learn & Verify
- 후속 Improve & Re-verify

Web의 1회는 버튼 실행 횟수가 아니라 Service Case 단위다.

Service Case = System + Programs + Baseline + Scope + Capacity + Validity + Deliverables

### 4.2 VS Code Continuous Subscription
- Developer
- Team
- Enterprise
- Unlimited Systems & Programs

VS Code는 지속 학습, 지속 리뷰, 지속 검증, 지속 개선을 제공한다.

Unlimited는 등록·활성화 가능한 System·Program 수 제한만 제거하는 옵션이며, AI·학습·GPU·컴퓨팅·Storage·전문가 지원까지 무제한을 의미하지 않는다(`docs/v2/00_MASTER_INDEX.md` §4). Seat, Credit, 동시 실행, Compute, Storage는 별도 계약 한도를 적용한다.

## 5. 프로그램 구성
- OLearning: Repository와 실행 구조를 학습하고 Program Profile을 생성한다.
- OPlanning: 검토·검증·개선 계획과 실행 순서를 생성한다.
- OReview: 요구사항·설계·정책·코드·AI·보안·테스트·품질·Merge 리뷰를 수행한다.
- OVerification: 정적·동적·시나리오·적대·회귀 검증을 수행한다.
- OImprovement: Finding 기반 RCA와 Patch를 생성하고 회귀검증한다.
- OTraining: 검증된 Finding과 승인된 목표에 근거해 대상 프로그램의 RAG·Prompt·Agent·Model을 재학습·개선하고, 배포 전 독립 재검증을 거친다(Target AI Auto-Learning).
- OEvidence: 입력·정책·결과·실행환경·해시·Receipt를 관리한다.
- OMemory: 유효했던 개선 패턴과 실패 패턴을 재사용 가능한 지식으로 축적하고, 자동 판정이 놓친 결함을 재귀학습으로 흡수해 탐지 능력을 지속 보강한다.
- OGit: Worktree, Branch, Commit, Push, Draft PR, CI 상태를 관리한다.
- ODelivery: 보고서, Patch, PR, Evidence Pack, Program Profile을 납품한다.
- OLicense Adapter: ORUDA/OLicense의 Entitlement와 Credit을 검증·소비·보고한다.

### 5-1. Semantic Assurance Companion Capability Set (`DESIGN_ONLY`)
기존 프로그램 구성을 대체하지 않고 OLearning/OPlanning/OReview/OVerification/OEvidence/OMemory에 흡수되는 고급 검증 Capability다. 상세 책임은 [semantic-assurance/00_INTEGRATION_AND_OWNERSHIP.md](semantic-assurance/00_INTEGRATION_AND_OWNERSHIP.md)를 기준으로 한다.

- SA-01 Evidence Reperformance & Truth Binding
- SA-02 Denominator & Coverage Discovery
- SA-03 Obligation Closure Engine
- SA-04 Authority Lifecycle Validator
- SA-05 Canonical State Authority Validator
- SA-06 Rights & Remedy Executability
- SA-07 Distributed Effect Integrity
- SA-08 Freshness & Invalidation Graph
- SA-09 Principal / Policy / SoD Validator
- SA-10 Privacy Disclosure & Observer Validator
- SA-11 AI Lifecycle & Authority Closure
- SA-12 Cross-Model Semantic Trace Validator
- SA-13 Business Semantic Integrity
- SA-14 Validator Requalification Engine

이 목록은 신규 서비스 14개를 의미하지 않는다. 기존 ONSure 프로그램이 재사용하는 검증 Capability 분류이며, 실제 Runtime 경계는 `04_ARCHITECTURE_DATA_API_OLICENSE.md`와 향후 Contract에서 결정한다. 현재는 `DESIGN_ONLY`다.

### 5-2. Semantic Assurance Finding Authority (`DESIGN_ONLY`)
반복 독립검토에서 발견한 구체 결함은 [semantic-assurance/10_FINDING_LEDGER.md](semantic-assurance/10_FINDING_LEDGER.md)를 canonical human ledger로 사용한다. 원시 검토의 candidate observation count와 canonical defect count를 분리하며, 동일 주제라도 실패 시나리오·영향·필수 계약 변경이 다르면 별도 Finding으로 유지한다.

현재 Ledger는 P0 `FL-P0-001~132`, P1 `FL-P1-001~048`의 source-confirmed canonical batch를 기록하고, machine 후보는 `contracts/semantic-assurance-finding-ledger.candidate.v1.json`에 둔다. 이 등록은 결함 해결을 의미하지 않는다.

계약 변경 설계는 [semantic-assurance/11_CONTRACT_UPGRADE_BLUEPRINT.md](semantic-assurance/11_CONTRACT_UPGRADE_BLUEPRINT.md)를 따른다.

## 6. 핵심 경계
### Review와 Verification
Review는 구현 또는 변경이 적절한지 판단한다. Verification은 요구사항과 정책을 실제로 만족하는지 증거로 판정한다. Review PASS가 Verification PASS를 의미하지 않으며, 두 결과는 독립 저장한다.

### Improvement 시작 조건
개선은 임의 코딩 요청에서 시작하지 않는다. 반드시 검증된 Finding 또는 승인된 Review Finding에서 시작한다.

### Learning 경계
학습은 범용 기업 지식관리 제품으로 확장하지 않는다. Program Learning, Behavior Learning, Improvement Learning, Target AI Auto-Learning(대상 프로그램의 RAG·Prompt·Agent·Model에 한정)에 한정한다.

### Train 시작 조건과 배포 경계
Target AI Auto-Learning도 Improvement와 동일하게 임의 요청에서 시작하지 않고 검증된 Finding 또는 승인된 목표에서만 시작한다. Training 결과는 독립 재검증(Independently Re-verify)을 통과하고 권한자 승인을 받기 전까지 운영에 배포하지 않는다. Deploy·Observe·Re-learn은 자동화 편의를 위해 승인 경계를 생략하지 않는다([05_UI_UX_WORKFLOW_SPECIFICATION.md](05_UI_UX_WORKFLOW_SPECIFICATION.md) §7 고위험 별도 승인).

### Semantic Assurance 경계
Semantic Assurance Capability는 기존 기능/검증 결과의 의미적 closure를 강화하는 계층이다. 문서상 적용, Schema 존재, Runtime 구현, 실행, Evidence 결속, 독립 검증, Qualification은 서로 다른 상태다. `contracts/semantic-assurance-capability-registry.candidate.v1.json`은 현재 Candidate Registry이며 구현 권위를 만들지 않는다.

### Canonical Gate 편입 경계
Semantic Assurance가 실제 Final Gate가 되려면 설계 문서나 Candidate Registry 존재만으로 충분하지 않다. 최소 다음 네 경로에 모두 편입되어야 한다.

1. canonical product lineage
2. workflow operation registry
3. validation case/negative fixture denominator
4. Final acceptance/publication/freshness reconstruction path

한 곳이라도 빠지면 `DESIGNED_CONTROL_OUTSIDE_CANONICAL_GATE_PATH`로 취급한다. 특히 `10_FINDING_LEDGER.md`의 canonical gate blocker가 OPEN이면 Semantic Assurance를 근거로 Full-Chain/Final Candidate/FinalLock/Production/Commercial positive claim을 승격하지 않는다.

## 7. 주요 산출물
- Business Plan
- Product Requirement Document
- Service Policy
- Program Specification
- OReview Specification
- OVerification Specification
- OImprovement Specification
- UI/UX Specification
- Architecture and Data Model
- API/Event/Token Contract
- OLicense Integration Contract
- Security and Privacy Design
- Test Strategy and Fixtures
- Operation and Deployment Runbook
- Epic/Capability/Story Backlog
- Component Model and AI Agent Methodology ([07_COMPONENT_MODEL_AND_AI_METHODOLOGY.md](07_COMPONENT_MODEL_AND_AI_METHODOLOGY.md))
- Knowledge Pattern Library and Recursive Detection Learning Design
- Semantic Assurance Integration/Ownership ([semantic-assurance/00_INTEGRATION_AND_OWNERSHIP.md](semantic-assurance/00_INTEGRATION_AND_OWNERSHIP.md))
- Semantic Assurance Functional Extension ([semantic-assurance/02_FUNCTIONAL_REQUIREMENTS_EXTENSION.md](semantic-assurance/02_FUNCTIONAL_REQUIREMENTS_EXTENSION.md))
- Semantic Assurance Review Extension ([semantic-assurance/03_REVIEW_SPECIFICATION_EXTENSION.md](semantic-assurance/03_REVIEW_SPECIFICATION_EXTENSION.md))
- Semantic Assurance Architecture/Data/API Extension ([semantic-assurance/04_ARCHITECTURE_DATA_API_EXTENSION.md](semantic-assurance/04_ARCHITECTURE_DATA_API_EXTENSION.md))
- Semantic Assurance UI/UX Extension ([semantic-assurance/05_UI_UX_WORKFLOW_EXTENSION.md](semantic-assurance/05_UI_UX_WORKFLOW_EXTENSION.md))
- Semantic Assurance Test/Operation Extension ([semantic-assurance/06_TEST_OPERATION_EXTENSION.md](semantic-assurance/06_TEST_OPERATION_EXTENSION.md))
- Semantic Assurance AI/Agent Method Extension ([semantic-assurance/07_AI_AGENT_METHOD_EXTENSION.md](semantic-assurance/07_AI_AGENT_METHOD_EXTENSION.md))
- Semantic Assurance Open Decisions ([semantic-assurance/08_OPEN_DECISIONS_EXTENSION.md](semantic-assurance/08_OPEN_DECISIONS_EXTENSION.md))
- Semantic Assurance Independent Review Integration ([semantic-assurance/09_INDEPENDENT_REVIEW_FINDINGS_INTEGRATION.md](semantic-assurance/09_INDEPENDENT_REVIEW_FINDINGS_INTEGRATION.md))
- Semantic Assurance Finding Ledger ([semantic-assurance/10_FINDING_LEDGER.md](semantic-assurance/10_FINDING_LEDGER.md))
- Semantic Assurance Contract Upgrade Blueprint ([semantic-assurance/11_CONTRACT_UPGRADE_BLUEPRINT.md](semantic-assurance/11_CONTRACT_UPGRADE_BLUEPRINT.md))

## 8. 출시 Gate
다음이 모두 충족되어야 상용 출시 후보가 된다.
- 요구사항 Traceability 100%
- Critical/High 미해결 결함 0건
- Web Full-Chain 연속 2회 PASS
- VS Code Full-Chain 연속 2회 PASS
- OLicense 발급·정지·만료·폐기·Offline 시나리오 PASS
- Code Review와 Independent Review 완료
- 보안·개인정보·소스 삭제 검증 PASS
- 운영 복구 및 Rollback 시험 PASS

Semantic Assurance Capability가 출시 Gate의 하드 조건으로 승격되려면 각 Capability별 Contract/Runtime/Execution/Qualification이 먼저 제정되어야 한다. Candidate Registry 존재만으로 출시 Gate를 확대하거나 통과시킨 것으로 간주하지 않는다.

추가로 `semantic-assurance/10_FINDING_LEDGER.md`의 `CANONICAL_GATE_BYPASS`, `SEMANTIC_TYPE_ERASURE`, Status/Receipt/Final/Independent trust-chain P0 blocker가 OPEN인 동안 신규 Semantic Assurance를 근거로 Final Gate를 강화 완료했다고 주장하지 않는다. P0 blocker의 해소는 문서 반영이 아니라 Contracted→Implemented→Executed→Evidence Bound→Independently Verified→Qualified 상태로 확인한다.

## 9. 비최종 상태
문서 작성, 코드 구현, 단위시험, PR 생성만으로 Final PASS를 선언하지 않는다. 실제 환경 E2E, 독립 리뷰, Evidence 고정 전까지 모든 결과는 NON_FINAL이다.

Target AI Auto-Learning(OTraining)은 다른 프로그램보다 사업성 검증이 늦은 단계다. 초기에는 GPU 학습이 필요한 전체 Model Fine-tuning까지 동시에 개발하지 않고, 재현성과 고객가치가 확인된 RAG 재인덱싱·Prompt 개선·AI 생성 코드 안정화부터 유료 Case로 시장을 검증한다([01_BUSINESS_PRODUCT_SERVICE_PLAN.md](01_BUSINESS_PRODUCT_SERVICE_PLAN.md) §11-2).
