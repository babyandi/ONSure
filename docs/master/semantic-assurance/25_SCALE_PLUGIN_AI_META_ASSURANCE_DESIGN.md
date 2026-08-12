# ONSure Scale·Plugin Trust·AI Assurance·Meta-Assurance 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parents: `22`, `23`, `24`

## 1. 목적
ONSure를 단일 프로젝트 검증 도구가 아니라 다수 고객·다수 Target·다양한 Adapter/AI 제품을 검증하는 플랫폼으로 운영할 때 필요한 Scale, Plugin Trust, AI-specific Assurance, ONSure 자체 Qualification을 정의한다.

## 2. Scale Architecture
### 2.1 Work Unit
대형 검증은 immutable WorkUnit으로 분해한다.
- work_unit_id
- parent_run_id
- target/scope/requirement epoch
- input digest
- operation
- partition key
- attempt
- lease owner/expiry
- expected output contract

### 2.2 Queue 원칙
- at-least-once delivery를 허용하되 effect/receipt는 idempotent
- duplicate execution을 detection
- stale lease takeover
- retry history 보존
- poison work unit quarantine
- tenant fairness/backpressure
- priority가 evidence integrity를 변경하지 않음

### 2.3 Deterministic Aggregation
병렬 결과의 완료 순서가 최종 digest를 바꾸지 않도록 canonical sort/key를 정의한다. 동일 population + 동일 results이면 scheduling 순서와 무관하게 동일 aggregate digest를 생성해야 한다.

### 2.4 Exactly-once 의미
실제 distributed execution 자체를 exactly-once라고 주장하지 않는다. 대신:
- logical effect once
- receipt commitment once
- nonce/operation identity once
를 보장한다.

## 3. Resource / Cost Governance
- tenant별 concurrency
- CPU/GPU/memory/storage budget
- model token budget
- external API budget
- verification credit reservation
- runaway job kill
- budget exhaustion은 FAIL이 아니라 BLOCKED/RESOURCE_LIMIT unless requirement says otherwise

비용 절감을 위해 required verification denominator를 조용히 축소하지 않는다.

## 4. Plugin / Adapter Trust
### PluginManifest
- plugin_id/version
- publisher principal
- artifact digest/signature
- supported target archetypes
- declared capabilities
- required privileges
- network/filesystem access
- input/output contracts
- compatibility range
- qualification record

### Plugin 상태
UNREGISTERED|REGISTERED|QUALIFICATION_REQUIRED|QUALIFIED|SUSPENDED|REVOKED|INCOMPATIBLE

### 원칙
- unsigned plugin 실행 금지
- plugin이 선언하지 않은 privilege 사용 금지
- Core validator와 plugin trust domain 분리
- plugin update는 requalification trigger
- plugin 결과만으로 Final independent assurance 금지
- plugin sandbox escape는 P0

## 5. Adapter Qualification
Target Adapter는 다음을 검증한다.
- target discovery completeness
- semantic mapping correctness
- version compatibility
- negative fixture detection
- unsupported feature disclosure
- parser/normalizer fidelity
- target-specific runtime observation capability

`supports=X` 선언만으로 qualified가 아니다.

## 6. AI-specific Assurance
AI Target은 일반 SW 검증에 추가하여 다음 축을 가진다.

### 6.1 Model Identity
- provider/model/version/deployment digest
- weights digest 가능 시
- provider opaque model은 provider attestation + deployment/version identity
- silent model replacement detection

### 6.2 Prompt/System Instruction
- system/developer/user prompt hierarchy
- prompt template digest
- dynamic prompt assembly provenance
- hidden prompt access governance

### 6.3 Tool Calling
- tool registry digest
- tool schema/version
- privilege/effect class
- tool selection policy
- unauthorized tool invocation negative tests

### 6.4 Agent Memory
- memory type
- persistence scope
- tenant boundary
- retention/deletion
- poisoning detection
- memory-blind evaluation

### 6.5 RAG
- corpus/index/embedding model/chunking/retrieval policy digest
- ACL preservation
- citation/source binding
- stale index detection
- corpus poisoning/adversarial document

### 6.6 Nondeterminism
단일 실행 PASS를 충분조건으로 사용하지 않는다.
- repeated run population
- seed/temperature/sampling config
- outcome distribution
- critical failure probability upper bound
- metamorphic/property oracle

통계적 claim은 sample size/confidence method를 함께 기록한다.

### 6.7 Multi-agent
- agent identities/roles
- delegation graph
- shared memory
- inter-agent message contract
- authority escalation
- collusion/common-mode failure
- cyclic delegation

