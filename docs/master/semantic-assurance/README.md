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

## Machine-level 후보
- `contracts/semantic-assurance-capability-registry.candidate.v1.json`: SA-01~SA-14 Capability registry
- `contracts/semantic-assurance-cross-cutting-controls.candidate.v1.json`: XC-01~XC-30 독립검토 cross-cutting control registry
- `contracts/semantic-assurance-finding-ledger.candidate.v1.json`: source-confirmed Finding/defect-family/canonical-gate block의 machine-readable candidate registry

모든 Registry는 현재 `DESIGN_ONLY_NONFINAL`이며 구현·실행·Qualification을 주장하지 않는다.

## 적용 원칙
기존 02~08을 대체하지 않는다. 각 companion 문서는 해당 parent 문서에 병합될 상세 설계를 별도 보존한 것이며, 기존 본문을 삭제하거나 약화하지 않는다.

OBuilder Gate를 이름만 바꿔 복제하지 않는다. 새로운 Finding이 발생하면 먼저 SA-01~SA-14 중 기존 Capability에 흡수 가능한지 확인하고, 기존 14개로 표현할 수 없는 독립 defect class가 입증될 때만 신규 Capability를 검토한다.

## Finding 관리 원칙
1. Review 횟수와 Finding 개수를 혼동하지 않는다.
2. 서로 다른 실패 시나리오·영향·계약 변경이 필요한 결함은 같은 테마라도 합치지 않는다.
3. 동일 root defect가 여러 contract에서 반복되면 canonical Finding 하나에 source를 추가한다.
4. `NEW_DEFECT_CLASS`, `EXISTING_CONTROL_ENFORCEMENT_GAP`, `CROSS_CONTRACT_SEMANTIC_CONFLICT`, `SEMANTIC_TYPE_ERASURE`, `CANONICAL_GATE_BYPASS`, `DECLARATIVE_ASSERTION_WITHOUT_ENFORCEMENT_PROOF`, `COUNT_OR_LABEL_AS_PROOF`, `STRONG_SEMANTIC_LABEL_EXCEEDS_AVAILABLE_EVIDENCE`를 기본 분류로 사용한다.
5. Finding은 문서에 적혔다고 CLOSED가 아니다. `CONTRACTED -> IMPLEMENTED -> EXECUTED -> EVIDENCE_BOUND -> INDEPENDENTLY_VERIFIED -> QUALIFIED`를 통과해야 한다.

## Canonical Gate 편입 원칙
Semantic Assurance가 실제 제품 Gate가 되려면 설계 문서 존재만으로 충분하지 않다. 최소 다음 네 곳에 모두 편입되어야 한다.

- canonical product lineage
- workflow operation registry
- validation case/negative fixture denominator
- Final acceptance/publication/freshness reconstruction path

한 곳이라도 빠지면 `DESIGNED_CONTROL_OUTSIDE_CANONICAL_GATE_PATH`로 취급하며 Final positive claim의 근거가 될 수 없다.
