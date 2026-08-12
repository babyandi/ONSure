# ONSure Semantic Assurance UI·UX 및 업무흐름 상세설계 확장

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parent authority: `../05_UI_UX_WORKFLOW_SPECIFICATION.md`

## 1. 목적
Semantic Assurance의 내부 상태가 사용자 화면에서 단순 PASS/점수로 축약되어 false assurance를 만들지 않도록 한다. 기존 Case Dashboard, Review, Verification, Finding Explorer, Evidence, Delivery 흐름을 유지하면서 **Scope·Evidence Strength·Rights·Authority·Freshness·Unknown·Qualification**을 동일 화면 맥락에서 이해할 수 있게 확장한다.

## 2. UX 공통 원칙
- `PASS`보다 `무엇을 실제 실행·재수행했고 무엇이 미확인인지`를 우선 표시한다.
- `IMPLEMENTED`, `EXECUTED`, `EVIDENCE_BOUND`, `INDEPENDENTLY_VERIFIED`, `QUALIFIED`를 하나의 완료 진행률로 합치지 않는다.
- `DECLARED_RESULT_ONLY`와 `REPERFORMED_BOUND`를 같은 녹색 배지로 표현하지 않는다.
- Coverage %만 보여주지 않고 denominator source, excluded, unknown, unobservable을 함께 보여준다.
- Right가 존재해도 현재 행사 불가하면 `TEMPORARILY_BLOCKED/UNREACHABLE/POLICY_PROHIBITED`를 명시한다.
- stale artifact/report/certificate는 과거 PASS 문구보다 현재 disposition을 시각적으로 우선한다.
- AI recommendation과 Human decision을 한 결과로 합치지 않는다.

## 3. Case Dashboard 확장
기존 Case 상태/ProgramRiskScore/CoverageReport에 다음 카드 추가.

### Semantic Assurance Summary
- target_manifest_digest
- denominator_epoch
- requirement/scope epoch
- evidence_strength_summary
- current stale count
- required semantic capabilities 적용/미적용/HOLD
- critical unknowns
- validator qualification summary

### Coverage Universe
단순 included/excluded 외에:
- discovered denominator by dimension
- accepted denominator changes
- open denominator candidates
- negative-space coverage
- lifecycle coverage
- rights/remedy coverage

Open HIGH denominator candidate가 있으면 상단에 `COVERAGE_UNIVERSE_NOT_CLOSED` 배너를 표시한다.

## 4. Verification 화면 확장
### Evidence Strength 패널
각 Test/Claim에:
- upstream declared result
- current execution status
- subject read-back status
- reperformed Oracle status
- Evidence Strength
- exact source/profile/environment revision

`DECLARED_RESULT_ONLY`이면 “실행됨” 아이콘을 사용하지 않는다.

### Fixture → Test → Execution Trace
사용자는 한 화면에서:
`Requirement/Claim -> Test Case -> Fixture -> Execution Run -> Read-back -> Evidence`
를 펼쳐 볼 수 있어야 한다.

상태는 각 layer별로 독립 표시한다.
- Fixture: DESIGNED/MATERIALIZED/STALE
- Test: DEFINED/EXECUTABLE/STALE
- Execution: NOT_RUN/PASS/FAIL/BLOCKED/STALE

## 5. Rights & Remedy 화면
Finding 또는 Case에서 `Rights & Remedies` 탭을 제공한다.

각 right:
- holder
- actionability
- required Function/Command/API/UX
- current exercisability
- blocked reason
- allowed remedy
- authority source
- evidence

### 표시규칙
- 권리가 문서상 존재하지만 typed path가 없으면 `DECLARED_NOT_EXECUTABLE`
- TEMPORARILY_BLOCKED면 복구/appeal path를 같은 화면에 제시
- OPERATOR_ACTIONABLE인데 direct DB 절차만 있으면 Critical warning
- restore/recovery 후 권리 regression이 있으면 before/after 비교

## 6. Authority & SoD 화면
고위험 작업 승인 전 다음을 표시한다.
- requester / subject / approver / executor / verifier / overrider
- 같은 principal 허용 여부
- quorum uniqueness
- authority source/revision
- policy revision
- effect-time revalidation 필요 여부
- approval freshness deadline

### 경고
- 자기 승인
- quorum role collapse
- stale authority
- expired/revoked representation claim
- emergency override without post-review

## 7. Distributed Effect 화면
### Handoff Timeline
`Source committed -> Handoff durable -> Target claimed -> Effected -> Read-back verified`
각 단계의 timestamp, owner, receipt, failure/ambiguity 표시.

### Batch Detail
batch summary와 item receipt를 분리한다.
- total
- verified success
- verified fail
- unknown/ambiguous
- retry eligible
- already committed

