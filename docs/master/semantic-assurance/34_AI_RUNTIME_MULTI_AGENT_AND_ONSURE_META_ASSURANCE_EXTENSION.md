# ONSure AI Runtime·Multi-Agent·ONSure Meta-Assurance 방법론 확장

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parent: `docs/master/07_COMPONENT_MODEL_AND_AI_METHODOLOGY.md`
Requirements: `FR-META-057~060`
Architecture: `semantic-assurance/29~32`

## 1. 목적
기존 AI Agent 방법론을 정적 Prompt/RAG/Tool 검토에서 운영 중 AI identity, nondeterminism, multi-agent interaction, provider drift, ONSure 자체 AI/validator qualification까지 확장한다.

## 2. AI Runtime Identity Model
AI Component의 identity는 model name 하나가 아니라 다음 묶음이다.
- provider_id
- model_family/model_version/deployment_id
- model artifact/weights digest 가능 시
- system/developer prompt bundle digest
- dynamic prompt assembly rule/version
- tool registry digest
- tool schema/permission/effect-class digest
- memory backend/type/scope/retention epoch
- RAG corpus/index/embedding/chunking/retrieval-policy digest
- safety/policy pack digest
- sampling configuration
- external provider contract digest

Opaque provider의 weights digest를 얻을 수 없는 경우 provider attestation + immutable deployment/version identity + observed behavior profile을 사용하되 `WEIGHTS_NOT_OBSERVABLE` limitation을 숨기지 않는다.

## 3. Prompt Provenance
Prompt는 단순 문자열 hash가 아니라 계층/조립 계보를 가진다.
- system fragment
- developer fragment
- tenant policy fragment
- product template
- user input
- retrieved context
- tool result
- runtime-injected state

각 fragment는 source/ref/version/digest를 갖고 최종 assembled prompt digest와 연결한다. 동적 fragment가 identity에서 빠지면 prompt currentness를 주장할 수 없다.

## 4. Tool Authority Method
각 Tool은 다음을 가진다.
- tool_id/version
- schema digest
- provider/implementation digest
- required role/permit
- effect class: READ_ONLY|LOCAL_MUTATION|EXTERNAL_MUTATION|FINANCIAL|IRREVERSIBLE
- target/resource scope
- network/filesystem scope
- confirmation policy

Agent의 자연어 의도를 authorization proof로 사용하지 않는다. 실제 tool call마다 server-side authority evaluation과 receipt를 요구한다.

## 5. Agent Memory Assurance
Memory는 종류별로 분리한다.
- SESSION_EPHEMERAL
- USER_PERSISTENT
- PROJECT_PERSISTENT
- ORGANIZATION_SHARED
- MODEL/AGENT_LEARNING_MEMORY

필수 검증:
- tenant/user/project scope
- write authority
- retrieval authority
- retention/deletion
- poisoning provenance
- stale memory handling
- cross-session/cross-tenant leakage
- memory-blind independent evaluation

Memory-aware 평가와 Memory-blind 평가가 충돌하면 자동 majority로 해결하지 않고 HOLD/추가 Oracle로 보낸다.

## 6. RAG Assurance
RAG identity는 corpus만이 아니라 다음 전체를 포함한다.
`Corpus → ACL → Chunking → Embedding Model → Index Build → Retrieval Policy → Reranker → Citation/Source Binding`.

검증영역:
- Corpus provenance/rights
- ACL preservation
- Poisoning/adversarial document
- stale index
- index/corpus mismatch
- retrieval omission/over-retrieval
- citation correctness
- cross-tenant retrieval
- embedding/model drift

Corpus 변경 또는 silent reindex는 source code가 같아도 AI target epoch 변경이다.

## 7. Nondeterministic Validation Method
단일 AI run PASS를 confidence 있는 behavioral claim으로 승격하지 않는다.

### 7.1 Population Definition
- scenario population digest
- precommitted seed/generator policy
- temperature/top-p/sampling parameters
- repetition count
- model/deployment identity
- observation window

### 7.2 Metrics
- sample_size
- success_count/failure_count
- critical_failure_count
- empirical_failure_rate
- confidence interval/bound method
- worst-case observed category
- diversity/coverage indicators

### 7.3 Critical Claims
Critical safety/authorization/tenant isolation claim은 평균 success rate가 높다는 이유만으로 통과시키지 않는다. Known Critical failure 1건은 해당 claim positive assurance를 차단한다. 통계적 absence claim은 별도의 confidence/coverage requirement를 만족해야 한다.

### 7.4 Favorable Sampling 방지
결과를 본 뒤 seed/scenario를 제외하지 않는다. 제외는 사전에 정의된 invalid/equivalent criterion과 independent review가 있어야 한다.

## 8. Metamorphic / Property Oracle
AI output exact match가 부적절한 경우 property를 검증한다.
예:
- 다른 표현이어도 동일 권한경계 유지
- 민감정보는 어떤 paraphrase에서도 비노출
- 동일 contract fact에 모순 답변 금지
- irrelevant context injection에도 target scope 유지
- Tool call은 허용된 resource boundary를 넘지 않음

Property Oracle 자체의 version/digest/qualification을 기록한다.

## 9. Multi-Agent Assurance
### 9.1 AgentGraph
- agent_id/role
- model/runtime identity
- authority scope
- input/output contract
- memory scope
- allowed peers
- delegation capability

### 9.2 Message Contract
Inter-agent message는 sender/receiver, correlation/causation, message schema, content digest, authority context, timestamp/nonce를 가진다.

### 9.3 Delegation
Agent A가 Agent B에 일을 위임해도 B가 A보다 넓은 권한을 획득하지 않는다. delegation chain, purpose, TTL, subject scope를 보존한다.

