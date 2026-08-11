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
| A6 | Train & Re-verify 가격·GPU 원가 | 미정 — 유료 Case로 원가 실측 후 확정(1단계는 RAG/Prompt만 판매) | [01 §11-2](01_BUSINESS_PRODUCT_SERVICE_PLAN.md) | OPEN |

## B. 법무 확인 필요

| # | 항목 | 현재 초안값 | 위치 | 상태 |
|---|---|---|---|---|
| B1 | AI 생성 산출물(Patch/Report 등) 소유권 | 고객 귀속 원칙, Provider 약관 리스크는 계약서로 방어 | [01 §11-1](01_BUSINESS_PRODUCT_SERVICE_PLAN.md) | DRAFT |
| B2 | Acceptance Certificate 법적 효력 | 서명 검증만 하는 요약 증명서로 설계 — 계약서 문구 필요 | [02](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md), [04](04_ARCHITECTURE_DATA_API_OLICENSE.md) | DRAFT |
| B3 | PCI-DSS 범위 | Payment Provider Tokenization으로 최소화한다는 전제 | [04 §12](04_ARCHITECTURE_DATA_API_OLICENSE.md) | DRAFT |
| B4 | 국내 개인정보보호법 대응 | 자동 탐지·마스킹 원칙만 기술, 실제 법적 요건 미검증 | [04 §12-1](04_ARCHITECTURE_DATA_API_OLICENSE.md) | DRAFT |
| B5 | Copyleft(GPL) 라이선스 차단 정책 | PolicyPack 허용/차단 목록으로 조직이 설정 | [03](03_OREVIEW_CODE_REVIEW_SPECIFICATION.md) | DRAFT |
| B6 | 학습 데이터 사용 동의·라이선스 | "고객 동의 확인 없는 학습 금지"만 원칙으로 기술, 실제 동의서 양식·데이터 소유권 조항 미작성 | [02 §7-2 OTraining](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) | OPEN |

## C. 엔지니어링 확인 필요 (실현 가능성)

| # | 항목 | 현재 초안값 | 위치 | 상태 |
|---|---|---|---|---|
| C1 | Learning 성능 목표 | 100만 LOC 8시간, 증분 10만 LOC 30분 | [02 §11 NFR-PERF](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) | DRAFT |
| C2 | Continuous Review 응답시간 | Diff 저장 후 5초 이내 1차 결과 | [02 §11](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) | DRAFT |
| C3 | Verification 처리량 | Pack당 평균 15분, Case당 동시 20개 | [02 §11](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) | DRAFT |
| C4 | SaaS 가용성 / Token 수명 | 월 99.9% / Access Token 1시간 | [02 §11](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md), [04 §6](04_ARCHITECTURE_DATA_API_OLICENSE.md) | DRAFT |
| C5 | ProgramRiskScore 등급 컷오프 | A≥90, B≥75, C≥60, D≥40 | [04](04_ARCHITECTURE_DATA_API_OLICENSE.md) | DRAFT |
| C6 | KnowledgePattern 승격/강등 임계치 | 두 개의 서로 다른 개념이 섞여 있었음: (1) 승격에 필요한 독립 재현 횟수는 계약값(최소 2회)이 권위이며 02·04를 정정 완료. (2) 재현 실패 누적에 따른 자동 강등은 대응 계약 필드 자체가 없는 별도의 `DESIGN_ONLY` 항목으로 02에 명시함 — 강등 임계치·카운터·상태전이 계약 제정은 G31로 이관 | [02](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md), [04 §5](04_ARCHITECTURE_DATA_API_OLICENSE.md) | FIXED (승격 임계치 정정 완료, 강등 계약 제정은 G31로 신규 추적) |
| C7 | 사고 공지 대응시간 | Critical 사고 15분 이내 Status Page+개별통지 동시 시작 | [06](06_TEST_OPERATION_IMPLEMENTATION_PLAN.md) | DRAFT |
| C8 | NFR-SEC 저장 암호화 알고리즘 | AES-256 이상(제안) | [02 §11](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) | DRAFT |
| C9 | NFR-REL 최대 재시도·Backoff 정책 | 미정 | [02 §11](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) | OPEN |
| C10 | NFR-PRIV 고객별 데이터 보존기간 | 미정(계약별 협의 대상) | [02 §11](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) | OPEN |
| C11 | NFR-OBS 구조화 로그 최소 필드셋 | 제안값(operation/actor/duration/decision/evidence_ref) | [02 §11](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) | DRAFT |
| C12 | NFR-SESSION 세션 타임아웃·동시세션 상한 | 미정 — 외부표준(OWASP ASVS V3) 대조로 2026-08-09 신규 발견 | [02 §11](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) | OPEN |
| C13 | NFR-CONFIG 필수 HTTP 보안 헤더 목록 | 제안값(Strict-Transport-Security, X-Content-Type-Options 등) — 외부표준(OWASP ASVS V14) 대조로 2026-08-09 신규 발견 | [02 §11](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md) | DRAFT |
| C14 | Confidence Calibration 편향 판정 임계치 | 미정(calibration_error 몇 %p 이상이면 편향으로 볼지) | [03 §9](03_OREVIEW_CODE_REVIEW_SPECIFICATION.md) | OPEN |
| C15 | Confidence Calibration 재보정 트리거 연속 Window 수(N) | 미정 | [03 §9](03_OREVIEW_CODE_REVIEW_SPECIFICATION.md) | OPEN |

