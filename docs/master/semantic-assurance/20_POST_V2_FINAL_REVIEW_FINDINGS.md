# ONSure Post-v2 Final Review Finding 확장 Ledger

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parent: `10_FINDING_LEDGER.md`, `19_FINAL_REVIEW_AND_EXECUTION_BLOCKERS.md`

## 1. 목적
v2 Candidate Contract·Runtime·Fixture를 만든 뒤 그 **새 구조 자체를 다시 검토**하면서 발견된 결함을 별도 canonical Finding으로 보존한다. 기존 FL-P0-001~132 / FL-P1-001~048을 덮어쓰지 않는다.

이번 확장으로 canonical range는 다음과 같이 증가한다.
- P0: `FL-P0-001~136`
- P1: `FL-P1-001~049`
- 기존 raw candidate observation 551에 post-v2 review observation 5건을 추가하여 raw observation baseline은 556으로 기록한다.

수정이 이미 Candidate branch에 반영됐더라도 실제 compile/test/independent verification 전에는 `VERIFIED_CLOSED`로 처리하지 않는다.

## 2. FL-P0-133 SEMANTIC_V2_BRIDGE_COMMON_AUTHORIZATION_BYPASS
- 분류: `CANONICAL_GATE_BYPASS`
- Source: `SemanticAssuranceV2DispatcherBridge.java`, `TenantRbacService.java`
- 결함: 초기 v2 Bridge가 자체 role check만 수행하고 기존 durable tenant/resource ownership boundary를 거치지 않았다.
- 실패 시나리오: semantic operation 이름에 대한 role은 허용되지만 target resource는 다른 tenant 소유인 경우 Bridge가 공통 ownership 검증 없이 v2 service를 호출할 수 있다.
- 영향: cross-tenant semantic validation/read/effect 후보.
- 조치: 모든 v2 target operation 전 `project.read-target`을 통한 authenticated tenant/resource preflight 추가. caller-supplied tenant context도 금지.
- 잔여 위험: preflight와 semantic effect가 동일 atomic authorization transaction은 아니다.
- Fixture: Bridge cross-tenant/authority-substitution JUnit 필요.
- 현재 disposition: `DESIGN_ACCEPTED / IMPLEMENTATION_CANDIDATE_PRESENT / EXECUTION_NOT_RUN`.

## 3. FL-P0-134 SEMANTIC_V2_TARGET_PATH_SCOPE_ESCAPE
- 분류: `NEW_DEFECT_CLASS`
- Source: `SemanticAssuranceV2WorkflowService.requiredInputPath`, Bridge target preflight, `ProductCatalog.RegisteredTarget`
- 결함: tenant-owned target이라는 사실을 확인해도 file path가 workspace 전체에서 선택 가능하면 같은 workspace의 다른 target/tenant artifact를 읽을 수 있다.
- 실패 시나리오: target A에 대한 semantic.reperformance 권한으로 target B 또는 unrelated workspace file을 `subject_path`로 지정.
- 영향: confidentiality violation, wrong-subject evidence binding, false assurance.
- 조치: Bridge가 server-resolved RegisteredTarget.sourceRoot를 authoritative path root로 사용하고 `subject_path`가 그 root 밖이면 `SEMANTIC_V2_TARGET_PATH_ESCAPE`로 거부한다. `_authorized_*` caller injection도 금지.
- Deployment 예외: 현재 v1 deployment install 구조가 target-scoped root를 제공하지 않으므로 `deployment.verify-installed`는 `TARGET_BOUND_DEPLOYMENT_IDENTITY_NOT_AVAILABLE`로 BLOCKED.
- Fixture: `SemanticAssuranceV2DispatcherBridgeTest`에 target path escape 및 deployment blocked 시험 추가.
- 현재 disposition: `DESIGN_ACCEPTED / IMPLEMENTATION_CANDIDATE_PRESENT / TEST_DESIGNED / EXECUTION_NOT_RUN`.

