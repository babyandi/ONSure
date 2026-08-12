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
- **Program Understanding Learning**: OLearning이 대상 프로그램의 목적·구조·기능·행동·실행환경을 학습한다.
- **Target AI Auto-Learning**: OTraining이 검증된 Finding과 승인 목표에 근거해 대상 프로그램 안의 RAG·Prompt·Agent·Model을 실제 데이터로 개선한다.

두 학습 모두 검증된 근거에서 시작하며 자기 산출물을 자기 자신이 Final 승인하지 않는다.

### 2-2. 재귀학습 원칙은 ONSure 자신에게도 적용된다
`contracts/learning-to-application-pipeline.v1.json`은 TARGET_PRODUCT_APPLY보다 ONSure 자체 VALIDATION_PACK_APPLY가 먼저 실제 승격되어야 함을 강제한다.

## 3. 독립성 원칙
- ONSure의 제품 정의, 실행 구조, 고객 데이터, 릴리스는 ORUDA에 종속되지 않는다.
- ONSure는 독립 저장소, 독립 배포, 독립 상품, 독립 SLA를 가진다.
- Offline Grace는 제한실행일 뿐 Final assurance 권위를 자동 보존하지 않는다.
- ONSure는 OLicense entitlement를 Validate/Consume/Report하며 임의 발급·변경하지 않는다.

## 4. 상품 체계
### 4.1 Web One-time Service
Learn / Verify / Learn & Verify / Improve & Re-verify.

### 4.2 VS Code Continuous Subscription
Developer / Team / Enterprise / Unlimited Systems & Programs.

판매 Plan은 기술적 Assurance Tier와 분리한다.

## 5. 프로그램 구성
- OLearning
- OPlanning
- OReview
- OVerification
- OImprovement
- OTraining
- OEvidence
- OMemory
- OGit
- ODelivery
- OLicense Adapter

### 5-1. Semantic Assurance Capability Set (`DESIGN_ONLY`)
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

### 5-2. Finding Authority
현재 source-grounded baseline:
- raw candidate observations: **562**
- canonical P0: **141**
- canonical P1: **50**
- VERIFIED_CLOSED: **0**

Candidate fix 존재만으로 Finding을 CLOSED하지 않는다.

## 6. 핵심 경계
- Review PASS != Verification PASS
- Self-validation PASS != Independent PASS
- Independent PASS != Qualified
- Qualified != Current Production-bound
- FinalLock은 historical issuance fact이며 currentness와 분리
- Human Acceptance != Technical Assurance
- Production/Commercial GO != 품질 강도 상승
- Product Plan != Assurance Tier

### Canonical Gate 편입
실제 Final/Certificate path는 최소 다음을 닫아야 한다.
1. Product Lineage
2. Workflow Operation Registry/Dispatcher
3. Requirement/Validation/Final exact denominator
4. Independent OTester/OAudit/Human Fact Validation
5. Final Reconstruction→Approval→Lock
6. Verified→Deployed→Running identity
7. Currentness/Revocation
8. Product Composition/Evidence Graph
9. Certificate issuance/current verification
10. Active Selector transition/rollback
11. ONSure Release Qualification
12. Policy/Authority/Persistence/Recovery/Observability integrity

## 7. 설계 산출물 기준선
기존 `docs/master/01~08` 정본과 `docs/master/semantic-assurance` companion을 함께 사용한다.

### Core/Migration/Runtime
`00~28` companion은 Integration, Finding Ledger, v2 migration, Runtime wiring, Independent Assurance, Deployment identity, Runtime Evidence, Selector, TCB, Requirement Universe, Canonicalization/Crypto, Distributed Currentness를 정의한다.

### Extended Assurance Architecture
`29~52`는 Deployment/Runtime Currentness, Product Composition, Evidence Graph, Certificate, Offline/Enterprise Governance, Scale/Plugin/AI/Meta-Assurance, Formal Algebra, Invalidation, Persistence, API, Security/Privacy, Observability, Physical Model, Threat Model, Versioning, DR, External Trust를 정의한다.

### Closure/Policy/Machine Semantics
`53~69`는 End-to-End Trace, Failure Sequence, Authority/SoD, Safe Default, Design Closure, P0 Machine Contract, Workflow Operation v2, Event/Receipt, Policy Profile, AuthorityGrant/RBAC, Canonical Serialization, Recovery Receipt, Design Trace Registry, Configurable Policy, Industry Profile, Assurance Tier, Claim Language를 정의한다.