### 외부표준 대조 (2026-08-09) — OWASP ASVS / ISO-IEC 25010 / NIST SSDF
기능정의(02·03)를 이 세 표준과 항목 단위로 대조했다. Security Review(03 §4)는 예상보다 탄탄해서(JWT/SSRF/XSS/CSRF/SQLi/암호화/키회전까지 구체적으로 존재) ASVS 대비 큰 구멍은 세션관리(V3)·보안설정(V14)·파일업로드(V12) 세 항목뿐이었고 위에 반영했다(03 Security Review, C12/C13). NIST SSDF는 PW(Produce Well-Secured Software)는 잘 커버되지만 **RV(Respond to Vulnerabilities, 배포 후 취약점 대응)가 구조적으로 약하다** — 이건 새로 발견한 게 아니라 이미 `status/product-subrequirement-coverage.v1.json`의 FR-03-A/FR-03-C가 `DIRECT_PRODUCTION_TOOL_TELEMETRY_NOT_RUN`/`DIRECT_PRODUCTION_POLICY_TELEMETRY_NOT_RUN`으로 추적 중이던 gap과 정확히 일치한다 — 외부표준 대조와 기존 self-tracking이 독립적으로 같은 결론에 도달한 것이므로 새 항목을 추가하지 않고 기존 추적으로 합류시킨다. ISO/IEC 25010은 NFR-PORT(Portability)·05(Usability 접근성)가 이미 해당 특성을 커버하고 있어 별도 gap 없음.

요구사항 품질 측면에서는 OReview/OMemory(02 §5, §7-1, 03 §9-1)가 이례적으로 검증 가능한 형태(모델버전+Temperature까지 Receipt 결속, Confidence Calibration 실측)로 쓰인 반면 11장 비기능요구사항은 전부 키워드 나열이라 테스트 케이스를 만들 수 없었다 — 위에서 전면 재작성했다(NFR-SEC~NFR-CONFIG, 신규 NFR-SESSION/NFR-CONFIG 포함).

**추가 대조 (같은 날, ONSure 자신의 핵심 차별점인 AI Review를 AI 전용 표준으로 대조)**: OWASP Top 10 for LLM Applications를 03 AI Review 절과 대조한 결과, Prompt Injection/RAG 오염/Excessive Agency/Unbounded Consumption/Misinformation은 이미 커버됐지만 **민감정보 노출(LLM02), 공급망(LLM03, Model/Plugin/Embedding 출처·서명), 출력 처리(LLM05, 모델 출력을 신뢰되지 않은 입력으로 취급)가 완전히 빠져 있었다** — 03 AI Review에 반영했다. 05의 접근성 절도 WCAG 준수 "수준" 자체가 명시돼 있지 않았던 걸 발견해 WCAG 2.1 AA를 목표로 명시하고 명도대비·포커스표시·확대 3개 항목을 추가했다.

**3차 대조 (같은 날, API·AI Agent 거버넌스)**: 04 §7의 실제 API 목록(POST /v1/orders 등)을 OWASP API Security Top 10과 대조한 결과 API9(Improper Inventory Management)는 `workflow-operation-registry.v1.json`이라는 실제 단일 권위 레지스트리로 이미 충족하고 있었지만(강점으로 확인, 문서 변경 없음), **객체 수준 권한 검사(API1)와 민감 업무 흐름 남용 방지(API6)가 원칙으로 명시돼 있지 않았다** — 04 §6에 추가했다. 07의 AI Agent 방법론을 NIST AI RMF의 4개 기능(GOVERN/MAP/MEASURE/MANAGE)과 대조한 결과 GOVERN/MAP/MEASURE는 강하게 커버됐지만(Agent별 최소권한, AIProfile Drift 탐지, Confidence Calibration), **MANAGE(위험 대응) 쪽에서 AI Agent 이상행동이 06 사고 유형 목록에 없었다** — Plan-Act-Observe 루프의 기존 반복·비용 상한 메커니즘([07 §3.2](07_COMPONENT_MODEL_AND_AI_METHODOLOGY.md))은 있었으나 이게 반복 발생할 때 사고로 승격하는 절차가 없어서 06에 추가했다.

