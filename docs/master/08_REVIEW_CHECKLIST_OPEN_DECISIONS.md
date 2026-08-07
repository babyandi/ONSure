# 검토용 체크리스트 — 확정 필요 항목

## 사용법
2026-08-07 세션에서 `docs/master`를 보완하며 채운 초안 수치·정책·법적 조항을 한 곳에 모았다. 담당자가 실제 값으로 확정하면 "상태" 열을 CONFIRMED로 바꾸고, 값이 바뀌면 원본 문서도 같은 변경에서 갱신한다. 이 문서 자체는 설계 권위가 아니라 검토 진행상황 추적용이며 [ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md](../architecture/ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md)의 권위 순위에 포함되지 않는다.

## A. 재무 확인 필요 (가격·정산)

| # | 항목 | 현재 초안값 | 위치 | 상태 |
|---|---|---|---|---|
| A1 | Learning Unit 산정 공식 가중치 | w1=5, w2=10, w3=8, w4=15, w5=12, w6=6, w7=3, w8=10, w9=20 | [01 §7](01_BUSINESS_PRODUCT_SERVICE_PLAN.md) | DRAFT |
| A2 | Preflight 예상치 신뢰구간 | ±15% | [01 §7](01_BUSINESS_PRODUCT_SERVICE_PLAN.md) | DRAFT |
| A3 | Quote 유효기간 / 재견적 트리거 | 14일 / 규모 20% 초과 변동 | [04](04_ARCHITECTURE_DATA_API_OLICENSE.md) | DRAFT |
| A4 | 환불정책 | 실행 전 전액, 실행 후 소비분 제외, 구독 비례환불 없음 | [01 §6-1](01_BUSINESS_PRODUCT_SERVICE_PLAN.md) | DRAFT |
| A5 | 지원 SLA | Web 1차응답 영업일 기준, Developer 2영업일, Team 1영업일, Enterprise 4시간(예시) | [05](05_UI_UX_WORKFLOW_SPECIFICATION.md) | DRAFT |

## B. 법무 확인 필요

| # | 항목 | 현재 초안값 | 위치 | 상태 |
|---|---|---|---|---|
| B1 | AI 생성 산출물(Patch/Report 등) 소유권 | 고객 귀속 원칙, Provider 약관 리스크는 계약서로 방어 | [01 §11-1](01_BUSINESS_PRODUCT_SERVICE_PLAN.md) | DRAFT |
| B2 | Acceptance Certificate 법적 효력 | 서명 검증만 하는 요약 증명서로 설계 — 계약서 문구 필요 | [02](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md), [04](04_ARCHITECTURE_DATA_API_OLICENSE.md) | DRAFT |
| B3 | PCI-DSS 범위 | Payment Provider Tokenization으로 최소화한다는 전제 | [04 §12](04_ARCHITECTURE_DATA_API_OLICENSE.md) | DRAFT |
| B4 | 국내 개인정보보호법 대응 | 자동 탐지·마스킹 원칙만 기술, 실제 법적 요건 미검증 | [04 §12-1](04_ARCHITECTURE_DATA_API_OLICENSE.md) | DRAFT |
| B5 | Copyleft(GPL) 라이선스 차단 정책 | PolicyPack 허용/차단 목록으로 조직이 설정 | [03](03_OREVIEW_CODE_REVIEW_SPECIFICATION.md) | DRAFT |

## C. 엔지니어링 확인 필요 (실현 가능성)

| # | 항목 | 현재 초안값 | 위치 | 상태 |
|---|---|---|---|---|
| C1 | Learning 성능 목표 | 100만 LOC 8시간, 증분 10만 LOC 30분 | [02 §11 NFR-PERF](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) | DRAFT |
| C2 | Continuous Review 응답시간 | Diff 저장 후 5초 이내 1차 결과 | [02 §11](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) | DRAFT |
| C3 | Verification 처리량 | Pack당 평균 15분, Case당 동시 20개 | [02 §11](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) | DRAFT |
| C4 | SaaS 가용성 / Token 수명 | 월 99.9% / Access Token 1시간 | [02 §11](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md), [04 §6](04_ARCHITECTURE_DATA_API_OLICENSE.md) | DRAFT |
| C5 | ProgramRiskScore 등급 컷오프 | A≥90, B≥75, C≥60, D≥40 | [04](04_ARCHITECTURE_DATA_API_OLICENSE.md) | DRAFT |
| C6 | KnowledgePattern 강등 임계치 | 설계서는 3회 이상, 실제 계약 `contracts/reusable-pattern-memory.v1.schema.json`은 `independent_reproduction_count` 최소 2회 — 계약값으로 정정 필요 | [02](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md), [04 §5](04_ARCHITECTURE_DATA_API_OLICENSE.md) | CONTRACT_MISMATCH |
| C7 | 사고 공지 대응시간 | Critical 사고 15분 이내 Status Page+개별통지 동시 시작 | [06](06_TEST_OPERATION_IMPLEMENTATION_PLAN.md) | DRAFT |

