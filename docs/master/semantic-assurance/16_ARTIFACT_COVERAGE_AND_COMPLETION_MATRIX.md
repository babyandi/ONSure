# ONSure Semantic Assurance 산출물 Coverage 및 완성도 Matrix

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
현재까지 검토된 P0/P1 Finding이 문서, Contract, 실행경로, Fixture, Migration 산출물에 빠짐없이 내려갔는지 확인한다. 이 문서는 산출물 개수 확인표가 아니라 **Finding→Artifact→Enforcement 상태**를 관리한다.

## 2. 설계 산출물 Coverage
| Layer | Artifact | 현재 상태 | 완료 조건 |
|---|---|---|---|
| 통합/권위 | `00_INTEGRATION_AND_OWNERSHIP.md` | DESIGN_PRESENT | active authority selector와 owner binding |
| 기능 | `02_FUNCTIONAL_REQUIREMENTS_EXTENSION.md` | DESIGN_PRESENT | atomic requirement + runtime operation binding |
| Review | `03_REVIEW_SPECIFICATION_EXTENSION.md` | DESIGN_PRESENT | Finding validator/rule IDs와 actual execution |
| Architecture/API | `04_ARCHITECTURE_DATA_API_EXTENSION.md` | DESIGN_PRESENT | v2 contracts + service/runtime implementation |
| UI/UX | `05_UI_UX_WORKFLOW_EXTENSION.md` | DESIGN_PRESENT | surface semantic parity execution |
| Test/Operation | `06_TEST_OPERATION_EXTENSION.md` | DESIGN_PRESENT | real fixture execution receipts |
| AI/Agent | `07_AI_AGENT_METHOD_EXTENSION.md` | DESIGN_PRESENT | qualification/blind/benchmark execution |
| Open Decision | `08_OPEN_DECISIONS_EXTENSION.md` | DESIGN_PRESENT | decision authority/closure receipts |
| Independent Findings | `09_INDEPENDENT_REVIEW_FINDINGS_INTEGRATION.md` | DESIGN_PRESENT | controls contracted and executed |
| Finding Ledger | `10_FINDING_LEDGER.md` | CANONICAL_BATCH_PRESENT | every Finding has disposition/evidence |
| Contract Blueprint | `11_CONTRACT_UPGRADE_BLUEPRINT.md` | DESIGN_PRESENT | bundle contracts all candidate/implemented |
| P0 Vertical Trace | `12_P0_VERTICAL_TRACEABILITY_AND_APPLICATION.md` | DESIGN_PRESENT | 02~08 columns all closed per P0 |
| Migration Plan | `13_V2_CONTRACT_MIGRATION_AND_VALIDATION_PLAN.md` | DESIGN_PRESENT | shadow→selector migration executed |
| v1-v2 Gap Matrix | `14_V1_V2_SEMANTIC_GAP_MATRIX.md` | DESIGN_PRESENT | every affected v1 contract classified |
| Static Fixture Spec | `15_V2_STATIC_QUALIFICATION_FIXTURE_SPEC.md` | DESIGN_PRESENT | fixture validator execution receipt |
| Coverage Matrix | `16_ARTIFACT_COVERAGE_AND_COMPLETION_MATRIX.md` | CURRENT_INDEX | maintained with every new artifact |

## 3. Machine Artifact Coverage
### Registry / Governance
- `semantic-assurance-capability-registry.candidate.v1.json`
- `semantic-assurance-cross-cutting-controls.candidate.v1.json`
- `semantic-assurance-finding-ledger.candidate.v1.json`
- `semantic-assurance-gate-integration.candidate.v1.json`
- `semantic-assurance-artifact-coverage.candidate.v1.json`

### Status / Receipt / Authority
- `assurance-status-vocabulary.candidate.v2.schema.json`
- `assurance-receipt-envelope.candidate.v2.schema.json`
- `authority-principal-profile.candidate.v2.schema.json`

### Denominator / Applicability / Population
- `semantic-denominator-epoch.candidate.v2.schema.json`
- `semantic-applicability-set.candidate.v2.schema.json`
- `assurance-population-denominator.candidate.v2.schema.json`

### Execution / Qualification
- `execution-identity.candidate.v2.schema.json`
- `validator-qualification-record.candidate.v2.schema.json`
- `blind-context-manifest.candidate.v2.schema.json`
- `human-reviewer-qualification.candidate.v2.schema.json`
- `qualification-benchmark-manifest.candidate.v2.schema.json`

### Gate / Workflow / Deployment / Activation
- `semantic-assurance-gate-receipt.candidate.v2.schema.json`
- `workflow-operation-registry.candidate.v2.json`
- `product-process-lineage.candidate.v2.json`
- `verified-to-deployed-receipt.candidate.v2.schema.json`
- `contract-active-selector.candidate.v2.schema.json`