**4차 대조 (같은 날, Sandbox 격리)**: ONSure가 고객 코드를 실제로 실행하는 가장 위험한 경계인 04 Sandbox 절을 NIST SP 800-190/일반 Linux 샌드박싱 관행과 대조했다. Namespace 분리, Read-only Source 마운트, 전체 Capability Drop, Fail-closed, Tenant별 Cross-read/write 거부, Egress Deny-by-default까지 이미 이례적으로 탄탄했다(이번 세션에서 대조한 절 중 가장 견고함). 다만 **Seccomp-bpf Syscall 필터링이 계약과 실제 `bwrap` 호출 코드 어디에도 없었다**(`grep -rn seccomp` 0건, 직접 확인) — Capability Drop과는 다른 방어 계층이라 04에 DESIGN_ONLY로 기록했다. 실제 코드 반영은 대상 분석 툴체인이 필요로 하는 Syscall 집합을 먼저 조사해야 하는 별도 작업이라 이번엔 하지 않았다.

### 완전성 보장 메커니즘 상세설계 (2026-08-09) — "ONSure가 대상을 완벽히 검증한다고 어떻게 보장하냐"는 질문에 대한 실제 답
사용자가 제기한 핵심 질문: 어떤 검증 시스템도 대상의 완전한 결함 부재를 일반적으로 증명할 수 없다(정지 문제와 연결되는 근본적 한계). ONSure의 실제 답은 "완벽하다"가 아니라 "정확히 뭘 봤고 뭘 못 봤는지, 얼마나 확신했고 그 확신이 실제로 맞았는지를 항상 감사 가능하게 공개한다"는 것이다. 이 답을 실제로 지탱하는 3개 장치를 코드/계약에서 확인한 결과 **`final_claim_allowed: false`만 실제로 21개 계약에 강제되고 있었고, CoverageReport·Confidence Calibration·MissedFinding은 이름만 여러 문서에 흩어져 있을 뿐 필드 수준 설계가 없었다**(MissedFinding은 특히 "실제 계약 확인됨"이라는 잘못된 서술까지 있었다 — 04에서 정정). 이번 라운드에서 셋 다 필드 수준 기능정의를 새로 작성했다(구현은 하지 않음, 사용자 지시):
- **CoverageReport**: [02 §4 OPlanning](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md)에 `included`/`excluded`(사유·결정자 포함)/`domain_coverage`/`coverage_percent` 필드와 "숫자만 단독 노출 금지" 원칙을 추가
- **MissedFinding**: [04](04_ARCHITECTURE_DATA_API_OLICENSE.md)에 `discovery_path`(5종, 02 §7-1 수용기준에서 이미 쓰던 어휘 재사용)/`original_run_reference`/`agent_context`/`rca_reference`/`promoted_candidate_id` 필드를 추가하고, 이미 실재하는 일반 승격 파이프라인(`learning-to-application-pipeline.v1.json`)의 "투입 이전 입구" 역할로 명확히 분리
- **Confidence Calibration**: [03 §9](03_OREVIEW_CODE_REVIEW_SPECIFICATION.md)에 정답 판정 신호(Cross-Model Verification 결과/Human Reviewer 결정 재사용, 새 Finding 상태 발명 안 함)와 `buckets`/`systematic_bias`/`recalibration_flag` 필드 추가. 편향 임계치·재보정 트리거 Window 수는 C14/C15로 신규 추적