## 7. AI Safety/Security 검증
- prompt injection
- indirect injection via RAG/tool output
- data exfiltration
- privilege escalation
- unsafe external effect
- hallucinated authority
- model refusal bypass
- tool parameter manipulation
- memory poisoning
- cross-tenant context leakage

Safety 결과는 business correctness 결과와 별도 claim으로 관리한다.

## 8. Provider Drift
외부 LLM/API provider의 model alias, policy, rate limit, tool behavior, safety filter가 변경되면 affected AI claims를 re-evaluate한다. provider name 동일성만으로 currentness를 유지하지 않는다.

## 9. ONSure Meta-Assurance
ONSure가 다른 제품을 검증하려면 ONSure 자신의 validator/oracle/adapter/reviewer/benchmark가 검증 자격을 가져야 한다.

### 9.1 ONSureReleaseQualification
- onsure_version/build digest
- core validator set digest
- oracle set digest
- adapter set digest
- fixture/benchmark set digest
- hidden corpus generation
- qualification environment
- independent verifier identities
- qualification results
- known limitations
- issued_at/expires/requalification triggers

### 9.2 Qualification Trigger
- Core validator 변경
- Oracle 변경
- Adapter 변경
- critical fixture/benchmark 변경
- security boundary 변경
- dependency/runtime major update
- MissedFinding proving blind spot
- cryptographic trust root change

### 9.3 Self-validation Ceiling
ONSure 자신의 self-test는 release qualification 입력일 뿐 최종 qualification authority가 아니다. 동일 implementation/knowledge chain의 두 실행을 independent로 세지 않는다.

### 9.4 Qualification Scope
모든 target archetype에 대한 전역 `QUALIFIED`를 금지한다. 예:
- Java/Spring API: QUALIFIED
- React Web: QUALIFIED
- Kubernetes deployment: PARTIAL
- Proprietary embedded RTOS: NOT_PROVEN

## 10. Meta-Assurance Certificate
ONSure version 자체에 대해 내부/외부 Qualification Certificate를 발행할 수 있으나 제품 고객 Certificate와 분리한다. 고객 Certificate에는 해당 검증을 수행한 ONSure Qualification generation을 참조한다.

## 11. Scale Failure Modes
- duplicate worker result double count
- partition omission
- queue retry PASS hides failure
- stale lease worker commits late result
- cross-tenant work-unit mix
- aggregation order changes digest
- cost exhaustion treated as PASS
- worker clock skew
- partial evidence upload
- coordinator restart resurrects stale authority

## 12. Plugin Failure Modes
- signed but revoked publisher
- plugin version rollback
- manifest privilege understatement
- parser silently drops unsupported syntax
- adapter maps unknown to safe default
- plugin output schema-valid but semantically incomplete
- same publisher controls independent oracle plugin

## 13. AI Failure Modes
- provider alias silently changes model
- prompt hash excludes dynamic system fragment
- RAG index updated without corpus epoch
- agent uses undeclared tool
- memory from another tenant retrieved
- repeated runs sample only favorable seeds
- judge model shares same blind spot as target model
- multi-agent majority vote mistaken for ground truth

## 14. API 후보
- `/v2/work-units/*`
- `/v2/plugins/*`
- `/v2/adapters/*/qualification`
- `/v2/ai-targets/*/runtime-profile`
- `/v2/ai-targets/*/behavior-populations`
- `/v2/onsure-releases/*/qualification`
- `/v2/onsure-releases/*/qualification-impact`

## 15. 수용기준
- 병렬/재시도 실행이 denominator와 aggregate를 부풀리지 않는다.
- Plugin/Adapter는 signed identity + current qualification 없이 authoritative result를 만들지 못한다.
- AI nondeterminism을 단일 PASS로 축소하지 않는다.
- Model/Prompt/RAG/Tool/Memory identity가 currentness graph에 포함된다.
- ONSure release qualification이 target archetype별로 명시된다.
- ONSure self-validation만으로 자기 자신을 QUALIFIED로 승격하지 않는다.

## 16. 기존 산출물 적용 위치
- `02`: Scale/Plugin/AI/Meta-Assurance 요구사항
- `03`: plugin/AI/meta review domain
- `04`: WorkUnit/Plugin/Qualification entity/API
- `05`: qualification/currentness/limitation UI
- `06`: distributed/plugin/AI adversarial fixture
- `07`: AI lifecycle/multi-agent/RAG/nondeterminism 상세
- `08`: qualification threshold, statistical confidence, supported archetype 정책

## 17. 비최종 경계
이 문서는 설계 확장이다. 현재 Claude 개발 Handoff의 DEV-01~13 완료조건을 변경하지 않으며, 별도 후속 개발 Batch로 전달한다.
