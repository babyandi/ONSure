# 126 Final Fresh Product Design Review & Scope Closure

Status: `PRODUCT_DESIGN_SCOPE_COMPLETE_CANDIDATE / DESIGN_QA_PENDING / NON_FINAL`

## 1. Review 목적
기존 Finding/Trace/Lock 문서를 전제로 세부 completeness를 다시 쪼개지 않고, ONSure를 처음 도입하는 실제 고객의 전체 생애주기 관점에서 독립적인 설계축 누락 여부만 재검토했다.

검토 생애주기:
`Discover/Buy → Authorize → Connect/Import → Understand → Plan → Review → Verify → Improve/Train → Prove/Deliver → Deploy/Observe → Reassess/Revoke/Recover → Audit/Support → Renew/Terminate/Exit`

새 발견으로 인정하는 기준:
- 기존 설계축의 단순 상세화가 아닐 것
- Trace/Schema/Scanner/Hash/Lock 같은 Design-QA 작업이 아닐 것
- 제품 역할·데이터·권위·고객 workflow에 독립적인 새로운 책임영역을 만들 정도일 것

## 2. 기존 설계축 대조
이미 존재하는 독립 축:
- Business/Product/Service/Commercial
- Learn/Plan/Review/Verify/Improve/Train
- Evidence/Memory/Git/Delivery/License
- Requirement Universe/Applicability/Trace
- Authority/RBAC/SoD/Delegation/Human Acceptance
- Independent Assurance/OTester/OAudit/Qualification
- Final Reconstruction/Approval/Lock
- Deployment/Runtime Currentness/Drift
- Invalidation/Revocation/Recovery/DR
- Multi-target Composition/Evidence Graph
- Certificate/Offline Verification
- Security/Privacy/Data Governance
- Persistence/API/Event/Receipt/Canonicalization
- Observability/SLO/Incident evidence
- Scale/Distributed Work
- Plugin/Adapter/External Integration Trust
- AI Model/Prompt/RAG/Tool/Memory/Multi-agent/Nondeterminism
- ONSure Meta-Assurance/TCB/Release Qualification
- Industry/Profile/Tier/Claim Language

## 3. Fresh Review 결과
**새로운 독립 대설계축: 0건.**

다만 기존 축 안에서 명시 요구가 약한 refinement 3건을 확인했다. 이 세 건은 별도 subsystem을 만들지 않고 기존 owner에 흡수한다.

### FR-FRESH-001 Rules of Engagement / Target Testing Authorization
Owner: OPlanning + AuthorityGrant + Security/External Effect

DAST/Fuzzing, deployed-service verification, external network access, load/performance/recovery test처럼 대상 시스템에 effect를 줄 수 있는 검증은 단순 tenant role/operation 권한 외에 engagement scope를 요구한다.

필수 최소 의미:
- target/system owner authorization
- allowed target endpoints/assets
- allowed test classes
- forbidden actions
- time window
- production/non-production classification
- rate/resource ceiling
- emergency stop/contact
- third-party/external service exclusion or approval
- authorization expiry/revocation
- exact plan/target digest binding

범위 밖 effect는 BLOCKED. 고객이 ONSure 사용권을 가지고 있다는 사실만으로 임의 외부 시스템 테스트 권한을 추정하지 않는다.

### FR-FRESH-002 Accessibility / Internationalization / Locale Integrity
Owner: UI/UX + NFR + Claim Language

ONSure Web/VS Code/Report/Certificate는 접근성과 locale 차이 때문에 상태 의미가 달라지면 안 된다.

필수 최소 의미:
- keyboard-only core workflow
- screen-reader semantic labels for critical decision/currentness/limitation
- color-only PASS/FAIL/HOLD 표현 금지
- locale-independent machine state/token
- timestamp/timezone explicit rendering
- number/currency/unit locale formatting과 machine canonical value 분리
- translated report/certificate가 canonical Claim 의미를 약화/확대하지 않음
- localization fallback이 UNKNOWN/HOLD/limitation 문구를 누락하지 않음

구체 WCAG level/지원 언어 목록은 Policy/Product decision으로 둘 수 있으나 machine state와 accessibility-safe critical communication은 설계 requirement다.

