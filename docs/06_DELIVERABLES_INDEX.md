# ONSURE 전체 산출물 대장

이 문서는 ONSURE 독립 제품의 설계·계약·구현·시험·운영 산출물을 관리한다. 문서 완료와 구현 완료를 분리하며, 실제 실행 증거 전에는 PASS를 주장하지 않는다.

## 0. 권위·추적성

- [x] 설계 권위와 문서 우선순위
- [x] 구현·검증 상태 용어 계약
- [x] Core와 Optional Target Adapter 경계
- [x] 제품·검증·개선·Git·출시 State Machine 분리와 Mapping
- [x] Requirement → Design → Contract → Code → Test → Evidence 추적성 기준선
- [x] 상세설계 Gap 검증 보고서
- [x] 구현 Matrix
- [x] 설계 충돌 대장
- [x] 누락 기능 대장
- [x] 단일 실행기와 Runbook

권위 파일:

- `docs/architecture/ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md`
- `contracts/status-vocabulary.v1.json`
- `contracts/core-extension-boundary.v1.json`
- `contracts/state-model-mapping.v1.json`
- `contracts/requirements-traceability.v1.json`
- `status/implementation-matrix.v1.json`

## A. 사업·제품

- [x] 제품 기준선
- [x] 사업계획서
- [ ] 시장·경쟁 분석
- [ ] 고객 세그먼트와 페르소나
- [ ] 제품·Edition·가격 확정
- [ ] 영업·파트너 전략
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
- [x] 증거 결속형 프로그램 학습 방법론
- [x] 학습 후보 상태·승격·롤백 상위 계약
- [x] Program Profile JSON Schema
- [x] Behavior Profile JSON Schema
- [x] Failure Memory JSON Schema
- [x] Improvement Memory JSON Schema
- [x] 프로젝트 기억과 범용 패턴 분리 원칙
- [ ] 실제 Repository Understanding Engine
- [ ] 실제 Behavior Observation Engine
- [ ] 증분 학습과 Profile Revision Runtime
- [ ] 독립 승격·폐기·롤백 Runtime

## D. 검증 체계

- [x] 검증 범위와 판정 기준
- [x] 정상·경계·실패·적대 시나리오 체계
- [x] 코드·프롬프트·RAG·도구·모델 검증 상위 계약
- [x] AI 행동 회귀검증 기준
- [x] RCA 상위 계약
- [x] 독립 판정과 자기검증 방지 원칙
- [x] 내부 책임 분리 계약
- [x] 상세 Evidence Receipt JSON Schema
- [x] 범용 Fixture·Harness 부분 구현
- [ ] 실제 Build·API·Container·Performance·Recovery 검증
- [ ] 실제 Model·Agent·Tool·RAG 행동 검증
- [ ] Trace 기반 RCA 확정 Runtime

## E. 자동 보완 개발

- [x] 수정 유형과 허용 범위
- [x] 위험도 분류와 자동 적용 정책
- [x] Patch·Branch·Commit 상위 계약
- [x] Before/After 상위 기준
- [x] 회귀 차단과 롤백 정책
- [ ] 실제 Patch 생성
- [ ] 파일·Hunk 승인
- [ ] Worktree 적용과 Rollback
- [ ] Test 생성·수정
- [ ] 동일 환경 Before/After 기계 판정

## F. 아키텍처·설계

- [x] 논리 아키텍처
- [x] Developer·Team·Enterprise 배포 아키텍처
- [x] 컴포넌트 책임과 경계
- [x] 내부 프로그램 책임 분리 기준
- [x] 핵심 데이터 객체
- [x] API·CLI·IDE 인터페이스 원칙
- [x] 실행 상태 모델 상위 정의
- [x] 제품·검증·출시 State Machine 명시적 Mapping
- [x] 모델 Provider 추상화 원칙
- [ ] Java Runtime 상태 모델 Migration
- [ ] 상세 DB 모델
- [x] 배포·DB migration 비최종 운영 경계 계약
- [x] bubblewrap 실행환경 진단과 구성 가이드
- [ ] Local Authenticated API 명세
- [ ] 멀티테넌시·격리 상세 설계

