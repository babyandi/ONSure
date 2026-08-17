# ONSure Master Design Set

## 1. 문서 목적
이 문서는 ONSure의 사업·제품·서비스·프로그램·라이선스·아키텍처·개발·리뷰·검증·개선·시험·운영을 하나의 개발 기준선으로 통합한다. ONSure는 ORUDA의 하위 프로그램이 아니라 독립 판매 제품이며, ORUDA/OLicense는 ONSure의 라이선스 생성·계약·Entitlement·Credit·감사 원장을 담당하는 중앙 상용 라이선스 서비스로 연계된다.

## 2. 제품 정의
ONSure는 AI가 작성하거나 AI를 포함한 소프트웨어를 대상으로 다음 전 과정을 수행하는 AI Software Engineering Assurance Platform이다.

Understand → Plan → Review → Verify → Improve → Prove → Remember

- Understand: 프로그램, 요구사항, 정책, 구조, AI 구성, 실행 특성을 학습
- Plan: 검토·검증·개선 범위와 순서를 수립
- Review: 요구사항부터 코드와 테스트까지 적정성 검토
- Verify: 요구사항·정책·보안·동작·성능 충족 여부를 실행 증거로 판정
- Improve: Finding에서 RCA, Patch, Regression 수행
- Prove: Evidence와 Receipt로 결과를 재현 가능하게 고정
- Remember: 유효한 개선과 실패 패턴을 재사용 가능한 지식으로 축적

### 2-1. 두 종류의 학습
- Program Understanding Learning: OLearning이 대상 프로그램의 목적·구조·기능·행동·실행환경을 학습
- Target AI Auto-Learning: OTraining이 검증된 Finding과 승인 목표에 근거해 RAG·Prompt·Agent·Model을 개선

두 학습 모두 자기 산출물을 자기 자신이 Final 승인하지 않는다.

## 3. 독립성 원칙
- ONSure 제품 정의·실행구조·고객데이터·릴리스는 ORUDA에 종속되지 않는다.
- ONSure는 독립 저장소·배포·상품·SLA를 가진다.
- Offline Grace는 제한실행일 뿐 Final assurance 권위를 자동 보존하지 않는다.
- ONSure는 OLicense entitlement를 Validate/Consume/Report하며 임의 발급·변경하지 않는다.

## 4. 상품 체계
- Web One-time: Learn / Verify / Learn & Verify / Improve & Re-verify
- VS Code Subscription: Developer / Team / Enterprise / Unlimited Systems & Programs

판매 Plan은 기술 Assurance Tier와 분리한다.

## 5. 프로그램 구성
OLearning, OPlanning, OReview, OVerification, OImprovement, OTraining, OEvidence, OMemory, OGit, ODelivery, OLicense Adapter.

### 5-1. Semantic Assurance Capability Set
SA-01 Evidence Reperformance & Truth Binding
SA-02 Denominator & Coverage Discovery
SA-03 Obligation Closure Engine
SA-04 Authority Lifecycle Validator
SA-05 Canonical State Authority Validator
SA-06 Rights & Remedy Executability
SA-07 Distributed Effect Integrity
SA-08 Freshness & Invalidation Graph
SA-09 Principal / Policy / SoD Validator
SA-10 Privacy Disclosure & Observer Validator
SA-11 AI Lifecycle & Authority Closure
SA-12 Cross-Model Semantic Trace Validator
SA-13 Business Semantic Integrity
SA-14 Validator Requalification Engine

## 6. 핵심 경계
- Review PASS != Verification PASS
- Self-validation PASS != Independent PASS
- Independent PASS != Qualified
- Qualified != Current Production-bound
- FinalLock historical issuance != currentness
- Human Acceptance != Technical Assurance
- Production/Commercial GO != 품질 강도 상승
- Product Plan != Assurance Tier

### Canonical Gate
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
13. Safety/Hazard Assurance where applicable
14. Contestability/Appeal Governance where applicable

## 7. 설계 산출물 기준선
`docs/master/01~08`, `08A`, `docs/master/semantic-assurance/*`를 함께 사용한다. 권위와 supersession은 `docs/architecture/ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md`를 따른다.

Semantic Assurance companion은 00~128+까지 Integration, Migration, Runtime, Independent Assurance, Deployment/Currentness, Composition, Evidence Graph, Certificate, Offline/Enterprise, Scale, Plugin/AI/Meta-Assurance, Formal Algebra, Invalidation, Persistence, API, Security/Privacy, Observability, DR, External Trust, Authority/SoD, Safe Default, Policy/Industry/Tier, Requirement Universe, Lock, Fresh Review, Safety/Hazard, Contestability/Appeal을 정의한다.

