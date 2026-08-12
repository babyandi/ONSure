# ONSure Semantic Assurance 1~15 최종 재검토 및 실행 Blocker 보고서

Status: `DRAFT / NON_FINAL / EXECUTION_BLOCKED_OR_NOT_RUN`

## 1. 목적
사용자가 지정한 1~15 작업을 설계·계약·Fixture·Runtime 후보까지 연속 적용한 뒤, 새 v2 구조 자체를 다시 공격하여 **새 구조가 만든 결함과 아직 실행으로 닫히지 않은 Blocker를 분리**한다.

이 문서는 Final PASS 보고서가 아니다. 현재 가능한 최고 상태와 외부 실행환경 의존 항목을 정직하게 고정한다.

## 2. 1~15 작업 결과
| No | 작업 | 산출물 상태 | 실행 상태 |
|---|---|---|---|
| 1 | v2 Schema 정적 검증 | 23 Schema / 23 valid / 46 invalid / validator entrypoint | BLOCKED_NOT_RUN |
| 2 | v1→v2 Gap 전수 설계 | `14_V1_V2_SEMANTIC_GAP_MATRIX.md` | DESIGN_PRESENT |
| 3 | Adapter/Reconstructor | `SemanticAssuranceV2Reconstructor` | IMPLEMENTATION_CANDIDATE / NOT_RUN |
| 4 | 기존 02~08 흡수 | FR-META-001~043 및 meta review/architecture/test/AI 직접 반영 확인 | DESIGN_PRESENT |
| 5 | Workflow Operation v2 | registry + workflow service + bridge | NOT_ACTIVE / NOT_RUN |
| 6 | Product Process Lineage v2 | candidate lineage | NOT_ACTIVE |
| 7 | Validation/Final denominator 전환 | exact population schemas/fixtures + migration doc | NOT_RUN |
| 8 | Final Candidate/Approval/Lock v2 | Gate/Approval/Lock schemas+fixtures | NOT_RUN |
| 9 | Independent OTester/OAudit 분리 | Independence Profile + Independent Receipt | NOT_RUN |
| 10 | Learning/Memory/Benchmark Qualification | Blind/Reviewer/Benchmark/GT/Hidden Corpus contracts | NOT_RUN |
| 11 | Verified-to-Deployed | schema + workflow runtime candidate + JUnit | NOT_RUN |
| 12 | Shadow Gate | schema + comparator + fixtures | NOT_RUN |
| 13 | Active Selector | selector schema + rollout HOLD | V2_NOT_ACTIVE |
| 14 | P0 Finding disposition | 132 P0 보수적 disposition registry | VERIFIED_CLOSED=0 |
| 15 | 재검토 | 본 문서 + 아래 수정사항 | SOURCE_REVIEW_PERFORMED |

## 3. 재검토에서 새로 발견하여 즉시 수정한 결함
### R15-001 Reconstructor Null Semantic Crash
초기 Reconstructor가 v1에 존재하지 않는 receipt ID/digest 등을 `null`로 넣은 후 `Map.copyOf`를 사용해 fail-closed HOLD가 아니라 예외로 종료될 가능성이 있었다.

조치:
- nullable source field는 `NOT_AVAILABLE` semantic sentinel로 보존
- 최종 map은 null-safe immutable copy 사용
- 누락 필드는 GapClass로 명시

상태: `SOURCE_FIXED / COMPILE_NOT_RUN`.

### R15-002 Schema Registry Drift
초기 Schema Instance Registry는 14개 Schema만 추적했고 이후 추가된 Final/Independent/GT/Hidden/Shadow 계약이 fixture coverage 밖에 남았다.

조치:
- Registry를 23개 Schema로 확대
- valid 23 / semantic invalid 46
- schema당 invalid 최소 2
- pending registration 0
- 별도 Schema Inventory도 pending 0으로 동기화

상태: `ARTIFACT_FIXED / VALIDATOR_EXECUTION_NOT_RUN`.

### R15-003 Semantic V2 Tenant Boundary Bypass
초기 `SemanticAssuranceV2DispatcherBridge`는 자체 role check 후 v2 service를 직접 호출하여 기존 `TenantRbacService`의 durable tenant/resource ownership boundary를 원자적으로 통과하지 않았다.

조치:
- 모든 semantic v2 operation에 `project_id + target_id` 요구
- 기존 `project.read-target` authenticated path로 tenant/resource ownership preflight
- actor/tenant_context substitution 추가 차단
- `authorization_atomic_with_effect=false`를 결과에 명시

중요: 현재 조치는 **preflight**이지 atomic semantic authorization은 아니다. Active authority가 되려면 `TenantRbacService` 또는 동등한 canonical guard가 semantic operation 자체를 1급 operation으로 소비해야 한다.

상태: `RISK_REDUCED / CANONICAL_ATOMIC_AUTHORIZATION_OPEN`.

### R15-004 Shadow Runtime / Schema Receipt Null Conflict
초기 Shadow Comparator는 receipt가 없으면 `NOT_AVAILABLE` 문자열을 기록했으나 Shadow Schema는 SHA-256 또는 null만 허용했다.

