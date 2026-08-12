# 126 Final Fresh Product Design Review

Status: `FRESH_REVIEW_COMPLETE / TWO_NEW_AXES_FOUND / NON_FINAL`

## 1. 목적
기존 00~125의 상세화·Trace·Scanner·Hash·Lock 작업을 새로운 제품 설계축으로 세지 않고, ONSure를 처음 보는 제품 관점에서 독립적인 설계영역 누락 여부만 재검토한다.

Fresh Review 질문:
> ONSure가 실제 고객의 소프트웨어를 학습·검토·검증·개선하고 그 결과를 신뢰 가능한 Assurance로 주장하기 위해 반드시 필요한 독립 설계축 중 현재 구조에 존재하지 않는 것이 있는가?

## 2. 이미 충분히 존재하는 축
다음은 신규 발견으로 세지 않는다.
- Product/Commerce/Case/Licensing
- Program Understanding / Planning
- Requirement/Architecture/Policy/Code/AI/Security/Test Review
- Verification / Improvement / Training
- Evidence / Receipt / Audit / Legal Hold
- Independent OTester/OAudit/Human Acceptance
- Authority/SoD/Delegation/Four-eyes/Break-glass
- Deployment→Running→Currentness
- Drift/Invalidation/Revocation/Recovery
- Multi-target Product Composition
- Certificate/Offline Verification
- Scale/Distributed Work
- Plugin/Adapter Trust
- AI Model/Prompt/RAG/Tool/Memory/Multi-Agent/Nondeterminism
- ONSure Meta-Assurance/Qualification
- Persistence/API/Threat/Privacy/Observability/DR/External Trust
- Requirement Universe/Trace/Design Lock

## 3. 신규 독립 설계축 1 — Safety / Hazard Assurance
### 발견 이유
현재 Review/Verification은 품질·보안·성능·회복성·AI 위험을 깊게 다루지만, **위해(harm)를 중심으로 한 Safety/Hazard lifecycle**은 독립 domain으로 정의되어 있지 않다.

ONSure의 대상에는 의료, 자동화 Workflow, Agentic System, 외부 Tool Effect가 포함될 수 있으므로 다음은 Security와 다른 의미를 가진다.
- 공격자가 없어도 위험한 동작이 발생할 수 있음
- 기능적으로 요구사항을 만족해도 안전하지 않을 수 있음
- 단순 FAIL이 아니라 safe state / fail-safe / degraded-safe가 필요할 수 있음
- residual risk를 명시적으로 인수해야 할 수 있음

### 필요한 독립 개념
- Hazard
- Harm
- Hazardous Scenario
- Safety Requirement
- Safety Control / Mitigation
- Safe State / Degraded Safe State
- Severity × Exposure × Controllability 또는 산업별 risk model
- Residual Safety Risk
- Safety Case / Claim-Argument-Evidence
- Safety-critical change impact
- Safety validation / fault injection
- Safety acceptance authority

### 기존 축과 관계
- Security: 악의적 위협 중심. Safety는 비악의적 failure/harm 포함.
- Reliability: 고장 확률 중심. Safety는 결과의 위해성 중심.
- Risk Accept: 일반 risk acceptance와 구분하여 safety residual risk authority를 별도 제한할 수 있음.

## 4. 신규 독립 설계축 2 — Contestability / Appeal / Dispute Governance
### 발견 이유
현재 Finding Explorer는 False Positive, Risk Accept, Expert Review를 제공하지만, ONSure 자체의 판정·제외·Certificate 상태를 **formal하게 challenge하고 재심하는 lifecycle**은 독립적으로 정의되어 있지 않다.

Assurance 제품은 판정을 강하게 할수록 다음이 필요하다.
- 고객이 Finding 근거를 다툴 수 있어야 함
- ONSure가 N/A/Excluded를 잘못 판단했을 수 있음
- External Acceptor와 Customer가 상반된 주장을 할 수 있음
- Certificate revoke/invalidate에 이의가 있을 수 있음
- 최초 판정자가 자기 판정을 재심하면 independence가 깨짐

### 필요한 독립 개념
- Challenge/Appeal Case
- challenged decision/evidence exact binding
- appellant identity/authority
- challenge reason/category
- new evidence submission
- preservation of original decision
- independent appeal reviewer
- review deadline/SLA
- decision: UPHOLD|REVERSE|MODIFY|REASSESSMENT_REQUIRED|INCONCLUSIVE
- downstream impact propagation
- certificate/finding/currentness supersession
- anti-retaliation / no silent deletion of original evidence

### 적용 대상
- Finding
- NOT_APPLICABLE / exclusion
- Accepted Risk
- Human Acceptance
- Qualification result
- Product Composition ceiling
- Certificate issuance/revocation
- ONSure-generated customer claim

## 5. Fresh Review에서 신규축으로 인정하지 않은 후보
다음은 이미 기존 설계에 포함되거나 기존 축의 상세화다.
- Standards mapping/interoperability → Industry/Policy/Certificate/External Trust의 상세화
- Incident response → Observability/Revocation/Recovery/Notification의 상세화
- Accessibility/localization → 일반 UX/NFR 상세화
- Vulnerability disclosure → Security/Incident governance 상세화
- Legal defensibility → Evidence/Legal Hold/Certificate/Authority 상세화
- Model/data rights → AI/Training/Privacy/External Trust에 존재
- Supply chain → Plugin/Adapter/External Trust/TCB에 존재

## 6. 판정
신규 독립축: **2개**
1. Safety/Hazard Assurance
2. Contestability/Appeal/Dispute Governance

이 둘을 설계 기준선에 반영한 후 동일 기준 Fresh Review를 1회 재수행한다. 그 재수행에서 신규 독립축이 0개이면 `PRODUCT_DESIGN_SCOPE_COMPLETE_CANDIDATE`를 선언하고 이후 새 설계축 탐색을 종료한다.
