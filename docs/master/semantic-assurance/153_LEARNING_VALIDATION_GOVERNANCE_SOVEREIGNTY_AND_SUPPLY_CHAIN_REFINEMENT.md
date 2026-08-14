# 153 Learning & Validation Governance / Sovereignty / Supply-chain Refinement

Status: `NORMATIVE_REFINEMENT / DESIGN_ONLY / NON_FINAL`
Parents: `146_LEARNING_VALIDATION_CLOSED_LOOP_AND_META_ASSURANCE.md`, `151_LEARNING_VALIDATION_OPERATIONAL_SECOND_ORDER_RISKS.md`, `151_LEARNING_VALIDATION_OPERATIONAL_MATURITY_REFINEMENT.md`
Purpose: FR-LEARN-001~052 이후에도 남는 연합학습·프라이버시·공급망·주권·외부 Provider·거버넌스 실패모드를 닫는다.

## FR-LEARN-053 Federated Learning / Aggregation Governance
여러 Tenant/Organization의 학습 신호를 집계할 경우 원본 데이터 이동 없이도 참여자 identity, aggregation policy, contribution weight, malicious-client resistance, aggregation receipt를 기록한다. 단일 참여자의 독성 업데이트가 global knowledge를 지배하지 못한다.

## FR-LEARN-054 Data Residency / Cross-region Learning
학습·검증 데이터와 파생 학습자산의 저장·처리·복제 지역을 기록하고 region 이동은 policy/contract/jurisdiction에 따라 제한한다. 허용되지 않은 cross-region 학습·집계를 금지한다.

## FR-LEARN-055 Privacy Budget / Differential Privacy
개인·민감 정보에서 통계적 학습을 허용하는 경우 privacy budget, mechanism, epsilon/delta 또는 동등 privacy guarantee, composition accounting을 기록한다. budget 초과 시 추가 학습/공유 승격을 차단한다.

## FR-LEARN-056 Machine Unlearning Verifiability
삭제·동의철회 후 원본 삭제뿐 아니라 모델/Rule/Pattern/Embedding/Corpus 파생 영향의 제거 또는 재학습 필요성을 증명한다. unlearning 요청은 receipt와 verification evidence 없이 CLOSED로 만들지 않는다.

## FR-LEARN-057 Provenance Tamper Resistance
Learning provenance, promotion receipt, corpus lineage, oracle qualification, decision snapshot의 hash chain/signature를 검증하여 사후 수정·삭제·교체를 탐지한다. provenance integrity 실패 시 관련 qualification은 HOLD다.

## FR-LEARN-058 Upstream Poisoned Dependency Propagation
외부 dataset/model/package/rule feed/CVE feed 등 upstream source가 오염·철회되면 이를 소비한 learning asset, validator, decision, certificate까지 impact propagation한다.

## FR-LEARN-059 Model / Provider Sunset Management
외부 Model/Provider가 deprecate, discontinue, policy-change, region-exit 될 경우 대체 provider 검증, compatibility, requalification, migration, rollback 계획을 요구한다. 종료된 provider identity에 의존한 qualification을 무기한 CURRENT로 유지하지 않는다.

## FR-LEARN-060 Learning Asset Supply-chain Signature
Rule/Pattern/Fixture/Prompt/Oracle/Detector/Corpus release는 producer identity, version, content digest, signature/attestation, dependency provenance를 가진다. unsigned/untrusted asset은 정책상 active/global scope에 승격할 수 없다.

## FR-LEARN-061 Reviewer Collusion / Consensus Bias
여러 Reviewer가 있어도 동일 조직·지시·모델·자료에 과도하게 의존하거나 상호 영향받으면 독립 review로 계산하지 않는다. reviewer independence와 collusion/common-source risk를 기록한다.

## FR-LEARN-062 Evaluator Capture / Authority Concentration
특정 evaluator, team, model, vendor가 candidate qualification·oracle approval·final policy를 과도하게 독점하지 않도록 concentration metric과 SoD를 적용한다. authority capture 위험이 임계치를 넘으면 독립 review를 요구한다.

## FR-LEARN-063 Adversarial Benchmark Generation Governance
자동 생성 adversarial/challenge fixture도 source, generation model, prompt, seed, novelty, contamination, safety review를 기록한다. generator가 자신의 benchmark 정답을 알고 동일 validator를 튜닝하는 폐루프를 금지한다.

## FR-LEARN-064 Reward Hacking / Proxy Optimization Guard
학습 Agent가 reward/score/coverage gate 자체를 우회하거나 쉬운 사례만 선택해 metric을 높이는 행동을 탐지한다. proxy metric 개선이 실제 assurance objective 개선을 대체하지 못한다.

