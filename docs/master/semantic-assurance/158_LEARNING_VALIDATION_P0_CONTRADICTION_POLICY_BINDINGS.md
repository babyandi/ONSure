# 158 Learning/Validation P0 Contradiction Policy Bindings

Status: `NORMATIVE_REFINEMENT / DESIGN_ONLY / NON_FINAL`
Scope: 11 P0 contradiction classes across FR-LEARN-001~095.

이 문서는 서로 정당한 요구가 동시에 적용될 때 precedence와 fail-closed 결과를 고정한다. 해결은 한 요구를 삭제하는 방식이 아니라 scope, authority, currentness, evidence minimization, HOLD semantics로 결합한다.

1. Retention vs Deletion/Legal Hold: 유효한 legal hold가 있으면 범위·기간·접근을 최소화해 보존한다. hold 해제 후 삭제권/보존정책을 재적용한다. hold 없이 무기한 보존하거나 hold 중 삭제 금지.
2. Privacy vs Reproducibility: 원문 민감정보의 영구보존을 재현성 전제조건으로 두지 않는다. 최소화/가명화 evidence, immutable digest, tombstone, decision snapshot을 우선한다.
3. Transparency vs Challenge Secrecy: 방법·범위·결과·권위는 공개 가능하되 sealed fixture/answer는 evaluator-only로 유지한다. 노출된 challenge set은 blind authority를 상실한다.
4. Tenant Isolation vs Global Learning: tenant-derived knowledge는 opt-in, lineage, transfer test, scope approval 없이 상위 scope로 승격하지 않는다.
5. Deletion vs Derived Global Knowledge: source 삭제/철회 시 lineage impact를 계산하고 unlearn/retrain/revoke 또는 비가역 집계임을 증명한다. disposition 없는 파생자산은 CURRENT 금지.
6. Fail-Closed vs Availability: safety/authority gate 장애는 PASS로 degrade하지 않는다. 비권위 기능만 제한된 degraded mode를 허용하고 최종 assurance는 HOLD한다.
7. Adaptive Learning vs Reproducibility: 모든 final decision은 decision-time knowledge epoch/snapshot에 결속한다. 현재 knowledge로 과거 판정을 재실행한 결과를 동일 replay로 주장하지 않는다.
8. Counterevidence vs Privacy: 반대증거도 decision relevance를 유지하되 최소화·가명화·접근통제로 보존한다. privacy를 이유로 불리한 증거만 제거하지 않는다.
9. Human Override vs Self-confirmation: override는 signal이며 truth가 아니다. reason/evidence와 독립 confirmation 없이 active knowledge로 승격하지 않는다.
10. Cost Budget vs Mandatory Assurance: risk/policy상 필수 gate는 비용 최적화가 생략할 수 없다. 예산 부족이면 shallow PASS가 아니라 HOLD/추가승인이다.
11. Ground-truth Drift vs Historical Immutability: 과거 decision snapshot은 수정하지 않는다. 새 truth version은 과거 decision currentness를 STALE/REVIEW_REQUIRED로 만들고 별도 reevaluation을 생성한다.

모든 binding은 negative fixture가 필요하며 실제 runtime PASS는 Claude 구현/검증 단계에서만 가능하다.