### FR-FRESH-003 Contract Termination / Tenant Offboarding Closure
Owner: License + Security/Privacy/Data Governance + Delivery + Authority

기존 Export/Deletion/Retention 기능을 계약종료 workflow로 닫는다.

최소 sequence:
`TerminationRequested → NewEffectBlocked → ExportWindow → Credential/Token/Grant Revocation → Pending Job Settlement/Abort → Evidence/Certificate Retention Classification → Customer Export → Deletion/LegalHold Processing → OffboardingReceipt`

규칙:
- 종료 후 신규 유료/변경 effect 금지
- 기존 Certificate historical verification과 tenant-private Evidence 접근권한을 분리
- outstanding webhook/job/credit/reservation을 명시적으로 settle
- customer export와 deletion receipt 제공
- legal hold는 deletion보다 우선하지만 access authority를 자동 연장하지 않음
- shared corpus opt-in material은 계약/정책에 따른 post-termination eligibility를 별도 판정
- offboarding 완료 전 tenant identifier 재사용 금지

## 4. 독립 축으로 승격하지 않은 후보와 이유
- Accessibility/I18n: 제품 전체의 신규 subsystem이 아니라 UI/NFR/Claim Language의 cross-cutting refinement.
- Offboarding: 이미 License, Export, Retention/Deletion, Authority가 존재하며 lifecycle closure만 추가하면 됨.
- Rules of Engagement: 신규 Authority 체계가 아니라 기존 Authority/Plan의 대상-effect scope 강화.
- Responsible-AI fairness/toxicity: Requirement Universe + Policy + Oracle/AI Assurance로 표현 가능한 evaluation domain이며 별도 platform architecture 축이 아님.
- Data residency/sovereignty: 기존 Security/Privacy/Industry/Offline/Deployment profile에서 policy dimension으로 처리 가능.
- Appeals/Dispute: Finding disposition, Professional Review, Human Acceptance, Support workflow의 조합으로 처리 가능하며 독립 platform axis 아님.

## 5. Scope Closure 판정
Fresh Review에서 독립적인 제품 설계축 누락이 추가로 발견되지 않았다.

따라서 제품 설계 범위 상태를 다음으로 올린다:

`PRODUCT_DESIGN_SCOPE_COMPLETE_CANDIDATE`

이 상태의 의미:
- 더 이상 일반적인 “더 검토해” 반복만으로 신규 설계 문서를 계속 생성하지 않는다.
- 새 설계는 실제 신규 요구/규제/고객 use case/구현에서 발견된 semantic gap이 있을 때 Change Queue를 통해서만 연다.
- Requirement materialization, orphan scan, SHA-256 inventory, Lock Check는 **Design QA**이며 Product Design 미완료로 세지 않는다.
- Claude code review/compile/test/runtime verification은 **Implementation/Verification**이며 Product Design 미완료로 세지 않는다.

## 6. 이후 Phase 분리
### Phase A — Product Design
`COMPLETE_CANDIDATE`

### Phase B — Design QA / Baseline Lock
현재 HOLD. Global Requirement exact population, applicability, trace/orphan/contradiction, exact artifact digest, baseline reconstructability를 검증한다.

### Phase C — Implementation
Claude가 Contract/Runtime/Migration/Batch F~K 등을 개발한다.

### Phase D — Implementation Verification / Independent Assurance
실제 compile/test/reperformance/OTester/OAudit/qualification/currentness를 수행한다.

## 7. Anti-Recursion Rule
Design QA를 검증하기 위한 또 다른 product design layer를 무한 추가하지 않는다. QA tool/scanner의 completeness는 QA acceptance criterion으로 관리하고, 그것을 새로운 ONSure 제품 기능축으로 재귀 승격하지 않는다.

## 8. 최종 표현
`PRODUCT_DESIGN_SCOPE_COMPLETE_CANDIDATE / THREE_EXISTING-OWNER_REFINEMENTS_IDENTIFIED / DESIGN_QA_AND_IMPLEMENTATION_PENDING / NON_FINAL`

이는 `DESIGN_BASELINE_LOCKED`, `IMPLEMENTED`, `QUALIFIED`, `PRODUCTION_READY`를 의미하지 않는다.
