# ONSURE 전체 산출물 대장

이 문서는 ONSURE를 독립형 AI 프로그램 학습·검증·보완 제품으로 재정의한 뒤 다시 작성해야 하는 전체 산출물을 관리합니다.

## A. 사업·제품

- [x] 제품 기준선
- [x] 사업계획서
- [ ] 시장·경쟁 분석
- [ ] 고객 세그먼트와 페르소나
- [ ] 제품·Edition·가격 정책
- [ ] 영업전략과 파트너 전략
- [ ] 투자·수익·비용 계획
- [x] 사업 위험과 대응 원칙

## B. 제품 기획

- [x] 제품 요구사항 정의서
- [x] 사용자 여정과 핵심 사용 시나리오
- [x] 기능 목록과 우선순위 기준
- [x] MVP 범위와 제외 범위
- [x] 비기능 요구사항
- [x] 권한·승인·자동화 수준 정책
- [ ] 제품 용어 사전

## C. 학습 체계

- [x] Program Learning 상위 정의
- [x] Behavior Learning 상위 정의
- [x] Improvement Learning 상위 정의
- [ ] Program Model JSON Schema
- [ ] Behavior Model JSON Schema
- [ ] Failure Memory JSON Schema
- [ ] Improvement Memory JSON Schema
- [x] 프로젝트 기억·범용 패턴 분리 원칙
- [x] 학습 후보 생성·검증·승격·롤백 상위 계약

## D. 검증 체계

- [x] 검증 범위와 판정 기준
- [x] 정상·경계·실패·적대 시나리오 체계
- [x] 코드·프롬프트·RAG·도구·모델 검증 상위 계약
- [x] AI 행동 회귀검증 기준
- [x] 근본원인분석 상위 계약
- [x] 독립 판정과 자기검증 방지 원칙
- [x] Evidence Ledger 상위 계약
- [ ] 세부 Receipt JSON Schema

## E. 자동 보완 개발

- [x] 수정 유형과 허용 범위
- [x] 위험도 분류와 자동 적용 정책
- [x] 코드·프롬프트·RAG·도구·설정 보완 상위 계약
- [x] Patch·Branch·Commit·PR 생성 계약
- [x] Before/After 개선 비교 기준
- [x] 회귀 차단과 롤백 정책

## F. 아키텍처·설계

- [x] 논리 아키텍처
- [x] Developer·Team·Enterprise 배포 아키텍처
- [x] 컴포넌트 책임과 경계
- [x] 핵심 데이터 객체
- [x] API·CLI·IDE 인터페이스 원칙
- [x] 실행 상태 모델
- [x] 모델 공급자와 실행 엔진 추상화
- [ ] 상세 DB 모델
- [ ] Local API 명세
- [ ] 멀티테넌시·격리 상세 설계

## G. VS Code·Claude형 환경

- [x] VS Code 정보구조
- [x] Chat·Ask·Plan·Act·Autopilot 정의
- [x] Program Profile·Learning·Verification·Improvement View
- [x] Diff·승인·Git & PR 사용자 흐름
- [x] 상태 지속성과 재접속 복구 기준
- [ ] 상세 화면 Wireframe
- [ ] VS Code Extension 명령·Contribution 명세
- [ ] 접근성·단축키·오류 UX

## H. Git·변경관리

- [x] Dirty Workspace 보호
- [x] Worktree·Branch 정책
- [x] Diff·Patch·Commit 계약
- [x] Push·Draft PR·CI 연결
- [x] Merge·Rollback 정책
- [ ] GitHub Adapter 상세 API
- [ ] GitLab Adapter 상세 API

## I. 개발·시험

- [x] 구현 Phase 로드맵
- [x] 단위·통합·E2E 시험 범주
- [x] 필수 E2E 시나리오
- [x] 보안·적대·장애·복구 시험 원칙
- [x] MVP Full-Chain 수용 기준
- [ ] 저장소 구조와 코딩 규칙
- [ ] Golden·비공개 Fixture 상세 계획
- [ ] 성능 목표 수치
- [ ] 독립 기술검토·Blind Review 절차

## J. 운영·상용화

- [x] Developer·Team·Enterprise·Assessment·Embedded Edition
- [x] 출시 단계와 출시 관문
- [x] 가격 구성 원칙
- [x] PoC 성공 기준
- [x] KPI와 출시 금지 조건
- [ ] 설치·구성 가이드
- [ ] 사용자·관리자 매뉴얼
- [ ] SLA·지원 정책
- [ ] 개인정보·데이터 보존 정책
- [ ] 릴리스·업그레이드·호환성 정책
- [ ] 제품 소개서·IR·제안서·데모 자료

## K. ORUDA 관계

- [x] 현재 제품·기술·판매상 무관계 원칙
- [x] 향후 Embedded/OEM 적용 원칙
- [x] ONSURE Standalone 기준 구현 원칙
- [ ] 기존 저장소 내 ORUDA 전용 표현·계약 전수 제거
- [ ] ORUDA 내장 시 별도 Adapter·라이선스 계약

## 현재 기준 문서

- `README.md`
- `docs/00_PRODUCT_BASELINE.md`
- `docs/01_BUSINESS_PLAN.md`
- `docs/02_CLAUDE_LIKE_WORK_ENVIRONMENT.md`
- `docs/03_GIT_AND_CHANGE_GOVERNANCE.md`
- `docs/04_IMPLEMENTATION_ROADMAP_VSCODE_AGENT_GIT.md`
- `docs/05_PRODUCT_REQUIREMENTS_AND_ACCEPTANCE.md`
- `docs/07_CORE_ARCHITECTURE_AND_STATE_MODEL.md`
- `docs/08_TEST_COMMERCIALIZATION_AND_RELEASE_PLAN.md`

## 실행 대장

실제 구현은 GitHub Issue #2의 L0~L7 Lane으로 관리합니다.

## 재작성 원칙

1. ONSURE는 독립 판매되는 완결형 제품입니다.
2. ORUDA는 현재 ONSURE의 고객, 구성요소, 첫 검증 대상 또는 필수 의존성이 아닙니다.
3. ONSURE의 중심은 검증만이 아니라 프로그램 학습·행동 학습·개선 학습입니다.
4. 자동 보완 개발은 학습 또는 검증으로 확인된 개선 필요에서 시작합니다.
5. VS Code·CLI·Local Runtime·Git Full-Chain은 핵심 제품 범위입니다.
6. 기업 전체 학습, 범용 신규 개발, 전사 운영 플랫폼으로 범위를 확대하지 않습니다.
7. 향후 ORUDA 내장 가능성은 Embedded/OEM 배포 시나리오로만 기술합니다.
8. 문서 완료와 구현 완료를 혼동하지 않으며 실제 시험 전에는 `PASS`를 주장하지 않습니다.
