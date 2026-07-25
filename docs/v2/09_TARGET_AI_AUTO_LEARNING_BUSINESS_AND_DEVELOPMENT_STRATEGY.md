# ONSure 대상 AI 자동학습·개선 사업 및 개발전략

## 0. 문서 상태와 목적

- 문서 유형: ONSure V2 사업·제품·개발 기준 보완안
- 적용 제품: ONSure Web, ONSure for VS Code, ONSure API, Enterprise/On-premises
- 기준선: `babyandi/ONSure` main `5a761ccf46a49cde467a968d61884a521b02e6ab`
- 상태: 구현 전 설계 기준, 모든 기능과 시장성 평가는 실제 검증 전까지 `NON_FINAL`

이 문서는 ONSure의 기존 `Learn → Verify → Improve → Re-verify → Prove → Remember` 구조를 확장해, 학습이 필요한 고객 프로그램의 AI 구성요소를 ONSure가 자동으로 학습·개선하는 사업과 개발 방향을 정의한다.

ONSure에서 사용하는 "학습"은 다음 두 의미를 반드시 구분한다.

1. **Program Understanding Learning**: ONSure가 대상 프로그램의 목적·구조·기능·행동·실행환경을 학습한다.
2. **Target AI Auto-Learning**: 대상 프로그램 안의 RAG, Prompt, Agent, Tool 선택정책, AI Model, 예측·분류·추천·비전 모델을 실제 데이터와 검증기준으로 학습·개선한다.

두 학습은 목적, 입력, 산출물, 비용, 위험, 라이선스 단위가 다르다. UI, API, 데이터 모델, Evidence, OLicense Entitlement에서도 혼용하지 않는다.

---

## 1. 경영 요약

### 1.1 최종 제품 정의

ONSure는 AI가 작성했거나 AI 기능을 포함한 프로그램을 인수해 다음 전 과정을 수행하는 독립 상용 플랫폼이다.

```text
Understand
→ Verify
→ Diagnose
→ Decide
→ Improve or Train
→ Independently Re-verify
→ Prove
→ Deploy
→ Observe
→ Re-learn
```

ONSure는 단순 코드검사기, AI 코드리뷰 도구, 모델 학습도구, 외주개발 서비스 중 하나로 한정되지 않는다. 프로그램 전체를 먼저 이해하고, 실제 문제의 원인이 코드·정책·데이터·검색·Prompt·Agent·Model 중 어디에 있는지 판정한 뒤, 검증된 Finding에 근거해 가장 적절한 개선 또는 학습을 수행하고 효과를 증명한다.

### 1.2 고객에게 제공하는 결과

고객은 기술 기능 자체보다 다음 결과를 구매한다.

- 개발자가 떠난 프로그램의 구조와 상태를 이해할 수 있다.
- AI로 만든 프로그램을 실제 서비스 가능한 상태로 개선할 수 있다.
- 외주 납품 프로그램이 요구사항대로 동작하는지 확인할 수 있다.
- RAG·Prompt·Agent·Model의 품질 부족 원인을 구분할 수 있다.
- 필요한 경우 대상 AI를 자동학습시켜 정확도·안정성·속도·비용을 개선할 수 있다.
- 수정·학습 전후 결과를 동일 기준으로 비교하고 증명할 수 있다.
- 운영 중 새 데이터와 실패사례를 안전하게 재학습에 반영할 수 있다.

### 1.3 핵심 사업 판단

현재 공개된 외주 수요에는 다음 문제가 반복된다.

- ChatGPT 등으로 초기 코드를 만들었으나 전문적인 검토·오류 수정·안정화가 필요하다.
- 기존 AI 서비스가 느리거나 불안정해 재구축·고도화가 필요하다.
- 객체 인식·추적 등 AI 기능의 실제 환경 성능이 부족하다.
- 기존 개발자가 퇴사·중단·폐업해 소스 분석과 인수인계가 필요하다.
- RAG·챗봇·문서자동화·추천·이미지 생성 서비스를 만들고 싶지만 품질판정 기준이 부족하다.

이는 ONSure가 가정한 문제가 실제로 존재한다는 시장 신호다. 다만 공개 사례는 수요 존재를 보여주는 정성 근거이며 시장규모와 지불의사를 최종 증명하지 않는다. 유료 Case, 원가, 전환율, 재구매율을 실제로 측정해야 한다.

### 1.4 사업성 종합평가

| 평가항목 | 현 시점 판단 | 근거와 조건 |
|---|---:|---|
| 고객 문제의 실재성 | 높음 | AI 생성 코드 안정화, 기존 AI 서비스 오류 수정·고도화 의뢰가 존재 |
| 지불 가능성 | 중상 | 공개 의뢰 예산이 소규모 100만 원대부터 1,000만 원 이상까지 분포 |
| 차별화 가능성 | 높음 | 프로그램 전체 학습, 원인 판정, 개선·학습, 독립 재검증, Evidence를 하나의 폐쇄루프로 제공 |
| 반복매출 가능성 | 높음 | 운영 데이터, 모델 성능저하, RAG 문서변경, Release마다 재검증·재학습 수요 발생 |
| 구현 난이도 | 매우 높음 | 다양한 언어·프레임워크·모델·데이터·실행환경·GPU·보안 요구 |
| 초기 원가위험 | 높음 | 모델 API, Sandbox, GPU, Storage, 전문가 검토 비용 |
| 현 사업 준비도 | 낮음 | 가격, 원가 Benchmark, 유료고객 검증, 출시 E2E가 아직 미완료 |

**현재 결론:** 사업기회는 유효하지만 아직 사업성이 입증된 것은 아니다. 초기에는 모든 자동학습 유형을 동시에 개발하지 않고, 재현성과 고객가치가 높은 RAG·Prompt 개선과 AI 생성 코드 안정화부터 유료 Case로 검증해야 한다.

---

