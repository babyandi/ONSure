# 113 Baseline Manifest Regeneration Attempt

Status: `REGENERATED_INCOMPLETE / NON_FINAL`

Frozen design commit: `abe122ef9438f41e8b074f03b8017b6d30cbc389`

## 입력 결속 상태
- Design Git identity: available
- Semantic-assurance subtree Git tree SHA: `44a290f0369ef6991fcaf363c452a1b24f2842a2`
- Explicit Requirement snapshot: 73 IDs / partial universe
- Applicability snapshot: 73 UNKNOWN_PENDING_CONTEXT / non-authoritative
- Global trace: 60/73 explicit IDs traced
- Orphan scan: partial; explicit trace orphan candidates 13
- Contradiction scan: partial; known numbering collision 1
- content SHA-256 inventory: missing

## 재생성 판정
Baseline manifest candidate는 생성할 수 있으나, authoritative Design Baseline Manifest로 승격할 수 없다.

누락 필수 commitment:
1. global requirement population digest
2. authoritative applicability population digest
3. global trace population digest
4. repository-wide orphan=0 proof
5. unresolved P0 contradiction=0 proof
6. content SHA-256 artifact population digest

따라서 regenerated manifest state는 `INCOMPLETE_HOLD`다.
