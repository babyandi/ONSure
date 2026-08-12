# ONSure Safe Default·Naming·Master Completion·Orphan Closure 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Covers: Task 38~44

## 1. Global Safe Default
Input/Dependency state별 기본값:
- UNKNOWN → HOLD/UNKNOWN
- STALE → positive CURRENT 금지
- PARTIAL → scope-limited result 또는 HOLD
- TIMEOUT → INCONCLUSIVE/BLOCKED, target FAIL 금지
- UNSUPPORTED → NOT_PROVEN ceiling
- MISSING_AUTHORITY → DENY/HOLD
- MISSING_EVIDENCE → claim unproven
- CONFLICT → CONFLICT_HOLD
- RECOVERY_UNQUALIFIED → REASSESSMENT_REQUIRED
- OBSERVER_BLIND → absence claim 금지

어떤 경로도 missing/unknown을 PASS로 coerce하지 않는다.

## 2. Naming/Version/Supersession
Canonical registry는 모든 design/contract/entity/state/operation/event/receipt 이름을 추적한다.
필드:
- canonical_name
- kind
- current_version
- aliases[]
- deprecated_names[]
- supersedes/superseded_by
- authority_doc

중복 번호는 파일명 고유성이 보장되도록 향후 rename candidate를 관리하되, 역사적 commit link를 깨뜨리는 즉시 rename은 별도 migration으로 수행한다.

동일 개념 다른 이름 또는 동일 이름 다른 의미는 unresolved conflict로 처리한다.

## 3. Master/README/Trace 동기화
동기화 대상:
- `00_ONSURE_MASTER_DESIGN_SET.md`
- semantic-assurance README
- 02~08/08A
- Requirement Universe
- Contract/Operation/Event/Receipt registries
- DesignTraceRegistry
- Baseline Manifest

어느 하나의 인덱스가 최신 generation보다 뒤처지면 Design Lock 금지.

## 4. Design Completion Matrix
Completion dimension을 분리한다.
- Structural Design Coverage
- Requirement Materialization
- Machine Contract Specification
- Trace Closure
- Policy Decision Closure
- Runtime Implementation Dependency
- Verification Dependency

문서 존재만으로 각 dimension을 100%로 올리지 않는다.

## 5. Global Requirement Orphan Closure
P0/claim-gating active requirement는 최소 design + contract candidate + operation/gate + test strategy + policy/authority binding을 가져야 한다.

Global Universe materialization 전에는 orphan=0을 주장하지 않고 `PARTIAL_UNIVERSE`를 유지한다.

## 6. Global Contract/Operation/Event/Test Orphan Closure
Orphan 정의:
- Contract without design/requirement consumer
- Effect operation without event/receipt/authority
- Event without producer/consumer/retention
- Receipt without semantic purpose/gate consumer
- Test without requirement/contract relation
- UI claim without backend/evidence source

## 7. P0 Design Conflict Closure
충돌 class:
- state semantics
- authority precedence
- evidence binding
- currentness/TTL
- N/A/applicability
- independence/qualification
- product composition
- policy override

Unresolved P0 conflict count > 0이면 candidate lock 금지.

## 8. Acceptance
- fail-open coercion 0
- canonical naming conflict 0 후보
- all indices same baseline generation
- orphan scan universe completeness 상태 명시
- unresolved P0 design conflict 0일 때만 lock check 다음 단계 허용