## 2. 시장 문제와 초기 고객

### 2.1 핵심 고객 문제

#### AI 생성 코드 상용화 실패

- 실행은 되지만 예외·보안·성능·운영 기능이 부족하다.
- 생성된 코드의 구조와 의도를 발주자나 운영자가 이해하지 못한다.
- AI가 요구하지 않은 기능을 추가하거나 중요한 기능을 누락한다.
- 동일 기능이 여러 위치에 중복되고 책임 경계가 불명확하다.
- 개발환경에서는 작동하지만 모바일·운영·다른 OS에서는 실패한다.

#### AI 응용서비스 품질 부족

- Prompt 응답이 일관되지 않고 환각이 발생한다.
- RAG가 적절한 자료를 찾지 못하거나 오래된 정보를 반환한다.
- Agent가 잘못된 Tool을 호출하거나 중단조건을 지키지 않는다.
- 외부 Model API 장애·Rate Limit·비용급증에 대응하지 못한다.
- 모델 정확도는 높아 보여도 실제 업무의 중요한 실패를 놓친다.

#### 인수·운영 단절

- 개발자가 퇴사하거나 외주업체와 계약이 종료됐다.
- 소스는 있지만 빌드·배포·DB·Secret·운영문서가 불완전하다.
- 어떤 데이터로 어떤 모델을 학습했는지 알 수 없다.
- 모델·Prompt·RAG·데이터 버전과 운영결과의 계보가 없다.
- 수정 후 실제로 좋아졌는지 동일 기준으로 비교할 수 없다.

### 2.2 초기 목표고객

우선순위는 다음과 같다.

1. AI로 웹·앱·SaaS를 만든 개인 창업자와 소규모 사업자
2. 외주 AI 프로그램을 인수하거나 잔금 지급을 앞둔 발주자
3. AI Agent·RAG·챗봇을 출시하려는 스타트업
4. 기존 개발자가 이탈한 AI 프로그램을 운영해야 하는 기업
5. AI 프로그램을 반복 납품하는 개발사·SI·프리랜서
6. 품질·보안·감사 Evidence가 필요한 규제산업 조직

초기 B2C는 일반 소비자가 아니라 직접 구매결정을 하는 개인 개발자·1인 사업자·소형 발주자라는 `Developer Prosumer`로 정의한다. 중소기업과 스타트업은 소형 B2B로 함께 취급한다.

### 2.3 공개 시장신호 예시

다음 공개 사례는 상품 설계 참고용이며 ONSure의 매출을 보장하지 않는다.

- ChatGPT로 생성한 초기 앱 코드를 전문 개발자가 검토·오류 수정·안정화해 출시하려는 의뢰
  - https://www.wishket.com/project/150542/
- AI 상담 서비스를 재구축·고도화하려는 의뢰
  - https://www.wishket.com/project/156765/
- AI 캐릭터 채팅 플랫폼을 안정화·고도화하려는 의뢰
  - https://www.wishket.com/project/153636/
- 객체 인식·추적 앱의 실제환경 오류를 수정하고 안정화하려는 의뢰
  - https://www.wishket.com/project/156501/
- Gemini API 응답속도와 앱·서버 안정성을 함께 개선하려는 의뢰
  - https://www.wishket.com/project/156429/
- ChatGPT API 기반 핵심기능 MVP를 빠르게 검증하려는 의뢰
  - https://www.wishket.com/project/151684/

### 2.4 경쟁 기준

ONSure는 범용 AI 코딩도구와 기능 수로 경쟁하지 않는다. 다음 시장을 구분한다.

| 시장 | 고객이 구매하는 것 | ONSure 대응 |
|---|---|---|
| AI Coding Assistant | 코드 생성속도 | 직접 경쟁보다 생성 결과의 독립 검증 |
| AI Code Review | PR 결함·스타일 의견 | 실행·행동·Prompt·RAG·Model까지 확대 |
| Security Scanner | 취약점 탐지 | 보안은 Verification Pack 중 하나 |
| MLOps | 모델 학습·배포 인프라 | 문제진단·학습선택·독립평가·개선증명을 상위 계층에서 제공 |
| 외주개발 | 요구 기능 구현 인력 | 검증 Finding에 결속된 제한 개선 |
| QA/테스트 | 사전 정의된 시험 수행 | 프로그램 학습으로 필요한 시나리오를 생성하고 증거화 |

ONSure의 차별점은 하나의 도구기능이 아니라 `프로그램 이해 → 원인 판정 → 개선/학습 → 독립 재검증 → 증명`의 폐쇄루프다.

---

## 3. 두 종류의 학습 계약

### 3.1 Program Understanding Learning

목적은 ONSure가 대상 프로그램을 이해하고 검증기준을 만드는 것이다.

입력:

- 요구사항, 정책, 계약, 사용자 설명
- Source, Binary, Container, Infrastructure
- Prompt, Agent, Tool, RAG, Model 설정
- 테스트, 로그, 장애이력, 운영지표
- DB Schema, API, 외부 연계

산출물:

- Program Profile
- Behavior Profile
- Requirement/Policy Trace
- AI Component Map
- Verification Baseline
- Unknown/Blocked Register
- Failure/Improvement Memory

이 학습은 대상 AI Model의 Weight를 변경하지 않는다.

### 3.2 Target AI Auto-Learning

목적은 검증을 통해 확인된 품질 부족을 해결하기 위해 대상 프로그램의 학습 가능한 구성요소를 개선하는 것이다.

입력:

- 승인된 Finding와 RCA
- 고정된 Dataset·Knowledge Base·Feedback
- 목표 Metric과 Acceptance Threshold
- Base Model·Prompt·Retriever·Agent Policy
- Training Budget과 실행환경

산출물:

- Dataset Manifest와 Quality Report
- Training Plan
- Candidate Prompt/RAG/Policy/Model
- Training Run과 Hyperparameter Record
- Evaluation Report
- Before/After Evidence
- Model/Prompt/RAG/Agent Release Candidate
- Rollback Package

