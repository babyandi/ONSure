# 128 Final Fresh Review Rerun & Product Design Scope Closure

Status: `PRODUCT_DESIGN_SCOPE_COMPLETE_CANDIDATE / RECONCILED / NON_FINAL`

## 1. 목적
동일 branch에서 병렬 작업 중 생성된 Fresh Review 산출물을 대조해 결론을 재정렬한다.

관련 문서:
- `126_FINAL_FRESH_PRODUCT_DESIGN_REVIEW.md`: 신규 독립축 2개 발견
- `127_SAFETY_HAZARD_AND_CONTESTABILITY_GOVERNANCE.md`: Safety/Hazard + Contestability/Appeal 통합 설계
- `127_SAFETY_HAZARD_ASSURANCE_ARCHITECTURE.md`: Safety/Hazard 심화 설계
- `126_FINAL_FRESH_PRODUCT_DESIGN_REVIEW_AND_SCOPE_CLOSURE.md`: 독립축 0건으로 조기 판정한 병렬 산출물
- `127_PRODUCT_DESIGN_TO_DESIGN_QA_PHASE_HANDOFF.md`: 위 조기 판정을 기반으로 한 Phase handoff

본 문서는 위 충돌을 해소하는 최신 권위 판정이다.

## 2. 조기 판정 정정
`126_FINAL_FRESH_PRODUCT_DESIGN_REVIEW_AND_SCOPE_CLOSURE.md`의 "신규 독립축 0건"은 branch에 병렬로 추가된 Safety/Hazard와 Contestability/Appeal 설계를 대조하기 전에 작성되었으므로 **SUPERSEDED**한다.

동시에 해당 문서가 찾은 아래 3개는 독립축이 아니라 기존 owner refinement로 유지한다.
- FR-FRESH-001 Rules of Engagement / Target Testing Authorization
- FR-FRESH-002 Accessibility / Internationalization / Locale Integrity
- FR-FRESH-003 Contract Termination / Tenant Offboarding Closure

## 3. 확정된 신규 독립축
### AXIS-NEW-01 Safety / Hazard Assurance
Security/Reliability/Functional Correctness와 별도로 Harm, Hazardous Scenario, Safe/Degraded-safe State, Residual Safety Risk, Safety Case, Safety-specific Authority를 관리한다.

Canonical owner:
- `127_SAFETY_HAZARD_AND_CONTESTABILITY_GOVERNANCE.md`의 Safety section
- `127_SAFETY_HAZARD_ASSURANCE_ARCHITECTURE.md`는 심화 companion

### AXIS-NEW-02 Contestability / Appeal / Dispute Governance
Finding/N/A/Accepted Risk/Qualification/Composition/Currentness/Certificate 등 ONSure 판정을 immutable original record를 보존한 채 formal challenge하고 independent appeal review할 수 있게 한다.

Canonical owner:
- `127_SAFETY_HAZARD_AND_CONTESTABILITY_GOVERNANCE.md`의 Contestability section

## 4. 재수행 Fresh Review 기준
Safety/Hazard와 Contestability/Appeal을 이미 존재하는 축으로 포함한 상태에서 고객 생애주기를 다시 검토했다.

Lifecycle:
`Discover/Buy → Authorize → Connect → Understand → Plan → Review → Verify → Safety/Appeal when applicable → Improve/Train → Prove/Deliver → Deploy/Observe → Reassess/Revoke/Recover → Audit/Support → Renew/Terminate/Exit`

검토 관점:
- product responsibility
- assurance truth/authority
- harm/safety
- challenge/appeal
- customer lifecycle
- runtime/deployment
- evidence/legal/privacy
- AI/agent
- enterprise/offline/industry
- scale/plugin/external dependency
- self/meta-assurance

## 5. Rerun 결과
**추가 신규 독립 설계축: 0건.**

