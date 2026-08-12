# ONSure 다음 개발 Batch F~K 설계

Status: `DESIGN_ONLY / CANDIDATE / NON_FINAL`

## 목적
현재 `21_CLAUDE_DEVELOPMENT_HANDOFF.md`의 DEV-01~13 이후 Claude가 중단 없이 이어갈 후속 구현 Batch를 정의한다. 이 문서는 현재 개발 Handoff를 대체하지 않는다.

## Batch F — Currentness & Deployment Runtime
1. `DeploymentTarget`, `DeploymentRevision`, `RuntimeInstance`, `AssuranceCurrentnessSnapshot` Contract 제정
2. Target→BuildArtifact→DeploymentRevision→RuntimePopulation binding
3. read-back verifier 구현
4. rolling/blue-green/canary/multi-region population evaluator
5. artifact/config/model/prompt/RAG drift detector
6. stale/reassessment/invalidated propagation

완료 Evidence:
- positive/negative fixture
- mixed rollout fixture
- artifact substitution fixture
- runtime population digest receipt
- currentness calculation receipt

금지:
- image tag/source commit만으로 CURRENT
- 일부 canary/region 결과를 전체 population으로 승격

## Batch G — Composition & Evidence Graph
1. AssuranceSubject/DependencyEdge Contract
2. CompositionSnapshot Contract
3. HARD/SOFT/N/A/CONFLICT propagation engine
4. EvidenceGraph node/edge persistence
5. reverse impact index
6. explanation path generation

완료 Evidence:
- exact subject/edge population
- weakest-link propagation fixture
- conflicting evidence fixture
- graph cycle/dangling/cross-tenant fixture

## Batch H — Certificate & Revocation
1. AssuranceCertificate Contract
2. RevocationReceipt Contract
3. online verification endpoint
4. offline trust bundle verifier
5. limitation/exclusion projection
6. certificate currentness evaluator

금지:
- signature valid = CURRENT
- revoked/stale/offline uncertainty 은폐

## Batch I — Enterprise Authority & Policy
1. AuthorityGrant Contract/runtime
2. delegation depth/subset enforcement
3. four-eyes principal distinctness
4. break-glass workflow
5. AssurancePolicyProfile registry/runtime
6. policy weakening high-risk gate

## Batch J — Scale / Plugin / AI
1. WorkUnit + logical effect once/receipt once
2. deterministic aggregation
3. PluginManifest/qualification
4. AI Behavior Population
5. model/prompt/tool/memory/RAG identity closure
6. nondeterministic sampling/statistical receipt
7. multi-agent delegation/common-mode checks

## Batch K — ONSure Meta-Assurance
1. ONSureReleaseQualification Contract
2. validator/oracle/adapter/fixture/benchmark build binding
3. target-archetype scoped qualification
4. MissedFinding→validator RCA→requalification
5. self-validation ceiling enforcement

## 공통 Definition of Done
각 항목은 최소:
`Contract → Runtime Enforcement → Positive Fixture → Semantic Negative Fixture → Raw Evidence → Receipt → Trace Registry → NONFINAL Decision`
을 가진다.

다음 없이는 상태 승격 금지:
- compile/test evidence
- exact target/epoch/policy binding
- independent qualification where required
- no semantic fixture weakening

## 권위 경계
Batch F~K의 구현 완료는 자동으로 Active Selector/FinalLock/Production/Commercial GO 권위를 만들지 않는다.