Target AI Auto-Learning은 임의 기능개발 요청에서 시작하지 않는다. 반드시 검증된 Finding, 승인된 개선 목표, 사용권이 확인된 데이터, 고정된 평가기준을 전제로 한다.

### 3.3 명명 규칙

제품과 코드에서 다음 명칭을 권장한다.

- `OLearning`: Program Understanding Learning
- `OTargetLearning`: 대상 AI 자동학습 Orchestration
- `ODataset`: Dataset Intake, Quality, Version, Lineage
- `OEvaluation`: 학습 전후 독립평가와 Model/Prompt/RAG/Agent 비교
- `OModelRegistry`: Model·Adapter·Prompt·Retriever·Agent Policy 버전관리

`Learning` 단어만 단독으로 사용해 두 의미를 혼용하지 않는다.

---

## 4. 자동학습 적합성 판정

### 4.1 원인 우선 원칙

모든 품질 문제를 모델 재학습으로 해결하지 않는다. ONSure는 먼저 가장 작은 유효 개선수단을 선택한다.

| 증상 | 우선 진단 | 1차 개선수단 | 재학습 조건 |
|---|---|---|---|
| 기능이 실행되지 않음 | 코드·환경·권한·Dependency | 코드·설정 수정 | 기본기능 정상화 후 모델문제가 남을 때 |
| 답변이 사실과 다름 | RAG 검색·근거·Prompt·Model | Knowledge/Retriever/Prompt 개선 | 동일 근거에서도 모델행동이 불충분할 때 |
| 질문별 답변 편차가 큼 | Prompt·Sampling·Context | Prompt·Parameter·Guardrail | 반복 실패패턴이 충분하고 평가셋이 있을 때 |
| Agent가 잘못된 Tool 호출 | Tool Schema·Policy·Planner | Schema·Policy·중단조건 수정 | 정책수정만으로 목표치를 못 넘을 때 |
| 이미지 인식률이 낮음 | 데이터·라벨·환경차이 | 데이터 정제·증강 | 평가셋 분리 후 재학습 가치가 입증될 때 |
| 응답이 느리거나 비쌈 | Model·Token·Cache·Routing | Routing·Cache·Prompt 축약 | 경량모델 Fine-tuning이 경제적일 때 |
| 운영 성능이 점차 저하 | Data/Concept Drift | Drift 분석·데이터 갱신 | 승인된 신규 데이터가 충분할 때 |

### 4.2 Learning Readiness 판정

Target AI Auto-Learning 시작 전 다음을 판정한다.

- `READY`: 데이터·권리·목표·평가셋·예산·실행환경이 준비됨
- `READY_WITH_REMEDIATION`: 데이터 정제·라벨링·평가기준 보완 후 가능
- `RAG_OR_PROMPT_FIRST`: 모델 재학습보다 검색·Prompt 개선이 우선
- `CODE_OR_POLICY_FIRST`: 학습문제가 아니라 코드·정책 결함
- `CUSTOM_RESEARCH`: 일반 학습 파이프라인으로 목표달성 보장 불가
- `BLOCKED`: 권리·개인정보·보안·데이터 품질·재현성 문제로 실행 금지

### 4.3 자동학습 금지조건

- 데이터 수집·학습 사용권이 확인되지 않음
- 개인정보·민감정보 처리근거와 보호방안이 없음
- 평가셋이 학습데이터에 포함돼 성능검증이 오염됨
- 고객이 원하는 목표를 측정 가능한 Metric으로 정의하지 못함
- 악성·오염·편향 데이터 위험을 통제할 수 없음
- Base Model License가 상업적 Fine-tuning·재배포를 허용하지 않음
- Rollback 불가능
- 운영 시스템이 학습 결과를 승인 없이 즉시 반영하도록 요구함

---

## 5. 자동학습 유형별 제품기능

### 5.1 RAG Auto-Learning

수행범위:

- 문서·DB·API·파일 Intake
- 권위자료와 참고자료 역할 구분
- 중복·오래된 자료·충돌·비밀정보 탐지
- Parsing·Chunking·Metadata·Embedding 후보 생성
- Index·Retriever·Reranker 후보 비교
- 질문·정답·근거 Evaluation Set 생성과 승인
- Retrieval Recall, Precision, Groundedness 측정
- 실패질문 기반 증분개선
- 변경자료 영향분석과 부분 재색인

최종 답변 정확도와 검색 정확도를 분리한다. 좋은 답변처럼 보여도 근거자료가 잘못 검색되면 PASS하지 않는다.

### 5.2 Prompt Auto-Optimization

수행범위:

- System/User Prompt와 Few-shot 구조 분석
- Prompt 후보 자동생성
- 고정 Evaluation Set에 대한 반복실험
- 정확성·안전성·일관성·Token·Latency 비교
- Prompt Injection·Context Override 적대시험
- 승인된 Prompt Version 배포와 Rollback

평가셋에 과적합된 Prompt를 방지하기 위해 Hidden/Adversarial Set을 별도 유지한다.

### 5.3 Agent·Tool Policy Learning

수행범위:

- Agent Plan과 Tool Call Trace 수집
- 성공·실패 경로 비교
- Tool Schema·권한·Precondition·Postcondition 검증
- 잘못된 Tool 선택, 반복 Loop, 조기종료, 무단실행 탐지
- Policy·Planner·Router 후보 개선
- Sandbox에서 Scenario·Adversarial 재실행
- 고위험 Tool은 항상 사람 승인 Gate 유지

Agent가 생성한 계획과 최종판정은 동일 실행주체에 맡기지 않는다.

### 5.4 LLM Fine-tuning·Adapter Learning

초기 지원범위:

