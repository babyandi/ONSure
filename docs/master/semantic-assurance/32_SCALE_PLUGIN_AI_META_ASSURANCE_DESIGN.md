# ONSure Scale·Plugin Trust·AI Assurance·Meta-Assurance 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parents: `29`, `30`, `31`

## 1. 목적
ONSure를 다수 고객·다수 Target·다양한 Adapter/AI 제품을 검증하는 플랫폼으로 운영할 때 필요한 Scale, Plugin Trust, AI-specific Assurance, ONSure 자체 Qualification을 정의한다.

## 2. Scale Architecture
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

Queue는 at-least-once delivery를 허용하되 logical effect/receipt commitment/nonce consumption은 중복되지 않아야 한다. duplicate execution, stale lease takeover, retry history, poison work unit quarantine, tenant fairness/backpressure를 관리한다.

## 3. Deterministic Aggregation / Cost Governance
병렬 완료 순서가 aggregate digest를 바꾸지 않도록 canonical sort/key를 사용한다. 동일 population+results면 scheduling과 무관하게 동일 digest를 생성한다. CPU/GPU/memory/storage/model token/external API budget과 tenant concurrency를 관리하되 비용 절감 때문에 required verification denominator를 조용히 축소하지 않는다. Budget exhaustion은 요구사항상 실패가 아닌 한 BLOCKED/RESOURCE_LIMIT다.

## 4. Plugin / Adapter Trust
PluginManifest:
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

상태: UNREGISTERED|REGISTERED|QUALIFICATION_REQUIRED|QUALIFIED|SUSPENDED|REVOKED|INCOMPATIBLE.

unsigned plugin 실행 금지, undeclared privilege 금지, plugin update requalification, plugin 결과만으로 Final independent assurance 금지, sandbox escape P0.

## 5. Adapter Qualification
Target Adapter는 target discovery completeness, semantic mapping correctness, version compatibility, negative fixture detection, unsupported feature disclosure, parser/normalizer fidelity, runtime observation capability를 검증한다. `supports=X` 선언은 qualification이 아니다.

## 6. AI-specific Assurance
### Model Identity
provider/model/version/deployment digest, 가능 시 weights digest. opaque provider model은 provider attestation+deployment identity를 사용하고 silent model replacement를 탐지한다.

### Prompt/System Instruction
system/developer/user hierarchy, template digest, dynamic assembly provenance, hidden prompt governance.

### Tool Calling
registry digest, schema/version, privilege/effect class, selection policy, unauthorized invocation negative test.

### Agent Memory
memory type, persistence scope, tenant boundary, retention/deletion, poisoning detection, memory-blind evaluation.

### RAG
corpus/index/embedding/chunking/retrieval-policy digest, ACL preservation, citation/source binding, stale index, poisoning test.

### Nondeterminism
단일 PASS를 충분조건으로 사용하지 않는다. repeated run population, seed/temperature/sampling config, outcome distribution, critical failure probability upper bound, metamorphic/property oracle을 기록한다. 통계적 claim은 sample size/confidence method를 포함한다.

### Multi-agent
agent identity/role, delegation graph, shared memory, message contract, authority escalation, collusion/common-mode failure, cyclic delegation을 검증한다.

## 7. AI Safety/Security
prompt/indirect injection, data exfiltration, privilege escalation, unsafe external effect, hallucinated authority, refusal bypass, tool parameter manipulation, memory poisoning, cross-tenant leakage를 별도 claim으로 관리한다.

## 8. Provider Drift
외부 LLM/API provider의 model alias, policy, rate limit, tool behavior, safety filter가 바뀌면 affected AI claims를 재평가한다. provider name 동일성은 currentness 근거가 아니다.

## 9. ONSure Meta-Assurance
`ONSureReleaseQualification`은 onsure_version/build digest, validator/oracle/adapter set digest, fixture/benchmark set digest, hidden corpus generation, qualification environment, independent verifier identities, results, limitations, issued/expiry/requalification trigger를 가진다.

Qualification trigger:
- Core validator/oracle/adapter 변경
- critical fixture/benchmark 변경
- security boundary 변경
- dependency/runtime major update
- MissedFinding으로 blind spot 확인
- cryptographic trust root 변경

Self-test는 release qualification 입력일 뿐 최종 authority가 아니다. 같은 implementation/knowledge chain의 두 실행을 independent로 세지 않는다.

## 10. Qualification Scope
전역 `QUALIFIED` 금지. target archetype별로 QUALIFIED|PARTIAL|NOT_PROVEN을 유지한다. 고객 Certificate는 해당 검증을 수행한 ONSure Qualification generation을 참조한다.

## 11. Failure Modes
Scale: duplicate count, partition omission, retry PASS hides failure, stale lease late commit, cross-tenant mix, aggregation nondeterminism, cost exhaustion PASS, clock skew, partial evidence, restart stale authority.

Plugin: revoked publisher, version rollback, privilege understatement, unsupported syntax drop, unknown→safe default, semantically incomplete output, same publisher controls independent oracle.

AI: provider alias drift, dynamic prompt fragment hash omission, RAG epoch omission, undeclared tool, cross-tenant memory, favorable-seed sampling, judge/target common blind spot, multi-agent majority mistaken for ground truth.

## 12. API 후보
- `/v2/work-units/*`
- `/v2/plugins/*`
- `/v2/adapters/*/qualification`
- `/v2/ai-targets/*/runtime-profile`
- `/v2/ai-targets/*/behavior-populations`
- `/v2/onsure-releases/*/qualification`
- `/v2/onsure-releases/*/qualification-impact`

## 13. 수용기준
- 병렬/재시도가 denominator/aggregate를 부풀리지 않는다.
- Plugin/Adapter는 signed identity + current qualification 없이 authoritative result를 만들지 못한다.
- AI nondeterminism을 단일 PASS로 축소하지 않는다.
- Model/Prompt/RAG/Tool/Memory identity를 currentness graph에 포함한다.
- ONSure release qualification은 target archetype별이다.
- ONSure self-validation만으로 자기 자신을 QUALIFIED로 승격하지 않는다.

## 14. 기존 산출물 적용 위치
- `02`: Scale/Plugin/AI/Meta-Assurance 요구사항
- `03`: plugin/AI/meta review domain
- `04`: WorkUnit/Plugin/Qualification entity/API
- `05`: qualification/currentness/limitation UI
- `06`: distributed/plugin/AI adversarial fixture
- `07`: AI lifecycle/multi-agent/RAG/nondeterminism 상세
- `08`: qualification threshold/statistical confidence/supported archetype 정책

## 15. 비최종 경계
현재 Claude 개발 Handoff의 DEV-01~13 완료조건을 변경하지 않으며 별도 후속 개발 Batch로 전달한다.
