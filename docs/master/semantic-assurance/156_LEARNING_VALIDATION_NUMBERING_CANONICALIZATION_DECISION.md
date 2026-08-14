# 156 Learning/Validation Numbering Canonicalization Decision

Status: `DESIGN_AUTHORITY_DECISION / NON_FINAL`

현재 두 개의 서로 다른 normative refinement가 physical prefix `151`을 공유한다. 의미 권위는 충돌하지 않지만 baseline naming governance에는 부적합하다.

Canonical intent:
- `151_LEARNING_VALIDATION_OPERATIONAL_SECOND_ORDER_RISKS.md`는 FR-LEARN-026~040의 정본으로 유지한다.
- `151_LEARNING_VALIDATION_OPERATIONAL_MATURITY_REFINEMENT.md`는 FR-LEARN-041~052의 정본 내용이지만 최종 baseline 전에 새 고유 번호로 rename하고 모든 inbound reference를 함께 갱신한다.
- rename 전까지 두 문서는 content identity와 explicit FR range로 구분하며 어느 쪽도 다른 쪽을 supersede하지 않는다.

이 결정은 번호 충돌을 의미 충돌로 오인하지 않도록 하되, physical collision이 제거되기 전 `PHYSICAL_NAMING_CLEAN=true` 선언을 금지한다.
