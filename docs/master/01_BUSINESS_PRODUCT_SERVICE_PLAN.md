# ONSure 사업·제품·서비스 계획서

## 1. 사업 목적
AI를 활용한 소프트웨어 개발은 생산성을 높이지만 요구사항 누락, 정책 위반, 보안 취약점, 설명 불가능한 변경, 불충분한 테스트, AI 구성 오류가 함께 증가한다. ONSure는 결과물이 실행된다는 사실과 상용 제품으로 신뢰할 수 있다는 사실 사이의 간극을 줄이는 독립 제품이다.

## 2. 고객 문제
- AI가 생성한 코드를 개발자가 완전히 이해하지 못한다.
- 요구사항과 코드 사이의 추적성이 끊어진다.
- Prompt, RAG, Agent, Tool 변경은 일반 코드리뷰만으로 검증하기 어렵다.
- 정적 분석 도구는 실제 업무 시나리오 충족 여부를 증명하지 못한다.
- 수정이 새로운 결함을 만들지만 회귀검증이 불충분하다.
- 감사와 납품에 필요한 재현 가능한 Evidence가 남지 않는다.
- 기존 AI 서비스(RAG·챗봇·문서자동화·추천·이미지 인식 등)가 느리거나 부정확한데, 원인이 코드 결함인지 RAG 문서 품질인지 Prompt 설계인지 Model 자체 한계인지 구분할 방법이 없다(`docs/v2/09` §1.3에서 흡수).
- 원인을 구분해도 RAG·Prompt·Agent·Model을 실제로 재학습·개선할 수단이 코드 수정과 분리되어 있지 않다.

## 3. 목표 고객
- AI Coding을 도입한 일반 기업 개발조직
- AI Agent·RAG·LLM 서비스를 구축하는 기업
- 금융·공공·의료 등 규제 산업
- SI·컨설팅·품질관리 회사
- AI로 제품을 만든 비전문 개발자와 스타트업
- 고객 소프트웨어를 인수·검수해야 하는 발주기관

### 3-1. 목표 고객과 계약 `primary_users`의 관계

실제 `contracts/product-scope.v1.json`의 `primary_users`는 NON_DEVELOPER_AI_BUILDERS/SOFTWARE_DEVELOPERS/PRODUCT_TEAMS/ENTERPRISE_ASSURANCE_TEAMS 4종만 정의한다. 위 6개 세그먼트는 영업·마케팅 목적의 세분화 분류(`DESIGN_ONLY`)이며, 아래는 현재 파악한 최선의 대응 관계다. 다만 이 매핑은 완전한 1:1이 아니며, 표 아래에 남는 미해결 지점을 함께 명시한다.

| 01의 목표 고객 세그먼트 | 계약 `primary_users` 대응 | 비고 |
|---|---|---|
| AI Coding을 도입한 일반 기업 개발조직 | SOFTWARE_DEVELOPERS | 직접 대응 |
| AI Agent·RAG·LLM 서비스를 구축하는 기업 | SOFTWARE_DEVELOPERS 또는 PRODUCT_TEAMS | 불명확 — 아래 미해결 지점 2 참고 |
| 금융·공공·의료 등 규제 산업 | 대응 없음 | 아래 미해결 지점 1 참고 — role이 아니라 industry vertical |
| SI·컨설팅·품질관리 회사 | ENTERPRISE_ASSURANCE_TEAMS | 발주기관 세그먼트와 동일 계약값으로 수렴 |
| AI로 제품을 만든 비전문 개발자와 스타트업 | NON_DEVELOPER_AI_BUILDERS | 직접 대응 |
| 고객 소프트웨어를 인수·검수해야 하는 발주기관 | ENTERPRISE_ASSURANCE_TEAMS | SI·품질관리 세그먼트와 동일 계약값으로 수렴 |