## G. VS Code·Claude형 환경

- [x] VS Code 정보구조
- [x] Chat·Ask·Plan·Act·Autopilot 정의
- [x] Program Profile·Review·Verification·Improvement View 설계
- [x] Diff·승인·Git & PR 사용자 흐름 설계
- [x] 상태 지속성과 재접속 복구 기준
- [ ] 실제 VS Code Extension Shell
- [ ] Command·Contribution 명세
- [ ] View·Webview·인증·승인 UI
- [ ] 접근성·단축키·오류 UX
- [ ] Extension Host Full-Chain 시험

## H. Git·변경관리

- [x] Dirty Workspace 보호 원칙
- [x] Worktree·Branch 정책
- [x] Diff·Patch·Commit 계약
- [x] Push·Draft PR·CI 연결 설계
- [x] Merge·Rollback 정책
- [ ] 실제 Worktree·Branch·Commit·Push·Draft PR Engine
- [ ] GitHub Adapter 상세 API
- [ ] GitLab Adapter 상세 API

## I. 개발·시험

- [x] 구현 Phase 로드맵
- [x] 단위·통합·E2E 시험 범주
- [x] 필수 E2E 시나리오
- [x] 보안·적대·장애·복구 시험 원칙
- [x] MVP Full-Chain 수용 기준
- [x] Repository 정적 계약 검증기
- [x] Profile 기반 단일 실행기
- [ ] 저장소 구조와 코딩 규칙
- [ ] Golden·비공개 Fixture 상세 계획
- [ ] 정량 성능 목표
- [ ] 독립 기술검토·Blind Review 운영 절차
- [ ] 실제 저장소 Full-Chain 2회

## J. 운영·상용화

- [x] 상품·Edition 상위 구조
- [x] 출시 단계와 출시 관문
- [x] 가격 구성 원칙
- [x] PoC 성공 기준
- [x] KPI와 출시 금지 조건
- [ ] Web·Commerce·Payment·Refund Runtime
- [ ] OLicense 전 수명주기 Runtime
- [ ] Identity·Tenant·Sandbox·Queue·Storage
- [ ] 설치·구성 가이드
- [ ] 사용자·관리자 매뉴얼
- [ ] SLA·지원 정책
- [ ] 개인정보·데이터 보존·Legal Hold 정책
- [ ] 릴리스·업그레이드·호환성 정책
- [x] 배포 runtime·DB migration 도입 전 fail-closed 설계 기준

## K. ORUDA 관계

- [x] ONSURE Standalone 독립 원칙
- [x] ORUDA를 선택형 Target Adapter로 정의
- [x] Core Preflight와 ORUDA Profile 분리
- [x] ValidationEngine 기본 Adapter 등록과 선택형 ORUDA 등록 분리
- [ ] Runtime 실행 증거
- [ ] ORUDA Embedded/OEM 라이선스 계약
- [ ] ORUDA Adapter 독립 패키징

## Codespace 실행 정책

Codespace 또는 동등 실행환경은 모든 Codespace-free 설계·계약·코드·PR 보완이 끝난 뒤 마지막에 사용한다.

최종 단일 명령:

```bash
bash scripts/onsure-one-shot.sh --profile core
```

ORUDA Adapter는 필요할 때만 다음 명령으로 별도 검증한다.

```bash
bash scripts/onsure-one-shot.sh --profile oruda
```

## 현재 판정

- Standalone product Full-Chain: `BLOCKED`
- Current source canonical execution: `PASS_NONFINAL`
- Rootless bubblewrap: `BLOCKED_ENVIRONMENT / BWRAP_LOOPBACK_PERMISSION_DENIED`
- Local OCI validation fallback: `PASS_NONFINAL`; immutable local image, network none, read-only
  rootfs, capability 0. 배포 topology와 Production authority는 변경하지 않음
- Independent OTester/OAudit: `NOT_RUN`
- FinalLock: `false`
- Production GO: `false`
- Commercial GO: `false`