Safety/Hazard와 Contestability/Appeal을 반영한 뒤에는 현재 ONSure 책임구조로 표현할 수 없는 별도 subsystem급 책임영역을 추가로 발견하지 못했다.

아래 후보는 기존 축의 refinement로 처리한다.
- Rules of Engagement → Planning/Authority/Security effect scope
- Accessibility/I18n → UI/NFR/Claim Language
- Offboarding → License/Privacy/Delivery/Authority lifecycle
- Responsible AI fairness/toxicity → Requirement/Policy/Oracle/AI Assurance domain
- Data residency/sovereignty → Privacy/Industry/Deployment/Offline policy
- Incident notification → Observability/Notification/Revocation/Recovery
- Legal defensibility → Evidence/Certificate/Legal Hold/Authority
- Vendor/model exit → External Trust/Versioning/Currentness

## 6. Canonical Requirement 확장
다음 requirement candidate를 Global Requirement Universe materialization 때 포함한다.
- `FR-META-061 Safety/Hazard Assurance`
- `FR-META-062 Contestability/Appeal Governance`
- `FR-FRESH-001 Rules of Engagement / Target Testing Authorization`
- `FR-FRESH-002 Accessibility / Internationalization / Locale Integrity`
- `FR-FRESH-003 Contract Termination / Tenant Offboarding Closure`

FR-FRESH 3개는 Meta 독립축이 아니라 기존 owner requirement refinement다.

## 7. Product Design Scope 판정
Fresh Review 최초 pass에서 신규 독립축 2개가 발견되었고, 해당 두 축을 설계한 뒤 rerun에서 추가 독립축이 0개였다.

따라서:
`PRODUCT_DESIGN_SCOPE_COMPLETE_CANDIDATE`

를 선언한다.

## 8. 이후 작업 Phase
### Product Design
`COMPLETE_CANDIDATE`

### Design QA / Baseline Lock
`HOLD_PENDING`
- Global Requirement exact population
- applicability
- trace/orphan/contradiction
- exact artifact SHA-256
- registry digests
- baseline reconstructability
- Design Lock Check

### Implementation
`IN_PROGRESS_BY_CLAUDE`

### Implementation Verification / Independent Assurance
`PENDING`

## 9. Reopen rule
일반적인 "더 검토"만으로 Product Design을 다시 열지 않는다.

다음 경우에만 Change Queue를 통해 reopen:
1. 신규 고객 use case가 현재 축으로 표현 불가
2. 신규 법/규제가 별도 책임영역을 요구
3. 구현 중 architecture-level semantic contradiction 발견
4. 운영/검증에서 기존 축으로 분류 불가능한 failure class 발견

Trace 누락, SHA 미생성, scanner bug, compile/test failure, implementation gap은 Product Design reopen 사유가 아니다.

## 10. Supersession
- `126_FINAL_FRESH_PRODUCT_DESIGN_REVIEW.md`: historical first-pass review, VALID_HISTORY
- `127_SAFETY_HAZARD_AND_CONTESTABILITY_GOVERNANCE.md`: active new-axis design
- `127_SAFETY_HAZARD_ASSURANCE_ARCHITECTURE.md`: active safety deep-dive companion
- `126_FINAL_FRESH_PRODUCT_DESIGN_REVIEW_AND_SCOPE_CLOSURE.md`: SUPERSEDED_CONCLUSION
- `127_PRODUCT_DESIGN_TO_DESIGN_QA_PHASE_HANDOFF.md`: SUPERSEDED_BY_128 for phase authority

## 11. 현재 최고 표현
`PRODUCT_DESIGN_SCOPE_COMPLETE_CANDIDATE / SAFETY_AND_CONTESTABILITY_AXES_INTEGRATED / THREE_EXISTING_OWNER_REFINEMENTS_RECORDED / DESIGN_QA_HOLD_PENDING / IMPLEMENTATION_IN_PROGRESS / NON_FINAL`