## 4. FL-P0-135 SHADOW_RUNTIME_SCHEMA_SEMANTIC_DRIFT
- 분류: `CROSS_CONTRACT_SEMANTIC_CONFLICT`
- Source: `SemanticAssuranceShadowGateComparator.java`, `shadow-gate-comparison.candidate.v1.schema.json`
- 결함: Runtime comparator가 missing receipt를 문자열 `NOT_AVAILABLE`로 출력하고 contract discriminator도 Schema와 달랐다.
- 실패 시나리오: 실제 Shadow comparison은 생성되지만 자기 Candidate Schema 검증에서 실패하거나 consumer가 다른 contract family로 해석.
- 영향: Shadow Gate 결과가 qualification/activation 근거로 사용 불가, schema/runtime drift 은폐 위험.
- 조치: missing receipt는 JSON null로 보존하고 comparator contract를 `ONSURE_SHADOW_GATE_COMPARISON_V1_CANDIDATE`로 통일.
- 현재 disposition: `DESIGN_ACCEPTED / IMPLEMENTATION_CANDIDATE_PRESENT / EXECUTION_NOT_RUN`.

## 5. FL-P0-136 V2_SCHEMA_INSTANCE_REGISTRY_DRIFT
- 분류: `DESIGNED_CONTROL_OUTSIDE_CANONICAL_GATE_PATH`
- Source: v2 Candidate Schema inventory와 `semantic-assurance-v2-schema-instance-registry.candidate.v1.json`
- 결함: 후속 Final/Independence/GT/Hidden/Shadow Schema가 추가됐지만 Schema Instance Registry가 기존 14개에 머물렀다.
- 실패 시나리오: 신규 P0 계약이 valid/invalid fixture 검증을 전혀 받지 않은 채 전체 v2 qualification coverage가 완료된 것처럼 집계될 수 있음.
- 영향: untested contract가 active selector 후보로 유입될 수 있음.
- 조치: Registry를 23 Schema / valid 23 / semantic-invalid 46으로 확장하고 pending schema count=0으로 inventory 동기화. 최소 invalid 2개/schema hard rule 유지.
- 현재 disposition: `DESIGN_ACCEPTED / TEST_DESIGNED / EXECUTION_NOT_RUN`.

## 6. FL-P1-049 V2_RECONSTRUCTOR_NULL_FAIL_CLOSED_CRASH
- 분류: `EXISTING_CONTROL_ENFORCEMENT_GAP`
- Source: 초기 `SemanticAssuranceV2Reconstructor.java`
- 결함: v1에 source receipt id/digest 등 optional material이 없을 때 null 값이 `Map.copyOf()`에 들어가 fail-closed HOLD receipt 대신 runtime exception이 발생할 수 있었다.
- 영향: migration/reconstruction 가용성 저하 및 HOLD evidence 누락. positive false-pass는 아니므로 P1.
- 조치: 모든 unavailable legacy field를 `NOT_AVAILABLE` sentinel 또는 explicit gap requirement로 보존하고 immutable map 생성은 null-safe하게 변경.
- 현재 disposition: `DESIGN_ACCEPTED / IMPLEMENTATION_CANDIDATE_PRESENT / EXECUTION_NOT_RUN`.

## 7. Canonical Gate 영향
FL-P0-133~136은 모두 v2 activation blocker다. 다음이 실제 실행으로 확인되기 전 Active Selector를 허용하지 않는다.
- Bridge tenant/target isolation tests PASS
- target-path escape negative test PASS
- Shadow comparator instance Schema PASS
- 23 Schema / 69 Fixture static validator PASS
- primary dispatcher atomic authorization 설계/구현 및 independent review

## 8. 비최종 경계
이 확장 Ledger의 조치가 코드에 존재해도 compile/JUnit/static validation/independent reperformance가 실행되지 않았으므로 `IMPLEMENTED`, `EXECUTED`, `VERIFIED_CLOSED`를 주장하지 않는다.
