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
OMemory(§5)의 재귀학습과 Target AI Auto-Learning은 **같은 원칙을 공유하는 두 적용 사례**다. 검증된 근거에서만 시작하고, 자기 자신이 낸 개정을 스스로 승인하지 않으며, 독립 재검증을 통과해야 승격·배포된다.

이는 선언에 그치지 않고 `contracts/learning-to-application-pipeline.v1.json`이 순서를 강제한다. `TARGET_PRODUCT_APPLY`는 ONSure 자체 `VALIDATION_PACK_APPLY`가 최소 1건 `APPLIED_LOCKED`까지 증명되기 전 허용되지 않는다.

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

VS Code는 지속 학습, 지속 리뷰, 지속 검증, 지속 개선을 제공한다. Unlimited는 등록·활성화 가능한 System·Program 수 제한만 제거하며 AI·학습·GPU·Compute·Storage·지원까지 무제한을 의미하지 않는다.

## 5. 프로그램 구성
- OLearning: Repository와 실행 구조를 학습하고 Program Profile을 생성한다.
- OPlanning: 검토·검증·개선 계획과 실행 순서를 생성한다.
- OReview: 요구사항·설계·정책·코드·AI·보안·테스트·품질·Merge 리뷰를 수행한다.
- OVerification: 정적·동적·시나리오·적대·회귀 검증을 수행한다.
- OImprovement: Finding 기반 RCA와 Patch를 생성하고 회귀검증한다.
- OTraining: 검증된 Finding과 승인된 목표에 근거해 대상 프로그램의 RAG·Prompt·Agent·Model을 재학습·개선하고, 배포 전 독립 재검증을 거친다.
- OEvidence: 입력·정책·결과·실행환경·해시·Receipt를 관리한다.
- OMemory: 개선·실패 패턴과 MissedFinding을 재귀학습으로 축적한다.
- OGit: Worktree, Branch, Commit, Push, Draft PR, CI 상태를 관리한다.
- ODelivery: 보고서, Patch, PR, Evidence Pack, Program Profile을 납품한다.
- OLicense Adapter: ORUDA/OLicense의 Entitlement와 Credit을 검증·소비·보고한다.

### 5-1. Semantic Assurance Companion Capability Set (`DESIGN_ONLY`)
기존 프로그램을 대체하지 않고 OLearning/OPlanning/OReview/OVerification/OEvidence/OMemory가 재사용하는 고급 검증 Capability다.

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

이 목록은 신규 서비스 14개를 의미하지 않는다. 실제 Runtime 경계는 04 문서와 Candidate/향후 Active Contract에서 결정한다.

### 5-2. Semantic Assurance Finding Authority (`DESIGN_ONLY`)
반복 독립검토에서 발견한 결함은 최초 Ledger [semantic-assurance/10_FINDING_LEDGER.md](semantic-assurance/10_FINDING_LEDGER.md)와 post-v2 Ledger [semantic-assurance/20_POST_V2_FINAL_REVIEW_FINDINGS.md](semantic-assurance/20_POST_V2_FINAL_REVIEW_FINDINGS.md)를 함께 canonical human ledger로 사용한다.

현재 source-grounded baseline:
- raw candidate observation: **562**
- canonical P0: **FL-P0-001~141 / 141건**
- canonical P1: **FL-P1-001~050 / 50건**
- verified closed: **0**

Machine index는 `contracts/semantic-assurance-finding-ledger.candidate.v1.json`, disposition은 `contracts/semantic-assurance-finding-disposition.candidate.v1.json`이 추적한다. Candidate fix가 같은 branch에 존재한다는 사실만으로 Finding을 CLOSED하지 않는다.

## 6. 핵심 경계
### Review와 Verification
Review는 구현 또는 변경이 적절한지 판단한다. Verification은 요구사항과 정책을 실제로 만족하는지 증거로 판정한다. Review PASS가 Verification PASS를 의미하지 않는다.

### Improvement 시작 조건
개선은 반드시 검증된 Finding 또는 승인된 Review Finding에서 시작한다.

### Learning 경계
학습은 Program Learning, Behavior Learning, Improvement Learning, Target AI Auto-Learning에 한정한다.

### Train 시작 조건과 배포 경계
Target AI Auto-Learning 결과는 독립 재검증과 권한자 승인 전 운영 배포하지 않는다.

### Semantic Assurance 경계
문서상 적용, Schema 존재, Fixture 존재, Runtime Candidate, 실행, Evidence 결속, 독립 검증, Qualification은 서로 다른 상태다.

### Canonical Gate 편입 경계
Semantic Assurance가 실제 Final Gate가 되려면 최소 다음 경로에 모두 편입되어야 한다.
1. canonical product lineage
2. workflow operation registry / dispatcher
3. validation case exact denominator
4. Final acceptance exact denominator + publication/freshness reconstruction

Post-v2 runtime에서는 semantic operation이 `TenantRbacService` durable authorization 경계 안에서 실제 operation 이름으로 기록되도록 Candidate를 보강했고, RegisteredTarget의 server-resolved sourceRoot로 target file access를 제한했다. 하지만 compile/JUnit/independent verification이 실행되지 않았으므로 active authority가 아니다.

독립성·Human Acceptance·Validator Qualification·Authority effect-time validity는 caller boolean/string으로 승격하지 않는다. 실제 cryptographic/runtime verifier가 연결되기 전 Candidate Runtime은 HOLD한다.

## 7. 주요 산출물
기존 Business/Product/Service/Program/Review/Verification/Improvement/UI/Architecture/API/Security/Test/Operation/Backlog 산출물에 더해 다음 Semantic Assurance companion set을 관리한다.

