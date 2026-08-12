# ONSure Assurance Policy Profile·Rule Governance 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parents: `35`, `38`, `39`, `41`

## 1. 목적
Currentness, Composition, Certificate, Qualification, Offline, Enterprise approval의 정책값을 코드 상수나 문서 문장에 흩어두지 않고 **versioned Assurance Policy Profile**로 관리한다. 정책 변경이 기존 Assurance 의미를 조용히 바꾸지 않도록 한다.

## 2. Policy Profile 종류
- COMPOSITION_POLICY
- CURRENTNESS_POLICY
- INVALIDATION_POLICY
- CERTIFICATE_POLICY
- OFFLINE_POLICY
- AUTHORITY_POLICY
- QUALIFICATION_POLICY
- AI_STATISTICAL_POLICY
- DISTRIBUTED_EXECUTION_POLICY
- PLUGIN_TRUST_POLICY

하나의 거대 profile 대신 domain profile로 분리하고, Final/Certificate에는 사용한 전체 policy set digest를 결속한다.

## 3. 공통 필드
- policy_profile_id
- policy_type
- version
- organization_scope 또는 GLOBAL_DEFAULT
- applicable_product/plan/archetype
- effective_from
- effective_until nullable
- predecessor_version nullable
- canonical rules object
- policy_digest
- owner
- reviewer/approver refs
- state: DRAFT|REVIEW|APPROVED|ACTIVE|SUPERSEDED|REVOKED
- created_at/activated_at

## 4. Rule Entity
- rule_id
- rule_version
- condition
- outcome/action
- severity/priority
- rationale
- source requirement/standard refs
- configurable parameters
- non-overridable flag
- effective interval

Rule DSL/representation은 machine-evaluable하되 임의 code execution을 허용하지 않는다. 정확한 DSL은 구현단계에서 별도 결정한다.

## 5. Composition Policy 예
- Critical HARD negative propagation
- unresolved child ceiling
- STALE child currentness ceiling
- N/A approval requirement
- SOFT dependency non-impact proof minimum
- minimum assurance strength by certificate tier

Critical fail-closed invariant는 tenant가 완화할 수 없는 `non_overridable=true` 후보로 관리한다.

## 6. Currentness Policy 예
- evidence type TTL
- validator qualification max age
- observer snapshot age
- dependency advisory freshness
- runtime population observation window
- materiality class→state mapping
- offline uncertainty thresholds

## 7. Invalidation Policy 예
Event type + subject type + affected claim class에 따라:
- LIMITED_REPERFORMANCE_REQUIRED
- REASSESSMENT_REQUIRED
- INVALIDATING
- REVOCATION_REVIEW_REQUIRED
을 mapping한다.

Rule이 coverage하지 못하는 event/subject 조합은 SAFE가 아니라 UNKNOWN_IMPACT다.

## 8. Certificate Policy 예
- product/plan별 minimum strength
- production-bound currentness required 여부
- certificate expiry/revalidation due
- required independent verifier count/profile
- material limitation disclosure rules
- public projection profile
- revocation authority

## 9. Offline Policy 예
- trust bundle validity
- revocation snapshot max age
- trusted time source requirements
- offline grace by assurance tier
- allowed high-risk operations
- reconnect conflict handling

License offline grace와 Assurance offline currentness grace를 서로 다른 parameter로 관리한다.

## 10. Authority Policy 예
- operation→required roles
- four-eyes principal count
- admin-owner separation requirement
- delegation depth/TTL
- break-glass allowed operations
- mandatory post-review SLA
- non-delegable operations

## 11. Qualification Policy 예
- target archetype별 required benchmark/fault classes
- Critical seeded escape tolerance
- recall/precision threshold
- qualification validity
- requalification trigger severity
- independent verifier requirements

## 12. AI Statistical Policy 예
- risk class별 minimum sample size
- confidence method
- critical failure tolerance
- seed/scenario precommit rules
- allowed exclusion criteria
- nondeterminism threshold