- Provider가 지원하는 Fine-tuning API
- LoRA/Adapter 기반 제한 학습
- Instruction/Classification/Extraction 등 측정 가능한 과제
- Base Model과 Candidate Model A/B 비교

필수 통제:

- Dataset License·Consent
- Train/Validation/Test/Hidden 분리
- PII·Secret·저작권 위험검사
- Hyperparameter·Seed·Environment 기록
- 비용·성능·안전성 동시평가
- Base Model·Adapter 조합과 배포물 계보

Foundation Model 사전학습은 초기 제품범위에서 제외한다.

### 5.5 예측·분류·추천·비전 재학습

수행범위:

- Schema·Label·Class Distribution 분석
- 결측·중복·오류라벨·Leakage·Bias 탐지
- Dataset Split과 환경별 시험셋 관리
- Baseline Model 재현
- Candidate Pipeline 학습
- Accuracy뿐 아니라 Recall, Precision, F1, Calibration, False Positive/Negative 비용 평가
- 실제 운영 Device·OS·Camera·Network 조건 검증
- Champion/Challenger와 Rollback

업무상 피해가 큰 오류 유형에는 전체 평균보다 별도 최소기준을 적용한다.

### 5.6 Synthetic Data·Label Support

합성데이터와 자동라벨은 부족한 데이터 보완수단이지 권위 정답이 아니다.

- 생성 출처·Model·Prompt·Seed 기록
- 실제데이터와 합성데이터 구분
- 중복·오염·개인정보·편향 검사
- 사람 검토 또는 독립 기준과 대조
- 합성비율별 성능과 부작용 비교
- 실제 Hidden Set에서 효과가 없으면 폐기

### 5.7 Continuous Learning

운영데이터를 즉시 자동학습시키지 않는다. 다음 폐쇄루프를 따른다.

```text
Observe
→ Candidate Feedback
→ Privacy·Quality Filter
→ Human/Policy Approval
→ Dataset Revision
→ Train in Isolation
→ Independent Evaluation
→ Deployment Approval
→ Canary
→ Monitor
→ Promote or Rollback
```

온라인 무승인 자기학습은 기본적으로 금지한다.

---

## 6. 개선·학습·검증의 책임 분리

ONSure가 직접 개선하고 다시 검증하므로 자기검증 편향을 구조적으로 차단해야 한다.

### 6.1 필수 역할

- **Understanding Actor**: 프로그램과 기준선을 학습
- **Diagnosis Actor**: Finding와 RCA 생성
- **Improvement/Training Actor**: Patch·Prompt·RAG·Model 후보 생성
- **Independent Evaluation Actor**: 고정 평가셋으로 후보 비교
- **Verification Actor**: 실제 요구사항·정책·보안·동작 판정
- **Approval Actor**: 고객 또는 권한 있는 승인자
- **Evidence Actor**: 모든 입력·환경·결과·Hash·Receipt 봉인

동일 Actor가 후보 생성과 최종판정을 모두 수행한 결과는 Final Evidence로 승격하지 않는다.

### 6.2 비교 계약

Before/After 비교에는 다음이 동일해야 한다.

- Source Baseline 또는 명시된 Patch 범위
- Dataset·Evaluation Set Revision
- Runtime·Dependency·Hardware Class
- Model Provider·Base Model Version
- Sampling·Timeout·Retry 정책
- Metric 정의와 Threshold

변경이 필요한 항목은 비교변수로 명시하고 영향도를 별도 판정한다.

### 6.3 최종판정

- `IMPROVED`: 목표지표 달성, Critical Regression 없음
- `PARTIALLY_IMPROVED`: 일부 지표 개선, 잔여위험 승인 필요
- `NO_MATERIAL_IMPROVEMENT`: 통계·업무적으로 의미 있는 개선 없음
- `REGRESSED`: 중요지표 또는 안전성이 악화됨
- `INCONCLUSIVE`: 데이터·환경·표본 부족
- `BLOCKED`: 권리·보안·실행환경·증거 문제

`REGRESSED`, `INCONCLUSIVE`, `BLOCKED` 결과를 성공으로 표시하지 않는다.

---

## 7. 상품과 수익모델

### 7.1 상품 사다리

#### A. Program Learn

- 기존 프로그램 이해
- Program/Behavior Profile
- AI Component Map
- 실행·학습 가능성 확인

#### B. Learn & Verify

- 요구사항·코드·AI 행동 검증
- Finding·RCA
- 학습 필요성 판정
- 개선·학습 우선순위와 견적

#### C. Auto-Learn & Improve

- Dataset·RAG·Prompt·Agent·Model 개선
- 코드·정책·실행환경 보완
- Candidate 생성과 비용관리

#### D. Re-verify & Prove

- 독립평가
- 회귀·보안·적대시험
- Before/After Evidence
- Release/Residual Risk Report

#### E. Continuous Improvement & Learning

- 변경 감지와 증분 Program Learning
- 운영 품질·Drift 감시
- 승인형 재학습
- Release별 재검증
- Failure/Improvement Memory

### 7.2 초기 고객용 결과중심 상품명

- AI 생성 코드 상용화
- AI 서비스 출시 준비
- 기존 AI 프로그램 인수·상태진단
- AI 오류 수정·안정화
- RAG 정확도 개선
- AI 응답 품질·속도·비용 최적화
- 객체인식·예측모델 재학습
- 외주 AI 프로그램 인수검수·개선

내부 엔진명보다 고객이 얻는 결과를 전면에 표시한다.

### 7.3 과금축

#### Web Case

```text
기본 Case
+ Program Understanding Unit
+ Dataset Intake/Quality Unit
+ Evaluation Scenario
+ Training Run·GPU·Model API
+ RAG/Prompt/Agent/Model Improvement Unit
+ Reverification
+ 전문가 검토
+ 긴급·격리·Offline 환경
```

#### VS Code/Team

