# ONSURE External Product and Failure Review v1

## 1. 검토 결론

ONSURE과 1:1로 같은 제품은 없다. ONSURE은 여러 제품군의 기능을 묶되, 단순 관측이나 테스트 도구가 아니라 독립 검증·증적·승격 통제 프로그램으로 설계해야 한다.

| 영역 | 유사 제품군 | ONSURE 반영 |
|---|---|---|
| LLM observability | LangSmith, Arize Phoenix | trace-first, dataset/eval linkage |
| LLM eval/red-team | promptfoo, Giskard | fixture, adversarial, regression |
| Guardrail | Guardrails AI | policy-as-code, output constraint |
| ML lifecycle | MLflow Model Registry | version, lineage, promotion |
| Drift monitoring | Evidently | drift and stale evidence detection |
| code/security validation | SonarQube, Snyk | static/security/supply-chain gate |

## 2. 실패사례 반영

| 사례 | 실패 원인 | ONSURE 설계 반영 |
|---|---|---|
| Air Canada chatbot | 잘못된 AI 안내도 회사 책임으로 이어짐 | 정책 원문 검증, 책임 Receipt, human escalation |
| Microsoft Tay | 공개 입력에 의한 오염과 악의적 유도 | 학습 피드백 격리, 적대 Harness |
| Zillow Offers | 예측 모델과 운영 리스크 관리 실패 | Drift, Shadow/Canary, Rollback |
| Amazon recruiting AI | 과거 데이터 편향 학습 | bias fixture, dataset 분포 검증 |
| Mata v. Avianca | 존재하지 않는 판례 인용 | source existence validation, citation receipt |

## 3. ONSURE 아키텍처 요구사항

외부 검토 결과 ONSURE 설계에 반드시 들어가야 하는 항목:

1. Trace-first execution
2. Dataset Registry
3. Policy-as-Code
4. Model/Prompt/Tool Registry
5. Incident Replay
6. Golden/Hidden set separation
7. adversarial and negative fixture harness
8. false pass / false fail calibration
9. Promotion/Rollback Gate
10. tamper-evident receipt chain

## 4. 설계 판단

ONSURE은 다음 포지션으로 잡는다.

```text
AI가 만든 코드/문서/판단/실행 결과를
독립적으로 검증하고
증적을 남기고
승격·차단·롤백까지 통제하는
Standalone Software Validation Platform
```

단순 LLM 평가 도구, 코드 스캐너, MLOps Registry 중 하나로 좁히지 않는다.
