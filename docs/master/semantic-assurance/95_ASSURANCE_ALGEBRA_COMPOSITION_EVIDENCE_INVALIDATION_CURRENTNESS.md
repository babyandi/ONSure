# ONSure Assurance Algebra·Composition·Evidence·Invalidation·Currentness 최종 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Covers: Task 17~21

## 1. Assurance vector
`A=(decision,strength,currentness,independence,qualification,uncertainty)`.

Positive product claim은 decision 하나로 결정하지 않는다.

### Ceiling
- strength: required critical child 최소값
- currentness: STALE/UNKNOWN child가 있으면 parent CURRENT 금지
- independence: required independent lane 미충족 시 independent claim 금지
- qualification: required validator/adapter/oracle NOT_PROVEN이면 해당 claim ceiling 적용
- uncertainty: critical unknown > allowed budget이면 HOLD

## 2. Product Composition
Dependency edge:
`HARD|SOFT|CONDITIONAL|INFORMATIONAL`.

HARD critical child:
- FAIL/BLOCKED/INVALIDATED/REVOKED → parent positive PASS 금지
- HOLD/UNKNOWN/NOT_RUN/INCONCLUSIVE → parent HOLD ceiling
- STALE/REASSESSMENT_REQUIRED → parent CURRENT 금지

SOFT는 영향 없음이 evidence로 증명되어야 한다.

Multi-region/canary/partial population은 exact population digest와 cohort scope를 사용한다.

## 3. Evidence Graph Formal Model
Node: Requirement, Source, Oracle, Execution, Observation, Finding, Qualification, Approval, Final, Deployment, Runtime, Composition, Certificate, Recovery.
Edge: DERIVED_FROM, DEPENDS_ON, SATISFIES, VIOLATES, CONTRADICTS, SUPERSEDES, INVALIDATES, REVOKES, DEPLOYMENT_OF, OBSERVATION_OF, QUALIFIES, APPROVES.

Acyclic required: DERIVED_FROM, SUPERSEDES. Dangling authoritative edge 금지.

## 4. Invalidation Rule
Trigger categories:
- requirement/policy
- artifact/dependency/config
- CVE/MissedFinding
- validator/oracle defect
- authority/key
- runtime/model/prompt/RAG/provider drift
- recovery/ledger inconsistency

Output:
`affected_node_set_digest`, action per node: NO_IMPACT|STALE|REASSESSMENT_REQUIRED|INVALIDATED|REVOKE_CANDIDATE.

자동 detector는 REVOKED를 직접 발행하지 않으며 signed revocation authority가 필요하다.

## 5. Currentness Engine
Input:
- final_lock
- target/deployment/runtime identity
- current policy epoch
- authority/key epoch
- validator qualification
- observation freshness
- unresolved drift/events

Result:
CURRENT|STALE|REASSESSMENT_REQUIRED|INVALIDATED|REVOKED|UNKNOWN.

TTL은 정책값이며 코드 상수 금지. observer failure는 UNKNOWN/HOLD 방향으로만 작동한다.

## 6. Acceptance
- parent state 계산이 deterministic
- ceiling explanation graph 제공
- retry/latest-wins로 contradiction 삭제 금지
- invalidation impact가 certificate까지 전파
- currentness는 저장된 PASS가 아니라 current inputs에서 재계산
