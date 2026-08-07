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
| C6 | KnowledgePattern 강등 임계치 | False Positive 3회 이상 | [02](02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md), [04](04_ARCHITECTURE_DATA_API_OLICENSE.md) | DRAFT |
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

## F. 문서 거버넌스 (참고, 결정 아님)

| # | 항목 | 현재 상태 | 위치 |
|---|---|---|---|
| F1 | `status/design-conflict-register.v1.json`의 CONFLICT-003/005/006이 여전히 `docs/05`를 authority로 인용 | 무관한 진행 중 작업이라 미수정 | [ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md §0](../architecture/ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md) |
| F2 | 229개 파일 규모의 기존 staged 변경(패키지 리네임 `io.onsure`→`kr.co.oruda.onsure` 등)이 일부 파일에서 디렉터리만 옮기고 `package` 선언은 갱신 안 됨 (예: `LocalAuthenticatedApiServerTest.java`가 `kr/co/oruda/...` 경로에 있으나 `package io.onsure.platform;`) | 빌드 깨질 가능성 높음, 커밋 보류 권장 | 리포 루트 (제가 수정하지 않음) |