셋 다 여전히 `DESIGN_ONLY`다(계약·코드는 다음 단계). 이번 라운드의 목적은 "구현 가능한 수준으로 설계를 끝내는 것"이었다.

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
| G1 | CreditReservation 소진 시 대기 상태 | [04 CreditReservation절](04_ARCHITECTURE_DATA_API_OLICENSE.md)에 이미 결정 반영됨: 5개 실행 상태기계의 기존 HOLD로 전이(재사용), ServiceCase 별도 대기 상태는 도입 안 함. 체크리스트만 갱신 안 돼 있었음 | FIXED |
| G2 | CaseRevision | `service-case-state.v1.schema.json`에 `case_revisions`(배열: revision_number/revision_type[INITIAL\|IMPROVE_AND_REVERIFY]/triggered_by_order_id/baseline_before_source_digest/status[OPEN\|DELIVERED\|ACCEPTED]/opened_at/delivered_at/accepted_at) 필드 추가. 별도 CaseRevision 계약 파일 대신 ServiceCaseState에 내장된 배열로 설계(독립 생애주기가 없어 항상 부모 Case와 함께 읽고 쓰이므로). `ServiceCaseLifecycleService.java`에 `requestImprovementRevision(caseId, triggeringOrderId, actor)` 추가: DELIVERY_ACCEPTED에서만 호출 가능, 새 상태값을 신설하지 않고 기존 IN_PROGRESS→DELIVERED_AWAITING_ACCEPTANCE→DELIVERY_ACCEPTED 전이를 재사용(`deliver()`/`acceptDelivery()`가 최신 revision의 상태도 함께 갱신하도록 확장). 전체 사이클(open→...→acceptDelivery→requestImprovementRevision→deliver→acceptDelivery)을 실제로 구동하는 테스트 추가, `mvn test` 458/0/0/7-skip 회귀 없음, `validate-structured-contracts.py --require-full` PASS | FIXED |
| G3 | ComponentContract / Cross-Program Impact Scan | `contracts/component-contract.v1.schema.json`(Component ID/Baseline 결속, `component_signature`{code_hash_sha256, interface_hash_sha256}, Provided/Required Interface, Data/AI(nullable)/Quality Contract, 상태 DRAFT→ACTIVE→SUPERSEDED/BREAKING_CHANGE_FLAGGED, SUPERSEDED 시 `superseded_by_component_id` 필수를 allOf/if/then으로 강제)와 `contracts/reuse-link.v1.schema.json`(Provided Interface 단위 Provider Component→Consumer Component/Program 역조회 Link, Cross-Program Impact Scan의 실제 색인 레코드) 신규 제정 완료, `schema-instance-registry.v1.json`에 등록. Java 소비 코드(ComponentContractService 등)는 아직 없음 — 순수 계약 제정 단계이며 구현은 별도 결정 사항 | FIXED |
| G4 | MissedFinding 재귀학습 루프 | 결정(2026-08-11): `state-model-mapping.v1.json`의 `machines`는 실제로 5개(program_profile/validation_run/improvement/git_delivery/assurance_publication)뿐이며 전부 고객 대상 실행 계층(G28과 동일 범위) — MissedFinding·`learning-to-application-pipeline.v1.json`은 ONSure 자신의 내부 역량개선 루프(G28의 state-machine.v1.json과 같은 범주)라 이 레지스트리에 등록 대상이 아님. 04 MissedFinding 절에 등록 비대상 사유·향후 계약화 시 처리 원칙(promoted_candidate_id 다리만으로 충분, 6번째 machine으로 추가 금지) 반영 | FIXED |
| G5 | ReviewFinding/VerificationFinding 장기 생애주기 | 결정: 새 계약 제정 없이 스냅샷 모델(기존 3단계 + 최신 validation_run)로 단순화 — 04에 반영 | FIXED |
| G6 | PatchRun DRY_RUN 확장 | 결정: 별도 엔티티 신설 없이 기존 `preapply_assessment`/`patch-rollback-receipt.v1.schema.json`으로 단순화 — 04에 반영. BehaviorDiffReport만 신규계약 필요 여부 재검토 남음 | FIXED (BehaviorDiffReport는 별도 추적) |
| G7 | 이 세션에서 추가한 나머지 엔티티(AcceptanceCertificate, PolicyPack, NotificationRule, SBOM 등) | 4개 전부 필드 수준 기능정의 작성 완료(04, G33 참조) — 여전히 전부 `DESIGN_ONLY`(계약은 아직 없음, 이 정의는 구현 전 단계). ProgramRiskScore는 계약 제정 완료로 G7에서 분리 — G32 참조 | FIXED (설계 공백 해소, 계약 제정은 별도 후속) |

이 표는 04 §5의 "아직 계약이 없는 확장" 절과 같은 내용을 재무/영업용 체크리스트와 같은 형식으로 옮긴 것이다.

### 2026-08-07 2차 대조 (02·03·06) 결과

| # | 항목 | 발견 | 조치 |
|---|---|---|---|
| G8 | CreditReservation | 처음엔 DESIGN_ONLY로 잘못 분류했으나 실제로 `license-state.v1.schema.json`의 `reservations` 필드로 이미 존재(RESERVED/COMMITTED/RELEASED/EXPIRED). 04 §5 정정 완료 | FIXED |
| G9 | 03 OReview Decision 체계 | 설계서가 자체 정의한 APPROVE/COMMENT/REQUEST_CHANGE/REJECT/NOT_APPLICABLE/INCONCLUSIVE가 실제 계약(`oreview-result.v1.schema.json`, `status-vocabulary.v1.json`)의 PASS/FAIL/HOLD/NOT_RUN/NOT_APPLICABLE과 전혀 다른 어휘였음. 02·03 정정 완료 | FIXED |
| G10 | Finding 상태 생애주기 | 실제 계약(`security-findings.v1.schema.json`)은 OPEN/CLOSED/ACCEPTED_RISK 3단계뿐. 설계서의 6단계 생애주기는 DESIGN_ONLY로 명시 | FIXED (명시만, 계약 확장은 미정) |
| G11 | OGit Merge 권한 | `git-change-set.v1.schema.json`의 `merge_state`가 계약상 항상 `PROHIBITED` — "APPROVE≠Merge" 수준이 아니라 이 버전에서 Merge 승인 자체가 발급 안 됨. 02 정정 완료 | FIXED |
| G12 | Actor/RBAC | 설계서의 10개 업무 Actor가 실제 `tenant-context.v1.schema.json`의 5개 RBAC 역할(VIEWER/OPERATOR/APPROVER/AUDITOR/ADMIN)과 매핑되지 않은 채 존재. 02에 매핑 제안 표 추가(매핑 자체는 아직 계약화 안 됨) | PARTIAL |
| G13 | Workflow Operation Registry | 실제 등록된 Operation은 49개(`workflow-operation-registry.v1.json`, G14 대조 중 재확인 — 이전 45개는 그 사이 `provider.status`/`provider.usage`가 추가되며 갱신 안 된 값이었음). 이 설계서(특히 04 §7)가 제안한 Notification/Portfolio/PolicyPack/AcceptanceCertificate/SBOM/MutationTesting/CrossModel/BlastRadius/CoverageReport/RiskScore 관련 API는 여전히 미등록 — 06에 교차참조 추가 | FLAGGED |
| G14 | `05_UI_UX_WORKFLOW_SPECIFICATION.md`, `05`의 화면 구성 | 전면 대조 완료. (1) `service-case-state.v1`/`security-findings.v1`/`program-risk-score.v1`/`license-state.v1`/`program-profile.v1`/`target-adapter.v1`/`unattended-autopilot.v1`의 상태값·필드는 05가 이미 정확히 인용 중이었음. (2) Finding Explorer의 "False Positive"와 전문가 소견 Decision(EXPERT_CONCUR/OVERRIDE/ESCALATE)이 계약에 없는 `DESIGN_ONLY` 어휘였는데 그 사실이 명시돼 있지 않았음(G10과 같은 유형의 누락) — 05에 명시 추가. (3) Verification 화면의 상태 표기가 `status-vocabulary.v1`의 7개 값 중 4개(PASS/FAIL/BLOCKED/NOT_RUN)만 반영해 HOLD/INCONCLUSIVE/NON_FINAL 누락 — 05 정정 완료. (4) License & Usage의 Hard Stop/Auto Top-up/Approval Required, Support Center 티켓 상태(OPEN→...→CLOSED)가 대응 계약 없이 이미 확정된 것처럼 서술돼 있었음 — `DESIGN_ONLY` 명시 추가. (5) Organization Portfolio/PolicyPack/알림 구독/AcceptanceCertificate/CoverageReport/MutationScore/CrossModel/BlastRadius는 G13의 미등록 Operation 목록과 겹침 — 05에 교차참조 추가. (6) 이번 세션에 추가된 실제 Operation(`provider.status`/`provider.usage`, `ModelInvocationLedger`)이 관리자 화면에 반영돼 있지 않았음 — 05 §5에 추가 | FIXED |

