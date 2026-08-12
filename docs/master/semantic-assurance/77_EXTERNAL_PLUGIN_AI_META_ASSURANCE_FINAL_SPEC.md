# ONSure External Integration·Plugin·AI·Meta-Assurance Final Specification

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. External Integration Trust
모든 외부 연동은 `IntegrationIdentity`를 가진다.
- integration_type: GIT|CI|ARTIFACT_REGISTRY|OLICENSE|AI_PROVIDER|PAYMENT|IDENTITY|EXTERNAL_API
- provider/org/account/project identifiers
- endpoint/audience
- credential type/key id
- contract/schema version
- expected security/provenance profile
- current trust state

### 공통 원칙
- external response는 authoritative truth가 아니라 provenance-bound input이다.
- webhook/event는 signature/audience/replay/idempotency 검증 후 소비한다.
- external mutable identifier(tag/branch/model alias)를 immutable artifact identity로 사용하지 않는다.
- 외부 provider 상태와 ONSure local state 불일치는 reconciliation event로 남긴다.
- provider failure/timeout을 domain PASS로 변환하지 않는다.

## 2. Git/CI/Registry
- commit/tree/release/artifact digest를 분리한다.
- CI PASS는 정확한 commit/artifact/config에 결속한다.
- artifact registry tag 대신 content digest read-back을 사용한다.
- build provenance/SBOM/signing identity가 검증 artifact lineage에 들어간다.

## 3. OLicense
- entitlement display와 authorization을 분리한다.
- effect 전 current license/entitlement를 재확인한다.
- offline grace는 signed snapshot + clock/revocation uncertainty에 결속한다.
- credit exhaustion은 scope를 조용히 축소하지 않고 HOLD/BLOCKED/RESOURCE_LIMIT 처리한다.

## 4. AI Provider
- provider/model/deployment/version identity를 기록한다.
- alias 변경/모델 교체/provider policy change는 drift trigger다.
- customer data reuse, region, retention, training policy를 contract profile로 관리한다.
- provider fallback은 qualified allowed set 안에서만 허용한다.

## 5. PluginManifest
Required:
- plugin_id/version
- publisher principal
- artifact digest/signature
- capability list
- supported target archetype/version
- required privilege manifest
- filesystem/network/tool access
- input/output contracts
- sandbox profile
- qualification record/expiry

Invariant:
- unsigned/revoked publisher → execution prohibited
- undeclared privilege → block + security event
- update/artifact digest change → requalification required
- plugin result alone cannot create independent Final assurance

## 6. Adapter Qualification
Adapter가 증명해야 하는 것:
- discovery completeness
- parser/normalizer fidelity
- unsupported feature disclosure
- semantic mapping correctness
- version compatibility
- negative fixture detection
- target runtime observation capability

Unknown/unsupported syntax를 안전한 default로 매핑하면 실패다.

## 7. AI-specific Assurance
### Model
provider/model/version/deployment identity, weights/attestation, currentness.

### Prompt
system/developer/user hierarchy, prompt template/dynamic fragment provenance.

### Tool
registry/schema/effect class/purpose/resource/parameter binding.

### Memory
namespace/tenant/persistence/retention/poisoning/deletion/memory-blind evaluation.

### RAG
corpus/index/embedding/chunking/retrieval policy/ACL/source lineage/currentness.

### Nondeterminism
exact scenario population, sample size, seeds/config, observed failure distribution, confidence method.

### Multi-agent
agent identity/role/delegation/message/shared memory/common-mode dependency.

## 8. AI Judge/Oracle Independence
- target model과 same provider/model family/judge prompt/oracle lineage를 기록한다.
- same-family judge agreement는 corroboration이다.
- critical claim은 independent tool/executable oracle/expert/real-world evidence를 우선한다.

## 9. ONSure Meta-Assurance
ONSure 자신은 release별 qualification scope를 가진다.
Required dimensions:
- release/build digest
- validator/oracle/adapter generations
- fixture/benchmark/hidden corpus generation
- TCB/SBOM/build provenance
- supported target archetype map
- independent verifier receipts
- known limitations
- validity/requalification triggers

## 10. Meta-Assurance Gate
다음은 qualification stale/requalification trigger다.
- validator/oracle/adapter 변경
- canonicalization/digest profile 변경
- security boundary/TCB major change
- hidden benchmark/critical fixture material change
- trust root/key registry change
- critical MissedFinding proving blind spot
- target archetype support expansion

## 11. Self-validation Ceiling
ONSure unit/integration/meta tests는 필수이지만 독립 qualification을 대신하지 않는다. 동일 코드/팀/knowledge chain의 여러 run은 independent count를 늘리지 않는다.

## 12. 고객 결과와 ONSure Qualification 연결
모든 AT3+ 결과는 어떤 ONSure release/validator/oracle qualification generation이 사용되었는지 참조한다. 해당 generation이 나중에 invalidated되면 affected customer Final/Certificate impact scan을 수행한다.

## 13. Negative Test Set
- mutable external tag substitution
- CI PASS for different commit
- replayed webhook
- expired/revoked OLicense snapshot
- AI provider silent model replacement
- plugin manifest privilege understatement
- adapter silent unsupported syntax drop
- same-family judge as ground truth
- self-qualified ONSure release
- unqualified archetype inherits global qualification

## 14. 수용기준
- 외부 trust가 immutable identity/provenance에 결속
- plugin/adapter/AI provider drift가 currentness에 전파
- AI nondeterminism이 단일 favorable result로 축소되지 않음
- ONSure 자체 qualification scope/limitation이 고객 claim에 연결
- self-validation만으로 independent/qualified state 발행 금지
