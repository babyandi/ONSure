# ONSure Semantic Assurance 검토 체크리스트·미확정 항목 확장

Status: `DRAFT / NON_FINAL`
Parent tracker: `../08_REVIEW_CHECKLIST_OPEN_DECISIONS.md`

## 1. 목적
Semantic Assurance Companion Design의 항목 중 아직 실제 Contract/Runtime/운영 임계치가 없는 부분을 명시적으로 추적한다. 이 문서는 권위 원본이 아니라 결정·구현 진행상황 추적용이다.

상태:
- `OPEN`: 결정 또는 설계가 아직 없음
- `DRAFT`: 제안값/설계가 있으나 확정되지 않음
- `DESIGN_ONLY`: 상세설계는 있으나 계약/코드가 없음
- `CONTRACT_NEEDED`: machine contract 제정 필요
- `IMPLEMENTATION_NEEDED`: contract는 있으나 runtime 미구현
- `NOT_RUN`: 실행 검증 미실행
- `CONFIRMED`: 권위자/계약/실행으로 확정

## A. Capability 계약 제정
| ID | 항목 | 현재 상태 | 필요한 산출물 |
|---|---|---|---|
| SA-C01 | Semantic Capability Registry | DESIGN_ONLY | capability registry contract + validator |
| SA-C02 | Denominator Discovery/Epoch | CONTRACT_NEEDED | denominator epoch/change-candidate schema |
| SA-C03 | Obligation Closure | CONTRACT_NEEDED | resolution expression + routing schema |
| SA-C04 | Evidence Reperformance | CONTRACT_NEEDED | reperformance run/report schema |
| SA-C05 | Evidence Strength Vocabulary | DRAFT | canonical enum/decision mapping |
| SA-C06 | Canonical State Authority Map | CONTRACT_NEEDED | state owner/command/effect schema |
| SA-C07 | Rights Reachability/Fixed Point | CONTRACT_NEEDED | rights assurance schema/validator |
| SA-C08 | Authority Lifecycle/Decision-Effect | CONTRACT_NEEDED | authority lifecycle/snapshot schema |
| SA-C09 | Distributed Effect/Handoff/Batch | CONTRACT_NEEDED | handoff/batch/item-effect schemas |
| SA-C10 | Observer/Disclosure Equivalence | CONTRACT_NEEDED | equivalence/disclosure profile schema |
| SA-C11 | Cross-Model Mapping | CONTRACT_NEEDED | mapping/relation schema |
| SA-C12 | Business Semantic Invariants | CONTRACT_NEEDED | quantitative invariant schema |
| SA-C13 | AI Use Case Assurance | CONTRACT_NEEDED | AI-UC closure/profile/TEVV mapping schema |
| SA-C14 | Validator Requalification | CONTRACT_NEEDED | qualification method/run/receipt schema |

## B. Authority / Identity 결정 필요
| ID | 항목 | 상태 | 확인 필요 |
|---|---|---|---|
| SA-A01 | First Organization Owner bootstrap authority | OPEN | signup/SSO/invite/provider 중 canonical source |
| SA-A02 | Last Owner exit policy | OPEN | successor 강제/controlled dissolution/HOLD 정책 |
| SA-A03 | Delegation max depth/redelegation | OPEN | product별 허용치와 cycle prohibition |
| SA-A04 | High-risk effect authorization mode | DRAFT | SNAPSHOT/REVALIDATE/HYBRID 분류 기준 |
| SA-A05 | Representation proof level | OPEN | SERVICE_VERIFIED/STRONG_VERIFIED의 실제 증거 기준 |
| SA-A06 | Emergency override policy | OPEN | 사용주체, expiry, quorum, post-review SLA |
| SA-A07 | SoD required operation set | DRAFT | Enterprise 외 일반 Plan 적용범위 |
| SA-A08 | Policy precedence algorithm | OPEN | deny/allow/specificity/exception 우선순위 |

## C. Evidence / Qualification 결정 필요
| ID | 항목 | 상태 | 확인 필요 |
|---|---|---|---|
| SA-E01 | Material Claim 최소 Evidence Strength | OPEN | REPERFORMED_BOUND 의무 적용 범위 |
| SA-E02 | Reperformance 표본 vs 전수 | OPEN | risk-based sampling 허용 범위 |
| SA-E03 | Raw log 보존기간 | OPEN | privacy/비용/감사 요구 균형 |
| SA-E04 | Independent Oracle 정의 | DRAFT | target-code dependency NONE/PARTIAL/SHARED 허용기준 |
| SA-E05 | Hidden Qualification corpus owner | OPEN | 개발조직과 분리된 authority 필요 |
| SA-E06 | Hidden corpus access/rotation | OPEN | 접근주체, rotation 주기, leakage 시 무효화 범위 |
| SA-E07 | Critical strict recall 기준 | DRAFT | L5는 100% 후보, lower assurance 기준 미정 |
| SA-E08 | Qualification expiry | OPEN | 시간/변경 trigger 조합 |
| SA-E09 | Method transport fidelity | DESIGN_ONLY | canonical manifest + validator 필요 |

