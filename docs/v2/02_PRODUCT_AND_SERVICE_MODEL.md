# ONSure V2 제품·서비스 모델

## 1. 제품 정의

ONSure는 등록된 AI 프로그램을 학습하고, 검증하고, 검증 결과에 근거해 보완하며, 수정 전후의 개선 효과를 입증하는 제품이다.

## 2. 채널 구분

| 구분 | ONSure Web | ONSure for VS Code |
|---|---|---|
| 제공형태 | 1회성 Service Case | 월·연간 구독 |
| 기준 | 고정된 대상·기준선·범위 | 계속 변경되는 Workspace |
| 학습 | Case 범위의 일괄 학습 | 최초+증분 학습 |
| 검증 | 계약된 검증팩 | 반복·Commit·Release 검증 |
| 개선 | 후속 주문 | 권한·Credit 내 지속 수행 |
| 종료 | 결과 납품·한도 소진·기간 만료 | 구독 해지·만료 |
| 라이선스 | Case License | Subscription Entitlement |

## 3. 웹 기본 서비스

### Learn

프로그램의 목적·구조·기능·AI 구성·데이터 흐름을 분석해 Program Profile을 납품한다. 품질 PASS나 보안 적합성을 보증하지 않는다.

### Verify

고객이 제공한 요구사항·기준·테스트가 충분한 경우 고정 기준선에 대해 검증한다. 최소 Intake 분석은 수행하되 전체 Program Learning을 상품 범위로 제공하지 않는다.

### Learn & Verify

대상을 먼저 학습해 검증 기준과 시나리오를 구성한 뒤 검증한다. 문서가 부족하거나 AI 생성 비중이 높은 프로그램의 기본 권장 서비스다.

### Improve & Re-verify

Verify 또는 Learn & Verify에서 승인된 Finding을 선택해 개선하고 재검증한다. Learn만으로는 결함이 입증되지 않았으므로 직접 개선 주문으로 연결하지 않는다.

## 4. 웹 서비스 선택 규칙

```text
요구사항·기준·테스트가 충분함
→ Verify 가능

프로그램 구조를 알고 싶음
→ Learn

프로그램 이해도와 검증 기준이 부족함
→ Learn & Verify

유효 Finding이 존재함
→ Improve & Re-verify
```

## 5. 학습량

학습 상품을 다단계로 쪼개지 않고 하나의 Learn으로 제공하되, 실제 학습량을 가격에 반영한다.

내부 산정요소:

- 코드·설정·문서·테스트·로그 규모
- Repository·프로그램·배포 단위
- 언어·Framework
- Agent·Prompt·Tool
- RAG 문서·Index·Data Source
- 외부 연계·권한·정책
- 구조 복잡도·중복도

고객 표시:

- 예상 Learning Unit
- 기본 포함량
- 초과량
- 포함 자료와 제외 자료
- 최종 견적

## 6. 시스템과 프로그램

### System

하나의 업무 목적·운영 책임·서비스 경계를 가진 상위 대상이다.

### Program

독립적으로 학습·검증·배포·버전관리할 필요가 있는 실행 단위다.

Repository 수와 Program 수는 같지 않다. 하나의 Repository가 여러 Program을 포함하거나 여러 Repository가 하나의 Program을 구성할 수 있다.

별도 Program 판정기준:

- 독립 배포·실행 여부
- 별도 버전·Release 여부
- 독립 장애·검증 기준선 여부
- 독립 Program Profile 필요 여부

공통 Library·문서·Fixture·동일 프로그램의 개발/시험/운영 환경은 원칙적으로 별도 Program으로 세지 않는다.

## 7. 웹 대상 한도

기본 Case는 System 1개를 원칙으로 한다. Program과 학습량은 Case별 포함량을 두고 초과분을 추가 과금한다. 복수 System은 Enterprise Batch 계약으로 처리하되 Case·기준선·결과는 System별 추적 가능해야 한다.

## 8. VS Code 상품

- Developer: 개인·소규모 개발
- Team: 공유 Credit, 승인 Workflow, CI/CD
- Enterprise: 계약형 Seat·System·Program·Credit, SSO·RBAC·감사·온프레미스
- Unlimited Systems & Programs: System·Program 수 무제한 옵션

Unlimited에서도 Seat, Credit, 동시 실행, AI·Compute, Storage, Support는 계약 한도를 적용한다.

## 9. 공통 산출물

- Program Profile
- Verification Plan
- Finding·Severity·Reproduction
- RCA
- Improvement Plan·Patch
- Before/After Comparison
- Evidence Bundle
- Final Report
- Git Branch·Commit·Draft PR(해당 상품)

## 10. 제품 경계

ONSure는 범용 신규 시스템 개발, 전사 지식경영, 일반 프로젝트 관리, 문서·디자인 제작 제품이 아니다. 자동 개발은 학습·검증에서 확인된 결함 또는 측정된 품질 부족에 한정한다.