**미해결 지점 (깨끗한 매핑이 아직 아님, 사업 판단 필요):**
1. "금융·공공·의료 등 규제 산업"은 사용자 역할(role)이 아니라 산업(industry vertical)이다. 규제 산업에 속한 조직도 내부적으로는 SOFTWARE_DEVELOPERS·NON_DEVELOPER_AI_BUILDERS·ENTERPRISE_ASSURANCE_TEAMS 중 무엇이든 될 수 있어 `primary_users`와 같은 축이 아니다.
2. "AI Agent·RAG·LLM 서비스를 구축하는 기업"은 SOFTWARE_DEVELOPERS와 PRODUCT_TEAMS 중 어느 쪽인지 계약이 구분하지 않는다. 같은 회사 안에 개발자와 제품 책임자가 함께 있을 수 있어 세그먼트 대 enum이 1:1이 아니다.
3. "SI·컨설팅·품질관리 회사"(검증을 위탁받는 공급자)와 "발주기관"(검증을 위탁하는 발주자)은 서로 다른 사업 관계이지만 계약상으로는 둘 다 ENTERPRISE_ASSURANCE_TEAMS로 수렴한다. 계약이 "누가 검증을 요청했는가"라는 관계를 구분하지 않기 때문이다.

**결론:** 01의 6개 세그먼트는 계약의 `primary_users`(사용자 역할 축)와 산업·사업관계 축을 함께 담은 GTM(영업) 분류이며, 4종 enum으로 깨끗하게 축소되지 않는다. `primary_users` enum을 01의 세분화 수준까지 확장할지(예: industry·사업관계를 별도 필드로 분리), 아니면 01의 세그먼트를 계약이 구분하는 4종 역할 축 기준으로 재정리할지는 아직 결정되지 않은 사업 판단 사항으로 남긴다.

### 3-2. 검증 대상 유형 (Target Types)

계약 `product-scope.v1.json`의 `supported_target_types`는 AI_APPLICATION, AGENTIC_SYSTEM, GENERAL_SOFTWARE, WEB_APPLICATION, API_SERVICE, DESKTOP_APPLICATION, MOBILE_APPLICATION, AUTOMATION_WORKFLOW 8종을 정의한다. 01의 검증 대상 범위는 이 8종 전체를 포함한다 — Desktop Application·Mobile Application·Automation Workflow도 Web Application·API Service·AI Application·Agentic System·General Software와 동일하게 Learn·Verify 상품의 검증 대상이며, 이 문서가 특정 대상 유형만을 다룬다고 해석해서는 안 된다.

### 3-3. 엔진 배포 형태와 상품 채널

`delivery_modes`(STANDALONE_DESKTOP_OR_SERVER, LOCAL_CLI, PRIVATE_NETWORK_SERVICE, EMBEDDED_VALIDATION_MODULE, TARGET_SIDE_VALIDATION_AGENT)는 검증 엔진 자체의 배포 형태를 말하며, 이 문서의 "Web One-time / VS Code 구독" 같은 판매채널·상품 구조와는 다른 계층이다. 엔진 배포 형태와 상품/과금 구조를 같은 표로 섞어 쓰지 않도록 주의해야 한다 — 엔진은 로컬/독립 실행이 기본이고, Web은 그 위에 얹히는 상거래 채널(ServiceCase)이다.

## 4. 가치 제안
### 경영진
AI 개발의 속도를 유지하면서 품질·보안·감사 위험을 수치와 증거로 관리한다.

### 개발조직
Repository를 이해한 상태에서 요구사항, 설계, 코드, AI 구성, 테스트를 연결해 리뷰하고 수정한다.

### 품질·감사조직
판정 근거, 실행환경, 정책 버전, 입력 Hash, 결과 Hash가 결속된 Evidence Pack을 확보한다.

### 비전문 개발자
AI가 만든 코드의 위험을 찾고, Finding에서 출발한 제한적 자동 개선을 받을 수 있다.