## D. Coverage / Denominator 결정 필요
| ID | 항목 | 상태 | 확인 필요 |
|---|---|---|---|
| SA-D01 | authoritative denominator source ordering | OPEN | contract/user docs/code/runtime/standard 간 우선순위 |
| SA-D02 | High denominator change candidate 승인 authority | OPEN | Customer Owner/Reviewer/Professional/Compliance 역할 |
| SA-D03 | Critical negative-space mandatory set | DRAFT | 제품 archetype별 필수 cancel/revoke/recovery 등 |
| SA-D04 | Out-of-scope critical exclusion | OPEN | 절대금지 vs signed risk disposition |
| SA-D05 | Unknown discovery completeness | OPEN | unknown_count=0을 어떻게 qualification할지 |

## E. Distributed Effect 결정 필요
| ID | 항목 | 상태 | 확인 필요 |
|---|---|---|---|
| SA-X01 | Batch atomicity class selection | OPEN | ATOMIC/BEST_EFFORT/BOUNDED_PARTIAL/SAGA 적용 규칙 |
| SA-X02 | External ambiguous effect timeout | OPEN | reconciliation deadline/operator escalation |
| SA-X03 | Irreversible effect class inventory | OPEN | Payment/Git/Certificate/Notification/Publication 등 |
| SA-X04 | Compensation evidence model | DESIGN_ONLY | original effect와 immutable correlation contract |
| SA-X05 | Terminal dependency graph authority | OPEN | runtime-discovered graph vs registry ownership |

## F. Privacy / Observer 결정 필요
| ID | 항목 | 상태 | 확인 필요 |
|---|---|---|---|
| SA-P01 | Observable equivalence tolerance | OPEN | latency/length bucket 허용 기준 |
| SA-P02 | Padding/constant-time 적용범위 | OPEN | usability/비용 대비 high-risk surface 선정 |
| SA-P03 | Appeal disclosure minimum | OPEN | contestability와 anti-retaliation 균형 |
| SA-P04 | Support/operator disclosure boundary | OPEN | support macro와 audit view 분리 |
| SA-P05 | Accessibility/localization differential test | DESIGN_ONLY | 자동/수동 시험 방식 |

## G. Business Semantic 결정 필요
| ID | 항목 | 상태 | 확인 필요 |
|---|---|---|---|
| SA-B01 | authoritative money type | OPEN | decimal/minor-unit representation 표준 |
| SA-B02 | rounding mode/stage | OPEN | 상품/세금/환불별 정책 |
| SA-B03 | FX authority | OPEN | source/timestamp/revision 기준 |
| SA-B04 | quota/credit conservation invariant | DRAFT | reserve/commit/release와 semantic pack 연결 |
| SA-B05 | ProgramRiskScore semantic hard gate | DRAFT | score가 hard invariant를 우회하지 않는 계약 강화 |

## H. AI / Human 결정 필요
| ID | 항목 | 상태 | 확인 필요 |
|---|---|---|---|
| SA-H01 | AI-UC canonical denominator | CONTRACT_NEEDED | applicability/use-case/TEVV identity 통합 |
| SA-H02 | Automation effect ceiling vocabulary | DRAFT | product별 canonical enum |
| SA-H03 | High-risk human decision mode | OPEN | 어떤 operation에 AI_NOT_SHOWN baseline을 강제할지 |
| SA-H04 | Reviewer automation-bias metric | OPEN | agreement/disagreement를 품질로 오용하지 않는 지표 |
| SA-H05 | Memory-blind technical isolation | DESIGN_ONLY | denied source/retrieval/cache 증거 방식 |
| SA-H06 | Ground Truth qualification | OPEN | GT 등급별 최소 provenance/evidence |

## I. 구현 / Operation Registry
모든 신규 workflow operation은 구현 전에 `contracts/workflow-operation-registry.v1.json`에 등록되어야 한다.

후보:
- semantic.denominator.discover
- semantic.denominator.challenge
- semantic.reperformance.run
- semantic.authority.assess
- semantic.rights.assess
- semantic.state-authority.assess
- semantic.handoff.assess
- semantic.batch.assess
- semantic.validator.qualify

현재 모두 `DESIGN_ONLY`; 등록 전 구현 완료 주장 금지.

## J. 문서 병합 체크
Companion design을 기존 02~08 정본으로 병합할 때 다음을 확인한다.
- 기존 본문 삭제/의미 약화 없음
- 기존 계약 권위와 충돌하는 신규 enum은 DESIGN_ONLY 표시
- 같은 기능을 신규 서비스로 중복 생성하지 않음
- 02 기능 요구 ↔ 03 Review ↔ 04 Architecture ↔ 05 UX ↔ 06 Test ↔ 07 Method ↔ 08 Tracker가 같은 Capability ID를 사용
- 적용 후 doc/contract/status denominator와 stale 상태 재검증

## K. Final 경계
위 표의 OPEN/DRAFT/DESIGN_ONLY/CONTRACT_NEEDED 항목이 존재하는 동안 해당 Capability는 구현 완료 또는 Qualified로 표현하지 않는다. 이 체크리스트의 완료는 Merge/Deployment/Production/Commercial/FinalLock 권위를 만들지 않는다.
