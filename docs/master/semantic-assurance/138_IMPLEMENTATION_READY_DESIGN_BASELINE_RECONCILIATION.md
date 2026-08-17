# 138 Implementation-Ready Design Baseline Reconciliation

Status: `PRE_IMPLEMENTATION_RECONCILIATION_COMPLETE / DESIGN_QA_HOLD / CLAUDE_IMPLEMENTATION_NOT_STARTED / NON_FINAL`

이 문서는 Fresh Review에서 확인한 6개 개발 전 정합성 문제를 한 번에 닫는다. 새 제품 설계축을 추가하지 않는다.

## 1. Authority / Supersession 정리 — DONE
`docs/architecture/ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md`를 개정해 L0 Product Principle, L1 Master Design, L2 Semantic Assurance, L3 Machine Semantic Contract, L4 Implementation/Test/Evidence 계층을 정의했다.

핵심 규칙:
- README는 제품 불변원칙과 금지 경계 권위이며 상세 enum/count를 덮어쓰지 않는다.
- `00~08A`는 제품/기능/아키텍처 정본이다.
- `semantic-assurance/*`는 L1에 없는 상세 assurance semantics의 companion authority다.
- Active canonical machine contract는 실행 가능한 필드/enum/population의 단일 권위다.
- Candidate contract는 Active contract를 자동 대체하지 않는다.
- 128은 Product Design Scope closure, 136은 Phase 상태, 137은 Claude 개발 진입점의 최신 권위다.

## 2. Master 최신화 — DONE
`00_ONSURE_MASTER_DESIGN_SET.md`를 최신화했다.
- Safety/Hazard와 Contestability/Appeal을 Canonical Gate에 포함
- Product Design Scope를 `COMPLETE_CANDIDATE`로 갱신
- Design QA는 별도 `IN_PROGRESS/HOLD`
- Claude Implementation은 `NOT_STARTED`
- 128/136/137을 최신 closure/handoff authority로 명시
- FR-FRESH-001~003 refinement를 유지

## 3. 상태 / Operation 숫자 충돌 제거 — DONE AS AUTHORITY RECONCILIATION
Active `contracts/workflow-operation-registry.v1.json`의 exact operation count는 49다. 과거 문서의 45 표기는 descriptive drift이며 runtime authority가 아니다.

`contracts/runtime-authority-reconciliation.v1.json`에 다음을 고정했다.
- active operation count = 49
- stale prose count = 45
- Product Design Scope = COMPLETE_CANDIDATE
- Design QA = IN_PROGRESS_HOLD
- Claude Implementation = NOT_STARTED
- Test/Independent/Production = NOT_STARTED

따라서 128의 과거 `IN_PROGRESS_BY_CLAUDE` 상태표현은 Phase 상태에 한해 superseded다.

## 4. Business Actor → RBAC/Authority 계약화 — DONE AS CANDIDATE
`contracts/business-actor-rbac-mapping.candidate.v1.json`을 추가했다.

다음 업무 Actor를 tenant 역할과 authority class에 결속한다.
Customer Owner, Customer Admin, Developer, Reviewer, Professional Reviewer, ONSure Operator, Security Auditor, External Acceptor, Compliance Officer.

중요 규칙:
- RBAC role은 고위험 effect authority의 필요조건일 뿐 충분조건이 아니다.
- AuthorityGrant, purpose/resource/parameter binding, expiry, SoD를 추가 검증한다.
- 규제산업 Patch Author / Reverify Approver / Delivery Acceptor의 동일 principal 삼중 겸직 금지.
- Appeal reviewer와 original decision maker 분리.
- 동일 principal의 여러 key는 four-eyes 두 명으로 세지 않는다.

Candidate activation은 negative SoD/cross-tenant/authority context test 후 수행한다.

