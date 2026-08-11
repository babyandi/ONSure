# ONSure Semantic Assurance Companion Design Set

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

이 디렉터리는 기존 `docs/master/02~08`의 문서 책임과 상세도에 맞춰, OBuilder에서 재사용 가치가 확인된 검증 메커니즘을 ONSure용으로 재구성한 companion design set이다.

## 문서
- `00_INTEGRATION_AND_OWNERSHIP.md`: 14개 Capability 통합·권위·중복 방지
- `02_FUNCTIONAL_REQUIREMENTS_EXTENSION.md`: 기능·입력·산출물·수용기준
- `03_REVIEW_SPECIFICATION_EXTENSION.md`: Review Domain·Finding·Decision 규칙
- `04_ARCHITECTURE_DATA_API_EXTENSION.md`: Service·Entity·State·API·Invariant
- `05_UI_UX_WORKFLOW_EXTENSION.md`: Dashboard·Verification·Rights·Authority·Freshness·AI UX
- `06_TEST_OPERATION_EXTENSION.md`: negative/adversarial fixture·failure injection·Runbook
- `07_AI_AGENT_METHOD_EXTENSION.md`: AI-UC authority, TEVV, Human judgment, Method requalification
- `08_OPEN_DECISIONS_EXTENSION.md`: Contract/정책/임계치/구현 미확정 추적

## 적용 원칙
기존 02~08을 대체하지 않는다. 각 companion 문서는 해당 parent 문서에 병합될 상세 설계를 별도 보존한 것이며, 기존 본문을 삭제하거나 약화하지 않는다.

Machine-level 후보 registry는 `contracts/semantic-assurance-capability-registry.candidate.v1.json`이다. 이 Registry는 현재 `DESIGN_ONLY_NONFINAL`이며 구현·실행·Qualification을 주장하지 않는다.