```text
Plan Base
+ Seat
+ Active System·Program Capacity
+ Monthly ONSure Credit
+ Training Credit
+ Model/Prompt/RAG Version Capacity
+ Concurrent Run·GPU·Storage
+ Support·SLA
```

### 7.4 가격 가설

다음은 시장검증을 위한 가설이며 가격표로 확정하지 않는다.

| 초기 상품 | 가격 가설 | 비고 |
|---|---:|---|
| Preflight | 무료~10만 원 | 적합성·범위·예상원가만 제시 |
| Program Learn/상태진단 | 30만~100만 원 | 소형 프로그램 기준 |
| Learn & Verify | 50만~300만 원 | 실행환경·검증팩에 따라 변동 |
| Auto-Learn & Improve | 200만~1,500만 원 이상 | 데이터·GPU·개선범위별 견적 |
| Continuous Developer | 월 3만~10만 원+Credit | 실제 원가 Benchmark 후 확정 |
| Team/Enterprise | 계약형 | Seat·Program·Training·SLA |

고객에게는 확정견적과 포함범위를 제시하고, 내부적으로 Unit/Credit으로 원가를 통제한다. 저가 무제한 학습·검증은 제공하지 않는다.

### 7.5 단위경제

Case별로 다음을 측정한다.

- 매출
- Model API·GPU·Sandbox·Storage 원가
- 자동화 실행시간과 실패 재시도
- 전문가 개입시간
- 고객 환경 복구·지원시간
- 결제·플랫폼·판매 수수료
- Warranty 재작업
- Contribution Margin

초기 목표 가설:

- 표준 Web Case 매출총이익률 60% 이상
- ONSure 플랫폼 장애 재실행 비용은 고객에게 전가하지 않음
- 견적 오차가 큰 Case는 고정가가 아니라 2단계 진단·본계약으로 분리
- GPU·모델비용은 Reserve 후 실행하고 미사용분 Release

### 7.6 시장진입

#### 1단계: Service-assisted

- 외주 플랫폼의 AI 오류 수정·안정화·인수 요청을 직접 수행
- ONSure 자동화와 전문가 검토를 함께 사용
- 실제 Failure, Dataset, Evaluation, 원가 데이터를 확보

#### 2단계: 표준 Web Case

- Preflight 자동화
- Program Learn, AI Launch Check, RAG/Prompt Improve를 표준상품화
- 결과보고서와 Before/After Evidence를 동일 양식으로 납품

#### 3단계: Developer/Team

- 반복고객을 VS Code와 CI/CD 구독으로 전환
- 운영 Feedback·Drift·Release를 지속관리

#### 4단계: Platform Partnership

- 외주개발 플랫폼·개발사·투자사·지원사업 기관과 독립 검수·개선 연계
- `개발완료 → ONSure 검증·개선 → 재검증 → 인수/잔금` 절차를 제공

### 7.7 사업화 검증 Gate

초기 90일 또는 유료 Case 20건 중 먼저 도달한 시점에 평가한다.

- 실제 유료 Case 20건
- 30% 이상이 Improve/Auto-Learn 후속구매
- 15% 이상이 반복검증·지속학습 의사 표시
- 표준 Case 매출총이익률 60% 이상
- 고객이 인정한 Critical/High Finding 발견
- 개선 후 합의 Metric 달성률
- 납기 준수율
- 데이터·보안 사고 0건
- 과장된 품질보장·허위 PASS 0건

미달하면 기능을 확대하지 않고 대상고객·상품·원가·자동화 수준을 재설계한다.

---

## 8. 라이선스·계약·데이터 정책 확장

### 8.1 신규 ServiceType·Option 제안

```text
ServiceType:
- PROGRAM_LEARN
- LEARN_VERIFY
- TARGET_LEARNING_READINESS
- RAG_IMPROVE
- PROMPT_OPTIMIZE
- AGENT_IMPROVE
- MODEL_TRAIN
- ML_RETRAIN
- IMPROVE_REVERIFY
- CONTINUOUS_LEARNING

Option:
- GPU_TRAINING
- PRIVATE_MODEL_PROVIDER
- LOCAL_TRAINING
- OFFLINE_TRAINING
- EXPERT_DATA_REVIEW
- EXPERT_MODEL_REVIEW
- PRIORITY_EXECUTION
```

### 8.2 Web Case License 필드 확장

- DatasetBinding과 DatasetDigest
- BaseModelId·Provider·Version·License
- TrainingType
- TrainingUnitLimit
- GPUMinute/ComputeLimit
- ModelAPIBudget
- TrainingRunLimit
- CandidateLimit
- EvaluationSetBinding
- EvaluationRunLimit
- Metric·Threshold
- Model/Prompt/RAG/Policy Version Limit
- DeploymentPermission
- ContinuousLearningPermission
- DataRetention·DeletionPolicy

### 8.3 Subscription Entitlement 확장

- MonthlyTrainingCredit
- DatasetCapacity
- ModelRegistryCapacity
- CandidateVersionCapacity
- ConcurrentTrainingRun
- GPUClass/Provider
- EvaluationPack
- DriftMonitoring
- AutoRetrainCandidateGeneration
- HumanApprovalRequired
- DeploymentEnvironment

### 8.4 데이터와 산출물 소유권

계약에서 다음을 분리한다.

- 고객 원본 Source·Data·Document·Log
- ONSure가 생성한 Program Profile
- 정제·라벨링된 Dataset
- 합성데이터
- Prompt·Retriever·Agent Policy
- Fine-tuned Model·Adapter
- 범용화된 익명 Failure/Improvement Pattern
- Evidence·Report·Receipt

고객 데이터와 고객 전용 학습산출물은 기본적으로 고객 계약범위에 귀속한다. ONSure의 범용 패턴 재사용은 별도 동의와 비식별·권리검증 없이 허용하지 않는다.

### 8.5 보증과 책임