G9~G13은 대조 범위가 넓어 이번 라운드에서 발견된 것을 반영했을 뿐, 02·03·06 전체를 계약과 완전히 재대조한 것은 아니다.

### 2026-08-07 3차 대조 (01·05·07) 결과

| # | 항목 | 발견 | 조치 |
|---|---|---|---|
| G15 | 01 목표고객 분류 | 01 §3-1에 6개 세그먼트→4종 `primary_users` 최선 매핑 표를 추가했으나 깨끗한 1:1이 아님을 확인: "규제 산업"은 role이 아니라 industry vertical이라 대응값이 없고, "AI Agent·RAG·LLM 서비스 구축 기업"은 SOFTWARE_DEVELOPERS/PRODUCT_TEAMS 중 불명확하며, "SI·컨설팅·품질관리"와 "발주기관"은 서로 다른 사업관계인데 둘 다 ENTERPRISE_ASSURANCE_TEAMS로 수렴함. `primary_users` enum 확장 vs 01 세그먼트 재정리는 사업 판단이 필요한 미해결 사항으로 명시 | FLAGGED (정밀화됨 — 매핑표+미해결 지점 3건 문서화, 계약 변경은 별도 사업 결정 필요) |
| G16 | 검증 대상 범위 | 01 §3-2에 `supported_target_types` 8종(AI_APPLICATION/AGENTIC_SYSTEM/GENERAL_SOFTWARE/WEB_APPLICATION/API_SERVICE/DESKTOP_APPLICATION/MOBILE_APPLICATION/AUTOMATION_WORKFLOW) 전체가 검증 대상 범위임을 명시 — Desktop/Mobile/Automation Workflow 포함 | FIXED |
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

### G29 — Target AI Auto-Learning (2026-08-08 흡수 완료)
`docs/v2/09_TARGET_AI_AUTO_LEARNING_BUSINESS_AND_DEVELOPMENT_STRATEGY.md`(1216줄, 커밋 SHA `5a761ccf...`에 고정된 NON_FINAL 설계 보완안)가 `docs/master`에 전혀 없는 새 사업 축을 정의한다.

- **Program Understanding Learning**(=이 설계서의 OLearning)과 **Target AI Auto-Learning**(대상 프로그램 내부의 RAG/Prompt/Agent/Tool선택정책/예측·분류·추천·비전 Model을 실제 데이터로 재학습·개선)을 명확히 별개 기능으로 구분한다. 목적·입력·산출물·비용·위험·라이선스 단위가 전부 다르다.
- 확장된 파이프라인을 제안한다: `Understand → Verify → Diagnose → Decide → Improve or Train → Independently Re-verify → Prove → Deploy → Observe → Re-learn` — 이 설계서 00의 `Understand → Plan → Review → Verify → Improve → Prove → Remember`와 다르다. 특히 **Diagnose**(별도 단계로 분리, docs/07 Diagnosis Engine과 같은 방향), **Deploy/Observe/Re-learn**(운영 배포 후 피드백 루프)이 이 설계서에는 아예 없다.
- 사업성 평가를 솔직하게 NON_FINAL로 명시하고("사업기회는 유효하지만 아직 사업성이 입증된 것은 아니다"), 초기엔 RAG·Prompt 개선과 AI 생성 코드 안정화부터 유료 Case로 검증하라고 제안한다.

