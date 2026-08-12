# 127 Product Design → Design QA Phase Handoff

Status: `PHASE_HANDOFF / NON_FINAL`
Parent: `126_FINAL_FRESH_PRODUCT_DESIGN_REVIEW_AND_SCOPE_CLOSURE.md`

## 1. Product Design Phase
상태: `PRODUCT_DESIGN_SCOPE_COMPLETE_CANDIDATE`

신규 독립 설계축은 Fresh Review에서 0건이었다. 기존 owner 안에서 보강할 refinement는 FR-FRESH-001~003으로 126에 고정했다.

이후 일반적인 반복 검토는 Product Design Phase를 다시 열지 않는다.

## 2. Design QA Phase
다음은 제품 설계가 아니라 설계 QA다.
- Global Requirement exact materialization
- Applicability exact population
- Global Trace closure
- repository-wide orphan scan
- contradiction scan
- exact artifact SHA-256 inventory
- canonical registry digests
- baseline reconstructability
- Design Lock Check

Design QA 실패는 `PRODUCT_DESIGN_SCOPE_COMPLETE_CANDIDATE`를 자동 취소하지 않는다. 다만 실제로 새로운 독립 설계축 또는 의미결함이 발견되면 Change Queue를 통해 Product Design을 선택적으로 reopen한다.

## 3. Implementation Phase
Claude가 담당한다.
- machine contracts
- runtime
- persistence/migration
- API/event/receipt
- Batch F~K
- compile/test execution

Implementation 누락/결함은 설계 완료율을 임의로 낮추지 않고 Implementation Gap으로 추적한다. 구현 과정에서 설계 semantics 자체의 결함이 발견되면 Change Queue를 사용한다.

## 4. Verification Phase
Implementation 이후 별도로 수행한다.
- semantic reverse alignment
- compile/JUnit/static fixtures
- reperformance
- independent OTester/OAudit
- validator/ONSure qualification
- deployment/currentness
- certificate/final gate

## 5. Reopen 조건
Product Design Phase를 다시 여는 유효 사유:
1. 새로운 고객 use case가 현재 책임구조로 표현 불가
2. 신규 법/규제/산업 요구가 새 책임영역을 요구
3. 구현 중 현재 Architecture로 해결 불가능한 semantic contradiction 발견
4. 실제 검증/운영 중 기존 독립 축으로 분류할 수 없는 failure class 발견

다음은 reopen 사유가 아니다.
- trace row 누락
- SHA 계산 미완료
- scanner bug
- compile failure
- fixture failure
- 구현 누락
- 문서 번호 충돌
- baseline manifest 생성 실패

## 6. 현재 공식 Phase 표현
`PRODUCT_DESIGN=COMPLETE_CANDIDATE / DESIGN_QA=HOLD_PENDING / IMPLEMENTATION=IN_PROGRESS / VERIFICATION=PENDING / NON_FINAL`
