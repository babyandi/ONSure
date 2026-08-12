# ONSure Requirement Conflict·Design Inventory·Baseline·Change Control 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Covers: Task 6~10

## 1. Requirement Conflict Resolver
Relation:
`DUPLICATE|REFINES|SUPERSEDES|CONFLICTS|OVERRIDES_BY_POLICY|OVERRIDES_BY_CONTRACT|DERIVED_FROM`.

우선순위는 단순 문서 최신순이 아니다. authority, scope, effective time, policy profile, customer contract를 함께 평가한다. Critical conflict unresolved이면 HOLD.

## 2. Exact Design Artifact Inventory
각 authoritative design artifact:
- path
- git_blob_sha
- content_sha256
- authority_class: MASTER|MASTER_APPENDIX|COMPANION|HANDOFF|MACHINE_CANDIDATE
- lifecycle: ACTIVE|SUPERSEDED|DEPRECATED|DRAFT
- supersedes/superseded_by
- first_commit/current_commit

Git blob SHA와 content SHA-256은 별도 필드다.

## 3. Design Lock Check
LockCheck는 최소 다음 count를 산출한다.
- requirement_orphan_count
- contract_orphan_count
- operation_orphan_count
- event_orphan_count
- receipt_orphan_count
- test_orphan_count
- policy_orphan_count
- ui_claim_orphan_count
- unresolved_p0_conflict_count
- duplicate_canonical_name_count

모든 P0 count=0 전에는 `LOCK_CANDIDATE` 불가.

## 4. Baseline Population Commitment
Baseline tuple:
`design_population_digest + requirement_universe_digest + contract_registry_digest + operation_registry_digest + policy_profile_digest + naming_registry_digest`.

Baseline은 파일 수가 아니라 exact member list + digest로 고정한다.

## 5. Change Control
Design change class:
- PATCH_EDITORIAL
- MINOR_NON_BREAKING
- MAJOR_SEMANTIC
- BREAKING_AUTHORITY_OR_GATE

BREAKING 예:
- PASS/HOLD semantics 변경
- authority 축소
- evidence binding 완화
- N/A proof 완화
- currentness TTL 증가
- independence requirement 축소

Breaking change는 새로운 baseline generation, impact scan, migration plan, shadow comparison을 요구한다.

## 6. Acceptance
- canonical conflict relation 명시
- exact design population 생성 가능
- baseline generation 간 diff 재현 가능
- breaking change가 이전 Final/Certificate impact를 계산
- 문서 추가만으로 baseline 자동 갱신 금지