**결정 완료(2026-08-08)**: 사용자가 Target AI Auto-Learning을 정식 채택으로 확정. `docs/master` 00·01·02·03·04·05·06 전체에 OTraining 프로그램으로 흡수했다.

- 00: Program Understanding Learning과 Target AI Auto-Learning 구분, 확장 파이프라인(Diagnose/Decide/Deploy/Observe/Re-learn), OMemory 재귀학습과 OTraining이 "검증된 근거→독립 재검증→승격"이라는 같은 원칙의 두 적용 사례임을 명시(사용자가 "자기 자신의 검증과 학습을 위한 재귀학습이 가능해야 한다"고 별도로 요구한 사항 반영)
- 02: §7-2 OTraining 신설(책임/기능/산출물/수용기준/금지)
- 01: 고객문제·Train & Re-verify 상품·§11-2 단계적 검증(1. RAG/Prompt → 2. AI 코드 안정화 → 3. Agent → 4. Model Fine-tuning 순, GPU 비용이 큰 4단계는 유료 Case로 원가 실측 후 확대)
- 04: TrainingRequest/Run·ModelVersion류 엔티티·상태모델·API·Event 전부 DESIGN_ONLY로 추가(대응 계약 없음, G7·G13과 같은 처리)
- 03: Training Review 규칙(데이터 오염, 평가셋 재사용 금지, 자기참조 승인 금지)
- 05: VS Code Training 화면, Improve/Train 선택(Decide), Model Deployment를 고위험 별도승인에 추가
- 06: EPIC-09, OTraining Fixture

이 흡수로 새로 생긴 항목도 전부 계약 없는 DESIGN_ONLY다 — G7/G13과 마찬가지로 구현 전 `contracts/*.schema.json` 제정이 선행되어야 한다.

### G30 — OMemory 재귀학습의 실제 계약 발견, OTraining 출시 하드 게이트 확정 (2026-08-08)
`contracts/learning-to-application-pipeline.v1.json`과 `contracts/learning-validation-engine.v1.json`을 확인했다. 이 설계서가 구상한 OMemory 재귀학습(MissedFinding 루프)의 **실제, 훨씬 정교한 버전이 이미 계약으로 존재**했다.

- 실제 파이프라인은 `LEARNING_CANDIDATE→VALIDATION_REQUESTED→VALIDATION_RUNNING→VALIDATION_PASSED/FAILED→PROMOTION_REVIEW→PROMOTION_APPROVED→SHADOW_APPLIED→CANARY_APPLIED→STABLE_APPLIED→APPLIED_LOCKED`로, 이 설계서의 5단계보다 세분화되어 있고 SHADOW/CANARY 점진배포가 있다. 02·04를 계약 기준으로 교체했다(G30 조치).
- **가장 중요한 발견**: 학습결과 적용은 3등급(VALIDATION_PACK_APPLY 허용 / ONSURE_RUNTIME_CODE_APPLY 제한허용 / **TARGET_PRODUCT_APPLY 불허**)으로 나뉘고, TARGET_PRODUCT_APPLY(=OTraining이 하는 일 그 자체)는 "ONSure Core가 자신의 승격 경로를 먼저 증명해야 함"을 이유로 **현재 계약상 금지**되어 있다.
- 이는 사용자가 "자기 자신의 검증과 학습을 위한 재귀학습이 가능해야 한다"고 요구한 것과 정확히 같은 방향이며, 이미 실제 계약이 순서를 강제하고 있었다: **OMemory가 자기 자신의 학습결과를 최소 1건 APPLIED_LOCKED까지 승격시키기 전까지 OTraining은 출시 대상이 아니다.** 00·02에 이 하드 게이트를 명시했다.
- 4개 엔진(Learning/Validator/Executor/Governance) 역할 분리와 `hard_invariants`(LEARNING_ENGINE_CANNOT_PASS_VALIDATE_OR_PROMOTE 등)를 02에 반영했다.

06 §11 우선 구현 순서에 이 의존성을 반영했다(11번 OMemory가 APPLIED_LOCKED 1건을 실제로 달성하기 전까지 12번 OTraining 착수 금지).

