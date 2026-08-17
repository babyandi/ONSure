# 167 Post-Delta Authority, EPOCH 0003 and Closure Execution Preparation

Status: `AUTHORITY_ADMISSION_MATERIALIZED / EXECUTION_READY / SATURATION_HOLD / NON_FINAL`

## 1. 목적
DD-001~040 discovery 이후 남아 있던 Requirement Authority 편입, EPOCH 0003 canonicalization, Design QA 및 CLEAN 실행 경계를 하나의 현재 상태로 고정한다. 문서 존재를 실행 증거로 승격하지 않는다.

## 2. 완료된 구조 작업
- delta authority explicit allowlist: `contracts/requirement-authority-source-allowlist.delta-final-target.v1.json`
- human-reviewed source-role seed: `contracts/requirement-authority-source-review.seed-final-target-delta.v1.json`
- canonical DD authority admission: `contracts/post-final-target-dd-001-040-authority-admission.candidate.v1.json`
- DD→FR-FIN parent mapping: 40/40, `contracts/post-final-target-dd-to-fr-fin-relation.candidate.v1.json`
- post-delta authority materializer: `scripts/materialize-product-design-authority.py`
- post-delta EPOCH 0003 generator: `scripts/generate-product-design-epoch-0003.py`
- DD-aware reverse orphan scanner: `scripts/scan-reverse-orphan-product-design.py`
- independent discovery wave frozen intake: `contracts/independent-design-discovery-wave-intake.candidate.v1.json`
- saturation validator: `scripts/validate-design-discovery-saturation.py`
- end-to-end post-delta runner: `scripts/run-product-design-closure-post-delta.sh`

## 3. Authority admission semantics
162/163/165/166은 post-final-target Product Design의 명시적 delta authority source로 추적한다. 164와 같은 실행/status 문서는 Requirement-originating authority에서 제외한다.

DD-001~040은 `CANONICAL_DISCOVERY_OBLIGATION` node로 Product Design denominator에 들어간다. 단 `top_level_product_requirement=false`, `double_count_with_parent_fr_fin=false`를 강제한다. 따라서 FR-FIN 부모와 DD child/refinement를 숫자상 중복 합산해 coverage를 부풀리지 않는다.

## 4. Generator anti-contamination rule
기본 Requirement Universe는 historical master + final-target authority로 먼저 생성한다. 그 뒤 full delta authority population digest를 합성하고 DD-001~040만 admission registry로 추가한다.

이 순서는 162/165의 설명용 `FR-FRESH-*`/기존 Requirement mention이 기존 canonical Requirement text를 우연히 덮어쓰는 것을 막는다.

## 5. Independent Discovery Saturation
현재 `GLOBAL_DISCOVERY_SATURATION_PROVEN=false`다.

이 상태를 제가 동일 대화/동일 결론 context로 반복 실행해 true로 바꾸는 것은 DD-040 위반이다. 필요한 외부/독립 입력은 두 개다:
- `INDEPENDENT-SATURATION-A`
- `INDEPENDENT-SATURATION-B`

각 wave는 같은 frozen scope/authority를 사용하되 prior accepted DD conclusions를 소비하지 않아야 하고, mandatory lens 100%, untriaged 0, new P0 0, independence attested를 만족해야 한다.

결과는 `.onsure/design-discovery/<wave-id>.json`에 materialize하고 `scripts/validate-design-discovery-saturation.py`로 검증한다. 하나라도 새 P0가 나오면 saturation counter를 reset하고 Requirement Authority를 다시 연다.

## 6. EPOCH 0003 실행 순서
독립 saturation이 통과한 실행 노드에서:

```bash
bash scripts/run-product-design-closure-post-delta.sh
```

runner는 순서대로:
1. saturation evidence 검증
2. raw-byte SHA-256 기반 post-delta authority materialization
3. EPOCH 0003 A/B 생성 및 denominator digest 동일성
4. FR-FIN-01~22 + DD-001~040 exact presence 확인
5. candidate view에서 forward global trace
6. DD-aware reverse orphan
7. final-product/design coverage validator
8. Global Lock preflight
9. historical live EPOCH 0002 복원
10. independent/local assurance CLEAN 2회
11. fail-closed receipt 집계
를 수행한다.

## 7. 현재 미실행 항목
이 GitHub-direct 세션에서는 실제 repository checkout execution node 또는 PR-triggered Actions run이 없었으므로 다음은 `NOT_RUN`이다.
- raw-byte authority materializer actual run
- EPOCH 0003 deterministic A/B actual run
- applicability regeneration
- global trace/orphan actual run
- Design Lock preflight actual run
- CLEAN #1/#2 actual run

따라서 0 orphan, deterministic digest, CLEAN, Design Lock을 주장하지 않는다.

## 8. 현재 blocker
1. `GLOBAL_DISCOVERY_SATURATION_NOT_PROVEN` — 독립 blind wave A/B 필요
2. `POST_DELTA_RUNTIME_EXECUTION_NOT_RUN` — 실행 노드에서 runner 필요
3. `HUMAN_DESIGN_AUTHORITY_DECISIONS_REMAIN` — 기존 P1 policy contradiction/미확정 policy는 별도 authority 결정을 유지

## 9. 최고 허용 상태
`POST_DELTA_REQUIREMENT_AUTHORITY_ADMISSION_MATERIALIZED / EPOCH_0003_EXECUTION_PATH_READY / DISCOVERY_SATURATION_HOLD / DESIGN_QA_NOT_RUN / CLEAN_NOT_RUN / NON_FINAL`

Design Lock, FinalApproval, FinalLock, Production GO, Commercial GO는 금지한다.
