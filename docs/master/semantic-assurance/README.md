# ONSure Semantic Assurance Companion Design Set

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

이 디렉터리는 기존 `docs/master/02~08`의 문서 책임과 상세도에 맞춰, OBuilder에서 재사용 가치가 확인된 검증 메커니즘과 ONSure 자체 독립검토에서 발견한 false-assurance·권위·시간·독립성·상태·파생 Receipt 의미손실·canonical gate 우회 문제를 ONSure용으로 재구성한 companion design set이다.

## 문서
- `00_INTEGRATION_AND_OWNERSHIP.md`: 14개 Capability 통합·권위·중복 방지 + 독립검토 cross-cutting control 배치
- `02_FUNCTIONAL_REQUIREMENTS_EXTENSION.md`: 기능·입력·산출물·수용기준
- `03_REVIEW_SPECIFICATION_EXTENSION.md`: Review Domain·Finding·Decision 규칙
- `04_ARCHITECTURE_DATA_API_EXTENSION.md`: Service·Entity·State·API·Invariant
- `05_UI_UX_WORKFLOW_EXTENSION.md`: Dashboard·Verification·Rights·Authority·Freshness·AI UX
- `06_TEST_OPERATION_EXTENSION.md`: negative/adversarial fixture·failure injection·Runbook
- `07_AI_AGENT_METHOD_EXTENSION.md`: AI-UC authority, TEVV, Human judgment, Method requalification
- `08_OPEN_DECISIONS_EXTENSION.md`: Contract/정책/임계치/구현 미확정 추적
- `09_INDEPENDENT_REVIEW_FINDINGS_INTEGRATION.md`: Requirement Universe, Meta-Validator, Hidden/Golden governance, Trusted Time, Offline Revocation, Retry Cherry-Picking, Reviewer Qualification 등 독립검토 결과의 14개 Capability 통합설계
- `10_FINDING_LEDGER.md`: 반복 독립검토에서 실제 source로 확인한 P0/P1 Finding의 canonical ledger. Raw candidate observation과 canonical defect를 분리하고 source/실패시나리오/영향/SA-XC mapping/필수 변경을 보존
- `11_CONTRACT_UPGRADE_BLUEPRINT.md`: Finding을 Status/Receipt/Authority/Target/Requirement/Harness/Learning/Patch-Git-Deployment/Final/Workflow/Meta-validator v2 Contract로 내리는 상세 설계
- `12_P0_VERTICAL_TRACEABILITY_AND_APPLICATION.md`: P0 Finding을 기존 02~08 책임구조로 역매핑한 수직 적용설계. Finding→Requirement→Review→Contract→UX→Fixture→Qualification 계보 정의
- `13_V2_CONTRACT_MIGRATION_AND_VALIDATION_PLAN.md`: Candidate v2 Contract의 v1→v2 migration, static qualification, negative fixture, shadow gate, active selector, rollback 계획

## Machine-level 후보
### Semantic Assurance Registry
- `contracts/semantic-assurance-capability-registry.candidate.v1.json`: SA-01~SA-14 Capability registry
- `contracts/semantic-assurance-cross-cutting-controls.candidate.v1.json`: XC-01~XC-30 독립검토 cross-cutting control registry
- `contracts/semantic-assurance-finding-ledger.candidate.v1.json`: source-confirmed Finding/defect-family/canonical-gate block의 machine-readable candidate registry
- `contracts/semantic-assurance-gate-integration.candidate.v1.json`: Product Lineage, Workflow Operation, Validation Case, Final Acceptance/Publication 네 canonical path에 Semantic Assurance를 편입하기 위한 hard-block/operation 후보 contract

### P0 Core Contract v2 Candidate
- `contracts/assurance-status-vocabulary.candidate.v2.schema.json`
- `contracts/assurance-receipt-envelope.candidate.v2.schema.json`
- `contracts/authority-principal-profile.candidate.v2.schema.json`
- `contracts/semantic-assurance-gate-receipt.candidate.v2.schema.json`
- `contracts/workflow-operation-registry.candidate.v2.json`
- `contracts/product-process-lineage.candidate.v2.json`

