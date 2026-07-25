# ONSure V2 산출물 마스터 인덱스

## 1. 목적

이 문서 세트는 ONSure를 다음 두 가지 사업모델을 함께 제공하는 독립 상용 제품으로 재정의한다.

1. 웹 기반 1회성 학습·검증·개선 서비스
2. VS Code 기반 지속형 학습·검증·개선 구독 서비스

라이선스 발급·계약·Entitlement·사용량 원장은 ORUDA의 `OLicense`가 담당한다. ONSure는 OLicense와 표준 API로 연계하지만 제품·저장소·실행환경·판매체계는 독립적으로 유지한다.

## 2. 기준 문서

- `01_BUSINESS_PLAN.md`: 사업계획서
- `02_PRODUCT_AND_SERVICE_MODEL.md`: 제품·서비스 모델
- `03_WEB_ONE_TIME_SERVICE_POLICY.md`: 웹 1회성 서비스 정책
- `04_VSCODE_SUBSCRIPTION_POLICY.md`: VS Code 구독 정책
- `05_LICENSE_PAYMENT_OLICENSE_INTEGRATION.md`: 라이선스·결제·OLicense 연계
- `06_OPERATING_PROCESS_AND_CUSTOMER_JOURNEY.md`: 운영 프로세스와 고객 여정
- `07_ARCHITECTURE_AND_DATA_MODEL.md`: 아키텍처와 데이터 모델
- `08_IMPLEMENTATION_ROADMAP_AND_ACCEPTANCE.md`: 구현 로드맵과 수용 기준
- `09_TARGET_AI_AUTO_LEARNING_BUSINESS_AND_DEVELOPMENT_STRATEGY.md`: 대상 AI 자동학습·개선 사업, 라이선스, 아키텍처, 개발전략과 출시 Gate

## 3. 최종 제품 구조

```text
ONSure Web
- Program Learn
- Verify
- Learn & Verify
- Target AI Auto-Learning
- Improve & Re-verify
- 1회성 Service Case

ONSure for VS Code
- 지속 증분 Program Learning
- 반복 검증
- 자동 보완 개발
- 승인형 Target AI 재학습
- Git·PR·CI 연계
- 월·연간 구독

ORUDA / OLicense
- 상품 Catalog
- 주문·결제 결과 연계
- 라이선스·Entitlement 발급
- 시스템·프로그램·Seat·Credit 관리
- Dataset·Training·Model Version 한도
- 사용량·감사·취소·만료 관리
```

## 4. 공통 원칙

- ONSure는 등록된 AI 프로그램을 학습하고 검증하며 검증 근거에 따라 보완한다.
- `Program Understanding Learning`은 ONSure가 대상 프로그램을 이해하는 학습이다.
- `Target AI Auto-Learning`은 검증된 Finding과 승인된 목표에 근거해 대상 프로그램의 RAG·Prompt·Agent·Model 등을 학습·개선하는 기능이다.
- 두 Learning은 입력·산출물·비용·위험·라이선스·Evidence를 분리한다.
- 자동학습 결과는 독립평가와 재검증을 통과하고 권한 있는 승인을 받기 전까지 운영에 배포하지 않는다.
- 웹서비스는 특정 기준선과 계약 범위를 납품하고 종료되는 1회성 서비스다.
- VS Code 서비스는 개발 변경을 지속 추적하는 구독형 서비스다.
- 시스템 수, 프로그램 수, 학습량, 검증범위, 개선량, Dataset, Training Run은 서로 다른 개념으로 관리한다.
- Unlimited는 시스템·프로그램 등록 범위에만 적용하며 AI·학습·GPU·컴퓨팅·스토리지·지원까지 무제한을 의미하지 않는다.
- OLicense가 최종 라이선스 권위이며 ONSure 클라이언트는 라이선스를 임의 생성하거나 확대하지 않는다.