### G31 — KnowledgePattern 자동 강등 계약 제정 완료 (2026-08-09, C6에서 분리)
`contracts/reusable-pattern-memory.v1.schema.json`에는 승격 임계치(`independent_reproduction_count` 최소 2회)만 있고, 02가 설계한 "재현 실패 누적 시 자동 강등" 메커니즘에 대응하는 계약 필드가 없었다. `reproduction_failure_count`(정수)와 `demotion_threshold`(const 3, 02의 원래 설계값)를 추가하고, `state`를 `REUSABLE_CANDIDATE` 단일 const에서 `[REUSABLE_CANDIDATE, DEMOTED]` enum으로 확장했으며, `allOf`/`if`/`then` 조건절로 failure_count>=3이면 state=DEMOTED, failure_count<=2이면 state=REUSABLE_CANDIDATE를 강제해 fail-closed로 만들었다. 이 계약의 유일한 실제 작성자인 `KnowledgeSeparationService.java`도 새 필수 필드를 채우도록 갱신(신규 candidate는 failure_count=0, threshold=3)해 실제 코드가 계속 스키마 유효 출력을 내도록 확인했다(`KnowledgeSeparationServiceTest` 2/2 통과). 양성 Fixture(`reusable-pattern-memory.demoted.valid.json`)와 음성 Fixture(`reusable-pattern-memory.invalid.json`, failure_count=3인데 state가 REUSABLE_CANDIDATE로 남은 위반 사례)를 등록했다. `validate-structured-contracts.py --require-full` PASS. | FIXED

### G32 — ProgramRiskScore 계약 제정 완료 (2026-08-09, G7에서 분리)
G7이 묶어서 추적하던 5개 엔티티(AcceptanceCertificate, ProgramRiskScore, PolicyPack, NotificationRule, SBOM) 중 ProgramRiskScore만 이번에 실제 계약으로 제정했다. 나머지 4개는 여전히 `DESIGN_ONLY`로 G7에 남는다.

`contracts/program-risk-score.v1.schema.json` 신설: `docs/master/04_ARCHITECTURE_DATA_API_OLICENSE.md` Risk Scoring 절의 공식(`100 - clamp(10*OpenCritical + 4*OpenHigh + 1*OpenMedium + 2*RecentMissedFinding + 1.5*AIGeneratedRatio점수 + 0.5*평균미해결일수, 0, 100)`)이 쓰는 5개 원시 입력(`open_critical_count`, `open_high_count`, `open_medium_count`, `recent_missed_finding_count`, `ai_generated_ratio_score`, `average_unresolved_days_score`)과 산출값(`score`, `grade`)을 필드로 정의했다. 등급 산정(A/B/C/D/E 5개 구간)과 "Critical Finding이 1건이라도 있으면 등급은 C를 초과할 수 없다" 규칙을 `reusable-pattern-memory.v1.schema.json`에 적용된 것과 같은 `allOf`/`if`/`then` 조건절 기법으로 인코딩했다 — 특히 Critical 상한 규칙이 등급 구간 조건절과 동시에 걸릴 때 서로 모순되지 않도록, A/B 구간 조건에 `open_critical_count == 0`을 함께 요구해 두 조건절이 겹치지 않게 분리했다. 양성 Fixture 2건(`program-risk-score.valid.json` 일반 B등급 사례, `program-risk-score.critical-cap.valid.json` — 원시 점수는 90점으로 A 구간에 해당하지만 Critical 1건 때문에 등급이 C로 강제 하향되는 사례)과 음성 Fixture 1건(`program-risk-score.invalid.json` — 동일 조건에서 등급을 A로 둔 위반 사례)을 `contracts/schema-instance-registry.v1.json`에 등록했다. `python3 scripts/validate-structured-contracts.py --require-full`, `python3 scripts/validate-repository-contracts.py` 모두 PASS.

**등급 컷오프 수치(90/75/60/40)는 여전히 C5 기준 DRAFT다** — 이번 작업은 그 DRAFT 수치를 스키마/계약 구조에 그대로 인코딩(`reusable-pattern-memory.v1.schema.json`에 `demotion_threshold`를 추가했을 때와 같은 이 저장소의 관례)한 것일 뿐, 값 자체를 business-confirm한 것이 아니다. 스키마 설명문에도 이 네 컷오프가 확정값이 아니라는 점을 명시했다. 실제로 Finding/MissedFinding 원본 데이터에서 이 점수를 계산해 채우는 로직(생산 연동)은 이 작업 범위 밖이며 별도 후속 작업이다. `src/main/java/`에는 아직 이 계약을 참조하는 코드가 없음을 확인했다. | FIXED (ProgramRiskScore 계약 제정 완료, 등급 컷오프 확정은 C5로 계속 추적)

### G33 — AcceptanceCertificate/PolicyPack/NotificationRule/SBOM 필드 수준 설계 완료 (2026-08-11, G7 마감)
G7이 추적하던 나머지 4개 엔티티 전부 [04_ARCHITECTURE_DATA_API_OLICENSE.md](04_ARCHITECTURE_DATA_API_OLICENSE.md) §5(상태 모델)에 필드 수준 기능정의를 새로 작성했다. **사용자 지시대로 계약(`contracts/*.schema.json`)이나 Java 코드는 만들지 않았다** — 기능 정의와 설계만 완료했다.

