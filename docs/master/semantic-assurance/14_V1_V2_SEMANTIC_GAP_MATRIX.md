# ONSure v1→v2 Semantic Gap Matrix

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
이 문서는 P0 Finding을 실제 v1 Contract에서 v2 Candidate Contract로 이행할 때 어떤 의미가 **직접 보존되는지, read-back/reperformance가 필요한지, v1만으로는 복구 불가능한지**를 계약별로 고정한다.

분류 상태는 다음과 같다.
- `DIRECTLY_MAPPABLE`: v1의 명시 필드에서 손실 없이 변환 가능
- `DERIVABLE_WITH_PROOF`: 계산/조합 가능하나 source proof가 필요
- `REQUIRES_READBACK`: 현재 authoritative 대상의 재조회 필요
- `REQUIRES_REPERFORMANCE`: 실제 검증/Oracle 재실행 필요
- `REQUIRES_HUMAN_OR_EXTERNAL_AUTHORITY`: 외부 또는 인간 권위 증적 필요
- `UNRECOVERABLE_FROM_V1`: v1만으로는 복구 불가, 추정값 생성 금지

## 2. Status / Finality
| v1 Contract | v1 의미 | v2 요구 | Gap 분류 | Migration rule |
|---|---|---|---|---|
| `status-vocabulary.v1.json` | implementation/verification 2축 | execution/evidence/independence/qualification/freshness/publication 다차원 | UNRECOVERABLE_FROM_V1 | 누락 차원은 UNKNOWN/NOT_ASSESSED/NOT_QUALIFIED로 유지 |
| `state-model-mapping.v1.json` | publication 순서 문자열/상태 | revocation/reassessment edge 포함 publication state | DERIVABLE_WITH_PROOF | 기존 state는 historical projection으로만 보존 |
| `oruda-final-candidate-gate.v1.schema.json` | job id 2개 + eligible/decision | exact receipt set + epochs + semantic closure | REQUIRES_RECONSTRUCTION | v1 candidate를 v2 PASS로 자동 승격 금지 |
| `oruda-final-lock.v1.schema.json` | candidate/job/approval hash 중심 | freshness/independence/qualification/human acceptance/finding closure | UNRECOVERABLE_FROM_V1 | v2 gate receipt 없으면 NON_FINAL/HOLD |

## 3. Evidence / Receipt
| v1 Contract | 손실 위험 | v2 필수 보강 | Gap 분류 |
|---|---|---|---|
| `evidence-receipt.v1.schema.json` | signature/canonicalization/context/qualification 약함 | typed subject/context/authority/independence/qualification/freshness/signature | REQUIRES_RECONSTRUCTION |
| `receipt-envelope.v1.schema.json` | authority/state free text, tenant/toolchain/independence 소실 | `assurance-receipt-envelope.v2` typed sections | UNRECOVERABLE_FROM_V1 일부 |
| `local-agent-receipt.v1.schema.json` | OTESTER/OAUDIT 이름과 SELF_VALIDATION_NONFINAL 혼재 | assurance class + independent=false를 downstream까지 보존 | DIRECTLY_MAPPABLE + SEMANTIC_PRESERVATION_REQUIRED |
| `oruda-independent-run-receipt.v1.schema.json` | signature/oracle/validator/qualification 없음 | independent profile + qualification + exact oracle/validator set | REQUIRES_REPERFORMANCE |
| `oruda-blind-review-receipt.v1.schema.json` | blind context/서명/qualification 없음 | BlindContextManifest + DeniedSourceAccessReceipt + reviewer profile | REQUIRES_HUMAN_OR_EXTERNAL_AUTHORITY |

## 4. Authority / Principal
| v1 Contract | Gap | v2 처리 |
|---|---|---|
| `oruda-authority-key-registry.v1.schema.json` | registry unsigned, public key path only, role/principal SoD 약함 | signed registry epoch + public key digest + scope + principal/admin owner |
| approval receipt family | actor/key는 있으나 target/tenant/purpose/authority epoch 일관성 부족 | authority profile digest와 effect-time validity 필수 |
| durable job approval history | nonce/expiry/authority epoch 소실 | derived contract preservation set 필수 |

