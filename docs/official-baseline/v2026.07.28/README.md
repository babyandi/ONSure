# ONSure 공식 설계 기준선 v2026.07.28

- 기준 브랜치: `audit/onsure-meta-omission-remediation-20260728`
- 기준 Commit: `81dfe0275f171b010c97648b58c17b8f745a3a50`
- 승인상태: `BASELINE_CANDIDATE / NONFINAL / HOLD`
- FinalLock: `false`

## 정본 원칙

- 기존 185페이지 본문은 삭제·축약하지 않고 저장소 설계 근거와 충돌 해결 부록을 추가했다.
- DOCX는 사람 승인용 규범 정본, MD·JSON·YAML은 실행·검증용 동반 계약이다.
- ONSure는 ORUDA와 독립 실행하며 ORUDA는 선택형 교차검증 Adapter로만 사용한다.
- ONTester·ONAudit·ONFinalGate는 제품 내부의 분리된 신뢰영역이다.
- PENDING·UNKNOWN·INCONCLUSIVE·BLOCKED·NOT_RUN·HOLD 중 하나라도 있으면 Final 금지다.

## 문서 목록

| 문서 | 파일 | SHA-256 | 상태 |
|---|---|---|---|
| 설계 정본 통합대장 | `00_ONSURE_설계정본_통합대장_v2026.07.28.docx` | `0e2e7e19cfe568621e32c6d2cc2ffc066b3337731eed0e56c58c52783d8d5870` | `BASELINE_CANDIDATE` |
| 산출물 통합대장·정본관리 | `00_ONSURE_산출물_통합대장_및_정본관리계획_공식정본_통합본.docx` | `0305b93672e9a6044eeceebc14ca3848a381cd34ebfeec2d31bf0d815c6dd399` | `BASELINE_CANDIDATE` |
| 제품·업무 요구사항·수용기준 | `01_ONSURE_제품요구사항_업무요구_수용기준_공식정본_통합본.docx` | `563b50835a7b66b5122bdec3201efeadc84ed466216091a7a4d3ad68e73cad20` | `BASELINE_CANDIDATE` |
| 최종 목표 아키텍처 | `02_ONSURE_최종목표_아키텍처_상세설계_공식정본_통합본.docx` | `23556240bbd71b5c689575f37156c25b2dece4e79771f61968cd110b546204b2` | `BASELINE_CANDIDATE` |
| AI Agent·Session·Orchestration·Memory | `03_ONSURE_AI_Agent_Session_Orchestration_Memory_설계_공식정본_통합본.docx` | `2af3bbd0b59d18fce319359f108c7a6d8e3905b356eb99589e5e68612fa82f69` | `BASELINE_CANDIDATE` |
| Developer·Git·Package·Delivery | `04_ONSURE_Developer_Git_Package_Delivery_상세설계_공식정본_통합본.docx` | `5a18ff3e7bd4d6d3beb394df4514ef591952825b4e070d76e88a7dd75a603246` | `BASELINE_CANDIDATE` |
| Work·Artifact·Research·Connector·Automation | `05_ONSURE_Work_Artifact_Research_Connector_Automation_설계_공식정본_통합본.docx` | `b3765ab0ee6caf44136a4458921e346e8dae27a78af3603b6c7a7e027d59b77b` | `BASELINE_CANDIDATE` |
| 금융권 보안·IAM·데이터·망·공급망 | `06_ONSURE_금융권_보안_IAM_데이터_망_공급망_설계_공식정본_통합본.docx` | `e3ed76e5f64ff66e66ecf25c5498fe4acbf97ce257a88273d6fc817720eed6bc` | `BASELINE_CANDIDATE` |
| 데이터모델·API·Event·Receipt | `07_ONSURE_데이터모델_API_Event_Receipt_계약설계_공식정본_통합본.docx` | `f45e3695d9d1424db1aca98d45939bd97e35c4925d99bf0cc7f17944bdcff120` | `BASELINE_CANDIDATE` |
| 독립 ONTester | `08_ONSURE_독립_ONTester_상세설계_공식정본_통합본.docx` | `dc42c12a4380bb2e358469755daf59238ec864ac334d33666702962e1aa966ac` | `BASELINE_CANDIDATE` |
| 독립 ONAudit·ONEvidence·ONFinalGate | `09_ONSURE_독립_ONAudit_ONEvidence_ONFinalGate_상세설계_공식정본_통합본.docx` | `d6efcea6ed08e0cccc0473452e13b4e30e248e3761f531938fa26b67e3310cd4` | `BASELINE_CANDIDATE` |
| 설치·배포·운영·관제·DR | `10_ONSURE_설치_배포_운영_관제_DR_Runbook_설계_공식정본_통합본.docx` | `0363a6f4fa2e4ec9befe431cea7a4357e927419bd29a41f73ca579da554274b2` | `BASELINE_CANDIDATE` |
| 요구사항 추적·시험·수용·구현로드맵 | `11_ONSURE_요구사항_추적_시험_수용_구현로드맵_공식정본_통합본.docx` | `cf2b7aad0ff1a2a86bb53a8eaf2c42716a6071df974dd0070290b96d56ddf81e` | `BASELINE_CANDIDATE` |

## 승인 필요

본 기준선은 설계책임자의 명시적 승인 전까지 공식 승인 완료로 간주하지 않는다.