## 5. 수익모델
### Web One-time
- Learn: 학습량 기반 1회 과금
- Verify: 검증 범위·검증팩·실행량 기반 1회 과금
- Learn & Verify: 학습과 맞춤 검증 결합
- Improve & Re-verify: 고객이 선택한 Finding의 개선량과 재검증량 기반 후속 과금
- 전문가 검토, 긴급처리, 격리환경, 상세보고서 옵션

### VS Code Subscription
- Plan 기본료
- Seat
- Active System 및 Program Capacity
- 월간 ONSure Credit
- 초과 Credit
- Enterprise 보안·온프레미스·전용 지원

## 5-1. Preflight 판정 (docs/v2/03에서 흡수)
`docs/v2/03_WEB_ONE_TIME_SERVICE_POLICY.md`(§4)가 정의한 Preflight 판정 결과를 채택한다.

계정·조직 확인 → 대상 연결 또는 업로드 → 악성코드·비밀정보·권한 사전검사 → System·Program 경계 판정 → 학습량·실행가능성 산정 → 서비스 적합성 판정 → 견적·기간·제외범위 제시

판정 결과:
- READY: 정액 또는 확정견적으로 진행
- NEEDS_BASELINE: Verify 전 기준 작성(Learn) 필요
- RECOMMEND_LEARN_VERIFY: 자료 부족으로 통합서비스 권장
- CUSTOM_QUOTE: 대규모·고위험·폐쇄환경
- REJECT/HOLD: 불법·권한불명·악성코드·실행불가

소규모 확정견적은 전액 선결제, 불확실한 대규모는 사전진단비와 본 서비스 차액의 2단계 결제를 지원한다. 고비용 실행은 예상 사용량을 OLicense에서 미리 예약(CreditReservation)한다.

## 6. Web 상품 정의
### Learn
입력: Source, Configuration, Prompt, RAG, Tool, Test, Document, 선택 로그
처리: 구조·의존성·배포·AI 구성·행동 특성 학습
산출물: Program Profile, Architecture Map, AI Component Map, Dependency Inventory, Unknown/Conflict List
제외: 결함 판정과 자동 수정

### Verify
입력: 고정 Baseline, 요구사항, 정책, 검증팩, 고객 제공 ScopeManifest(대상 Component/Module/AI 구성 목록)
처리: 정적·동적·시나리오·적대·회귀 검증
산출물: Finding, Severity, RCA 후보, Evidence, Verification Report
제외: Program Profile 납품과 자동 Patch

Verify 단독 상품은 OLearning의 전체 Program Profile을 생성하지 않지만, 시나리오 생성과 요구사항 추적에 필요한 최소 구조 정보(대상 Component, Dependency, AI 구성)는 고객이 ScopeManifest로 직접 제공해야 한다. ScopeManifest가 불충분하면 Preflight 단계에서 Unknown/Conflict로 표시하고 해당 범위는 INCONCLUSIVE로 처리하며 Learn 추가 구매를 안내한다.

### Learn & Verify
Program Profile을 만든 뒤 해당 구조와 위험에 맞게 검증 시나리오를 생성한다. 일반 고객의 대표 상품으로 둔다.

### Improve & Re-verify
검증된 Finding 중 고객이 승인한 항목만 대상으로 RCA, Patch, Regression, Before/After Evidence를 제공한다.

### Train & Re-verify (OTraining, `docs/v2/09`에서 흡수)
입력: 검증된 Finding 또는 승인된 개선 목표(정확도·안정성·속도·비용), 학습 데이터, 평가 데이터셋
처리: RCA로 원인이 RAG·Prompt·Agent 정책·Model 중 어디인지 확인된 항목에 한해 재학습(Training Plan→Training Run→독립 재검증)
산출물: EvaluationReport(Before/After), 승인된 ModelVersion/RAGIndexVersion/PromptVersion/AgentPolicyVersion, DeploymentApproval
제외: Improve와 동일한 프로그램 코드 Patch(별도 상품), GPU 대규모 Model Fine-tuning은 1단계 출시 범위 밖(§11-2 참조)