## D. 영업/상품 확인 필요

| # | 항목 | 현재 초안값 | 위치 | 상태 |
|---|---|---|---|---|
| D1 | 공유 Corpus 기여 기본값 | Opt-out (Pattern Library 성장 속도에 영향) | [02 FR-COM-009](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) | DRAFT |
| D2 | Web 4상품 구조가 실제 가격표와 일치하는지 | Learn / Verify / Learn&Verify / Improve&Reverify | [01 §6](01_BUSINESS_PRODUCT_SERVICE_PLAN.md) | DRAFT |
| D3 | Enterprise 전용 기능 가격 정책 | SoD·규제 프레임워크 매핑·폐쇄망 등 Feature Gate로만 설계, 가격 미정 | [04 §12-1](04_ARCHITECTURE_DATA_API_OLICENSE.md) | DRAFT |

## E. 규제/컴플라이언스 확인 필요

| # | 항목 | 현재 초안값 | 위치 | 상태 |
|---|---|---|---|---|
| E1 | NIST/ISO/OWASP/MITRE/금융 MRM 프레임워크 매핑 방식 | PolicyPack에 버전 단위로 매핑한다는 원칙만 기술 | [04 §12-1](04_ARCHITECTURE_DATA_API_OLICENSE.md) | DRAFT |
| E2 | SoD(직무분리) 강제 범위가 실제 감사 기준을 충족하는지 | 동일인 Patch/재검증승인/인수승인 겸직 금지 | [02 FR-COM-013](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) | DRAFT |

## G. 계약 정합성 (엔지니어링, 신규 — 2026-08-07 발견)

`docs/master/04` §5 상태모델을 실제 `contracts/*.schema.json`과 대조한 결과, 다수의 설계 개념이 아직 대응하는 계약이 없다(`DESIGN_ONLY`). 우선순위 순서로 나열한다.

| # | 항목 | 필요 조치 | 상태 |
|---|---|---|---|
| G1 | CreditReservation 소진 시 대기 상태 | 5개 실행 상태기계의 HOLD를 재사용할지, 별도 상태 신설할지 결정 | OPEN |
| G2 | CaseRevision | `service-case-state.v1.schema.json`에 필드/상태 확장 필요 | OPEN |
| G3 | ComponentContract / Cross-Program Impact Scan | 신규 `component-contract.v1.schema.json` 계약 제정 필요 | OPEN |
| G4 | MissedFinding 재귀학습 루프 | 신규 계약 제정 필요, `contracts/state-model-mapping.v1.json`과의 관계 정의 | OPEN |
| G5 | ReviewFinding/VerificationFinding 장기 생애주기 | 실제는 `validation_run`마다 스냅샷(`oreview-result.v1.schema.json`)만 존재 — Finding을 가로지르는 생애주기 계약이 필요한지, 아니면 설계를 스냅샷 모델에 맞출지 결정 | OPEN |
| G6 | PatchRun DRY_RUN 확장 | `patch-plan.v1.schema.json`의 `preapply_assessment`로 이미 부분 커버됨 — 설계서를 계약에 맞춰 단순화할지 검토 | OPEN |
| G7 | 이 세션에서 추가한 나머지 엔티티(AcceptanceCertificate, ProgramRiskScore, PolicyPack, NotificationRule, SBOM 등) | 전부 `DESIGN_ONLY` — 계약 제정 우선순위를 06 §11 우선구현순서와 맞춰 재정렬 필요 | OPEN |

이 표는 04 §5의 "아직 계약이 없는 확장" 절과 같은 내용을 재무/영업용 체크리스트와 같은 형식으로 옮긴 것이다.