- [semantic-assurance/00_INTEGRATION_AND_OWNERSHIP.md](semantic-assurance/00_INTEGRATION_AND_OWNERSHIP.md)
- [semantic-assurance/02_FUNCTIONAL_REQUIREMENTS_EXTENSION.md](semantic-assurance/02_FUNCTIONAL_REQUIREMENTS_EXTENSION.md)
- [semantic-assurance/03_REVIEW_SPECIFICATION_EXTENSION.md](semantic-assurance/03_REVIEW_SPECIFICATION_EXTENSION.md)
- [semantic-assurance/04_ARCHITECTURE_DATA_API_EXTENSION.md](semantic-assurance/04_ARCHITECTURE_DATA_API_EXTENSION.md)
- [semantic-assurance/05_UI_UX_WORKFLOW_EXTENSION.md](semantic-assurance/05_UI_UX_WORKFLOW_EXTENSION.md)
- [semantic-assurance/06_TEST_OPERATION_EXTENSION.md](semantic-assurance/06_TEST_OPERATION_EXTENSION.md)
- [semantic-assurance/07_AI_AGENT_METHOD_EXTENSION.md](semantic-assurance/07_AI_AGENT_METHOD_EXTENSION.md)
- [semantic-assurance/08_OPEN_DECISIONS_EXTENSION.md](semantic-assurance/08_OPEN_DECISIONS_EXTENSION.md)
- [semantic-assurance/09_INDEPENDENT_REVIEW_FINDINGS_INTEGRATION.md](semantic-assurance/09_INDEPENDENT_REVIEW_FINDINGS_INTEGRATION.md)
- [semantic-assurance/10_FINDING_LEDGER.md](semantic-assurance/10_FINDING_LEDGER.md)
- [semantic-assurance/11_CONTRACT_UPGRADE_BLUEPRINT.md](semantic-assurance/11_CONTRACT_UPGRADE_BLUEPRINT.md)
- [semantic-assurance/12_P0_VERTICAL_TRACEABILITY_AND_APPLICATION.md](semantic-assurance/12_P0_VERTICAL_TRACEABILITY_AND_APPLICATION.md)
- [semantic-assurance/13_V2_CONTRACT_MIGRATION_AND_VALIDATION_PLAN.md](semantic-assurance/13_V2_CONTRACT_MIGRATION_AND_VALIDATION_PLAN.md)
- [semantic-assurance/14_V1_V2_SEMANTIC_GAP_MATRIX.md](semantic-assurance/14_V1_V2_SEMANTIC_GAP_MATRIX.md)
- [semantic-assurance/15_V2_STATIC_QUALIFICATION_FIXTURE_SPEC.md](semantic-assurance/15_V2_STATIC_QUALIFICATION_FIXTURE_SPEC.md)
- [semantic-assurance/16_ARTIFACT_COVERAGE_AND_COMPLETION_MATRIX.md](semantic-assurance/16_ARTIFACT_COVERAGE_AND_COMPLETION_MATRIX.md)
- [semantic-assurance/17_RUNTIME_WIRING_AND_ADAPTER_IMPLEMENTATION.md](semantic-assurance/17_RUNTIME_WIRING_AND_ADAPTER_IMPLEMENTATION.md)
- [semantic-assurance/18_VALIDATION_FINAL_DENOMINATOR_MIGRATION.md](semantic-assurance/18_VALIDATION_FINAL_DENOMINATOR_MIGRATION.md)
- [semantic-assurance/19_FINAL_REVIEW_AND_EXECUTION_BLOCKERS.md](semantic-assurance/19_FINAL_REVIEW_AND_EXECUTION_BLOCKERS.md)
- [semantic-assurance/20_POST_V2_FINAL_REVIEW_FINDINGS.md](semantic-assurance/20_POST_V2_FINAL_REVIEW_FINDINGS.md)

Machine Candidate 현황은 `contracts/semantic-assurance-artifact-coverage.candidate.v1.json`을 따른다. 현재 23 Schema, valid 23, semantic-invalid 46, pending registration 0이다.

## 8. 출시 Gate
상용 출시 후보는 요구사항 Traceability, Critical/High unresolved 0, Web/VS Code Full-Chain 연속 PASS, OLicense lifecycle, Code/Independent Review, 보안·개인정보·삭제, 복구·Rollback 등 기존 Gate를 모두 만족해야 한다.

Semantic Assurance를 근거로 Final/Release를 강화하려면 추가로 다음이 필요하다.
- 23 Schema / 69 Fixture 실제 validation PASS
- Java compile/JUnit PASS
- 실제 v1→v2 reconstruction population 실행
- Validation/Final exact denominator migration 실행
- true independent OTester/OAudit execution + qualification
- signed Human Acceptance verification
- Validator Qualification independent execution
- target-bound deployment identity + Verified-to-Deployed 실행
- Shadow Gate disagreement closure
- signed Active Selector 승인

이 조건 전까지 v1 authority를 유지하고 v2 Candidate는 Active가 아니다.

## 9. 비최종 상태
문서 작성, Candidate Contract, Fixture, Runtime class, 단위시험 source, PR 생성만으로 Final PASS를 선언하지 않는다. 실제 실행환경 E2E, Evidence 고정, 독립 검증, Qualification 전까지 결과는 NON_FINAL이다.

현재 Static validator 실행은 repository local mount 및 github.com DNS 부재로 `BLOCKED_NOT_RUN`이며, `evidence/semantic-assurance/v2-static-validation-attempt-20260812.json`에 기록되어 있다. GitHub Actions로 우회하지 않았고 unverified PASS를 만들지 않았다.

현재 최고 표현은 **`DESIGN_CONTRACT_FIXTURE_AND_FAIL_CLOSED_IMPLEMENTATION_CANDIDATES_PRESENT / EXECUTION_BLOCKED_OR_NOT_RUN / NON_FINAL`**이다.
