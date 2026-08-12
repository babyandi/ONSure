# ONSure 설계 완성도·잔여 공백 기준선

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Scope: `docs/master/00~08`, `semantic-assurance/00~35`

## 1. 목적
ONSure 설계가 어느 영역까지 개발자가 추가 설명 없이 구현 가능한 수준으로 닫혔는지, 남은 공백이 단순 상세화인지 구조적 설계 공백인지 구분한다. 이 완성도는 **구현률·검증률이 아니라 설계 명세 완성도**다.

## 2. 평가 기준
각 영역을 다음 5축으로 평가한다.
1. Functional Closure: 기능/입력/출력/상태/권한이 정의됐는가
2. Architecture Closure: Entity/API/Event/Invariant가 정의됐는가
3. Failure Closure: negative/adversarial/recovery가 정의됐는가
4. Governance Closure: authority/freshness/revocation/SoD가 정의됐는가
5. Trace Closure: Parent requirement→review→architecture→UX→test→AI/open decision이 연결되는가

100%는 실제 구현·실행 완료가 아니라 **설계상 더 이상 독립된 필수 축이 남지 않았다는 후보 상태**를 의미한다.

## 3. 영역별 현재 완성도
| 영역 | 완성도 | 상태 | 남은 핵심 |
|---|---:|---|---|
| Product/Service Definition | 94% | HIGH | Certificate/Assurance tier 상품화 정책 |
| Functional Requirements | 97% | VERY_HIGH | FR-META-044~060 Contract trace |
| OReview | 95% | VERY_HIGH | 신규 domain machine registry 연결 |
| Architecture/Data/API | 94% | HIGH | 신규 Entity별 persistence/event schema 상세 |
| UI/UX/Workflow | 92% | HIGH | Certificate public verifier, graph visualization detail |
| Test/Operation | 94% | HIGH | 33 companion의 fixture ID/schema화 |
| AI/Agent Methodology | 94% | HIGH | statistical threshold/Open Decision 확정 |
| Governance/Open Decisions | 89% | HIGH | 35의 정책값 확정 필요 |
| Semantic Assurance Core | 96% | VERY_HIGH | 신규 29~35 Contract 편입 |
| v1→v2 Migration | 90% | HIGH | 실제 migration exception policy는 실행결과 필요 |
| Authority/Independence | 93% | HIGH | enterprise delegation contract 세부화 |
| Final Reconstruction/Lock | 94% | HIGH | Certificate/currentness post-final chain 연결 |
| Deployment/Runtime Currentness | 91% | HIGH | machine contract/persistence/observer profile |
| Revocation/Recovery | 90% | HIGH | revocation policy matrix/impact algorithm contract |
| Distributed Assurance Composition | 89% | HIGH | formal composition contract/algebra fixture |
| Evidence Graph | 88% | HIGH | graph-head/canonical edge contract/persistence |
| Certificate/Public Proof | 87% | HIGH | certificate schema/public verification contract |
| Offline Assurance | 86% | HIGH | trusted-time provider profile/grace policy |
| Enterprise Governance | 87% | HIGH | AuthorityGrant/four-eyes/break-glass machine contract |
| Scale/Distributed Work | 85% | HIGH | WorkUnit/lease/aggregation receipt schema |
| Plugin/Adapter Trust | 86% | HIGH | PluginManifest/qualification contract |
| AI Runtime Assurance | 91% | HIGH | behavior population/statistics contract |
| ONSure Meta-Assurance | 89% | HIGH | release/archetype qualification contract |

## 4. 전체 판단
현재 설계 기준선은 약 **92~94%** 수준으로 평가한다. 29~35를 추가하기 전의 주요 구조 공백이던 Deployment Currentness, Revocation, Composition, Evidence Graph, Certificate, Offline, Enterprise, Scale, Plugin, AI Runtime, Meta-Assurance는 이제 독립 상세설계가 존재한다.

남은 6~8%는 주로 다음이다.
- 상세설계를 machine contract로 내리는 작업
- formal composition/invalidation algorithms의 canonical rule 표현
- 정책값(Open Decision) 확정
- persistence/index/query 전략 상세
- 대규모 graph/currentness 성능 전략
- public Certificate interoperability/profile

이는 새로운 큰 제품 기능이 빠져 있다는 의미보다는 **설계→계약 경계의 미완성** 비중이 커졌다는 뜻이다.

## 5. P0 설계 공백
현재 설계 차원에서 반드시 닫혀야 하는 다음 P0 후보는 아래다.

### DG-P0-01 Product Composition Contract 부재
29~35에는 규칙이 있으나 exact machine schema가 아직 없다. HARD child failure/unknown/stale propagation을 consumer 구현마다 다르게 만들 위험.

### DG-P0-02 Evidence Graph Canonical Contract 부재
Node/edge relation은 정의했으나 graph head, canonical ordering, cycle policy, tenant namespace, invalidation transaction을 machine contract로 내려야 한다.

### DG-P0-03 Certificate Contract v2 부재
기존 Acceptance Certificate 개념과 신규 Assurance Certificate의 currentness/revocation/limitation/independence semantics가 machine level에서 분리되지 않았다.

### DG-P0-04 AuthorityGrant/Delegation Contract 부재
Enterprise four-eyes/delegation/break-glass 원칙이 설계 수준이다. 실제 principal uniqueness/delegation subset 검사를 Contract로 강제해야 한다.

### DG-P0-05 WorkUnit Aggregation Integrity Contract 부재
Distributed execution의 duplicate/stale lease/partition closure/deterministic aggregation을 machine contract로 표현해야 한다.

### DG-P0-06 AI Behavior Population Contract 부재
Nondeterministic AI 검증에서 sample population/seed/exclusion/confidence 결과를 standard object로 고정해야 한다.

### DG-P0-07 ONSure Release Qualification Contract 부재
ONSure 자신이 어느 target archetype에 대해 어떤 자격을 갖는지 Final consumer가 machine-read할 권위가 필요하다.

## 6. P1 설계 공백
- Certificate 공개 profile/interoperability format
- Evidence Graph 장기보관/compaction
- Multi-region very-large population snapshot tree
- Offline trusted-time provider adapter
- plugin marketplace lifecycle
- qualification cost/risk optimization
- currentness evaluation cache invalidation
- customer-facing explanation localization

## 7. 설계 종료조건 후보
ONSure 설계는 다음이 만족되면 `DESIGN_BASELINE_CANDIDATE_COMPLETE`로 올릴 수 있다.
- 29~35의 모든 핵심 Entity가 Candidate Contract를 가짐
- FR-META-001~060이 최소 하나의 Contract/Review/Test trace를 가짐
- Product Composition과 Invalidation rule이 machine-readable
- Certificate/Offline/Enterprise authority가 Contracted
- AI Runtime/Behavior Population과 ONSure Release Qualification이 Contracted
- 35 Open Decisions 중 P0 machine semantics에 영향을 주는 항목이 CONFIRMED 또는 명시적 configurable policy로 변환됨
- 02~08/companion 사이 trace gap 0

## 8. 구현과의 경계
설계 완성도 94%가 제품 완성도 94%를 의미하지 않는다. 실제 status는 계속 `CANDIDATE_ONLY / NON_FINAL`이다. Claude 구현/시험 결과가 나오기 전까지 Implementation/Execution/Qualification 상태를 승격하지 않는다.
