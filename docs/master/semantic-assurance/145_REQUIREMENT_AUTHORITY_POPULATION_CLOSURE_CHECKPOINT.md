# 145 Requirement Authority Population Closure Checkpoint

Status: `DESIGN_QA_EXECUTION_EVIDENCE / AUTHORITY_POPULATION_IN_PROGRESS / NON_FINAL`

이 문서는 143~144 이후 Product Design Requirement Universe의 exact denominator를 닫기 위해 Requirement origination authority population을 정제한 실행 checkpoint다. 새 제품 기능이나 독립 설계축을 추가하지 않는다.

## 1. 확정된 원인
기존 Batch 0 generator는 `docs/master`의 tracked Markdown 전체를 authority population으로 읽고, 동일 explicit ID의 여러 text variant 중 가장 긴 occurrence를 canonical로 선택한다. 이 방식은 Design Artifact Inventory와 Requirement Origination Authority를 혼동하며 authority ordering을 text length로 대체한다.

따라서 기존 899 record population은 `LEGACY_UNFILTERED_ALL_TRACKED_MARKDOWN_RECORD_POPULATION_CANDIDATE`로 보존하되 exact active denominator로 사용하지 않는다.

## 2. Authority source model
Machine candidate:
- `contracts/requirement-authority-source-manifest.candidate.v1.json`
- `contracts/requirement-authority-classification-policy.candidate.v1.json`

핵심 분리:
- DesignArtifactInventory = provenance/reconstructability population
- ProductDesignRequirementAuthorityPopulation = CURRENT Requirement를 originate할 수 있는 normative source population

## 3. Reviewed seed population
현재 content-role을 직접 검토해 seed로 고정한 것은 16개다.

### Master set 10
`contracts/requirement-authority-source-review.seed.v1.json`
- 00~07: NORMATIVE_CURRENT
- 08: REFERENCE_ONLY (문서가 스스로 design authority가 아니라고 명시)
- 08A: OPEN_DECISION_INPUT_ONLY

### Semantic assurance 6
`contracts/requirement-authority-source-review.seed-semantic.v1.json`
- 88, 92: NORMATIVE_CURRENT — 141이 Product Design RU authority로 지정
- 141, 142: NORMATIVE_REFINEMENT — explicit DESIGN_AUTHORITY_DECISION
- 143, 144: QA_EVIDENCE_ONLY

이 seed의 Git blob SHA는 review identity 보조값이며 content SHA-256을 대체하지 않는다. Full manifest materializer가 raw bytes의 SHA-256을 계산해야 한다.

## 4. Content-based classification rule
`requirement-authority-classification-policy.candidate.v1.json`은 filename-only inference를 금지한다. 명시적 content marker로 확정 가능한 경우만 자동 review하며, `DESIGN_ONLY/DRAFT/NON_FINAL`만 있는 문서는 자동으로 normative/non-normative 판정하지 않고 UNREVIEWED로 남긴다.

자동 확정 가능 예:
- 'not design authority' 명시 → REFERENCE_ONLY
- DESIGN_AUTHORITY_DECISION → NORMATIVE_REFINEMENT
- DEVELOPMENT_HANDOFF → HANDOFF_ONLY
- QA execution/status/result/blocker closure → QA_EVIDENCE_ONLY
- SUPERSEDED/RETIRED 명시 → provenance only

## 5. Authoritative regeneration gate
다음이 모두 충족되기 전 authoritative RU regeneration을 봉인하지 않는다.
1. every scanned source has manifest row
2. content SHA-256 complete
3. UNREVIEWED=0
4. disputed P0=0
5. supersession explicit
6. only eligible source dispositions feed RU generator
7. authority manifest digest bound to generation receipt
8. longest-text-wins removed
9. multiple eligible normative variants unresolved이면 exact denominator sealing BLOCK

## 6. 현재 blocker
- full source row population 미완료
- requirement authority schema path materialization은 GitHub connector write anomaly로 재확인 필요
- authority-filtered RU 실행 전
- variants/duplicates authority-filtered recount 전
- FR-COM-008 external branch-protection evidence는 connector permission 403으로 별도 BLOCKED

## 7. 다음 closure 순서
1. full source manifest materializer 구현/실행
2. ambiguous documents manual review
3. authority-filtered RU 재생성
4. semantic variant/duplicate review evidence commit
5. canonical disposition
6. exact active denominator + digest
7. Applicability 1:1 population
8. Global Trace rerun
9. naming/artifact SHA/registry digests/baseline reconstructability
10. Design Lock Check rerun

현재 `DESIGN_BASELINE_READY_FOR_LOCK=false`이며, 이 checkpoint는 이를 승격하지 않는다.