## 6-1. 환불 정책
- LEARNING/VERIFYING 실행 시작 전 취소: 전액 환불
- 실행 시작 후 취소: 실제 소비된 Learning Unit/Credit에 해당하는 금액을 제외하고 잔액 환불
- ONSure 내부 오류로 인한 재실행·실패는 애초에 과금하지 않으므로 환불 대상에서 제외한다(FR-COM-006과 동일 원칙)
- VS Code 구독은 해지 시 당월 잔여 기간 비례 환불 없음이 기본이며, Annual Plan 중도 해지는 계약서에 별도 명시된 경우에만 예외 적용
- 환불 승인은 Payment Provider의 RefundCompleted 이벤트로 확정하고 License는 즉시 SUSPENDED로 전이한다

## 7. 학습량 정책
상품 단계는 늘리지 않고 Learn 하나를 유지한다. 내부적으로 Learning Unit을 산정한다.

Learning Unit 산정요소:
- 분석 파일과 코드 규모
- 언어·프레임워크 수
- Repository와 독립 배포단위
- Prompt·Agent·Tool 수
- RAG 문서·인덱스 규모
- 테스트·로그·설정 규모
- 외부 연계 수
- 동적 구조와 복잡도

### 산정 공식(초안)
LearningUnit = w1·log(TotalFiles+1) + w2·(AnalyzedLOC/1000) + w3·LanguageFrameworkCount + w4·DeploymentUnitCount + w5·(PromptCount+AgentCount+ToolCount) + w6·(RAGDocCount/1000+RAGIndexCount) + w7·((TestCount+ConfigCount)/500) + w8·ExternalIntegrationCount + w9·DynamicComplexityScore

기본 가중치(예시, 가격정책위원회 승인 필요): w1=5, w2=10, w3=8, w4=15, w5=12, w6=6, w7=3, w8=10, w9=20

Preflight는 이 공식으로 예상 LearningUnit과 신뢰구간(±15%)을 제시하며, 실제 정산은 실행 후 실측값을 기준으로 한다. DynamicComplexityScore는 순환복잡도, 모듈 간 의존 Fan-in/Fan-out, AI Component의 Tool 권한 범위를 정규화해 합산한다. 가중치는 분기별로 실측 원가와 대조해 재보정하며 재보정 이력은 Evidence로 남긴다.

원칙:
- LOC만으로 과금하지 않는다.
- Preflight에서 예상량과 포함량을 제시한다.
- ONSure 자체 실패에 따른 재실행은 차감하지 않는다.
- 범위 밖 자료 추가와 중대한 Baseline 변경은 변경계약으로 처리한다.

## 8. VS Code 가치
- Repository 증분 학습
- 변경 즉시 Continuous Review
- 실행 전 정책·권한 Gate
- Finding 기반 Patch
- Worktree 격리
- Commit/Push/Draft PR 자동화
- CI 결과 회수
- Evidence 자동 고정

### 8-1. Credit 초과정책 (`docs/v2/04` §6에서 흡수)
고객에게는 ONSure Credit 하나만 표시하고 내부적으로 Learning/Verification/AI Model/Sandbox/Improvement/Storage 원가를 측정한다. 월간 한도 초과 시 조직이 아래 중 선택한다.

- HARD_STOP(기본값): 초과 시 즉시 실행 중단
- AUTO_TOP_UP: 자동 추가 구매
- PAY_AS_YOU_GO: 초과분 후청구
- ADMIN_APPROVAL_REQUIRED: Customer Admin 승인 후 재개

