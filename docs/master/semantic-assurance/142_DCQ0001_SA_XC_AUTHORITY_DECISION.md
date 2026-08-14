# 142 DCQ-0001 SA/XC Authority Decision

Status: `DESIGN_AUTHORITY_DECISION / DCQ-0001_RESOLVED_FOR_DESIGN`

DCQ-0001의 SA-01~SA-14와 XC-01~XC-30은 Product Design Requirement Universe의 독립 Requirement ID가 아니라 Semantic Assurance companion의 Design Capability / Cross-Cutting Control ID로 판정한다.

## 근거
`00_INTEGRATION_AND_OWNERSHIP.md`는 SA-01~14를 기존 `02~08`의 책임구조에 맞춰 재구성·흡수되는 검증 Capability로 정의하고, 신규 Capability가 향후 `Requirement -> Contract -> Runtime Enforcement -> Negative Fixture -> Execution Evidence -> Qualification` 계보를 가져야 한다고 명시한다. 즉 SA ID 자체가 Requirement의 대체 ID가 아니라 Requirement를 구현·검증하는 설계 Capability다.

XC 계열도 같은 companion 설계의 교차 통제이며 Requirement denominator를 별도로 증가시키는 독립 요구 원천으로 취급하지 않는다.

## Canonical rule
- SA-* = DESIGN_CAPABILITY
- XC-* = DESIGN_CONTROL
- Product Design Requirement Universe의 `requirement_id` population에는 직접 산입하지 않는다.
- SA/XC는 Global Trace의 `design_refs[]`에 기록한다.
- 모든 authoritative SA/XC는 최소 하나 이상의 Requirement에 의해 정당화되어야 한다. Requirement 연결이 없는 SA/XC는 `DESIGN_WITHOUT_REQUIREMENT` orphan이다.
- 하나의 Requirement가 여러 SA/XC를 참조할 수 있고, 하나의 SA/XC가 여러 Requirement를 구현할 수 있다.
- SA/XC를 EXPLICIT_ID Requirement로 중복 materialize하여 denominator를 부풀리는 것을 금지한다.

## Development consequence
Batch 0 extractor가 SA-*/XC-*를 EXPLICIT_ID에서 제외한 것은 올바르다. `requirement-record.v2.schema.json`의 source_class enum에 SA/XC 전용 다섯 번째/여덟 번째 source class를 추가하지 않는다.

DCQ-0001은 `RESOLVED_WITH_AUTHORITY`로 disposition한다. 다만 SA/XC -> Requirement reverse trace completeness는 별도 orphan gate에서 검증한다.
