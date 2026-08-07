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

G9~G13은 대조 범위가 넓어 이번 라운드에서 발견된 것을 반영했을 뿐, 02·03·06 전체를 계약과 완전히 재대조한 것은 아니다. `docs/master/01·07` 및 05의 나머지 부분은 아직 이 수준의 대조를 거치지 않았다.

## F. 문서 거버넌스 (참고, 결정 아님)

| # | 항목 | 현재 상태 | 위치 |
|---|---|---|---|
| F1 | `status/design-conflict-register.v1.json`의 CONFLICT-003/005/006이 여전히 `docs/05`를 authority로 인용 | 무관한 진행 중 작업이라 미수정 | [ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md §0](../architecture/ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md) |
| F2 | 229개 파일 규모의 기존 staged 변경(패키지 리네임 `io.onsure`→`kr.co.oruda.onsure` 등)이 일부 파일에서 디렉터리만 옮기고 `package` 선언은 갱신 안 됨 (예: `LocalAuthenticatedApiServerTest.java`가 `kr/co/oruda/...` 경로에 있으나 `package io.onsure.platform;`) | 빌드 깨질 가능성 높음, 커밋 보류 권장 | 리포 루트 (제가 수정하지 않음) |