## FR-LEARN-065 Policy-Learning Conflict Resolution
학습 결과가 기존 정책·규제·계약 requirement와 충돌할 경우 learning asset은 policy authority를 자동 override하지 못한다. conflict case와 resolution authority를 요구한다.

## FR-LEARN-066 Autonomous Learning Budget Control
자동 학습은 compute, token, storage, external API, reviewer effort, financial credit budget을 가진다. budget 초과·무한 학습 루프·비정상 retry amplification을 차단하고 stop receipt를 남긴다.

## FR-LEARN-067 Emergency Global Revocation Propagation
Global/Industry knowledge에서 critical defect가 발견되면 모든 dependent tenant/validator/decision에 revocation을 전파하고 신규 사용을 즉시 차단하며 재평가 queue를 생성한다.

## FR-LEARN-068 Offline / Air-gapped Learning Synchronization
Air-gapped 환경의 learning asset import/export는 signed bundle, epoch, conflict, freshness, revocation delta를 검증한다. 오래된 offline bundle이 최신 revocation을 덮어쓰지 못한다.

## FR-LEARN-069 Knowledge Fork / Merge Governance
Tenant/Industry/Global knowledge가 fork된 뒤 merge될 수 있으므로 ancestor epoch, divergent changes, conflicts, chosen resolution, merge receipt를 보존한다. silent overwrite 금지.

## FR-LEARN-070 Tenant-specific Override Inheritance
Tenant override는 parent/global knowledge와의 precedence, scope, expiration, inheritance 여부를 명시한다. 한 tenant의 override가 sibling tenant 또는 global rule로 암묵 전파되지 않는다.

## FR-LEARN-071 Semantic Versioning of Learned Knowledge
Learning asset version은 breaking/non-breaking/metadata-only 변화를 구분하고 semantic compatibility와 requalification requirement를 연결한다. 의미가 바뀐 asset을 patch-level 변경으로 숨기지 않는다.

## FR-LEARN-072 Learning History Migration
Schema/registry/knowledge-store migration 시 candidate lifecycle, lineage, old decisions, revoked assets, qualification evidence를 보존한다. migration 후 history loss가 있으면 reconstructability PASS 금지.

## FR-LEARN-073 Evidence Retention vs Deletion Tension
재현성/감사를 위한 evidence retention과 privacy deletion 요구가 충돌할 경우 최소보존·비식별·cryptographic tombstone·legal basis를 policy로 결정하며 둘 중 하나를 임의 우선하지 않는다.

## FR-LEARN-074 Legal Hold vs Right-to-delete Conflict
Legal hold 대상 데이터와 삭제 요청이 충돌하면 legal authority, scope, duration, access restriction, release trigger를 기록하고 파생 학습자산 처리를 별도로 disposition한다.

## FR-LEARN-075 Jurisdiction-specific Learning Restrictions
국가/산업/규제권역별로 학습 허용 데이터, 모델 사용, 자동화 수준, 보존기간, human oversight 요구가 다를 수 있으므로 jurisdiction policy binding을 learning run과 asset에 결속한다.

## FR-LEARN-076 Export-control / Data Sovereignty Guard
모델·데이터·암호기술·학습자산의 국외 이전 또는 특정 provider 사용이 제한될 수 있으므로 export/data-sovereignty policy를 activation, synchronization, provider selection gate에 적용한다.

## FR-LEARN-077 External LLM / Provider Provenance Boundary
외부 LLM/provider에 전달한 prompt/context/evidence와 반환된 output을 provider/version/region/retention/training-use policy와 함께 기록한다. 외부 provider 출력이 내부 provenance를 대체하지 않으며 training-use 금지 계약을 위반하는 재사용을 차단한다.

## P0 우선 Gate
P0: 054, 055, 056, 057, 058, 060, 065, 067, 073, 074, 075, 076, 077.

## 핵심 불변식
1. jurisdiction/residency/export policy 위반 자산의 activation/scope promotion 금지.
2. unlearning verification이 끝나지 않은 파생 자산은 `DELETION_CLOSED` 금지.
3. provenance/signature integrity 실패 asset은 qualification authority 상실.
4. revoked upstream/source의 dependent decision은 impact 재평가 전 CURRENT 보장 금지.
5. learning policy는 법·계약·고객 policy를 자동 override하지 못한다.
6. offline merge가 최신 revocation/freshness를 되돌리지 못한다.
7. external provider output은 독립 provenance/qualification 없이 final authority가 되지 못한다.

## Design Lock 영향
FR-LEARN-053~077을 Requirement/Applicability/Trace/Test expectation에 materialize하기 전 전체 Learning/Validation design closure를 최종 선언하지 않는다.
