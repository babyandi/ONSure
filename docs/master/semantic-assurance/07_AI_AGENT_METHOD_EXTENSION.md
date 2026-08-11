# ONSure Semantic Assurance AI·Agent 방법론 확장

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`
Parent authority: `../07_COMPONENT_MODEL_AND_AI_METHODOLOGY.md`

## 1. 목적
기존 ONSure의 Agent 역할 분리, 최소권한, Plan-Act-Observe, 모델 버전 고정, OMemory 재귀학습 원칙을 유지하면서 AI Use Case별 authority/TEVV/fallback/human judgment와 Validator Method Requalification을 더 엄격하게 정의한다.

## 2. AI Use Case를 1급 권위 객체로 관리
AI 적용 후보, 자동화 등급표, 모델/Prompt 문서, TEVV family가 따로 존재하는 것만으로 AI 설계가 닫혔다고 보지 않는다.

각 AI Use Case는 stable `ai_use_case_id`를 가지며 다음 closure를 갖는다.

`AI-UC`
→ Applicability Decision
→ Non-AI Baseline
→ Value/Kill Criteria
→ Automation & Canonical Effect Ceiling
→ Authorized Data/Egress
→ Model/Profile
→ Prompt/Instruction
→ Knowledge/RAG
→ Tool Registry/Permit
→ Failure/Fallback
→ TEVV Cases
→ Execution Evidence
→ Human/Domain Disposition
→ Freshness/Requalification

## 3. Applicability / Non-AI Baseline
각 AI-UC는 최소 다음을 가진다.
- AI 적용 목적
- deterministic/manual baseline
- AI가 개선해야 할 metric/UX
- adoption prerequisite
- defer/reject/kill criteria
- AI OFF fallback
- provider failure fallback

AI 적용이 기술적으로 가능하다는 이유만으로 ADOPT하지 않는다.

상태:
- ADOPT_CANDIDATE
- ADOPT_CANDIDATE_WITH_REVIEW
- ADOPT_ONLY_IF_GROUNDED
- DEFER
- REJECT
- INPUT_REQUIRED
- HOLD

DEFER/REJECT도 denominator에서 삭제하지 않고 disposition evidence를 보존한다.

## 4. Automation / Effect Ceiling
각 AI-UC는 canonical effect ceiling을 명시한다.
- READ_ONLY
- ADVISORY
- SUGGESTION
- SIMULATION
- PROPOSE_EFFECT
- EFFECT_WITH_HUMAN_GATE
- FORBIDDEN_EFFECT

모델 출력 자체는 permit/authority를 생성하지 않는다.

Tool availability 또는 권한이 증가해 effect class가 커지면 기존 TEVV를 stale 처리한다.

## 5. AI Profile Identity
AI profile은 model name 하나가 아니다.

최소 구성:
- model/provider/deployment digest
- inference/safety config
- prompt revisions
- knowledge/RAG corpus/index
- retrieval/rerank config
- tool registry/policy
- product policy
- privacy/egress profile
- output schema
- step/token/cost budget

`latest` 같은 mutable alias만으로 current TEVV authority를 만들지 않는다.

## 6. Prompt / Instruction Authority
- system/product/task/user/RAG/tool input 권위를 분리
- prompt ID/revision/hash 결속
- missing evidence/uncertainty 처리 명시
- model output을 canonical policy/evidence로 승격 금지
- indirect prompt injection과 retrieved instruction 구분
- prompt text에 적힌 rule만으로 runtime enforcement PASS 금지

## 7. Dataset / Knowledge / RAG
### Dataset Separation
- TRAINING
- VALIDATION
- HIDDEN_QUALIFICATION
- GOLDEN_REGRESSION
- ROTATING_UNSEEN

byte hash뿐 아니라 semantic/implementation/source-project family를 관리한다.

### Knowledge Authority
각 source는:
- source ID/revision/hash
- authority
- tenant/resource/purpose
- classification
- rights/license
- effective/superseded
을 가진다.

### RAG Retrieval
`actor/tenant/purpose -> ACL prefilter -> retrieval -> authority/freshness filter -> rerank -> context budget -> citation map`

RAG relevance score가 authority를 대신하지 않는다.

## 8. Tool / Agent Authority
각 Tool은 최소:
- tool_id/version/schema
- actor/tenant/purpose/resource constraints
- effect class
- permit authority
- timeout/retry/idempotency
- read-back/evidence
- forbidden effect
을 가진다.

Unregistered network/filesystem/DB/shell/secret access는 default deny다.

Tool result는 새로운 instruction authority가 아니다.

## 9. Per-AI-UC TEVV
각 adopted candidate는 최소 개별 case ID로:
1. Normal / quality
2. Boundary / malformed / no-evidence
3. Failure / fallback
4. Authority / effect negative
5. Privacy / security
6. Drift / freshness
를 가진다.

TEVV family 이름만으로 per-UC coverage를 대체하지 않는다.

Run Identity는 최소:
- model/config
- prompt
- knowledge/index
- retrieval/rerank
- tool registry/policy
- dataset/oracle
- product policy
- environment
의 exact hash/version을 결속한다.

## 10. Human Judgment / Automation Bias Resistance
### 원칙
`HUMAN CLICK != HUMAN JUDGMENT`.

High-risk canonical decision에서 사람은 AI 추천과 독립된 evidence source에 접근할 수 있어야 하며, AI와 다른 판단을 할 수 있어야 한다.

### Human Decision Mode
- INDEPENDENT_REVIEW_AI_NOT_SHOWN
- INDEPENDENT_REVIEW_THEN_AI_CONSULTED
- AI_CONSULTED_WITH_EVIDENCE_REVIEW
- AI_OVERRIDE_BY_HUMAN
- INSUFFICIENT_REVIEW_EVIDENCE

마지막 상태는 high-risk canonical decision의 충분한 Human Review Evidence가 아니다.

### 금지
- high-risk action AI recommendation default select
- source evidence를 보지 않아도 one-click approve
- confidence를 truth probability처럼 표시
- AI와 다른 판단에 자동 penalty
- AI-human agreement가 높다는 이유로 품질 PASS

## 11. Independence Profile
기존 07의 Agent role 분리를 다음 6축으로 확장한다.
- Execution Independence
- Principal Independence
- Implementation Independence
- Oracle Independence
- Discovery Independence
- Knowledge Independence

`different_model=true` 또는 `different_run=true`만으로 Independent PASS를 발급하지 않는다.

## 12. Memory-Blind Lane
일부 고신뢰 검증은 이전 Finding/Score/Verdict/KnowledgePattern/Vendor reputation을 입력에서 제거한다.

필수:
- blind_context_manifest
- denied_source_inventory
- memory/retrieval access audit
- previous verdict absence proof

Memory-aware와 blind 결과가 충돌하면 자동 다수결로 처리하지 않고 `DISAGREEMENT_HOLD` 또는 제3 검증으로 보낸다.

## 13. Ground Truth 등급과 Oracle Coupling
Ground Truth:
- GT0_UNKNOWN
- GT1_CORROBORATED
- GT2_INDEPENDENT_TOOL
- GT3_EXECUTABLE_ORACLE
- GT4_EXPERT_VERIFIED
- GT5_REAL_WORLD_OBSERVED

Oracle은 target-code dependency를:
- NONE
- PARTIAL
- SHARED
로 기록한다.

Critical Claim의 primary Oracle은 가능하면 NONE이어야 하며 SHARED는 보조증거로만 사용한다.

## 14. Validator / Detector Method Requalification
ONSure 자신의 validator, detector, rule pack, oracle, scenario generator가 바뀌면 기존 qualification을 자동 상속하지 않는다.

### Requalification Trigger
- detector 추가/삭제/완화
- severity/blocking policy 변경
- oracle 변경
- coverage/denominator policy 변경
- scenario generator 변경
- evidence binding logic 변경
- isolation/runner 변경
- model/prompt/knowledge/tool change가 validator behavior에 영향

### Requalification 단계
1. canonical method manifest freeze
2. transport/content fidelity 확인
3. public regression
4. genuinely isolated/private qualification
5. hidden/rotating/OOD benchmark
6. critical denominator 계산
7. strict recall 및 escaped critical 측정
8. independent evidence reperformance
9. historical impact scan

### 금지 Substitute
- static file 존재
- SHADOW run
- nonblind replay
- manual prompt reconstruction
- caller-declared isolation PASS
- partial Golden recall

## 15. Learning Regression Guard
Rule Pack 또는 AI reviewer update 후 전체 평균이 좋아져도 다음은 승격 금지.
- 기존 Critical seeded defect escape
- Critical Recall 감소
- authority/tenant/evidence-integrity class recall 감소
- false negative 증가를 평균 F1로 숨김
- 특정 framework만 좋아지고 타 domain 심각 악화

Detector weakening/removal, Oracle change는 신규 detector 추가보다 더 강한 승인과 hidden benchmark를 요구한다.

## 16. Hidden Dataset Governance
Hidden이라는 label만으로 독립성이 성립하지 않는다.

최소 관리:
- corpus owner
- access control
- access log
- initial seal time
- semantic family
- rotation/retirement
- leakage incident
- benchmark selection precommitment

결과를 본 뒤 잘 나온 corpus만 Qualification에 선택하지 못한다.

## 17. AI Resource / Availability Integrity
- token/tool/step/cost ceiling
- concurrency/queue/backpressure
- provider quota/rate limit
- noisy-neighbor isolation
- AI pressure가 canonical core/evidence integrity를 고갈시키지 않음
- fallback 전환이 authority/evidence 수준을 자동 유지한다고 가정하지 않음

## 18. Freshness Graph
AI Profile 구성요소 하나라도 변경 시 영향분석한다.

`Model+Config+Prompt+Knowledge/Index+Retrieval/Rerank+Tool Registry/Policy+Dataset/Oracle+Product Policy+Privacy/Egress`

Targeted revalidation은 unaffected scope를 machine-readable evidence로 입증해야 한다.

## 19. AI Receipt 분리
AI Candidate Receipt는 provenance이며 domain canonical decision receipt가 아니다.

AI evidence 후보:
- AI-UC/profile identity
- input projection/source refs
- model/prompt/knowledge/tool revisions
- citation map
- tool permit/request/result/read-back
- output hash/policy flags
- human disposition
- TEVV links

Privacy 때문에 raw prompt/response 전체 저장을 default evidence requirement로 두지 않는다.

## 20. 수용기준
- adopted AI-UC가 applicability/profile/fallback/TEVV execution 없이 PASS하지 않음
- required AI-UC `fresh_pass < adopt_candidate`이면 subsystem Full PASS 금지
- human high-risk decision은 review mode와 evidence access를 기록
- memory-blind lane이 선언만이 아니라 기술적으로 context 차단을 증명
- validator method 변경 후 current qualification receipt가 없으면 L5/고신뢰 claim 불가
- hidden leakage가 확인되면 영향받는 qualification을 무효화
