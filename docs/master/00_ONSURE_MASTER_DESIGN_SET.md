# ONSure Master Design Set

## 1. 문서 목적
이 문서는 ONSure의 사업·제품·서비스·프로그램·라이선스·아키텍처·개발·리뷰·검증·개선·시험·운영을 하나의 개발 기준선으로 통합한다. ONSure는 ORUDA의 하위 프로그램이 아니라 독립 판매 제품이며, ORUDA/OLicense는 ONSure의 라이선스 생성·계약·Entitlement·Credit·감사 원장을 담당하는 중앙 상용 라이선스 서비스로 연계된다.

## 2. 제품 정의
ONSure는 AI가 작성하거나 AI를 포함한 소프트웨어를 대상으로 다음 전 과정을 수행하는 AI Software Engineering Assurance Platform이다.

Understand → Plan → Review → Verify → Improve → Prove → Remember

- Understand: 프로그램, 요구사항, 정책, 구조, AI 구성, 실행 특성을 학습한다.
- Plan: 검토·검증·개선 범위와 순서를 수립한다.
- Review: 요구사항부터 코드와 테스트까지 변경 전후의 적정성을 검토한다.
- Verify: 실제 요구사항·정책·보안·동작·성능 충족 여부를 실행 증거로 판정한다.
- Improve: 검증 Finding에서 시작해 RCA, Patch, Regression을 수행한다.
- Prove: Evidence와 Receipt로 결과를 재현 가능하게 고정한다.
- Remember: 유효했던 개선과 실패 패턴을 재사용 가능한 지식으로 축적한다.

## 3. 독립성 원칙
- ONSure의 제품 정의, 실행 구조, 고객 데이터, 릴리스는 ORUDA에 종속되지 않는다.
- ONSure는 독립 저장소, 독립 배포, 독립 상품, 독립 SLA를 가진다.
- OLicense 연계가 중단되어도 계약된 Offline Grace 정책 범위에서 제한 실행할 수 있다.
- ONSure는 라이선스를 생성하거나 임의 변경하지 않는다. Validate, Consume, Report만 수행한다.

## 4. 상품 체계
### 4.1 Web One-time Service
- Learn
- Verify
- Learn & Verify
- 후속 Improve & Re-verify

Web의 1회는 버튼 실행 횟수가 아니라 Service Case 단위다.

Service Case = System + Programs + Baseline + Scope + Capacity + Validity + Deliverables

### 4.2 VS Code Continuous Subscription
- Developer
- Team
- Enterprise
- Unlimited Systems & Programs

VS Code는 지속 학습, 지속 리뷰, 지속 검증, 지속 개선을 제공한다.

## 5. 프로그램 구성
- OLearning: Repository와 실행 구조를 학습하고 Program Profile을 생성한다.
- OPlanning: 검토·검증·개선 계획과 실행 순서를 생성한다.
- OReview: 요구사항·설계·정책·코드·AI·보안·테스트·품질·Merge 리뷰를 수행한다.
- OVerification: 정적·동적·시나리오·적대·회귀 검증을 수행한다.
- OImprovement: Finding 기반 RCA와 Patch를 생성하고 회귀검증한다.
- OEvidence: 입력·정책·결과·실행환경·해시·Receipt를 관리한다.
- OGit: Worktree, Branch, Commit, Push, Draft PR, CI 상태를 관리한다.
- ODelivery: 보고서, Patch, PR, Evidence Pack, Program Profile을 납품한다.
- OLicense Adapter: ORUDA/OLicense의 Entitlement와 Credit을 검증·소비·보고한다.

## 6. 핵심 경계
### Review와 Verification
Review는 구현 또는 변경이 적절한지 판단한다. Verification은 요구사항과 정책을 실제로 만족하는지 증거로 판정한다. Review PASS가 Verification PASS를 의미하지 않으며, 두 결과는 독립 저장한다.

### Improvement 시작 조건
개선은 임의 코딩 요청에서 시작하지 않는다. 반드시 검증된 Finding 또는 승인된 Review Finding에서 시작한다.

### Learning 경계
학습은 범용 기업 지식관리 제품으로 확장하지 않는다. Program Learning, Behavior Learning, Improvement Learning에 한정한다.

## 7. 주요 산출물
- Business Plan
- Product Requirement Document
- Service Policy
- Program Specification
- OReview Specification
- OVerification Specification
- OImprovement Specification
- UI/UX Specification
- Architecture and Data Model
- API/Event/Token Contract
- OLicense Integration Contract
- Security and Privacy Design
- Test Strategy and Fixtures
- Operation and Deployment Runbook
- Epic/Capability/Story Backlog

## 8. 출시 Gate
다음이 모두 충족되어야 상용 출시 후보가 된다.
- 요구사항 Traceability 100%
- Critical/High 미해결 결함 0건
- Web Full-Chain 연속 2회 PASS
- VS Code Full-Chain 연속 2회 PASS
- OLicense 발급·정지·만료·폐기·Offline 시나리오 PASS
- Code Review와 Independent Review 완료
- 보안·개인정보·소스 삭제 검증 PASS
- 운영 복구 및 Rollback 시험 PASS

## 9. 비최종 상태
문서 작성, 코드 구현, 단위시험, PR 생성만으로 Final PASS를 선언하지 않는다. 실제 환경 E2E, 독립 리뷰, Evidence 고정 전까지 모든 결과는 NON_FINAL이다.