### 8-2. Web↔VS Code 전환
Web에서 구매한 Learn/Learn&Verify 결과(Program Profile)는 동일 고객의 VS Code 최초 Program Profile로 이전할 수 있다. 동일 Baseline·유효기간 내 이전이면 전체 재학습을 강제하지 않고 증분 학습부터 Credit을 사용한다. 반대로 VS Code에서 나온 Finding에 전문가 최종 검증이 필요하면 별도 Web Professional Case로 전환한다(기존 Professional Reviewer 요청 흐름 재사용).

## 9. 판매전략
- 초기: AI 개발 결과 검수와 1회 Learn & Verify 중심
- 확장: 검증 결과에서 Improve & Re-verify 전환
- 지속화: 반복 고객을 VS Code Team 구독으로 전환
- Enterprise: 폐쇄망, SSO, 정책팩, 전문 리뷰, SLA 판매

## 10. 핵심 KPI
- Preflight 대비 실제 학습량 오차
- Finding 재현율과 오탐률
- Critical/High 발견률
- Patch 승인율
- Patch 후 회귀 성공률
- Web 재구매율
- Web에서 VS Code 전환율
- 월 활성 개발자와 Credit 사용률
- Evidence 재현 성공률
- 고객 소스 삭제 SLA 준수율

## 11. 단계별 사업화
### Phase 1
Web Learn & Verify, 기본 보고서, Git 연결, OLicense Case 발급

### Phase 2
Improve & Re-verify, VS Code Developer, Continuous Review, Draft PR

### Phase 3
Team, 공유 정책, CI/CD, 관리자 대시보드, 전문가 리뷰

### Phase 4
Enterprise, 폐쇄망, 전용 모델, 정책 Marketplace, 파트너 채널

## 11-1. 산출물 소유권
- AI가 생성한 Patch, Program Profile, Report, Evidence Pack 등 Case 산출물의 소유권과 사용권은 고객에게 귀속된다.
- ONSure는 익명화된 Pattern/Fixture를 [02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) FR-COM-009 Opt-in 조건 하에서만 공유 Corpus 학습에 사용할 권리를 가지며, 고객 소스 코드 자체나 식별 가능한 파생물은 어떤 경우에도 재사용하지 않는다.
- AI Model Provider의 이용약관상 생성물 저작권이 불확실한 관할권에서는 계약서에 "고객 귀속" 조항을 명시해 분쟁 소지를 제거한다.

## 11-2. Target AI Auto-Learning 단계적 검증
`docs/v2/09`(특정 커밋에 고정된 NON_FINAL 사업 보완안)의 사업성 평가를 그대로 채택한다: "사업기회는 유효하지만 아직 사업성이 입증된 것은 아니다." 다음 순서로 유료 Case를 통해 검증하며, 앞 단계 검증 전에는 뒷 단계를 상용 판매하지 않는다.

1. RAG 재인덱싱·Prompt 개선 (재현성 높고 GPU 불필요, 최소 원가)
2. AI로 생성된 코드의 안정화(Improve 상품과 결합, Train 없이도 가능한 범위)
3. Agent 선택정책 재학습
4. Model Fine-tuning (GPU·Dataset 원가가 크므로 유료 Case로 원가·전환율·재구매율을 실측한 뒤 확대)

지불 가능성은 "중상" 수준으로 평가한다 — 공개 의뢰 예산이 소규모(100만 원대)부터 1,000만 원 이상까지 분포하며, 유료 Case·원가·전환율·재구매율을 실제로 측정하기 전까지 가격을 확정하지 않는다.

## 12. 사업 위험과 대응
- AI 원가 급증: Credit, Hard Stop, 모델별 원가 Meter
- 오탐 불신: Evidence, 재현, Confidence, 인간 승인
- 소스 유출 우려: 격리, 암호화, 최소보존, 삭제증명
- 자동 수정 사고: Finding 기원 제한, Worktree, Diff 승인, Rollback
- 라이선스 장애: Signed Snapshot, Offline Grace, Revocation 방어
- 범위 분쟁: Baseline·System·Program·Case 계약 고정