결과를 본 뒤 threshold를 바꾸려면 새 policy version + historical impact analysis가 필요하다.

## 13. Distributed Execution Policy 예
- WorkUnit lease duration
- heartbeat
- retry limits/backoff
- quarantine thresholds
- tenant concurrency/fairness
- aggregation timeout

Resource 정책이 truth/denominator semantics를 완화하지 못한다.

## 14. Plugin Trust Policy 예
- publisher trust tier
- required signature roots
- privilege class
- sandbox profile
- qualification minimum
- compatibility/deprecation
- update requalification rule

## 15. Policy Activation
DRAFT → REVIEW → APPROVED → ACTIVE.
Activation 전:
- schema validation
- semantic fixture
- conflict analysis
- backward/historical impact preview
- required human approval

ACTIVE profile은 signed selector/registry에서 정확한 version/digest를 지정한다.

## 16. Policy Conflict
Global/Organization/Product/Case profile이 겹칠 수 있다.
우선순위 후보:
1. non-overridable safety invariant
2. regulatory/contract hard requirement
3. organization policy
4. product/plan profile
5. case-specific stricter override

Case override는 상위 hard rule보다 약해질 수 없다. Conflict resolution 결과를 PolicyResolutionReceipt로 남긴다.

## 17. Stricter-only Override
특정 rule은 customer가 더 엄격하게만 조정 가능하다.
예:
- minimum assurance strength 상향
- TTL 단축
- reviewer count 증가
- offline grace 단축

낮추기는 privileged policy change + 별도 governance가 필요하거나 완전 금지한다.

## 18. Policy Change Historical Impact
ACTIVE policy material change 시:
1. affected claim/certificate population 검색
2. old vs new rule result shadow calculation
3. difference report
4. revalidation/reassessment population 결정
5. activation approval
6. currentness propagation

정책 변경 시 과거 Certificate를 자동 재작성하지 않는다.

## 19. Rule Weakening Guard
다음은 High-risk weakening:
- Critical propagation 완화
- N/A proof requirement 완화
- qualification threshold 하향
- evidence TTL 증가
- offline grace 증가
- independence requirement 축소
- certificate minimum strength 하향
- plugin privilege 확대

Hidden/qualification regression, human approval, historical impact analysis 없이는 activation 금지.

## 20. Policy Read / Cache
Runtime은 policy ID 이름만 읽지 않고 active version/digest를 resolve한다. Cache entry는 selector epoch와 policy digest를 가진다. selector/policy change 시 invalidation한다.

## 21. API 후보
- `POST /v2/assurance-policies`
- `POST /v2/assurance-policies/{id}/versions`
- `POST /v2/assurance-policies/{id}/versions/{v}/validate`
- `POST /v2/assurance-policies/{id}/versions/{v}/impact-preview`
- `POST /v2/assurance-policies/{id}/versions/{v}/activate`
- `GET /v2/assurance-policy-resolution`
- `GET /v2/assurance-policies/active-set`

## 22. Negative Test
- candidate policy file automatically active
- tenant override weakens non-overridable rule
- expired policy remains cached
- same ID different bytes without digest change detection
- result-visible benchmark then threshold weakening
- policy change without historical impact
- conflict picks permissive rule silently
- case override extends offline grace over global maximum
- qualification threshold lowered without approval
- active selector points DRAFT profile

## 23. 수용기준
- 모든 Final/Composition/Certificate result가 exact policy set digest를 참조.
- rule 변경이 previous result 의미를 조용히 바꾸지 않음.
- unknown/conflicting policy가 fail-open하지 않음.
- non-overridable invariant를 customer/operator가 약화하지 못함.
- policy activation은 impact preview와 approval evidence를 가짐.
- high-risk weakening은 일반 rule addition보다 강한 gate를 가짐.

## 24. 비최종 경계
구체 threshold 값은 35 Open Decisions 확정 전까지 DRAFT다. 이 문서는 값을 확정하는 것이 아니라 값을 안전하게 제정·변경하는 구조를 정의한다.
