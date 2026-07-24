# ONSure V2 사업계획서

## 1. Executive Summary

ONSure는 AI로 개발되었거나 AI 기능을 포함한 프로그램을 자동 학습하고, 실제 실행 결과를 검증하며, 확인된 결함과 부족함을 자동 보완한 뒤 개선 효과를 입증하는 독립 상용 제품이다.

ONSure는 서로 다른 고객 요구를 두 개의 사업모델로 수용한다.

- **ONSure Web**: 특정 시점의 특정 시스템을 학습·검증·개선하여 결과를 납품하는 1회성 서비스
- **ONSure for VS Code**: 개발과 변경이 계속되는 프로그램을 증분 학습·반복 검증·지속 개선하는 구독형 개발환경

라이선스는 ORUDA의 `OLicense`가 중앙에서 생성·관리한다. ONSure는 OLicense가 발급한 Entitlement를 검증하고 기능·한도·사용량을 집행한다.

## 2. 시장 문제

AI 코딩 도구와 생성형 AI 응용프로그램은 개발 속도를 높였지만 다음 문제를 확대했다.

- 개발자가 생성된 코드와 실제 동작을 충분히 이해하지 못한다.
- 코드, Prompt, RAG, Tool, Model 설정을 하나의 프로그램으로 검증하기 어렵다.
- 기존 테스트는 정상 경로에 치우치고 실패·경계·적대 조건을 놓친다.
- 프로그램을 수정해도 실제 품질이 좋아졌는지 객관적으로 입증하기 어렵다.
- 동일한 장애와 수정 경험이 프로젝트별로 반복 소모된다.
- 외주·AI 생성 프로그램을 인수하는 고객은 독립 검증 수단이 부족하다.
- 소규모 고객은 지속형 도구보다 한 번의 전문 검증·개선 결과를 원한다.
- 지속 개발팀은 매 변경마다 다시 학습·검증해야 하므로 일회성 서비스만으로 부족하다.

## 3. 제품 해법

```text
Understand
→ Learn
→ Verify
→ Diagnose
→ Improve
→ Re-verify
→ Prove
→ Remember
```

ONSure는 대상 프로그램마다 다음 자산을 생성한다.

- Program Profile: 목적·구조·기능·인터페이스·의존성
- Behavior Profile: 정상·실패·변동·취약 조건
- Verification Baseline: 기대 동작·허용 범위·시험 기준
- Failure Memory: 재현 조건·원인·영향·회귀시험
- Improvement Memory: 수정 내용·효과·재사용 가능한 패턴
- Evidence Ledger: 입력·환경·명령·결과·판정 근거

## 4. 사업모델

### 4.1 웹 1회성 서비스

고객은 `Learn`, `Verify`, `Learn & Verify` 중 하나를 선택한다. 검증 결과에서 실제 개선이 필요한 경우 `Improve & Re-verify`를 후속 주문한다.

웹서비스는 구독이 아니라 하나의 `Service Case`를 구매하는 구조다.

```text
대상 시스템·프로그램 접수
→ 무료 또는 유상 Preflight
→ 범위·학습량·검증팩 산정
→ 견적·결제
→ 기준선 Lock
→ 서비스 실행
→ 결과 납품
→ Case 종료
```

주요 고객:

- AI로 프로그램을 만든 개인·스타트업
- 외주 개발 결과를 인수하는 발주사
- 출시 전 품질·보안 검증이 필요한 조직
- 장애·정책 문제를 특정 시점에 해결하려는 기업
- PoC·검수·감사 자료가 필요한 고객

### 4.2 VS Code 지속형 구독

개발자가 VS Code 안에서 저장소를 지속 학습시키고, 변경마다 검증하며, 승인된 Patch를 적용하고 Git·PR까지 처리한다.

```text
Workspace 연결
→ 최초 학습
→ 변경 감지·증분 학습
→ 반복 검증
→ Finding·RCA
→ 승인된 개선
→ 회귀검증
→ Commit·PR
→ Program Memory 갱신
```

주요 고객:

- AI Agent·RAG 제품팀
- 반복 Release가 있는 개발팀
- 다수 프로그램을 운영하는 SI·솔루션 회사
- 품질·보안·내부통제가 필요한 기업

## 5. 서비스 포트폴리오

### Web

