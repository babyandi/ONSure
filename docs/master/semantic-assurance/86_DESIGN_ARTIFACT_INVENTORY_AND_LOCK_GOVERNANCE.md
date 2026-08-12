# Design Artifact Inventory·Lock Governance 설계

Status: `DESIGN_ONLY / CANDIDATE / NON_FINAL`

## 1. 목적
Design Baseline Candidate를 고정할 때 "파일이 대략 이 정도 있다"가 아니라 exact artifact set과 bytes identity를 보존한다.

## 2. 두 Digest 구분
### Git Blob SHA
Repository object identity. Git의 blob object 계산 규칙을 따른다.

### Content SHA-256
파일 raw bytes의 SHA-256. 외부 도구/Certificate/Evidence와 상호운용할 때 사용한다.

둘을 같은 hash라고 표현하지 않는다.

## 3. DesignArtifactInventory row
- artifact_path
- artifact_role
- authority_class: MASTER|COMPANION|CHECKLIST|MACHINE_CANDIDATE|HANDOFF
- git_blob_sha
- content_sha256
- byte_size
- semantic_version nullable
- parent_refs[]
- supersedes[]
- status

## 4. Inventory Scope
최소:
- `docs/master/00~08` + 08A
- `docs/master/semantic-assurance/*`
- design-governance machine candidate files
- Contract blueprints directly required by Design Lock

개발 runtime/source/fixture는 Design Baseline Inventory와 별도 Implementation Inventory로 관리한다.

## 5. BaselineManifest
- baseline_id
- branch/ref
- commit_sha
- artifact_population_digest
- artifact_count
- inventory_digest
- requirement_trace_registry_digest
- conflict_report_digest
- orphan_report_digest
- open_policy_registry_digest
- created_at
- created_by

## 6. Population Digest
artifact rows를 canonical path ascending으로 정렬한 뒤 `(path, content_sha256, authority_class)` tuple을 canonical serialize하여 SHA-256 계산한다.

파일 이름만 hash하거나 commit SHA 하나만 Design population proof로 사용하지 않는다.

## 7. Lock State
- INVENTORY_BUILDING
- INVENTORY_COMPLETE
- TRACE_CHECK_REQUIRED
- CONFLICT_CHECK_REQUIRED
- POLICY_CHECK_REQUIRED
- LOCK_CANDIDATE_READY
- LOCKED
- SUPERSEDED

`LOCKED`는 별도 signed LockReceipt가 있을 때만 사용한다. 현재 상태는 `LOCK_CANDIDATE_READY` 이하이다.

## 8. Lock Precondition
- exact inventory rows complete
- duplicate canonical names resolved
- requirement orphan = 0
- P0 design semantic conflict = 0
- open numeric/legal/business decisions are explicit policy inputs, not hidden constants
- Master/README/index population matches inventory
- superseded document relationship explicit

## 9. Change After Lock
locked artifact 변경은 silent edit 금지.
- new baseline generation
- changed artifact list
- semantic impact
- affected requirements/contracts
- re-lock receipt
을 남긴다.

## 10. 수용기준
Design Lock은 문서 수나 PR merge 여부가 아니라 exact bytes population + trace/conflict/policy closure로 판정한다.
