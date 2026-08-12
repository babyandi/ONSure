# ONSure Customer Delivery·Report·Claim Language Governance 상세설계

Status: `DESIGN_ONLY / DRAFT / NON_FINAL`

## 1. 목적
내부 상태가 고객용 Report/Executive Summary/Certificate로 변환될 때 의미가 강해지는 것을 막는다. UI뿐 아니라 문서 산출물도 동일 Assurance ontology와 ceiling을 사용한다.

## 2. Delivery Artifact 구분
- Executive Summary
- Technical Report
- Findings Export
- Coverage Report
- Evidence Pack
- Improvement/Patch Package
- Acceptance/Assurance Certificate
- Machine-readable Assurance Summary

## 3. Mandatory Claim Context
모든 결과 요약은 최소:
- subject/product/version
- target/scope/requirement generation
- decision
- assurance tier/strength
- currentness
- validation date
- excluded/unknown/unobservable scope
- accepted risks
- independent verification summary
- limitation/revalidation trigger
을 포함한다.

## 4. 표현 금지
근거 없이:
- “완전 검증”
- “결함 없음”
- “100% 안전”
- “모든 요구사항 충족”
- “독립 검증 완료”
- “운영환경 검증 완료”
를 사용하지 않는다.

## 5. 허용 표현 생성
Claim language는 free-form LLM 생성보다 machine state에서 template/profile로 생성하고, LLM은 문장 다듬기만 허용한다. LLM이 assurance strength를 재해석/상향하지 못한다.

## 6. Historical vs Current
예:
- “2026-08-xx 당시 AT4 검증 통과”
- “현재 상태: STALE / 재평가 필요”
를 분리한다.

과거 PASS를 현재 안전성으로 표현하지 않는다.

## 7. Coverage
coverage_percent 단독 노출 금지. excluded/unknown/unobservable 및 critical exclusion을 같은 report section에 표시한다.

## 8. Risk Acceptance
Accepted Risk는 fixed/closed와 다른 표/아이콘/문구로 표시하며 risk owner/expiry/compensating control을 포함한다.

## 9. Certificate vs Report
Report는 설명 산출물이고 Certificate는 signed public proof다. Report에 Certificate-like seal을 붙여 동일 권위로 오인시키지 않는다.

## 10. Machine-readable Summary
모든 Delivery에는 human report와 함께 선택적으로 signed/hashed JSON summary를 제공해 UI/외부 시스템이 같은 state를 재사용하게 한다.

## 11. Localization
번역 시 status/decision/assurance term은 canonical glossary를 사용한다. `HOLD`, `NON_FINAL`, `STALE`, `REVOKED`가 일반적인 “통과/완료”로 번역되지 않도록 한다.

## 12. Negative Test
- NON_FINAL PASS를 보고서에서 “검증 완료”로 번역
- STALE historical result를 Executive Summary에서 current PASS로 표현
- coverage 98%만 표시하고 critical exclusion 숨김
- accepted risk를 해결완료로 집계
- report PDF seal을 Certificate로 오인 가능한 표현
- LLM 요약이 AT2를 AT4로 상향

## 13. 수용기준
모든 고객 산출물의 claim은 canonical machine state보다 강해질 수 없고, 제한·제외·currentness를 숨기지 않는다.
