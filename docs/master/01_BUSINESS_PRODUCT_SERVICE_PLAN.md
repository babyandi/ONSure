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

## 3. 목표 고객
- AI Coding을 도입한 일반 기업 개발조직
- AI Agent·RAG·LLM 서비스를 구축하는 기업
- 금융·공공·의료 등 규제 산업
- SI·컨설팅·품질관리 회사
- AI로 제품을 만든 비전문 개발자와 스타트업
- 고객 소프트웨어를 인수·검수해야 하는 발주기관

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

## 6. Web 상품 정의
### Learn
입력: Source, Configuration, Prompt, RAG, Tool, Test, Document, 선택 로그
처리: 구조·의존성·배포·AI 구성·행동 특성 학습
산출물: Program Profile, Architecture Map, AI Component Map, Dependency Inventory, Unknown/Conflict List
제외: 결함 판정과 자동 수정

### Verify
입력: 고정 Baseline, 요구사항, 정책, 검증팩
처리: 정적·동적·시나리오·적대·회귀 검증
산출물: Finding, Severity, RCA 후보, Evidence, Verification Report
제외: Program Profile 납품과 자동 Patch

### Learn & Verify
Program Profile을 만든 뒤 해당 구조와 위험에 맞게 검증 시나리오를 생성한다. 일반 고객의 대표 상품으로 둔다.

### Improve & Re-verify
검증된 Finding 중 고객이 승인한 항목만 대상으로 RCA, Patch, Regression, Before/After Evidence를 제공한다.

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

## 12. 사업 위험과 대응
- AI 원가 급증: Credit, Hard Stop, 모델별 원가 Meter
- 오탐 불신: Evidence, 재현, Confidence, 인간 승인
- 소스 유출 우려: 격리, 암호화, 최소보존, 삭제증명
- 자동 수정 사고: Finding 기원 제한, Worktree, Diff 승인, Rollback
- 라이선스 장애: Signed Snapshot, Offline Grace, Revocation 방어
- 범위 분쟁: Baseline·System·Program·Case 계약 고정