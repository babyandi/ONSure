# ONSure Post-v2 Final Review Finding 확장 Ledger

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parent: `10_FINDING_LEDGER.md`, `19_FINAL_REVIEW_AND_EXECUTION_BLOCKERS.md`

## 1. 목적
v2 Candidate Contract·Runtime·Fixture를 만든 뒤 그 **새 구조 자체를 다시 검토**하면서 발견된 결함을 별도 canonical Finding으로 보존한다. 기존 FL-P0-001~132 / FL-P1-001~048을 덮어쓰지 않는다.

현재 확장 기준:
- P0: `FL-P0-001~141`
- P1: `FL-P1-001~050`
- raw candidate observation baseline: 562

수정이 같은 Candidate branch에 반영됐더라도 실제 compile/test/independent verification 전에는 `VERIFIED_CLOSED`로 처리하지 않는다.

## 2. FL-P0-133 SEMANTIC_V2_BRIDGE_COMMON_AUTHORIZATION_BYPASS
- 분류: `CANONICAL_GATE_BYPASS`
- Source: `SemanticAssuranceV2DispatcherBridge.java`, `TenantRbacService.java`
- 결함: 초기 v2 Bridge가 자체 role check만 수행하고 기존 durable tenant/resource ownership boundary를 거치지 않았다.
- 실패 시나리오: semantic operation role은 허용되지만 target resource는 다른 tenant 소유인 경우 ownership 검증 없이 v2 service가 호출될 수 있다.
- 조치: `TenantRbacService`에 semantic operation family를 정식 등록하고 실제 semantic operation 이름으로 durable authorization transaction 안에서 semantic call을 실행하도록 수정.
- 추가 보강: cross-tenant semantic invocation JUnit 추가.
- 현재 disposition: `DESIGN_ACCEPTED / IMPLEMENTATION_CANDIDATE_PRESENT / TEST_DESIGNED / EXECUTION_NOT_RUN`.

## 3. FL-P0-134 SEMANTIC_V2_TARGET_PATH_SCOPE_ESCAPE
- 분류: `NEW_DEFECT_CLASS`
- Source: WorkflowService file read boundary, `ProductCatalog.RegisteredTarget`
- 결함: tenant-owned target이라는 사실을 확인해도 file path가 workspace 전체에서 선택 가능하면 다른 target/tenant artifact를 읽을 수 있다.
- 조치: RegisteredTarget.sourceRoot를 server-side authoritative root로 주입하고 Bridge와 Service 양쪽에서 target root 밖 path를 거부. `_authorized_*` caller injection 금지.
- Deployment: v1 deployment install 구조가 target-scoped root를 제공하지 않으므로 target-bound deployment identity가 생길 때까지 `deployment.verify-installed`는 BLOCKED.
- 현재 disposition: `DESIGN_ACCEPTED / IMPLEMENTATION_CANDIDATE_PRESENT / TEST_DESIGNED / EXECUTION_NOT_RUN`.

## 4. FL-P0-135 SHADOW_RUNTIME_SCHEMA_SEMANTIC_DRIFT
- 분류: `CROSS_CONTRACT_SEMANTIC_CONFLICT`
- Source: `SemanticAssuranceShadowGateComparator.java`, `shadow-gate-comparison.candidate.v1.schema.json`
- 결함: Runtime comparator의 missing receipt 표현과 contract discriminator가 Schema와 달랐다.
- 조치: missing receipt를 JSON null로 보존하고 contract discriminator를 Schema와 통일.
- 현재 disposition: `DESIGN_ACCEPTED / IMPLEMENTATION_CANDIDATE_PRESENT / EXECUTION_NOT_RUN`.

## 5. FL-P0-136 V2_SCHEMA_INSTANCE_REGISTRY_DRIFT
- 분류: `DESIGNED_CONTROL_OUTSIDE_CANONICAL_GATE_PATH`
- Source: v2 Candidate Schema inventory와 Schema Instance Registry
- 결함: 후속 Final/Independence/GT/Hidden/Shadow Schema가 추가됐지만 Registry가 기존 14개에 머물렀다.
- 조치: Registry를 23 Schema / valid 23 / semantic-invalid 46으로 확장하고 pending schema count=0으로 동기화.
- 현재 disposition: `DESIGN_ACCEPTED / TEST_DESIGNED / EXECUTION_NOT_RUN`.

## 6. FL-P0-137 SEMANTIC_V2_PUBLIC_WORKFLOW_SERVICE_BYPASS
- 분류: `CANONICAL_GATE_BYPASS`
- Source: 초기 `SemanticAssuranceV2WorkflowService`
- 결함: Service가 public constructor/public dispatch를 노출해 Bridge/TenantRbacService를 통하지 않는 same-process 호출 경로가 존재했다.
- 실패 시나리오: 내부 component가 WorkflowService를 직접 생성하고 target ownership/server-bound path 검증 없이 semantic operation 호출.
- 조치: WorkflowService를 package-local로 내리고 dispatch/supports/constructor를 package-local로 제한. 모든 operation에 `_authorized_project_id`, `_authorized_target_id`, `_authorized_target_root` server-bound context를 필수화.
- 현재 disposition: `DESIGN_ACCEPTED / IMPLEMENTATION_CANDIDATE_PRESENT / TEST_DESIGNED / EXECUTION_NOT_RUN`.