### 2026-08-07 2차 대조 (02·03·06) 결과

| # | 항목 | 발견 | 조치 |
|---|---|---|---|
| G8 | CreditReservation | 처음엔 DESIGN_ONLY로 잘못 분류했으나 실제로 `license-state.v1.schema.json`의 `reservations` 필드로 이미 존재(RESERVED/COMMITTED/RELEASED/EXPIRED). 04 §5 정정 완료 | FIXED |
| G9 | 03 OReview Decision 체계 | 설계서가 자체 정의한 APPROVE/COMMENT/REQUEST_CHANGE/REJECT/NOT_APPLICABLE/INCONCLUSIVE가 실제 계약(`oreview-result.v1.schema.json`, `status-vocabulary.v1.json`)의 PASS/FAIL/HOLD/NOT_RUN/NOT_APPLICABLE과 전혀 다른 어휘였음. 02·03 정정 완료 | FIXED |
| G10 | Finding 상태 생애주기 | 실제 계약(`security-findings.v1.schema.json`)은 OPEN/CLOSED/ACCEPTED_RISK 3단계뿐. 설계서의 6단계 생애주기는 DESIGN_ONLY로 명시 | FIXED (명시만, 계약 확장은 미정) |
| G11 | OGit Merge 권한 | `git-change-set.v1.schema.json`의 `merge_state`가 계약상 항상 `PROHIBITED` — "APPROVE≠Merge" 수준이 아니라 이 버전에서 Merge 승인 자체가 발급 안 됨. 02 정정 완료 | FIXED |
| G12 | Actor/RBAC | 설계서의 10개 업무 Actor가 실제 `tenant-context.v1.schema.json`의 5개 RBAC 역할(VIEWER/OPERATOR/APPROVER/AUDITOR/ADMIN)과 매핑되지 않은 채 존재. 02에 매핑 제안 표 추가(매핑 자체는 아직 계약화 안 됨) | PARTIAL |
| G13 | Workflow Operation Registry | 실제 등록된 Operation은 45개(`workflow-operation-registry.v1.json`)뿐. 이 설계서(특히 04 §7)가 제안한 Notification/Portfolio/PolicyPack/AcceptanceCertificate/SBOM/MutationTesting/CrossModel/BlastRadius/CoverageReport/RiskScore 관련 API는 전부 미등록 — 06에 교차참조 추가 | FLAGGED |
| G14 | `05_UI_UX_WORKFLOW_SPECIFICATION.md`, `05`의 화면 구성 | 아직 실제 계약과 대조 안 함(주로 화면 설계라 직접 대응 계약이 적을 것으로 예상되나 확인 필요) | OPEN |

G9~G13은 대조 범위가 넓어 이번 라운드에서 발견된 것을 반영했을 뿐, 02·03·06 전체를 계약과 완전히 재대조한 것은 아니다.

### 2026-08-07 3차 대조 (01·05·07) 결과

| # | 항목 | 발견 | 조치 |
|---|---|---|---|
| G15 | 01 목표고객 분류 | 실제 `product-scope.v1.json`은 `primary_users` 4종(NON_DEVELOPER_AI_BUILDERS/SOFTWARE_DEVELOPERS/PRODUCT_TEAMS/ENTERPRISE_ASSURANCE_TEAMS)만 정의 — 설계서의 6개 세그먼트는 더 세분화된 DESIGN_ONLY 분류, 계약 매핑 없음 | FLAGGED |
| G16 | 검증 대상 범위 | 계약의 `supported_target_types`에 Desktop/Mobile/Automation Workflow가 있는데 01은 언급 안 함 | FLAGGED |
| G17 | 상품 채널 vs 엔진 배포 형태 혼동 위험 | `delivery_modes`(로컬/독립실행 중심)와 01의 Web/VS Code 상품구조는 서로 다른 계층 — 01에 구분 설명 추가 | FIXED |
| G18 | 05 Web이 별도 Operation Surface인지 | 실제 `generic_surfaces`는 CLI/LOCAL_AUTHENTICATED_API/VSCODE뿐, WEB 없음. Web은 LOCAL_AUTHENTICATED_API 클라이언트로 구현돼야 함을 05에 명시 | FIXED |
| G19 | Preflight 입력 종류 | 실제 `target-adapter.v1.json`은 Package/Binary/Deployed Service/Document 세트도 지원하는데 05는 Git/Archive/Container만 언급 | FIXED |
| G20 | 07 Component 모델의 실제 선례 | `module-boundary.v1.json`/`core-extension-boundary.v1.json`이 이미 ONSure 자신에 CBD 원칙(Core vs ORUDA Adapter)을 적용한 실제 계약 — 07에 교차참조 추가, 대상 프로그램용 `component-contract.v1.schema.json`은 여전히 없음 | FIXED(교차참조), OPEN(신규계약) |
| G21 | 07 Agent 최소권한의 실제 선례 | `public-sdk-boundary.v1.json`이 외부 SDK에 이미 FINAL_CLAIM/MERGE/PRODUCTION_GO Authority 공개 금지를 강제 — 07에 교차참조 추가 | FIXED |