조치:
- missing receipt는 null로 보존
- null-safe unmodifiable map 사용
- digest가 존재하면 SHA-256 형식 검증

상태: `SOURCE_FIXED / EXECUTION_NOT_RUN`.

### R15-005 Shadow Contract Discriminator Drift
Comparator output contract discriminator가 Shadow Comparison Schema의 `const`와 달랐다.

조치:
- Runtime output discriminator를 `ONSURE_SHADOW_GATE_COMPARISON_V1_CANDIDATE`로 통일

상태: `SOURCE_FIXED / EXECUTION_NOT_RUN`.

## 4. 현재 Static Qualification Coverage
정본: `contracts/semantic-assurance-v2-schema-instance-registry.candidate.v1.json`.

현재:
- Schema 23
- valid 23
- invalid 46
- pending schema registration 0

Negative fixture는 단순 JSON type error만이 아니라 다음 semantic failure를 포함한다.
- PASS + NOT_RUN
- Final + STALE
- Independent class + independent=false
- revoked authority without revocation evidence
- open P0가 있는 Gate PASS
- self-validation OTester로 Final Gate PASS
- denominator empty/count-only authority
- validator critical miss가 있는데 QUALIFIED
- Blind cache/scratch 미격리
- post-result benchmark selection/exclusion
- Final Approval decision/lock contradiction
- Final Lock non-APPROVE/freshness 누락
- shared principal/admin independence collapse
- OTester/OAudit assurance class 교환
- Hidden Corpus confirmed leakage인데 qualification-ready/stale 유지
- Shadow disagreement를 AGREE로 세탁

## 5. 실제 실행 Blocker
2026-08-12 실행환경에서 다음을 확인했다.

`git ls-remote https://github.com/babyandi/ONSure.git HEAD`
→ `Could not resolve host: github.com`

따라서 현재 ChatGPT container에서는 branch materialization을 할 수 없어:
- Python JSON Schema validator
- Maven compile
- JUnit
- v1 actual receipt reconstruction
- Shadow Gate actual comparison
을 실행할 수 없다.

근거: `evidence/semantic-assurance/v2-static-validation-attempt-20260812.json`.

GitHub Actions를 우회 실행경로로 사용하지 않았다.

## 6. Runtime에서 아직 닫히지 않은 P0 경계
### 6.1 Atomic Semantic Authorization
현재 Bridge는 tenant/resource preflight를 수행하지만 semantic operation과 ownership check가 동일 durable transaction은 아니다.

필요:
- Semantic v2 operation을 canonical `TenantRbacService` operation registry에 등록
- operation별 required role/purpose/effect class
- project/target/resource ownership check
- approval/effect-time authority
- 동일 ledger transaction 또는 equivalent proof

### 6.2 Primary Dispatcher Activation
`SemanticAssuranceV2DispatcherBridge`는 default dispatcher가 아니다. 따라서 v2 operation은 아직 canonical product surface가 아니다.

### 6.3 v1 Actual Population Migration
Schema와 adapter가 있어도 현재 v1 Validation Case/Final Acceptance population에 실제 재구성을 수행하지 않았다.

### 6.4 Independent OTester/OAudit
타입 계약은 존재하지만 실제 distinct principal/credential admin/implementation/oracle/discovery/knowledge independence 실행 증적이 없다.

### 6.5 Final Shadow→Active
Legacy/v2 Shadow 비교가 실제로 한 번도 실행되지 않았다. Active Selector는 HOLD 유지가 정당하다.

## 7. Finding 상태 상한
현재 Finding 상태 상한은 다음과 같다.
- 설계 반영: `DESIGN_ACCEPTED`
- Candidate Contract/Fixture/Runtime class 존재: coverage metadata
- 실제 실행 없는 경우 canonical `CONTRACTED/IMPLEMENTED/EXECUTED` 승격 금지

따라서 P0 `VERIFIED_CLOSED=0`을 유지한다.

## 8. PR 상태
PR #44는:
- Draft
- Open
- Unmerged
- mergeable=true (최신 재확인)

v2 Candidate는 active authority가 아니며 FinalLock/Production/Commercial GO 권위를 만들지 않는다.

## 9. 외부 실행환경에서 즉시 이어야 할 순서
1. branch materialize
2. `scripts/validate-semantic-assurance-v2-contracts.py` 실행
3. 23 valid PASS / 46 invalid expected-fail 확인
4. Maven compile/JUnit
5. 실패한 schema/runtime candidate 수정
6. actual v1 receipt/population reconstruction
7. semantic operation canonical tenant authorization wiring
8. Validation/Final exact denominator shadow
9. true independent OTester/OAudit
10. Shadow Gate comparison
11. blocker 0 확인
12. signed Active Selector 검토

11까지 CLEAN이 아니면 12를 실행하지 않는다.

## 10. 결론
1~15에 필요한 설계·Contract·Fixture·Runtime 후보·Migration·Disposition·재검토 산출물은 현재 PR에 반영됐다. 그러나 실제 실행환경이 없으므로 **Execution/Independent Verification/Qualification은 완료되지 않았다**. 현재 상태를 Final 또는 구현 완료로 표현하지 않는다.