- Result Warranty는 고정 Baseline·Dataset·Environment·Model Version에만 적용
- 고객의 신규개발·데이터변경·Provider 모델변경은 신규평가 대상
- 확률적 모델에 100% 정확도를 보장하지 않음
- 합의된 Metric·Threshold와 검증범위를 명시
- 자동배포는 별도 권한과 승인정책이 있을 때만 수행
- 잔여위험과 UNKNOWN을 숨기지 않음

---

## 9. 기술 아키텍처

### 9.1 논리구조

```text
Web / VS Code / API
→ Intake & Preflight
→ OLearning
→ OReview / OVerification
→ Finding & RCA
→ Learning Decision
→ ODataset
→ OTargetLearning
→ Candidate Registry
→ Independent OEvaluation
→ OVerification
→ Approval
→ OModelRegistry / OGit / Deployment
→ Monitoring & Feedback
→ OEvidence / ODelivery
```

OLicense는 각 단계의 Entitlement, Credit, Case Binding, Usage를 관리한다.

### 9.2 Control Plane과 Data Plane

#### Control Plane

- Organization·Identity·License
- Case·Subscription·Approval
- Orchestration·Policy
- Metadata·Lineage·Evidence Index
- Usage·Billing·Audit

#### Customer Data/Training Plane

- Source·Dataset·Knowledge Base
- Sandbox·Build·Test
- Embedding·Index
- Training·Evaluation
- Model Artifact·Adapter

고객 Source와 Dataset을 SaaS로 전송하지 않는 Local/Hybrid 모드를 제공한다. Enterprise는 On-premises와 Air-gapped Training을 지원할 수 있다.

### 9.3 Training Run DAG

```text
Entitlement Validate
→ Data Rights Gate
→ Dataset Snapshot
→ Secret·PII·Poisoning Scan
→ Split/Hidden Set Lock
→ Baseline Evaluation
→ Credit Reserve
→ Candidate Training
→ Artifact Scan
→ Independent Evaluation
→ Security·Regression
→ Customer Approval
→ Credit Commit/Release
→ Registry
→ Canary/Deployment
→ Evidence Seal
```

어느 Gate라도 실패하면 후속단계를 차단한다.

### 9.4 데이터 엔터티

- LearningReadinessAssessment
- Dataset
- DatasetRevision
- DatasetManifest
- DataItem
- Label
- Consent/RightRecord
- DataQualityFinding
- TrainingPlan
- TrainingRun
- TrainingJob
- HyperparameterSet
- BaseModel
- ModelArtifact
- AdapterArtifact
- PromptVersion
- RetrieverVersion
- AgentPolicyVersion
- EvaluationSet
- EvaluationRun
- MetricResult
- CandidateDecision
- Deployment
- CanaryRun
- DriftSignal
- FeedbackCandidate
- RetrainingRequest
- RollbackReceipt

모든 Artifact는 Content Hash와 부모 Lineage를 가진다.

### 9.5 API 제안

```text
POST /v1/learning-readiness-assessments
POST /v1/datasets
POST /v1/datasets/{id}/revisions
POST /v1/datasets/{id}/quality-runs
POST /v1/training-plans
POST /v1/training-runs
POST /v1/training-runs/{id}/cancel
POST /v1/evaluation-sets
POST /v1/evaluation-runs
POST /v1/candidates/{id}/approve
POST /v1/candidates/{id}/reject
POST /v1/deployments
POST /v1/deployments/{id}/rollback
POST /v1/feedback-candidates
POST /v1/retraining-requests
GET  /v1/model-registry/artifacts
GET  /v1/evidence/training/{runId}
```

모든 Write는 Idempotency Key, Organization, Case/Subscription, Baseline, License Context를 요구한다.

### 9.6 Event 제안

- LearningReadinessCompleted
- DatasetRevisionLocked
- DatasetQualityFailed
- TrainingPlanApproved
- TrainingStarted/Completed/Failed/Cancelled
- CandidateCreated
- EvaluationCompleted
- CandidateApproved/Rejected
- DeploymentStarted/Promoted/RolledBack
- DriftDetected
- RetrainingRequested
- TrainingEvidenceSealed

---

## 10. 보안·개인정보·AI 안전

### 10.1 데이터 보안

- 최소권한 Connector
- Tenant·Case·Dataset 격리
- 전송·저장 암호화
- Secret·PII 탐지·마스킹
- Training Worker 임시 Credential
- Network Egress Allowlist
- 원본·중간·결과 Artifact별 보존기간
- 삭제 후 Deletion Receipt

### 10.2 학습데이터 위험

- Data Poisoning
- Label 오류
- Train/Test Leakage
- Membership/Prompt Leakage
- 저작권·개인정보·영업비밀 침해
- 특정 집단 편향
- 합성데이터 누적에 따른 품질저하
- 운영 Feedback 조작

위 위험은 별도 Verification Pack으로 제공하고 중요 위험이 남으면 자동학습과 배포를 차단한다.

### 10.3 Model Supply Chain

- Base Model 출처·License·Digest
- Provider Endpoint와 Version
- Adapter·Weight·Tokenizer·Config Hash
- Dependency·Container·SBOM
- Training Code Commit
- Dataset Revision
- Build/Training Provenance
- Artifact Signature

출처나 License를 확인할 수 없는 Model Artifact는 상용 납품하지 않는다.

### 10.4 사람 승인

다음은 기본적으로 사람 승인을 요구한다.

- 고객데이터의 학습 사용
- Fine-tuning 시작
- 고비용 GPU/Model Run
- 고위험 Agent Tool 권한변경
- 운영 Model·Prompt·RAG 교체
- Continuous Learning Dataset 편입
- Rollback 불가능한 변경

---

## 11. 단계별 개발전략

### Phase 0. 계약과 기준선

목표:

- 두 Learning의 용어·API·데이터·UI 분리
- Finding → Learning Decision → Training → Evaluation 계약
- Dataset/Model/Prompt/RAG/Agent Lineage
- OLicense Training Entitlement·Credit
- Data Rights·Retention·Deletion