`docs/master`의 8개 문서(00~07) 모두 최소 한 번씩 실제 계약과 대조를 거쳤다. 다만 이번 세 라운드(G8~G21)는 발견 즉시 수정한 것이라, 시간을 두고 전체를 처음부터 끝까지 계약과 한 줄씩 대조하는 완전한 재검토는 아니다.

### 2026-08-07 4차 대조 (심화) 결과

| # | 항목 | 발견 | 조치 |
|---|---|---|---|
| G22 | 05 Autopilot 정의 | 실제 `unattended-autopilot.v1.json`은 FINAL_PASS/PRODUCTION_GO/FORCE_PUSH/HARD_RESET 등 11개 명시적 금지행위, `merge_authorization.authorized` 항상 false, 단계별 최대 재시도 1회로 이 설계서보다 훨씬 엄격 — 05에 흡수 | FIXED |
| G23 | 04 Sandbox 격리기술 | 이 설계서가 "MicroVM 또는 동급"이라 쓴 건 부정확 — 실제는 Rootless Bubblewrap(`bwrap`)으로 고정(`remote_ci_backend: FORBIDDEN`). README의 "Rootless Bubblewrap Sandbox"와도 일치. 04 정정 완료 | FIXED |
| G24 | 01 Preflight 판정 분류 | `docs/v2/03_WEB_ONE_TIME_SERVICE_POLICY.md`에 READY/NEEDS_BASELINE/RECOMMEND_LEARN_VERIFY/CUSTOM_QUOTE/REJECT/HOLD 판정 분류와 2단계 결제(사전진단비+차액)가 이미 상세 설계돼 있었음 — 01에 흡수 | FIXED |
| G25 | 02 FR-COM-008 실제 근거 | `main-branch-protection.v1.json`(babyandi/ONSure main)의 구체 설정(PR 필수, 최소승인 1, Force Push 차단, 관리자 예외없음 등)을 02에 추가해 정책 문장에 실제 근거 결속 | FIXED |
| G26 | `docs/v2/` 문서군 발견 | 00~09 완전한 문서 세트(01_BUSINESS_PLAN, 02_PRODUCT_AND_SERVICE_MODEL, 03_WEB_ONE_TIME_SERVICE_POLICY, 04_VSCODE_SUBSCRIPTION_POLICY, 05_LICENSE_PAYMENT_OLICENSE_INTEGRATION, 06~09)가 별도로 존재. `docs/master`와 같은 주제(Web/VS Code 상품, OLicense)를 훨씬 상세히 다루며 내용이 상당히 정합적이었음(적대적이지 않음, docs/05·07과 다른 케이스). DESIGN_AUTHORITY 순위표에서 여전히 최하위(§2 순위 12번, "참고 자료")로만 취급되는 게 맞는지는 미확정 — 04·06~09는 아직 안 읽음 | PARTIAL(03·05만 흡수) |
| G27 | `contracts/requirements-traceability.v1.json`의 오래된 design_refs | 이 실제 추적 레지스트리가 WEB-SERVICE-CASE·OLICENSE·RCA·IMPROVEMENT-PATCH·IMPROVEMENT-PROOF·WORKSPACE-INTAKE·GIT-DELIVERY 항목에서 여전히 `docs/05`(이번 세션에 DEPRECATED 처리)와 `docs/v2/*`, `docs/03_GIT_AND_CHANGE_GOVERNANCE.md`를 design_refs로 인용 중. 이 파일은 현재 다른 무관한 작업으로 staged+unstaged 상태(MM)라 제가 수정하지 않음 — 그 작업 소유자가 docs/master 기준으로 재결속해야 함 | NOT_TOUCHED(소유자 확인 필요) |
| G28 | `contracts/state-machine.v1.json` | `state-model-mapping.v1.json`(고객 대상 실행)과는 별개로, ONSure 자신의 내부 개발/퍼블리시 파이프라인(UNINITIALIZED→...→PUBLICATION_ELIGIBLE) 상태기계가 따로 존재 — 고객向 설계서와 직접 관련 없어 반영 안 함, 혼동 방지용으로만 기록 | NOTED |