### 30개 설계 폐쇄 Batch
`70~80`은 30개 설계 폐쇄 작업을 구조화한다.

### 후속 개발·Lock·Global Denominator
`81~91`은 Claude Batch F~K, Schema Wave, persistence migration, policy bootstrap, runtime API semantics, exact design inventory, Lock scanner, Global Requirement Universe, requirement normalization, global trace scanner, RU-01~07 materialization을 정의한다.

### 50개 설계 정밀화 Batch
`92~101`은 50개 후속 설계 작업을 모두 설계 산출물로 닫는다.

### 15단계 Design Lock Closure
`102~107`은 Global Requirement materialization부터 Design Baseline Candidate 판정, Claude 구현 inventory alignment, semantic change intake까지 15개 후속 단계를 연결한다.
- `102_GLOBAL_REQUIREMENT_MATERIALIZATION_APPLICABILITY_AND_TRACE_EXECUTION_PLAN.md`
- `103_GLOBAL_ORPHAN_CONTRADICTION_INVENTORY_AND_BASELINE_LOCK.md`
- `104_DESIGN_BASELINE_CANDIDATE_DECISION_AND_CHANGE_CONTROL.md`
- `105_DESIGN_TO_IMPLEMENTATION_INVENTORY_ALIGNMENT.md`
- `106_CLAUDE_SEMANTIC_CHANGE_INTAKE_AND_DESIGN_LOCK_CANDIDATE.md`
- `107_FIFTEEN_STEP_DESIGN_LOCK_CLOSURE_MASTER_MATRIX.md`

Machine candidates:
- `contracts/fifteen-step-design-lock-closure.candidate.v1.json`
- `contracts/design-implementation-alignment.candidate.v1.json`
- `contracts/design-semantic-change-queue.candidate.v1.json`
- `contracts/design-baseline-candidate-decision.candidate.v1.json`

또한 06/07은 신규 Runtime/AI/Meta-Assurance 내용을 본문에 직접 흡수했고, 08은 기존 결정 이력을 보존하기 위해 `08A_ASSURANCE_POLICY_AND_OPEN_DECISION_INTEGRATION.md`를 부속 정본으로 사용한다.

## 8. Assurance Tier (`DESIGN_ONLY`)
- AT0 UNASSESSED
- AT1 EXECUTED
- AT2 EVIDENCE_BOUND
- AT3 INDEPENDENT
- AT4 QUALIFIED
- AT5 PRODUCTION_BOUND_CURRENT

Tier는 증거조건으로 계산한다. Enterprise 구매만으로 높은 Tier가 되지 않는다.

## 9. 상태 온톨로지
다음 축을 분리한다.
- Verification Decision
- Assurance Strength
- Currentness
- Qualification
- Independence
- Human Acceptance
- Deployment Authorization
- Commercial Authorization

Unknown/partial/stale/unverifiable은 positive strong claim으로 자동 승격하지 않는다.

## 10. 설계 폐쇄 상태
50개 설계 정밀화와 후속 15단계 closure는 설계/대조/판정 수준까지 수행됐다. 다만 다음 실제 materialization/scan은 아직 실행되지 않았다.
- Global Requirement Universe exact population
- Applicability exact population
- repository-wide global trace/orphan/contradiction scan
- exact content SHA-256 design inventory
- Design Lock Check
- full implementation reverse scan
- semantic change queue unresolved P0=0 증명

현재 최고 설계 상태:
**`FIFTEEN_STEP_DESIGN_CLOSURE_DESIGNED / IMPLEMENTATION_INVENTORY_PARTIAL / DESIGN_BASELINE_CANDIDATE_HOLD / MACHINE_CONTRACT_IMPLEMENTATION_PENDING / NON_FINAL`**

15개 작업을 다뤘다는 사실은 `DESIGN LOCKED` 또는 Product PASS를 의미하지 않는다.

## 11. 구현/검증과의 경계
다음 전까지 Product/Final authority를 올리지 않는다.
- Candidate Schema/registry 실제 제정 및 fixture
- compile/JUnit
- actual reconstruction/migration
- independent OTester/OAudit
- Human Acceptance verifier
- Validator/ONSure qualification
- target-bound deployment/currentness
- Shadow Gate disagreement closure
- Active Selector 승인

문서가 자세하다는 사실은 PASS/QUALIFIED/FINAL 증거가 아니다.