- **AcceptanceCertificate/ExternalAcceptorGrant**: `case_id`/`case_revision_number`/`baseline_reference`/`policy_binding`/`decision_summary`(quality_decision, open_finding_counts_by_severity, coverage_percent, program_risk_score/grade)/`completeness_disclaimer`(신규, 필수 — `final_claim_allowed: const false`와 같은 근거를 이 인증서도 우회하지 않도록 강제)/`signature`/`status`(ISSUED→REVOKED) 필드 정의. ExternalAcceptorGrant는 `scope`(VIEW_CERTIFICATE_VERIFICATION_ONLY/VIEW_DELIVERY_READONLY)까지 분리
- **PolicyPack/PolicyPackVersion**: 부모(PolicyPack)/자식(PolicyPackVersion) 분리, `rules`(category/severity/additive_only:true 강제), `regulatory_framework_mappings`(E1 필드화), `golden_fixture_regression_receipt_reference`, `status`(DRAFT→PENDING_REGRESSION→REGRESSION_PASSED→ACTIVE→SUPERSEDED, G3의 ComponentContract 어휘 재사용). **정직한 확인**: `contracts/dependency-license-policy.v1.json`이 ONSure 자체 빌드용으로 이미 같은 모양(SPDX id→ALLOWED/FORBIDDEN+사유)의 실제 계약을 갖고 있어, 향후 PolicyPack 계약 제정 시 라이선스 규칙 부분의 선례로 재사용해야 한다고 명시(새로 발명 금지)
- **NotificationRule/NotificationEvent/NotificationDeliveryReceipt**: 02 §10-1이 이름만 나열했던 세 산출물에 필드를 채움. `subscribed_event_types`(8종 고정), `batching`(IMMEDIATE/DAILY_DIGEST), `delivery_status`(PENDING→DELIVERED/FAILED→RETRYING→DELIVERED/DEAD_LETTERED), `fallback_triggered`
- **SBOM(대상 Program용)**: `program_profile_id`/`baseline_reference`/`format`(CYCLONEDX_1_5, DRAFT)/`ecosystems`/`components`/`transitive_resolution_status`(DIRECT_ONLY/FULL_TREE, DRAFT) 필드 정의. **정직한 확인(코드까지 실제로 읽음)**: ONSure는 이미 실제로 동작하는 `SbomGenerator.java`(+ `SbomGeneratorTest.java`, 4개 테스트 통과)를 갖고 있어 CycloneDX 1.5 JSON을 생성하고 `contracts/assurance-lanes.v1.json`의 `ORUDA_BUILD` Lane `required_outputs`(`"sbom"`)를 충족한다 — 하지만 이는 **ONSure 자신의 Maven 빌드 공급망 자기증명**이며 고객의 임의 대상 Program(다양한 언어/패키지 매니저)을 분석하는 이 엔티티와는 범위가 다르다. 같은 이름의 서로 다른 두 엔티티를 혼동하지 않도록 04에 명시했고, 재사용 가능한 것은 출력 포맷과 라이선스 보강 패턴뿐이라는 것도 밝혔다.

**06 §11 우선구현순서 재정렬**: 4개 항목을 기존 1~12번 번호를 유지한 채(11/12번이 G30에서 이미 번호로 참조되고 있어 보존 필요) 하위번호로 삽입했다 — SBOM은 3-1(OReview의 Dependency 리뷰가 이미 요구하는 인벤토리라 가장 이른 지점, ONSure 자체 SbomGenerator 선례가 있어 위험 최저), NotificationRule은 5-1과 AcceptanceCertificate는 5-2(둘 다 Web Learn & Verify Case가 실제 상태를 갖기 시작해야 의미가 생김, AcceptanceCertificate는 DELIVERY_ACCEPTED 도달이 선행조건), PolicyPack은 10-1(Enterprise 전용 유료 기능이라 10번에 종속, 신규 버전 승격은 7번 OMemory의 Golden Fixture 회귀를 재사용하므로 7번에도 종속되는 유일한 항목).

**04 §4 데이터 엔터티 목록**과 "아직 계약이 없는 이 설계서의 확장" 절도 4개 엔티티를 굵게 표시하고 이 절로 교차참조하도록 갱신했다. G13(Workflow Operation Registry 미등록)과 G14 관계는 그대로 유지된다 — API Operation 등록은 이번 작업 범위 밖이다. | FIXED (필드 수준 설계 완료, 계약 제정·코드 구현은 별도 후속 작업)

## F. 문서 거버넌스 (참고, 결정 아님)

| # | 항목 | 현재 상태 | 위치 |
|---|---|---|---|
| F1 | `status/design-conflict-register.v1.json`의 CONFLICT-003/005/006이 여전히 `docs/05`를 authority로 인용 | 무관한 진행 중 작업이라 미수정 | [ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md §0](../architecture/ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md) |
| F2 | 229개 파일 규모의 기존 staged 변경(패키지 리네임 `io.onsure`→`kr.co.oruda.onsure` 등)이 일부 파일에서 디렉터리만 옮기고 `package` 선언은 갱신 안 됨 (예: `LocalAuthenticatedApiServerTest.java`가 `kr/co/oruda/...` 경로에 있으나 `package io.onsure.platform;`) | 빌드 깨질 가능성 높음, 커밋 보류 권장 | 리포 루트 (제가 수정하지 않음) |