docs/v2/04(VS Code 구독정책), 06(운영프로세스·고객여정), 07(아키텍처·데이터모델), 08(구현로드맵), 09(AI 자동학습 전략)도 모두 읽었다. 04의 Credit 초과정책(HARD_STOP/AUTO_TOP_UP/PAY_AS_YOU_GO/ADMIN_APPROVAL_REQUIRED)과 Web↔VS Code Program Profile 전환, 07의 신뢰경계 원칙(Feature 표시≠권한 증거, 결제성공만으로 실행 불가)을 01·04에 흡수했다.

### G29 — 미흡수 대형 항목: Target AI Auto-Learning (결정 필요, 최우선)
`docs/v2/09_TARGET_AI_AUTO_LEARNING_BUSINESS_AND_DEVELOPMENT_STRATEGY.md`(1216줄, 커밋 SHA `5a761ccf...`에 고정된 NON_FINAL 설계 보완안)가 `docs/master`에 전혀 없는 새 사업 축을 정의한다.

- **Program Understanding Learning**(=이 설계서의 OLearning)과 **Target AI Auto-Learning**(대상 프로그램 내부의 RAG/Prompt/Agent/Tool선택정책/예측·분류·추천·비전 Model을 실제 데이터로 재학습·개선)을 명확히 별개 기능으로 구분한다. 목적·입력·산출물·비용·위험·라이선스 단위가 전부 다르다.
- 확장된 파이프라인을 제안한다: `Understand → Verify → Diagnose → Decide → Improve or Train → Independently Re-verify → Prove → Deploy → Observe → Re-learn` — 이 설계서 00의 `Understand → Plan → Review → Verify → Improve → Prove → Remember`와 다르다. 특히 **Diagnose**(별도 단계로 분리, docs/07 Diagnosis Engine과 같은 방향), **Deploy/Observe/Re-learn**(운영 배포 후 피드백 루프)이 이 설계서에는 아예 없다.
- 사업성 평가를 솔직하게 NON_FINAL로 명시하고("사업기회는 유효하지만 아직 사업성이 입증된 것은 아니다"), 초기엔 RAG·Prompt 개선과 AI 생성 코드 안정화부터 유료 Case로 검증하라고 제안한다.

**결정 필요**: 이 사업 축을 `docs/master`에 정식 흡수할지, 별도 트랙(`docs/v2`)으로 유지할지, 아니면 아직 시기상조로 보류할지 — 흡수하면 00의 파이프라인·02의 프로그램 구성·04의 데이터모델·01의 상품구조 전반에 영향을 주는 큰 변경이라 이번 세션에서 임의로 병합하지 않았다.

## F. 문서 거버넌스 (참고, 결정 아님)

| # | 항목 | 현재 상태 | 위치 |
|---|---|---|---|
| F1 | `status/design-conflict-register.v1.json`의 CONFLICT-003/005/006이 여전히 `docs/05`를 authority로 인용 | 무관한 진행 중 작업이라 미수정 | [ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md §0](../architecture/ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md) |
| F2 | 229개 파일 규모의 기존 staged 변경(패키지 리네임 `io.onsure`→`kr.co.oruda.onsure` 등)이 일부 파일에서 디렉터리만 옮기고 `package` 선언은 갱신 안 됨 (예: `LocalAuthenticatedApiServerTest.java`가 `kr/co/oruda/...` 경로에 있으나 `package io.onsure.platform;`) | 빌드 깨질 가능성 높음, 커밋 보류 권장 | 리포 루트 (제가 수정하지 않음) |