## 5. 핵심 DESIGN_ONLY Operation / Contract materialization — DONE AS CANDIDATE WAVE
### Operation
`contracts/workflow-operation-extension.candidate.v2.json`에 현재 UI/API/설계가 전제하지만 Active 49개 registry에는 없는 13개 operation을 materialize했다.
- notification.subscribe / notification.list-deliveries
- portfolio.read
- policy-pack.create-version / policy-pack.qualify-version
- acceptance-certificate.issue / verify
- coverage-report.read
- risk-score.read
- verification.run-mutation / verification.run-cross-model
- patch.preview-blast-radius
- sbom.generate

Active count는 여전히 49이며 candidate 13개를 합쳐 62라고 미리 활성화하지 않는다.

### Contract
개발 선행 Contract 후보를 추가했다.
- `coverage-report.candidate.v1.schema.json`
- `acceptance-certificate.candidate.v1.schema.json`
- `policy-pack-version.candidate.v1.schema.json`

CoverageReport는 excluded/unknown/unobservable을 숨긴 100% completeness claim을 막고 `final_claim_allowed=false`를 유지한다.
AcceptanceCertificate는 결함 부재 증명이 아니라 선언된 scope/policy/evidence/time에 대한 서명된 결과 artifact로 제한한다.
PolicyPackVersion은 fixed invariant 약화를 금지하고 qualification evidence를 요구한다.

## 6. Open Decision → Policy Binding — DONE AS CANDIDATE
`contracts/open-decision-policy-binding.candidate.v1.json`을 추가했다.

특히 구현 semantics에 직접 영향을 주는 C9/C10/C12/C14/C15, B4/B6와 상거래 A3/A5/A6를 다음 중 하나로 분류한다.
- FIXED_INVARIANT
- INDUSTRY_PROFILE
- TENANT_CONFIGURABLE_WITH_FLOOR
- CONTRACT_OVERRIDE_WITH_CEILING
- PRODUCT_TIER
- UNRESOLVED_SAFE_FLOOR

미확정값은 임의 상수로 숨기지 않는다. `policy_source=UNRESOLVED` 또는 safe floor provenance를 Receipt에 결속하고 필요한 경우 HOLD/BLOCKED/REASSESSMENT_REQUIRED로 fail-closed한다.

## 7. 이번 배치에서 해결한 Fresh Review Finding
1. Authority hierarchy가 Semantic Assurance를 포함하지 않음 → 해결
2. 00 Master가 128/136 이후 상태를 반영하지 않음 → 해결
3. operation count 45 vs 49 / implementation status 충돌 → 단일 machine authority로 해결
4. Business Actor가 5-role RBAC와 연결되지 않음 → Candidate mapping 계약 생성
5. 핵심 UI/API 기능이 Operation/Contract 없이 DESIGN_ONLY → 13 operation + 3 핵심 schema candidate materialization
6. Open Decision이 구현상 임의 상수가 될 위험 → policy binding/safe floor 생성

## 8. 남은 경계
이 6개를 닫았다고 Design QA가 PASS된 것은 아니다. 아래는 계속 Design QA/구현 작업이다.
- non-ID Requirement exact population
- authoritative Applicability
- repository-wide orphan/contradiction scan
- content SHA-256 inventory
- canonical registry digest/reconstructable baseline
- Candidate Contract fixture/validator/runtime activation
- compile/JUnit/실행 Evidence

## 9. Claude 개발 진입 판정
이제 Claude는 `137_CLAUDE_DEVELOPMENT_MASTER_HANDOFF.md`의 Batch 0을 시작할 수 있다.

개발 시작 가능 상태:
`PRE_IMPLEMENTATION_RECONCILIATION_COMPLETE / DEVELOPMENT_ENTRYPOINT_READY`

하지만 현재 authority는 여전히:
`DESIGN_QA_HOLD / IMPLEMENTATION_NOT_STARTED / TEST_NOT_STARTED / INDEPENDENT_ASSURANCE_NOT_STARTED / PRODUCTION_NOT_AUTHORIZED`

## 10. 승격 금지
이번 문서 또는 Candidate Contract 존재만으로 다음을 선언하지 않는다.
- DESIGN_LOCKED
- IMPLEMENTED
- TEST_PASS
- ACTIVE_SELECTOR
- FINAL_LOCK
- PRODUCTION_GO
- COMMERCIAL_GO
