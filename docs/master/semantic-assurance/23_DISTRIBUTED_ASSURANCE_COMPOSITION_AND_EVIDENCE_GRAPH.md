# ONSure Distributed Assurance Composition·Evidence Graph 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parent: `22_DEPLOYMENT_RUNTIME_CURRENTNESS_AND_REVOCATION_DESIGN.md`

## 1. 목적
현대 제품은 하나의 Target이 아니라 Web/API/DB/Batch/Agent/LLM/RAG/External Service/Region 등 여러 Target의 조합이다. 개별 Target 결과를 단순 평균하거나 모든 PASS 문자열을 모아 Product PASS로 만드는 것을 금지하고, **제품 수준 Assurance 합성 규칙과 Evidence Graph**를 정의한다.

## 2. 핵심 원칙
- Product Assurance는 Target Assurance의 산술평균이 아니다.
- Critical dependency의 HOLD/UNKNOWN/STALE는 상위 PASS를 제한한다.
- N/A는 PASS가 아니며 applicability proof가 필요하다.
- self-validation PASS와 independent PASS를 동일 강도로 합성하지 않는다.
- currentness가 다른 결과를 동일 generation처럼 합치지 않는다.
- 서로 모순되는 Evidence는 선택적으로 좋은 쪽만 채택하지 않는다.
- composition 결과는 구성요소 상태뿐 아니라 dependency topology와 obligation propagation을 사용한다.

## 3. Entity
### 3.1 AssuranceSubject
subject_type: PRODUCT|SYSTEM|SERVICE|MODULE|DEPLOYMENT|RUNTIME_INSTANCE|AI_COMPONENT|EXTERNAL_DEPENDENCY
subject_id
subject_digest
parent_subject_id nullable

### 3.2 AssuranceNodeResult
- subject_id
- claim_id
- decision
- assurance_level
- currentness_state
- independence_class
- qualification_state
- evidence_set_digest
- observation_epoch
- criticality

### 3.3 AssuranceDependencyEdge
- from_subject
- to_subject
- dependency_type: REQUIRES|CALLS|READS|WRITES|DEPLOYS_TO|AUTHORIZES|PROVIDES_GROUND_TRUTH|PROVIDES_ORACLE|PROVIDES_POLICY
- propagation_class: HARD|SOFT|CONDITIONAL|INFORMATIONAL
- affected_claims[]
- condition_expression_ref nullable

### 3.4 CompositionSnapshot
- composition_id
- product_subject_id
- subject_population_digest
- edge_population_digest
- requirement_epoch
- target_epoch
- evidence_epoch
- composition_rule_version
- generated_at
- decision
- ceiling_reason[]

## 4. Assurance Level 후보
Assurance Level은 Decision과 별도 축이다.
- AL0_UNASSESSED
- AL1_EXECUTED
- AL2_EVIDENCE_BOUND
- AL3_INDEPENDENTLY_REPERFORMED
- AL4_QUALIFIED
- AL5_PRODUCTION_BOUND_CURRENT

`PASS@AL1`은 `PASS@AL4`와 동일하지 않다.

상위 합성 Assurance Level은 기본적으로 필수 Critical Child의 최저 Level을 넘을 수 없다. 단, 특정 Child가 해당 Claim에 N/A임이 독립적으로 증명된 경우 denominator에서 제외할 수 있다.

## 5. Decision Composition
### 5.1 Hard Dependency
Critical hard dependency가 FAIL/BLOCKED/INVALIDATED/REVOKED이면 상위 PASS 금지.
HOLD/UNKNOWN/NOT_RUN/INCONCLUSIVE이면 상위 결과는 최소 HOLD/INCONCLUSIVE ceiling.
STALE/REASSESSMENT_REQUIRED이면 상위 currentness는 CURRENT 불가.

### 5.2 Soft Dependency
Soft dependency 실패가 상위 claim에 영향을 주지 않는다는 impact proof가 있어야 downgrade 없이 유지 가능하다. 단순 `soft=true` 자기선언 금지.

### 5.3 N/A
N/A는 다음을 요구한다.
- applicability rule id
- evaluated subject digest
- rationale
- evaluator identity
- evidence digest
- challenge/review status

### 5.4 Conflicting Results
동일 claim/context에서 PASS와 FAIL이 공존하면 최신 결과만 자동 선택하지 않는다.
- supersession relation이 증명되면 superseding result 사용
- 아니면 `CONFLICT_HOLD`

### 5.5 Retry
Retry PASS가 이전 실패를 삭제하지 않는다. Attempt graph를 유지하고 실패가 product state에 어떤 의미를 갖는지 disposition receipt가 있어야 한다.

## 6. Multi-target Product Composition 예
Product P:
- Web: PASS AL4 CURRENT
- API: PASS AL4 CURRENT
- DB: PASS AL3 CURRENT
- Agent: HOLD AL2 CURRENT
- RAG: PASS AL4 CURRENT
- LLM Provider: PASS AL4 STALE