위 v2 Candidate는 다음 P0 defect family를 우선 차단하도록 설계한다.
- success-only/strong-label false assurance
- semantic type erasure across derived receipts
- authority role/key self-attestation
- Local OTester/OAudit와 true independent gate 혼동
- stale/revoked/qualification state 표현 부재
- Final Candidate/Lock의 scope/epoch/freshness/OTester/OAudit/Human binding 부족
- Semantic Assurance 기능이 canonical operation/path 밖에 남는 문제
- deployment와 검증 artifact identity가 분리되는 문제

## 적용 원칙
기존 02~08을 대체하지 않는다. 각 companion 문서는 해당 parent 문서에 병합될 상세 설계를 별도 보존한 것이며, 기존 본문을 삭제하거나 약화하지 않는다.

OBuilder Gate를 이름만 바꿔 복제하지 않는다. 새로운 Finding이 발생하면 먼저 SA-01~SA-14 중 기존 Capability에 흡수 가능한지 확인하고, 기존 14개로 표현할 수 없는 독립 defect class가 입증될 때만 신규 Capability를 검토한다.

## Finding 관리 원칙
1. Review 횟수와 Finding 개수를 혼동하지 않는다.
2. 서로 다른 실패 시나리오·영향·계약 변경이 필요한 결함은 같은 테마라도 합치지 않는다.
3. 동일 root defect가 여러 contract에서 반복되면 canonical Finding 하나에 source를 추가한다.
4. `NEW_DEFECT_CLASS`, `EXISTING_CONTROL_ENFORCEMENT_GAP`, `CROSS_CONTRACT_SEMANTIC_CONFLICT`, `SEMANTIC_TYPE_ERASURE`, `CANONICAL_GATE_BYPASS`, `DECLARATIVE_ASSERTION_WITHOUT_ENFORCEMENT_PROOF`, `COUNT_OR_LABEL_AS_PROOF`, `STRONG_SEMANTIC_LABEL_EXCEEDS_AVAILABLE_EVIDENCE`를 기본 분류로 사용한다.
5. Finding은 문서에 적혔다고 CLOSED가 아니다. `CONTRACTED -> IMPLEMENTED -> EXECUTED -> EVIDENCE_BOUND -> INDEPENDENTLY_VERIFIED -> QUALIFIED`를 통과해야 한다.
6. P0 Finding은 `12_P0_VERTICAL_TRACEABILITY_AND_APPLICATION.md`의 02~08 책임열이 모두 닫히기 전에는 설계 완료로도 승격하지 않는다.

## Canonical Gate 편입 원칙
Semantic Assurance가 실제 제품 Gate가 되려면 설계 문서 존재만으로 충분하지 않다. 최소 다음 네 곳에 모두 편입되어야 한다.

- canonical product lineage
- workflow operation registry
- validation case/negative fixture denominator
- Final acceptance/publication/freshness reconstruction path

한 곳이라도 빠지면 `DESIGNED_CONTROL_OUTSIDE_CANONICAL_GATE_PATH`로 취급하며 Final positive claim의 근거가 될 수 없다. 현재 편입 설계 상태는 `contracts/semantic-assurance-gate-integration.candidate.v1.json`이 추적한다.

## v2 이행 경계
v2 Candidate Contract는 기존 v1을 즉시 대체하지 않는다.
- transition은 `Dual Read / Single Authority`를 따른다.
- v1 PASS를 v2 PASS로 자동 승격하지 않는다.
- v1에서 복원할 수 없는 nonce/expiry/authority/independence/freshness는 추정값으로 채우지 않는다.
- v2 Gate는 static qualification, negative fixture, runtime enforcement, shadow comparison, independent qualification 이후에만 active selector 후보가 된다.
- `workflow-operation-registry.candidate.v2.json`과 `product-process-lineage.candidate.v2.json`은 실행경로 후보이며 아직 dispatcher/runtime authority가 아니다.

## 현재 상태
- SA-01~14: `DESIGN_ONLY`
- XC-01~30: `DESIGN_ONLY`
- Finding Ledger: source-confirmed design input, Finding closure는 아님
- P0 수직 Traceability: 설계 반영 완료, Runtime 미실행
- v2 Core Contract 6종: `CONTRACT_CANDIDATE_CREATED / EXECUTION_NOT_RUN`
- v1→v2 adapter/runtime/active selector: 미구현
- independent OTester/OAudit qualification: 미실행
- FinalLock/Production/Commercial authority: 없음

따라서 현재 산출물은 설계·계약 후보 수준이며 구현·실행·Final PASS를 주장하지 않는다.
