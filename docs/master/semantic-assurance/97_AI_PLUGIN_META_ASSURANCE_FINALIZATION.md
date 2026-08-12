# ONSure AI·Plugin/Adapter·Meta-Assurance 최종 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Covers: Task 28~30

## 1. AI Assurance
### Identity closure
AI Target identity는 최소 다음을 포함한다.
- provider/model/deployment/version
- model artifact/attestation digest
- system/developer prompt bundle digest
- tool registry/schema/privilege digest
- memory store/type/tenant scope digest
- RAG corpus/index/embedding/chunking/retrieval-policy digest
- sampling/temperature/seed policy

이름/alias 동일성은 identity authority가 아니다.

### Nondeterminism
단일 PASS 금지. `BehaviorPopulation`은 run population digest, generator/version, sample size, outcome distribution, critical miss count, confidence method를 가진다. Retry 결과는 모두 남긴다.

### Multi-agent
AgentIdentity, role, delegation edge, shared memory, message contract, tool privilege, escalation path, common-mode dependency를 별도 graph로 관리한다. 다수결은 Ground Truth가 아니다.

### AI-specific adversarial
prompt injection, indirect injection, tool misuse, memory poisoning, cross-tenant memory, RAG poisoning, model/provider drift, hidden context leakage, judge-target common blind spot.

## 2. Plugin/Adapter Trust
PluginManifest:
- plugin/version/publisher
- artifact digest/signature
- requested privileges
- network/filesystem/runtime needs
- supported archetypes
- input/output contract versions
- qualification generation

State:
UNREGISTERED|REGISTERED|QUALIFICATION_REQUIRED|QUALIFIED|SUSPENDED|REVOKED|INCOMPATIBLE.

Plugin update/revoked publisher/privilege expansion은 requalification trigger다. unsigned/unqualified output은 authoritative Final evidence가 아니다.

## 3. Adapter Qualification
Qualification dimension:
- discovery completeness
- semantic mapping fidelity
- unsupported feature disclosure
- parser/normalizer fidelity
- target version compatibility
- negative fixture sensitivity
- runtime observation support

Unknown/unsupported를 safe default로 변환 금지.

## 4. ONSure Meta-Assurance
ONSureReleaseQualification:
- release/build digest
- validator/oracle/adapter set digest
- fixture/benchmark/hidden corpus generation
- runtime/TCB/environment digest
- target archetype scope
- independent verifier receipts
- known limitations
- validity/requalification trigger

Self-test는 입력일 뿐 self-qualification authority가 아니다.

Qualification은 archetype별 QUALIFIED|PARTIAL|NOT_PROVEN. 전역 QUALIFIED 금지.

## 5. Meta chain
Customer Certificate → ONSure release qualification generation → validator/oracle/adapter qualification → build provenance/TCB.

## 6. Acceptance
- AI identity drift가 currentness에 연결
- stochastic claim에 population/statistics 존재
- plugin privilege와 qualification 결속
- ONSure self-validation ceiling 유지
- NOT_PROVEN archetype에 high-assurance certificate 금지