## 5. Requirement / Denominator / Coverage
| v1/현재 산출물 | Gap | v2 처리 |
|---|---|---|
| requirements traceability | capability group 수준, atomic mapping pending | requirement universe snapshot + atomic IDs + denominator epoch |
| validation case registry | minimum count를 coverage proxy로 사용 | exact case ID/digest population + applicability disposition |
| final acceptance source registry | expected count/source anchor 의존 | source ID/digest + authority epoch + denominator digest |
| package/document/omission counts | count/label이 실체를 대신 | exact item identity + uniqueness + population digest |

## 6. Harness / Oracle / Behavior
| v1 Contract | Gap | v2 처리 |
|---|---|---|
| harness command manifest | command/script/fixture/oracle identity 불충분 | executable/script/fixture/oracle/validator/environment exact digest |
| behavior profile | observation decision과 oracle binding 약함, count/stable boolean 재계산 안 됨 | observation receipt set + oracle/validator profile + derived statistics proof |
| behavior observation receipt | oracle/executor/signature 불충분 | typed receipt envelope v2로 감쌈 |

## 7. Learning / Memory / Qualification
| v1 Contract | Gap | v2 처리 |
|---|---|---|
| failure memory | VERIFIED state에 verification receipt 필수 아님 | verification receipt digest + validator qualification epoch |
| improvement memory | improvement proven과 proof binding 약함 | ImprovementProof exact digest + regression run set |
| reusable pattern memory | privacy/rights review const PASS | reviewer principal/receipt/expiry |
| official learning ledger | authority/self-declared independence | signed ledger epoch + exact independent receipt set |

## 8. Patch / Git / Deployment
| v1 Contract | Gap | v2 처리 |
|---|---|---|
| patch apply receipt | approval expiry/action scope/test receipt 의미 소실 | approval semantic preservation + hunk-level binding |
| git delivery approval | base branch head 미고정 | approved base/head digest + remote repository identity |
| git change set | push PASS에 remote read-back 없음 | provider receipt + pushed ref digest |
| draft PR receipt | base SHA/read-back 부족 | provider PR identity + base/head read-back |
| workflow registry | deployment operation은 있으나 lineage 밖 | deployment + verified-to-deployed canonical stage |

## 9. Final / Independent Gate
v2 Final Candidate reconstruction은 최소 다음이 모두 source-observable해야 한다.
- target/source/artifact exact identity
- requirement/scope/denominator/policy/oracle/validator/authority epoch
- Evidence Bundle exact digest
- Semantic Capability applicable set and closure
- Freshness Barrier
- distinct Independent OTester/OAudit principal/profile/receipt
- Human Acceptance
- open P0/P1 exact blocking set
- revocation/current disposition
- deployment artifact equality when deployment is in scope

하나라도 `UNRECOVERABLE_FROM_V1`이면 v1 PASS를 v2 PASS로 변환하지 않는다.

## 10. Migration 우선순위
1. Receipt/Authority semantic preservation
2. Status/freshness/qualification ontology
3. Requirement/denominator exact population
4. Harness/oracle/validator identity
5. Workflow Operation v2
6. Product Lineage v2
7. Independent Gate reconstruction
8. Final Gate shadow comparison
9. Active selector

## 11. 수용기준
- 모든 P0 affected v1 contract가 최소 하나의 gap classification을 가짐
- `UNRECOVERABLE_FROM_V1`을 default PASS 값으로 채우는 adapter 0건
- v1→v2 변환 시 material field loss가 있으면 HOLD/INPUT_REQUIRED 또는 reperformance로 라우팅
- v2 활성화 전 shadow decision disagreement 원인을 모두 설명

현재 문서는 migration 설계이며 Runtime adapter 구현·실행을 의미하지 않는다.