Agent가 Product 핵심 기능의 HARD dependency이면 Product PASS 금지. LLM Provider가 STALE이면 Product currentness도 CURRENT 불가. 결과 예:
`decision=HOLD, assurance_level=AL2, currentness=REASSESSMENT_REQUIRED`.

## 7. Evidence Graph
### Node 종류
- Source
- Requirement
- Policy
- Oracle
- Fixture
- Execution
- Observation
- Finding
- RCA
- Patch
- Approval
- Qualification
- EvidenceReceipt
- FinalLock
- Deployment
- RuntimeObservation
- Certificate

### Edge 종류
- DERIVED_FROM
- REPERFORMED_FROM
- INDEPENDENTLY_CONFIRMS
- CONTRADICTS
- SUPERSEDES
- INVALIDATES
- REVOKES
- SATISFIES
- VIOLATES
- DEPENDS_ON
- DEPLOYMENT_OF
- OBSERVATION_OF
- QUALIFIES
- APPROVES

각 edge는 edge_id, created_at, producer, source_digest, target_digest, rule_id, evidence_digest를 가진다.

## 8. Graph Invariant
- cycle이 허용되는 관계와 금지되는 관계를 구분한다. DERIVED_FROM/SUPERSEDES는 cycle 금지.
- CONTRADICTS는 symmetric semantic relation으로 취급하지만 저장 edge는 방향성을 가질 수 있다.
- INVALIDATES/REVOKES는 기존 node 삭제가 아니라 validity generation 변경을 생성한다.
- FinalLock/Certificate는 모든 material parent를 graph에서 재구성 가능해야 한다.
- dangling edge 금지.
- tenant 간 edge 금지. cross-tenant external public authority는 별도 public-authority namespace를 사용한다.

## 9. Composition Engine
입력:
- exact subject population
- exact dependency edge population
- applicable claims/requirements
- current evidence graph head
- current policy/composition rule version

처리:
1. population lock
2. applicability closure
3. dependency topology validation
4. node result freshness validation
5. contradiction resolution
6. hard dependency propagation
7. assurance level ceiling
8. currentness ceiling
9. product result generation

출력:
- CompositionSnapshot
- CompositionReceipt
- unresolved conflict list
- ceiling explanation graph

## 10. API 후보
- `POST /v2/assurance-subjects`
- `POST /v2/assurance-dependencies`
- `GET /v2/assurance-graphs/{productId}`
- `POST /v2/assurance/compositions`
- `GET /v2/assurance/compositions/{id}`
- `GET /v2/assurance/compositions/{id}/explanation`
- `POST /v2/evidence-graph/edges`
- `GET /v2/evidence-graph/impact/{nodeId}`

## 11. UI/UX
제품 Dashboard는 단일 녹색 PASS 대신 다음을 함께 표시한다.
- Product decision
- Assurance Level
- Currentness
- Critical dependency 상태
- weakest required child
- unresolved conflict
- excluded/N/A population
- last composition time

사용자가 Product 결과를 클릭하면 `왜 이 상태인가`를 dependency/evidence graph로 설명한다.

## 12. Negative Test
- 동일 child를 두 번 넣어 denominator 부풀리기
- critical child를 soft로 위장
- N/A rationale 없는 제외
- stale child를 CURRENT product에 포함
- self-validation AL1을 independent AL4로 승격
- conflicting PASS/FAIL 중 PASS만 선택
- supersession 없는 latest-wins
- cross-tenant evidence edge
- graph cycle로 parent identity 숨기기
- retry PASS로 최초 critical failure 삭제
- region 하나를 population에서 누락

## 13. 수용기준
- Product 결과가 exact subject/edge population에서 재계산 가능해야 한다.
- 모든 ceiling은 최소 하나의 graph path로 설명 가능해야 한다.
- Critical HARD dependency의 부정/미확정 상태를 상위 PASS가 숨기지 못한다.
- composition rule version 변경 시 기존 Product result는 재평가 대상이다.
- composition snapshot이 없으면 Product-level Final Certificate 발급 금지.

## 14. 기존 산출물 적용 위치
- `02`: Multi-target/Product Assurance 기능 요구사항
- `03`: composition/graph review domain
- `04`: AssuranceSubject/Edge/Composition Entity/API
- `05`: weakest-link/explanation graph UX
- `06`: denominator manipulation/contradiction/propagation tests
- `07`: Agent/RAG/LLM dependency composition
- `08`: Assurance Level naming, hard/soft dependency governance를 Open Decision으로 관리

## 15. 비최종 경계
이 문서는 현재 Claude 개발 Batch의 active contract가 아니다. 후속 Contract/Fixture/Runtime 설계 후 별도 Handoff한다.