1. Learn
2. Verify
3. Learn & Verify
4. Improve & Re-verify
5. 전문가 검토, 긴급 처리, 규제·보안 검증팩

### VS Code

1. Developer
2. Team
3. Enterprise
4. Unlimited Systems & Programs 옵션

상품 단계는 최소화하고 실제 가격은 대상 규모와 사용량에 따라 조정한다.

## 6. 수익모델

### 웹

```text
기본 Service Case 가격
+ 학습량
+ 프로그램 규모
+ 검증팩
+ 개선 작업량
+ 추가 재검증
+ 전문가 검토
+ 긴급 납기·격리환경
```

### VS Code

```text
기본 구독 Plan
+ Seat
+ Active System·Program Capacity
+ ONSure Credit
+ 추가 Storage·동시 실행
+ Support Level
```

### 추가 수익

- Enterprise 온프레미스 라이선스
- 폐쇄망·Offline License
- 산업별 정책·검증팩
- 전문 Assessment·컨설팅
- OEM·Embedded 라이선스
- 전용 모델·전용 실행 인프라

## 7. 라이선스 전략

라이선스는 OLicense가 발급한다.

- Web Case License: 특정 Case·기준선·범위·기간에 결속
- VS Code Subscription: Seat·기능·시스템·프로그램·Credit에 결속
- Unlimited: 시스템·프로그램 수만 무제한, 실행량과 지원은 계약 한도
- 기능 권한은 UI 표시뿐 아니라 서버·Local Runtime에서 재검증
- Credit은 `Reserve → Commit → Release` 방식으로 처리

## 8. 시장 진입 전략

### 1단계: 웹 1회성 검증·개선

진입 장벽이 낮고 결과가 명확한 웹 Case로 초기 고객과 실제 실패·개선 데이터를 확보한다.

### 2단계: VS Code 전환

웹 Case 완료 고객 중 지속 개발 수요가 있는 고객에게 초기 Program Profile 이전 혜택을 제공한다.

### 3단계: Team·Enterprise

공유 정책, CI/CD, 승인 Workflow, 감사 Evidence, 온프레미스를 제공한다.

### 4단계: 산업별 확장

금융·공공·의료·교육 등 규제산업용 검증팩과 전문 서비스로 확장한다.

## 9. 경쟁 차별화

- 정적 분석보다 넓게 코드·Prompt·RAG·Tool·Model 행동을 함께 본다.
- 테스트 자동화와 달리 프로그램을 먼저 학습하고 필요한 시나리오를 생성한다.
- 평가 도구와 달리 RCA·보완·재검증·개선 입증까지 수행한다.
- 범용 코딩 Agent와 달리 자동 개발은 검증된 결함과 측정된 부족함에서만 시작한다.
- 웹 1회성과 VS Code 지속형을 하나의 Program Memory·라이선스 체계로 연결한다.

## 10. 핵심 KPI

### 웹

- Preflight에서 유상 Case 전환율
- Case당 평균 매출·원가·마진
- Program Profile 생성시간
- 유효 Finding 발견률
- 개선 주문 전환율
- 납기 준수율
- 결과 재사용·VS Code 전환율

### VS Code

- 유료 Seat·활성 시스템·활성 프로그램
- 월간 활성 사용자와 검증 Run
- Patch 채택률
- 회귀 차단률
- Credit 소비·초과 구매율
- 월간 이탈률·순매출 유지율

## 11. 주요 위험과 대응

- 자동 수정 오류: 승인 Gate, 위험도 분류, Rollback, 독립 재검증
- 원가 초과: 사전 학습량 산정, Credit Hold, 동시 실행·Budget 제한
- 라이선스 우회: OLicense 서명 Token, 서버 재검증, 사용량 원장
- 고객 데이터 유출: 프로젝트 격리, 보존정책, Enterprise Offline
- 범위 분쟁: 기준선·프로그램·검증팩·개선한도·종료조건 계약 결속
- 자기검증 편향: 생성·수정 경로와 최종 판정 경로 분리
- 제품 범위 팽창: 등록된 프로그램의 학습·검증·개선으로 제한

## 12. 사업 성공 정의

ONSure는 단순 검사도구가 아니라 다음을 반복 가능하게 만드는 사업이어야 한다.

> 고객의 AI 프로그램을 이해하고, 실제 위험을 증명하며, 안전하게 개선하고, 그 개선 효과를 납품하거나 지속 관리한다.