완료조건:

- Schema와 State Machine 승인
- 위조·초과사용·중복차감·기준선변경 적대시험 설계
- 최소 10개 대표 Fixture 정의

### Phase 1. RAG·Prompt Auto-Learning MVP

선정 이유:

- 고객수요가 많고 결과를 비교적 빠르게 측정 가능
- Foundation Model 학습보다 GPU 원가와 법적위험이 낮음
- Program Learning·Verification과 결합효과가 큼

구현:

- Knowledge Intake와 Dataset Manifest
- Chunk/Embedding/Retriever/Reranker 후보
- Prompt 후보실험
- 고정/Hidden/Adversarial Evaluation Set
- Before/After, 비용, Latency, Groundedness
- 승인·Version·Rollback

출시후보 조건:

- 실제 RAG 프로그램 3종 이상
- 동일 기준 Full-Chain 연속 2회 PASS
- 학습데이터 오염·근거 없는 답변 차단
- Critical/High 미해결 0

### Phase 2. Agent·Tool Improvement

구현:

- Trace Capture
- Tool Schema와 Policy Validator
- Planner/Router 후보
- Loop·권한·중단조건 적대시험
- 사람승인 Gate

출시후보 조건:

- 읽기·쓰기·외부전송 Tool별 권한시험
- 무단실행 0
- 실패 후 Rollback·Compensation
- 생성 Actor와 판정 Actor 분리

### Phase 3. Provider Fine-tuning·LoRA

구현:

- Dataset Split·Quality·Rights
- Provider Fine-tuning Adapter
- Training Credit Reserve/Commit
- Model Registry
- Baseline/Candidate/Hidden Evaluation
- Canary·Rollback

출시후보 조건:

- 최소 2개 Provider 또는 1개 Provider+Local Adapter
- 재현 가능한 Training Receipt
- 비용상한 초과 차단
- 성능개선 없는 Candidate 배포 차단

### Phase 4. 예측·분류·추천·비전

구현:

- Tabular/Image Dataset Quality
- Label·Bias·Leakage
- Training Pipeline Adapter
- 환경별 시험
- Business Cost Metric

제품군마다 별도 Capability Pack과 Acceptance를 둔다. 하나의 범용 정확도 기준으로 통합하지 않는다.

### Phase 5. Continuous Learning

구현:

- Feedback Candidate
- Drift Monitoring
- 승인형 Dataset Revision
- Scheduled/Triggered Retraining
- Champion/Challenger
- Canary·Promotion·Rollback

초기에는 자동으로 Candidate까지만 생성하고 운영 승격은 사람승인을 기본값으로 한다.

### Phase 6. Team·Enterprise

- Shared Dataset/Model Registry
- RBAC·SSO·Audit
- On-premises·Air-gapped Training
- Private Model Gateway
- 규제산업용 Data/Model Policy Pack
- 전문 검토·SLA

---

## 12. 개발 Lane과 우선 Backlog

### L0 Contract

- 용어·상태·식별자
- Dataset/Model/Prompt/RAG/Agent Baseline
- Metric·Threshold·Decision
- Lineage·Receipt

### L1 OLicense

- Training Product Catalog
- Training Entitlement
- GPU/Model API/Credit Meter
- Reserve/Commit/Release
- Offline Training Snapshot

### L2 ODataset

- Intake·Manifest·Version
- Rights·PII·Secret
- Quality·Label·Split
- Deletion

### L3 OTargetLearning

- Learning Decision
- Training Plan
- Provider/Local Adapter
- Run/Cancel/Retry/Checkpoint

### L4 OEvaluation

- Baseline/Candidate
- Fixed/Hidden/Adversarial Set
- Metric·Confidence·Business Cost
- Independent Decision

### L5 Registry·Deployment

- Model/Adapter/Prompt/Retriever/Policy Registry
- Approval
- Canary
- Promotion·Rollback

### L6 Experience

- Web Learning Case
- VS Code Learning Plan·Diff·Approval
- Credit/Cost Preview
- Before/After Dashboard
- Evidence Export

### L7 Security·Operations

- Tenant/Data Isolation
- Poisoning·Leakage
- Provider Failure
- GPU Quota
- Audit·Support·Dispute

---

## 13. 시험·검증·수용 기준

### 13.1 공통 Full-Chain

```text
Intake
→ Program Learn
→ Verify
→ Finding/RCA
→ Learning Readiness
→ Dataset Lock
→ Baseline Evaluate
→ Train/Improve
→ Independent Evaluate
→ Re-verify
→ Approve
→ Deploy/Deliver
→ Evidence Seal
→ Rollback Test
```

### 13.2 필수 정상 시나리오

- RAG 검색품질 개선
- Prompt 정확성·Token 개선
- Agent Tool 선택정책 개선
- Fine-tuning Candidate 개선
- 이미지/분류 모델 재학습
- 운영 Feedback 기반 Candidate 생성

### 13.3 필수 부정·적대 시나리오

- 권리 없는 데이터
- PII·Secret 포함
- 오염·중복·오류라벨
- Train/Test Leakage
- 평가셋 과적합
- Base Model Version 변경
- Provider 장애·Rate Limit
- GPU 비용한도 초과
- 악성 Model Artifact
- Prompt/Tool 공격
- Clock rollback·Offline License 위조
- 중복 Webhook·Credit 이중차감
- 승인 없는 운영배포
- 성능은 개선됐지만 안전성이 악화
- 평균은 개선됐지만 핵심 Class가 악화
- Evidence 일부 변조

### 13.4 Release Gate

- Traceability 100%
- Dataset·Model·Prompt·RAG·Agent Lineage 100%
- Critical/High 미해결 0
- 동일정책 Full-Chain 연속 2회 PASS
- 생성·학습 Actor와 최종판정 Actor 분리
- Credit 이중차감 0
- 승인 없는 배포 0
- Rollback 실제 성공
- 고객데이터 삭제 검증
- 독립 기술검토와 Blind Review