### Static Qualification
- `semantic-assurance-v2-schema-instance-registry.candidate.v1.json`
- `scripts/validate-semantic-assurance-v2-contracts.py`
- `fixtures/contracts/v2/*`

## 4. P0 Root Defect Family Coverage
| Defect family | Requirement | Review | Contract | Fixture | Execution Path | 현재 상태 |
|---|---|---|---|---|---|---|
| Canonical Identity | 02/12 | 03 | Receipt/Execution Identity v2 | 일부 | Reperformance | PARTIAL_CANDIDATE |
| Authority/SoD | 02/12 | 03 | Authority Principal v2 | 일부 | authority.revalidate | CONTRACT_CANDIDATE |
| Independent Gate | 02/12 | 03 | Receipt/Gate/Reviewer v2 | self-OTester fixture | OTester/OAudit accept | CONTRACT_CANDIDATE |
| Status/Finality | 02/12 | 03 | Status/Gate v2 | PASS/STALE fixtures | Final reconstruction | CONTRACT_CANDIDATE |
| Freshness/Revocation | 02/12 | 03 | Status/Gate/Lineage v2 | 일부 | freshness invalidate/reconstruct | PARTIAL_CANDIDATE |
| Denominator/Applicability | 02/12 | 03 | Denominator/Applicability/Population v2 | 미생성 | denominator operations | CONTRACT_CANDIDATE_FIXTURE_PENDING |
| Harness/Oracle Identity | 02/12 | 03 | Execution Identity v2 | 미생성 | reperformance | CONTRACT_CANDIDATE_FIXTURE_PENDING |
| Validator Qualification | 02/12 | 03/07 | Validator/Benchmark/Blind/Reviewer v2 | 미생성 | validator.requalify | CONTRACT_CANDIDATE_FIXTURE_PENDING |
| Semantic Type Preservation | 02/12 | 03 | Receipt Envelope v2 | 2 invalid | lineage parent preservation | CONTRACT_CANDIDATE |
| Patch/Git/Deployment | 02/12 | 03 | Verified-to-Deployed v2 + workflow/lineage | 미생성 | git.push/deployment.verify-installed | PARTIAL_CANDIDATE |
| Canonical Gate Integration | 02/12 | 03 | Gate/Workflow/Lineage v2 | gate invalid fixtures | final reconstruction | CONTRACT_CANDIDATE |
| Active Version Authority | 08/13 | 03/04 | Active Selector v2 | 미생성 | activation/rollback | CONTRACT_CANDIDATE_FIXTURE_PENDING |

## 5. Static Fixture Coverage
현재 4개 핵심 Schema에 대해 valid 4개, semantic invalid 8개를 생성했다.
- Status v2
- Receipt Envelope v2
- Authority Principal v2
- Semantic Gate v2

나머지 v2 Candidate는 Contract 생성까지 완료했으며 fixture는 후속 Batch에서 생성해야 한다. Fixture가 없는 Contract는 `STATIC_QUALIFICATION_PENDING`이다.

## 6. Canonical Gate Coverage
Semantic Assurance가 실제 Final hard gate가 되기 위해서는 다음 네 축이 모두 runtime에 연결되어야 한다.
1. Product Lineage v2
2. Workflow Operation Registry v2
3. Validation/Final population denominator v2
4. Final Reconstruction/Gate v2

현재 네 축 모두 **Candidate artifact는 존재**하지만 Runtime wiring/real execution은 아직 없다. 따라서 `DESIGNED_CONTROL_OUTSIDE_CANONICAL_GATE_PATH` Finding은 설계상 remediation이 정의됐으나 실행상 CLOSED가 아니다.

## 7. Finding Closure 상태
현재 허용 가능한 최고 상태:
- 문서 반영 Finding: `DESIGN_ACCEPTED`
- v2 Candidate Contract까지 생성된 Finding: `CONTRACTED_CANDIDATE`
- Fixture까지 생성된 Finding: `TEST_DESIGNED`

아직 허용하지 않는 상태:
- `IMPLEMENTED`
- `EXECUTED`
- `EVIDENCE_BOUND`
- `INDEPENDENTLY_VERIFIED`
- `QUALIFIED`
- `VERIFIED_CLOSED`

## 8. 다음 Completion Batch
1. Denominator/Applicability/Population v2 valid/invalid fixture
2. Execution Identity/Validator Qualification/Blind/Reviewer/Benchmark fixture
3. Verified-to-Deployed/Active Selector fixture
4. Static validator 실제 실행 및 receipt
5. v1→v2 adapter/reconstructor implementation design
6. Final Acceptance/Validation Case population migration instance
7. Shadow Gate comparison artifact

## 9. 비최종 경계
이 Matrix의 모든 `CONTRACT_CANDIDATE`는 active contract가 아니다. Candidate 존재만으로 기존 v1 authority를 폐기하거나 Final/Merge/Production/Commercial 권한을 생성하지 않는다.