`100/100 SUCCESS` 문구는 item read-back 100건이 없으면 사용할 수 없다.

### Irreversibility 표시
Destructive/high-risk action은 실행 전:
- REVERSIBLE
- COMPENSATABLE
- IRREVERSIBLE
- EXTERNALLY_AMBIGUOUS
중 하나를 표시하고, irreversible point와 post-effect remedy를 보여준다.

## 8. Freshness / Historical View
모든 주요 Report/Evidence/Certificate에는:
- historical_decision
- current_disposition
- target revision
- validated_at
- stale_since
- stale trigger
- required revalidation
을 표시한다.

Historical PASS만 있는 경우 현재 화면에서는 `HISTORICAL_PASS_ONLY`로 표시하고 최신 PASS로 색상 승격하지 않는다.

## 9. Observer / Disclosure UX
### Observer Class
동일 내부 상태라도 사용자/상대방/운영자/감사자/지원자에게 다른 projection을 허용할 수 있다. UI는 server-side disclosure class를 따라야 하며 client에서 raw reason을 받아 숨기는 방식으로 구현하지 않는다.

### Cross-channel 확인
설정/관리 화면에서 applicable channel을 표시한다.
- Web
- VS Code
- Email
- Push/SMS
- Webhook
- Export/Report
- Support response
- Accessibility text
- Localization

민감 private state가 한 channel에서 더 구체적으로 노출되면 `DISCLOSURE_DRIFT` 경고.

## 10. Human Review / Automation Bias UX
High-risk AI-assisted review에는 Human decision mode를 기록한다.
- AI_NOT_SHOWN
- INDEPENDENT_REVIEW_THEN_AI
- AI_WITH_EVIDENCE_REVIEW
- AI_OVERRIDE_BY_HUMAN
- INSUFFICIENT_REVIEW_EVIDENCE

### UI 규칙
- high-risk AI 추천을 default-selected action으로 두지 않는다.
- source evidence를 열지 않고 one-click approve 가능한 경로를 금지한다.
- AI confidence를 사실 확률처럼 단독 강조하지 않는다.
- manual/reject path를 AI 동의 path보다 의도적으로 더 어렵게 만들지 않는다.
- AI와 disagree한 reviewer에게 자동 penalty/추가 승인 부담을 주지 않는다.

## 11. Cross-Model Trace Explorer
Trace 화면에서 단순 ID 링크가 아니라 relation cardinality와 보존 책임을 표시한다.
예:
`Function F1 -> Requirements R1,R2 -> Context C1 -> Component P1 -> State Owner S1 -> APIs A1,A2 -> Tests T1..T4`

각 relation:
- 1:1 / 1:N / N:1 / N:M
- responsibility carried
- state/authority carried
- orphan/duplicate warning
- stale status

## 12. Business Semantic Integrity UX
금액·quota·score·SLA 화면에는 authoritative value semantics를 개발자/운영자 상세에서 확인할 수 있어야 한다.
- unit/currency
- precision
- rounding
- components
- source ledger
- adjustment/refund linkage
- reconciliation status

고객 일반 화면에는 복잡한 내부식을 강제 노출하지 않되, mismatch/rounding/adjustment가 Finding 원인인 경우 근거를 이해할 수 있는 수준으로 보여준다.

## 13. Validator Qualification 화면
Admin/Security Auditor는 Capability별:
- QUALIFIED/PARTIAL/NOT_PROVEN/STALE
- validator version/hash
- qualification date
- benchmark family
- critical recall
- critical miss
- isolation mode
- next requalification trigger
을 조회할 수 있다.

Qualification이 STALE인데 해당 capability 결과가 PASS인 경우 상단에 `VALIDATOR_QUALIFICATION_STALE` 경고를 표시한다.

## 14. Delivery/Report 규칙
Delivery Center의 Executive/Technical/Evidence/Certificate 모두 같은 상태 의미를 사용한다.
- excluded/unknown을 숨긴 coverage 숫자 금지
- nonfinal PASS를 Final PASS로 축약 금지
- stale certificate를 valid처럼 표시 금지
- evidence strength를 생략한 high-assurance claim 금지
- Accepted Risk를 Fixed와 같은 완료 배지로 표시 금지

## 15. 화면 수용기준
- 사용자는 10초 안에 현재 결과가 current인지 stale인지 판단 가능
- 사용자는 30초 안에 어떤 claim이 실제 execution/read-back/reperformance로 지지되는지 확인 가능
- actionable right가 blocked된 경우 remedy path를 30초 안에 찾을 수 있음
- batch/async operation의 실제 item/effect 상태가 summary 뒤에 숨지 않음
- AI 추천과 Human 판단 출처가 명확히 구분됨
- 사용자 projection만 보고 private counterparty state를 안정적으로 추론할 수 없어야 함