## 7. FL-P0-138 SEMANTIC_V2_INDEPENDENCE_SELF_ATTESTATION_ACCEPTED
- 분류: `DECLARATIVE_ASSERTION_WITHOUT_ENFORCEMENT_PROOF`
- Source: 초기 `semantic.independence.assess`, `assurance.otester.accept`, `assurance.oaudit.accept`
- 결함: caller가 `independent=true`, implementation/oracle/discovery/knowledge boolean, `signature_verified=true`, `qualification_state=QUALIFIED`를 제출하면 강한 independence acceptance candidate가 만들어질 수 있었다.
- 조치: caller-declared independence를 무시하고 cryptographic principal/profile/qualification verifier가 연결되기 전까지 `HOLD`, `independent=false`, `accepted=false`를 반환.
- 현재 disposition: `DESIGN_ACCEPTED / IMPLEMENTATION_CANDIDATE_PRESENT / TEST_DESIGNED / EXECUTION_NOT_RUN`.

## 8. FL-P0-139 SEMANTIC_V2_HUMAN_ACCEPTANCE_SELF_ATTESTATION
- 분류: `DECLARATIVE_ASSERTION_WITHOUT_ENFORCEMENT_PROOF`
- Source: 초기 `assurance.human-accept`
- 결함: caller의 `explicit_acceptance=true`, profile digest, receipt digest, freshness string만으로 Human Acceptance candidate가 만들어질 수 있었다.
- 조치: signed human acceptance authority verifier가 연결되기 전까지 항상 HOLD. caller-declared acceptance를 근거로 사용하지 않음.
- 현재 disposition: `DESIGN_ACCEPTED / IMPLEMENTATION_CANDIDATE_PRESENT / TEST_DESIGNED / EXECUTION_NOT_RUN`.

## 9. FL-P0-140 SEMANTIC_V2_REQUALIFICATION_SELF_ATTESTED_METRICS
- 분류: `DECLARATIVE_ASSERTION_WITHOUT_ENFORCEMENT_PROOF`
- Source: 초기 `semantic.validator.requalify`
- 결함: caller가 `critical_miss_count=0`, `isolated_execution_proven=true`, `benchmark_precommitted=true`를 선언하면 `QUALIFIED_NONFINAL`을 받을 수 있었다.
- 조치: 실제 qualification execution receipt와 independent reperformance가 연결될 때까지 `NOT_QUALIFIED/HOLD`; self-attested metrics 무시.
- 현재 disposition: `DESIGN_ACCEPTED / IMPLEMENTATION_CANDIDATE_PRESENT / TEST_DESIGNED / EXECUTION_NOT_RUN`.

## 10. FL-P0-141 SEMANTIC_V2_AUTHORITY_REVALIDATION_SELF_ATTESTATION
- 분류: `DECLARATIVE_ASSERTION_WITHOUT_ENFORCEMENT_PROOF`
- Source: 초기 `semantic.authority.revalidate`
- 결함: caller `revoked=false`, `expired=false`와 문자열 profile/epoch만으로 valid-at-effect candidate가 생성될 수 있었다.
- 조치: authority read-back receipt를 요구하고 실제 effect-time verifier가 연결되기 전에는 `valid_at_effect=false/HOLD` 유지.
- 현재 disposition: `DESIGN_ACCEPTED / IMPLEMENTATION_CANDIDATE_PRESENT / EXECUTION_NOT_RUN`.

## 11. FL-P1-049 V2_RECONSTRUCTOR_NULL_FAIL_CLOSED_CRASH
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- Source: 초기 Reconstructor
- 결함: missing legacy field null이 `Map.copyOf()`에 들어가 HOLD receipt 대신 runtime exception 가능.
- 조치: `NOT_AVAILABLE`/explicit gap + null-safe immutable map.
- 현재 disposition: `DESIGN_ACCEPTED / IMPLEMENTATION_CANDIDATE_PRESENT / EXECUTION_NOT_RUN`.

## 12. FL-P1-050 V2_COLLECTION_DIGEST_MAP_COERCION_CRASH
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- Source: 초기 WorkflowService `digest(Object)`
- 결함: denominator/applicability의 List를 `mapper.convertValue(..., Map.class)`로 강제 변환하여 정상 population digest 계산에서도 runtime exception 가능.
- 영향: semantic denominator/applicability 정상 경로 가용성 저하. false positive promotion은 아니므로 P1.
- 조치: ObjectMapper canonical serialization bytes를 직접 SHA-256하는 collection-safe digest로 교체하고 정상 list digest JUnit 추가.
- 현재 disposition: `DESIGN_ACCEPTED / IMPLEMENTATION_CANDIDATE_PRESENT / TEST_DESIGNED / EXECUTION_NOT_RUN`.

## 13. Canonical Gate 영향
FL-P0-133~141은 모두 v2 activation blocker다. 다음이 실제 실행으로 확인되기 전 Active Selector를 허용하지 않는다.
- TenantRbac semantic operation authorization tests PASS
- target-path escape negative test PASS
- direct WorkflowService server-bound-context denial test PASS
- self-attested independence/human/requalification rejection tests PASS
- Shadow comparator Schema PASS
- 23 Schema / 69 Fixture static validator PASS
- target-bound deployment identity 구현 및 검증

## 14. 비최종 경계
본 확장 Ledger의 조치가 코드에 존재해도 compile/JUnit/static validation/independent reperformance가 실행되지 않았으므로 `IMPLEMENTED`, `EXECUTED`, `VERIFIED_CLOSED`를 주장하지 않는다.