### 최신 Closure / Handoff Authority
- Product Design Scope closure: `128_FINAL_FRESH_REVIEW_RERUN_AND_PRODUCT_DESIGN_SCOPE_CLOSURE.md`
- 병합 전 Phase 상태 정정: `136_PRE_MERGE_STATUS_CORRECTION_AND_BASELINE_HANDOFF.md`
- Claude 개발 단일 진입점: `137_CLAUDE_DEVELOPMENT_MASTER_HANDOFF.md`
- 개발 진행상태: `contracts/claude-development-progress-registry.v1.json`
- 구현 중 설계변경 intake: `contracts/design-change-queue.v1.json`

## 8. Assurance Tier
- AT0 UNASSESSED
- AT1 EXECUTED
- AT2 EVIDENCE_BOUND
- AT3 INDEPENDENT
- AT4 QUALIFIED
- AT5 PRODUCTION_BOUND_CURRENT

Tier는 증거조건으로 계산하며 상품 Plan으로 자동 상승하지 않는다.

## 9. 상태 온톨로지
Verification Decision, Assurance Strength, Currentness, Qualification, Independence, Human Acceptance, Deployment Authorization, Commercial Authorization을 분리한다. Unknown/partial/stale/unverifiable을 positive strong claim으로 자동 승격하지 않는다.

## 10. Product Design Scope 최신 판정
Fresh Review에서 Safety/Hazard와 Contestability/Appeal이라는 두 독립축을 추가했고, 이를 반영한 rerun에서 추가 신규 독립축 0건을 확인했다.

따라서 제품 설계 범위 상태는:
**`PRODUCT_DESIGN_SCOPE_COMPLETE_CANDIDATE`**

다만 다음 3개 refinement는 기존 owner에 귀속한다.
- FR-FRESH-001 Rules of Engagement / Target Testing Authorization
- FR-FRESH-002 Accessibility / Internationalization / Locale Integrity
- FR-FRESH-003 Contract Termination / Tenant Offboarding Closure

Scope Complete Candidate는 Design QA PASS나 Design Lock을 의미하지 않는다.

## 11. Design QA / Baseline Lock 상태
현재 Design QA는 `IN_PROGRESS / HOLD`다. 다음은 여전히 실제 QA/봉인 작업이다.
- Global Requirement Universe exact population
- Applicability exact population
- repository-wide trace/orphan/contradiction zero 증명
- authoritative artifact content SHA-256 inventory
- canonical registry digests
- baseline reconstructability
- Design Lock Check

이 항목들은 제품 설계축 추가 작업이 아니라 Design QA다.

## 12. 구현 상태
현재 canonical 구현 상태:
- Claude Implementation: `NOT_STARTED`
- Test / Runtime Verification: `NOT_STARTED`
- Independent Assurance / Release Qualification: `NOT_STARTED`
- Production / Commercial: `NOT_AUTHORIZED`

`128`에 남아 있는 과거 `IN_PROGRESS_BY_CLAUDE` 표현은 Phase 상태에 한해 `136`에 의해 supersede된다.

## 13. 개발 전 정합성 보강
Claude Batch 0 시작 전 다음을 구현 준비 기준으로 사용한다.
1. Authority/Supersession hierarchy 정합화
2. Master 최신 상태 반영
3. machine contract의 exact population을 숫자 단일 권위로 사용
4. Business Actor→RBAC/Authority mapping 계약화
5. 핵심 DESIGN_ONLY 기능의 Operation/Contract materialization 후보 생성
6. Open Decision→Policy source/safe floor binding

완료 여부는 `138_IMPLEMENTATION_READY_DESIGN_BASELINE_RECONCILIATION.md`에서 추적한다.

## 14. 구현/검증 경계
다음 전까지 Product/Final authority를 올리지 않는다.
- Candidate Schema/registry 실제 제정 및 fixture
- compile/JUnit/자동검증 실제 실행
- actual reconstruction/migration
- independent OTester/OAudit
- Human Acceptance verifier
- Validator/ONSure qualification
- target-bound deployment/currentness
- Shadow Gate disagreement closure
- Active Selector 승인

문서가 자세하거나 Candidate Contract가 존재한다는 사실은 PASS/QUALIFIED/FINAL 증거가 아니다.

## 15. 현재 최고 표현
**`PRODUCT_DESIGN_SCOPE_COMPLETE_CANDIDATE / PRE_IMPLEMENTATION_DESIGN_RECONCILIATION_IN_PROGRESS / DESIGN_QA_HOLD / CLAUDE_IMPLEMENTATION_NOT_STARTED / NON_FINAL`**
