# 149 Learning & Validation Schema / Fixture Specification

Status: `NORMATIVE_REFINEMENT / DESIGN_ONLY / NON_FINAL`
Parents: 146, 147, 148
Purpose: 148의 14개 계약군을 개별 identity 기준 Schema 후보와 positive/negative fixture 기준으로 물질화한다.

## 공통 규칙
- 모든 객체는 `kind`, stable identity, source/version/currentness 또는 decision authority를 명시한다.
- 의미가 다른 객체를 generic blob 하나로 합치지 않는다.
- unknown/stale/unqualified/not-run 상태를 PASS로 축약하지 않는다.
- 모든 hash는 SHA-256 content identity를 기본으로 한다.

## A. LearningCandidateAsset
필수: `candidate_id`, `candidate_type`, `state`, `learning_domain`, `scope`, `source_lineage[]`, `content_sha256`, `created_by`, `created_at`, `final_decision_authority=false`.
상태: OBSERVED/CANDIDATE/EVALUATED/QUALIFIED/APPROVED/ACTIVE/RETIRED/REJECTED/QUARANTINED/STALE/REVOKED/SUPERSEDED.
Positive: source lineage와 hash가 있고 CANDIDATE 상태인 RuleCandidate.
Negative: `final_decision_authority=true`, source lineage 없음, ACTIVE인데 qualification 없음.

## B. LearningPromotionReceipt
필수: `receipt_id`, `candidate_id`, `from_state`, `to_state`, `actor_id`, `authority_ref`, `policy_ref`, `evidence_refs[]`, `created_at`.
Positive: EVALUATED→QUALIFIED 전이 + independent qualification evidence.
Negative: CANDIDATE→ACTIVE 직접 전이, self-approval, evidence 없음.

## C. CorpusIntegrityReport
필수: corpus identity, duplicate/label-error count, poisoning/tenant-leakage/benchmark-contamination 상태, decision.
Positive: leakage/poisoning/contamination CLEAR.
Negative: CONFIRMED contamination인데 PASS.

## D. LearningEffectivenessReport
필수: candidate/epoch, benchmark, before/after precision/recall/FP/FN/coverage/latency, variance/confidence, decision.
Positive: 동일 benchmark에서 개선 또는 동등성 근거가 명확함.
Negative: benchmark가 다르거나 regression인데 IMPROVED.

## E. OracleQualification
필수: oracle identity/version/hash, expected-result authority, benchmark refs, independence, result, qualified_at, fresh_until.
Positive: independent + QUALIFIED + current.
Negative: UNQUALIFIED/STALE oracle이 final PASS에 사용됨.

## F. OracleDisagreementCase
필수: case identity, subject, 2개 이상 oracle result, status, resolution authority/evidence.
Positive: disagreement OPEN이면 관련 decision HOLD.
Negative: OPEN disagreement인데 PASS 또는 단순 다수결 자동해소.

## G. ValidatorRegressionQualification
필수: validator identity/version, Golden/Blind/Challenge 결과, FP/FN drift, threshold, decision.
Positive: drift 임계치 이내 QUALIFIED.
Negative: threshold 초과인데 qualification 유지.

## H. DerivedLearningLineageDisposition
필수: source, derived assets, trigger, disposition, evidence, decided_at.
Disposition: REVOKE/DELETE/REQUALIFY_REQUIRED/NO_ACTION_WITH_PROOF.
Positive: consent withdrawal 후 derived assets 전부 disposition됨.
Negative: unresolved lineage asset이 ACTIVE 유지.

## I. LearningScopePromotion
필수: asset, from/to scope, consent, privacy/anonymization proof, corpus integrity, policy approval, decision.
Positive: ORGANIZATION→INDUSTRY 승격에 모든 proof 존재.
Negative: 자동 GLOBAL 승격 또는 consent 없음.

## J. ValidationExperiment
필수: experiment id, mode, subject, run_count, runs[], environment, result.
Mode-specific: STOCHASTIC(seed/model/provider), METAMORPHIC(transformation/relation), DIFFERENTIAL(comparator), ENVIRONMENT_MATRIX(matrix cell).
Positive: stochastic N-run 또는 declared matrix 전수결과.
Negative: single-run stability PASS, NOT_RUN matrix cell을 PASS 처리.

## K. EvidenceObservation
Canonical state: `EVIDENCE_NOT_COLLECTED`, `EVIDENCE_COLLECTION_FAILED`, `OBSERVED_ABSENCE`, `EVIDENCE_UNAVAILABLE`, `EVIDENCE_STALE`, `EVIDENCE_PRESENT`.
필수: observation id, subject, state, collector ref, observed_at.
Positive: 실제 수집 후 결과 없음은 OBSERVED_ABSENCE.
Negative: 미수집을 OBSERVED_ABSENCE로 변환.

## L. LearningStopDecision
필수: candidate/epoch, marginal gain, regression risk, FP cost, coverage saturation, budget state, CONTINUE/STOP/HOLD.
Positive: saturation + 높은 regression risk에서 STOP/HOLD.
Negative: 근거 없이 무한 CONTINUE.

## M. FailureRegistryEntry
필수: failure id/signature, root-cause state, recurrence, reproduction fixture, affected versions, status, closure evidence.
Positive: 재발 시 동일 signature lineage로 recurrence 증가.
Negative: fixture 없는 CLOSED 또는 재발 계보 단절.

## N. CoverageBalanceReport
필수: language/framework/industry/tenant/risk-class dimension distribution, learning/benchmark counts, coverage gaps, decision.
Positive: underrepresented bucket을 COVERAGE_GAP으로 노출.
Negative: 특정 bucket 0인데 전체 평균만으로 BALANCED.

## Cross-contract fixture set
P0 negative fixtures는 최소 다음을 포함한다.
1. Candidate→ACTIVE 직접 승격
2. creator=evaluator=approver self-approval
3. qualification evidence 없는 ACTIVE
4. stale/unqualified Oracle 사용
5. unresolved multi-oracle disagreement PASS
6. confirmed corpus poisoning/tenant leak에서 activation
7. consent withdrawal lineage unresolved
8. Golden/Blind contamination benchmark 사용
9. validator regression threshold 초과 qualification 유지
10. EVIDENCE_NOT_COLLECTED→OBSERVED_ABSENCE 변환
11. stale knowledge final authority 사용
12. single stochastic run stability PASS

## 구현 materialization 규칙
Claude는 각 객체를 별도 JSON Schema identity로 materialize하고 structured-contract registry에 등록한다. 각 Schema는 positive fixture 1개 이상, semantic-invalid fixture 1개 이상, cross-contract validator test를 가져야 한다. 이 문서는 설계 정본이며 runtime PASS evidence를 의미하지 않는다.