문서·코드·단위시험·Demo만으로 Final PASS를 선언하지 않는다.

---

## 14. 핵심 KPI

### 고객가치

- Critical/High Finding 발견률
- 유효 Finding 비율과 False Positive
- 목표 Metric 달성률
- Before/After 개선폭
- 출시·인수·장애복구까지 걸린 시간
- Patch/Prompt/RAG/Model Candidate 채택률
- 재발·회귀 차단률

### 자동학습 품질

- Dataset Quality Gate 통과율
- Baseline 재현률
- Candidate 중 실제 개선 비율
- Hidden/Adversarial Set 성능
- Regression/Rejected Candidate 비율
- Rollback 성공률
- Drift 탐지 후 대응시간

### 사업

- Preflight → 유료 Case 전환율
- Learn & Verify → Improve/Train 전환율
- Case당 평균매출·원가·마진
- 전문가 개입시간
- Web → VS Code/Continuous 전환율
- 반복구매율·월간 이탈률
- Credit 소진·초과구매
- Warranty 재작업률

### 신뢰

- 허위 PASS 0
- 승인 없는 배포 0
- 데이터·보안 사고 0
- Evidence 계보 누락 0
- 삭제기한 위반 0

---

## 15. 주요 위험과 대응

| 위험 | 영향 | 대응 |
|---|---|---|
| 제품범위 과대 | 개발 지연·품질저하 | RAG/Prompt MVP부터 단계 확장 |
| 일반 외주개발로 변질 | 차별성·마진 상실 | 검증 Finding에 결속된 개선만 수행 |
| 학습비용 초과 | Case 손실 | Preflight, Budget, Credit Reserve, Hard Stop |
| 데이터 권리침해 | 법적·신뢰 위험 | Rights Gate, Consent, Lineage, 삭제 |
| 잘못된 자동학습 | 품질·안전 악화 | 독립평가, Hidden Set, 승인, Rollback |
| 자기검증 편향 | Evidence 신뢰 상실 | Actor 분리, 고정 Baseline, Before/After |
| Provider 종속 | 가격·성능·정책 변화 | Provider Adapter, Version Lock, BYO 옵션 |
| 고객 환경 재현 실패 | 납기·분쟁 | Preflight, Local Runtime, Environment Capture |
| B2C 가격저항 | 전환율 저하 | 1회 결과중심 Case, 단계견적, 개선가치 제시 |
| 낮은 자동화율 | 전문가 비용 증가 | 반복 Failure를 Fixture·Policy Pack으로 전환 |

---

## 16. 의사결정과 제품 경계

### 16.1 포함

- 등록된 프로그램을 이해하기 위한 Program Learning
- 검증된 Finding의 코드·정책·데이터·RAG·Prompt·Agent·Model 개선
- 제한된 Fine-tuning·Adapter·재학습
- 독립평가와 재검증
- Version·Evidence·Rollback
- 승인형 Continuous Learning

### 16.2 초기 제외

- Foundation Model 사전학습
- 목적·평가기준 없는 연구형 모델개발
- 권리 불명 데이터의 학습
- 승인 없는 운영 자기학습
- Finding과 무관한 일반 신규 시스템 개발
- 결과 정확도 100% 보장
- 무제한 GPU·Model API·Storage

### 16.3 별도 계약

- 대규모 데이터 라벨링
- 전용 GPU Cluster
- 폐쇄망·Air-gapped Training
- 규제기관 제출용 전문검토
- 대규모 신규 기능개발
- 산업별 Model/RAG Policy Pack

---

## 17. 최종 사업 방향

ONSure의 핵심 메시지는 다음과 같다.

> ONSure는 프로그램을 먼저 학습해 현재 상태를 이해하고, 실제 문제와 학습 필요성을 판정하며, 코드·데이터·RAG·Prompt·Agent·AI Model을 자동으로 개선·학습한 뒤, 개선 효과를 독립적으로 입증한다.

사업구조는 다음과 같이 운영한다.

- **Program Learning은 기반자산**이다.
- **Verify는 고객의 문제와 개선기회를 증명하는 진입상품**이다.
- **Improve와 Target AI Auto-Learning은 핵심 프로젝트 매출**이다.
- **Re-verify와 Evidence는 신뢰와 차별성**이다.
- **Continuous Learning은 반복 구독매출**이다.
- **Failure/Improvement Memory는 시간이 지날수록 높아지는 제품자산**이다.

초기 성공의 기준은 많은 기능을 구현하는 것이 아니라, 실제 AI 프로그램을 대상으로 `Learn → Verify → Improve/Train → Independent Re-verify → Prove`를 반복 가능하고 수익성 있게 완료하는 것이다.

---

## 18. 즉시 실행항목

1. `Program Understanding Learning`과 `Target AI Auto-Learning` 용어를 전체 문서·UI·API에 분리한다.
2. `OTargetLearning`, `ODataset`, `OEvaluation`, `OModelRegistry` 책임과 인터페이스를 확정한다.
3. OLicense에 Training Entitlement·Credit·Dataset·Model Version 한도를 추가한다.
4. RAG·Prompt Auto-Learning MVP의 Fixture 3종과 Full-Chain을 먼저 구현한다.
5. 외부 AI 프로그램 유료 Case를 확보해 원가·전환율·개선효과를 측정한다.
6. 개선 생성과 최종 재검증 Actor를 분리하고 Evidence 계약에 결속한다.
7. Fine-tuning과 Continuous Learning은 RAG·Prompt MVP가 출시 Gate를 통과한 뒤 시작한다.
8. 모든 결과는 실제 E2E·독립검토·Evidence 봉인 전까지 `NON_FINAL`로 유지한다.