### 9.4 Common-mode Failure
서로 다른 agent_id/run_id라도 같은 model/provider/prompt/oracle/knowledge source를 공유하면 완전 독립으로 세지 않는다.

### 9.5 Majority Vote Ceiling
N개의 Agent가 같은 결론을 내도 Ground Truth가 아니다. agreement는 corroboration이며 독립 Executable Oracle/GT가 없으면 assurance strength ceiling을 유지한다.

### 9.6 Cyclic Delegation
A→B→C→A delegation loop, 책임 떠넘기기, 무한 handoff를 탐지한다. final authority는 명시된 principal/contract에서만 온다.

## 10. AI Safety/Security Claim 분리
Business correctness와 별도 claim set으로 유지한다.
- prompt injection resistance
- indirect RAG/tool injection
- data exfiltration
- unauthorized tool/effect
- privilege escalation
- hallucinated authority
- unsafe financial/external action
- memory poisoning
- cross-tenant leakage
- refusal/policy bypass

한 claim PASS가 다른 claim을 함의하지 않는다.

## 11. Provider Drift
Provider/model alias, safety filter, tool semantics, context window, rate limit, output policy, model routing 변경을 관찰한다.
Material change 발생 시:
1. affected target identity epoch 변경
2. historical claim impact 계산
3. qualification/currentness 재평가
4. 필요한 benchmark/reperformance 실행

Provider 이름이 같다는 이유만으로 currentness를 유지하지 않는다.

## 12. Judge / Reviewer Independence
AI Judge/Reviewer는 target model과 다음 축을 비교한다.
- provider/model family
- prompt/rubric implementation
- training/knowledge overlap 가능성
- oracle source
- hidden benchmark exposure
- memory/previous verdict access

동일 계열 Judge 결과는 보조 corroboration으로 사용할 수 있으나 고신뢰 independent lane을 대체하지 않는다.

## 13. ONSure Release Qualification
ONSure 자체를 하나의 검증 Target으로 취급한다.

### 13.1 Qualification Subject
- ONSure release/build digest
- Core validator set
- Oracle set
- Adapter/Plugin set
- Rule/Policy pack
- Fixture/Benchmark set
- Hidden/OOD corpus generation
- sandbox/TCB
- crypto/key registry
- execution environment

### 13.2 Qualification Dimensions
- target archetype coverage
- defect-class recall/precision
- seeded critical fault escape
- oracle correctness
- false-positive calibration
- evidence/receipt integrity
- sandbox/isolation
- currentness/revocation handling
- independent verification

### 13.3 Target Archetype Matrix
전역 QUALIFIED를 금지한다. 예:
| Target Archetype | 상태 | 제한 |
|---|---|---|
| Java/Spring API | QUALIFIED 후보 | 실제 benchmark 실행 필요 |
| React Web | QUALIFIED 후보 | browser/runtime coverage 필요 |
| Kubernetes | PARTIAL 후보 | deployment/currentness qualification 필요 |
| Agentic RAG | PARTIAL 후보 | nondeterminism/hidden benchmark 필요 |
| Unknown proprietary runtime | NOT_PROVEN | adapter/observer 없음 |

실제 값은 실행 증거 전까지 후보이며 문서가 qualification을 발행하지 않는다.

## 14. Requalification Trigger
- validator implementation 변경
- Oracle/rubric 변경
- Adapter/Plugin 변경
- benchmark/hidden corpus material 변경
- sandbox/TCB/crypto 변경
- major dependency/runtime 변경
- MissedFinding으로 blind spot 확인
- severity/coverage policy weakening
- provider/model change for AI validator

Impact analysis 결과에 따라 full/partial requalification 범위를 정한다.

## 15. Self-validation Ceiling
ONSure 자체 Unit/E2E/Meta-test가 PASS해도 self-validation이다.
최종 Qualification에는 정책이 요구하는 independent verifier/oracle/benchmark lane이 별도로 존재해야 한다.
다른 key/run/model name만으로 independent를 주장하지 않는다.

## 16. Learning / OMemory 연계
MissedFinding 또는 qualification escape가 발견되면:
`MissedFinding → Evidence-based RCA → Candidate Rule/Fixture/Oracle/Adapter Change → Hidden/Golden Regression → Independent Qualification → Shadow/Canary → Promotion`.

Hidden 결과를 반복 노출해 학습기가 benchmark에 과적합하지 않게 한다.

## 17. AI Currentness
AI Product CURRENT는 최소 다음 identity가 current여야 한다.
- model deployment
- prompt bundle
- tool registry
- memory policy/backend
- RAG stack
- external provider contract
- relevant validator qualification

하나가 material drift이면 Product Composition currentness에 전파한다.

## 18. Negative / Adversarial Method Set
- model alias silent swap
- dynamic system prompt omission
- tool registry privilege expansion
- cross-tenant memory retrieval
- stale RAG index
- RAG poisoned document
- favorable seed selection
- repeated run result cherry-picking
- same-model judge common blind spot
- multi-agent majority wrong
- cyclic delegation
- agent delegates broader privilege
- hidden benchmark leakage
- ONSure release changed but old qualification reused

## 19. 수용기준
- AI identity는 Model name 하나로 축소되지 않는다.
- nondeterministic claim은 population/statistical evidence를 가진다.
- critical failure는 평균 성공률에 의해 숨겨지지 않는다.
- Multi-agent agreement를 GT로 자동 승격하지 않는다.
- RAG/Prompt/Tool/Memory drift가 currentness에 전파된다.
- ONSure release qualification은 target archetype별이며 self-validation만으로 발급되지 않는다.
- 이 문서는 실제 qualification을 선언하지 않는 `DESIGN_ONLY`다.
