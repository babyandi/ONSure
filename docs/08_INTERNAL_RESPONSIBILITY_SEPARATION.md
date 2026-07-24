# ONSURE 내부 책임 분리 기준

이 문서는 ONSURE가 학습, 검증, 원인분석, 보완, 기억 승격, 최종 완료를 수행할 때 자기승인 구조가 생기지 않도록 내부 책임 경계를 고정한다.

## 핵심 원칙

ONSURE는 독립 제품이지만 내부 프로그램이 자기 결과를 직접 최종 승인하면 안 된다.

```text
생성자 != 최종 Gate
시나리오 생성자 != 단독 검증자
검증자 != 보완 작성자
보완 작성자 != 회귀 PASS 판정자
Memory 후보 저장자 != 단독 승격자
PENDING Receipt 존재 시 Final Complete 금지
```

## 분리 대상

| 영역 | 분리해야 하는 책임 |
|---|---|
| Program Learning | 소스 읽기, 프로그램 프로필 생성, 행동 추적, 학습 후보 기록 |
| Scenario Generation | 정상, 경계, 실패, 적대 시나리오 생성과 시나리오 Receipt 작성 |
| Verification | 기능, 정책, 안전성, 회귀, 증거 검증과 검증 판정 |
| Root Cause Analysis | 실패 분류, 원인 가설, 증거 결속, 원인 판정 |
| Improvement | 코드, 프롬프트, RAG, 도구 계약, 설정 보완과 보완 Receipt 작성 |
| Learning Memory | 후보 저장, 승격 Gate, 롤백, Memory Receipt 작성 |
| Release Gate | Receipt 수집, 독립성 확인, 최종 완료 판정 |

## 실행 흐름

```text
Program Learning
→ Scenario Generation
→ Verification
→ Root Cause Analysis
→ Improvement
→ Regression Verification
→ Learning Memory Promotion
→ Release Gate
```

각 단계는 부모 Receipt hash와 입력/출력 hash를 남겨야 한다. 중간 단계가 FAIL 또는 PENDING이면 최종 완료는 fail-closed로 차단한다.

## 금지 구조

다음 구조는 허용하지 않는다.

| 금지 구조 | 이유 |
|---|---|
| Scenario Generator가 자기 시나리오 실행 결과를 단독 PASS 처리 | 검증 독립성 상실 |
| Verifier가 직접 Patch를 작성하고 최종 개선 PASS 판정 | 자기수정 승인 |
| RAG Updater가 Memory 승격까지 단독 처리 | 학습 오염 위험 |
| Evidence Writer가 원본 증거를 수정 | 증적 신뢰성 상실 |
| Release Gate가 PENDING Receipt를 무시 | 완료 상태 위조 가능 |

## 계약 파일

세부 기계 판정 기준은 다음 계약을 따른다.

- `contracts/internal-responsibility-separation.v1.yaml`

## 적용 기준

새 ONSURE 코드와 기존 코드 개정은 다음 조건을 만족해야 한다.

1. 생성, 검증, 보완, 승격, 최종 완료 책임이 별도 클래스 또는 모듈로 식별 가능해야 한다.
2. Receipt에는 부모 hash, 입력 hash, 출력 hash, actor, decision이 포함되어야 한다.
3. 최종 완료는 verification, improvement, regression, independence receipt가 모두 PASS일 때만 가능하다.
4. 단일 프로그램의 PASS만으로 상위 완료 상태를 만들 수 없다.
