# ONSure 공식 설계 기준선 v2026.07.28

- 기준 브랜치: `audit/onsure-meta-omission-remediation-20260728`
- 기준 Commit: `464adefe585ac1f901b19aac974f443e964cca1d`
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
| 설계 정본 통합대장 | `00_ONSURE_설계정본_통합대장_v2026.07.28.docx` | `e9a506f2460e5869caa0198e607e96d3c654ccaf6bebc98cfc4f525630ccaa8e` | `BASELINE_CANDIDATE` |
| 산출물 통합대장·정본관리 | `00_ONSURE_산출물_통합대장_및_정본관리계획_공식정본_통합본.docx` | `5ea45623ecd3d2608d5fa8c768688a31a211d838a1faf4a61fd4156c3764f1d9` | `BASELINE_CANDIDATE` |
| 제품·업무 요구사항·수용기준 | `01_ONSURE_제품요구사항_업무요구_수용기준_공식정본_통합본.docx` | `20845e5328943599775b958d426e78971e44593c730733c1328b3b40749ba631` | `BASELINE_CANDIDATE` |
| 최종 목표 아키텍처 | `02_ONSURE_최종목표_아키텍처_상세설계_공식정본_통합본.docx` | `b49e8807daae3a4462e245b8fc93f79fd4a598b2930698de87253ba36bdabc3d` | `BASELINE_CANDIDATE` |
| AI Agent·Session·Orchestration·Memory | `03_ONSURE_AI_Agent_Session_Orchestration_Memory_설계_공식정본_통합본.docx` | `57fafa5c04b7128d9d0a878f33c3f8e680353dd8f5be7770ec507d50e5127ec0` | `BASELINE_CANDIDATE` |
| Developer·Git·Package·Delivery | `04_ONSURE_Developer_Git_Package_Delivery_상세설계_공식정본_통합본.docx` | `57cd869366935d19341b180f2cfaf570861929651e7d5e63796c5fd41802425b` | `BASELINE_CANDIDATE` |
| Work·Artifact·Research·Connector·Automation | `05_ONSURE_Work_Artifact_Research_Connector_Automation_설계_공식정본_통합본.docx` | `f90514f860e3ca365f7ec3828fc07f49b90513d8cc64d536349cdaf747041177` | `BASELINE_CANDIDATE` |
| 금융권 보안·IAM·데이터·망·공급망 | `06_ONSURE_금융권_보안_IAM_데이터_망_공급망_설계_공식정본_통합본.docx` | `47196c00089d81f9b5c11be14398930054a73ef7c1ef3f527440efb85e13ae46` | `BASELINE_CANDIDATE` |
| 데이터모델·API·Event·Receipt | `07_ONSURE_데이터모델_API_Event_Receipt_계약설계_공식정본_통합본.docx` | `7d18e4322a7cfee3f535a62ebe39f329b46102d8fcc5cecb55e9781ea92720ce` | `BASELINE_CANDIDATE` |
| 독립 ONTester | `08_ONSURE_독립_ONTester_상세설계_공식정본_통합본.docx` | `fcde18963245af8a3130e0dbeb35aeec5e2c2cf2f390e5eb1031ecb01c56b3c1` | `BASELINE_CANDIDATE` |
| 독립 ONAudit·ONEvidence·ONFinalGate | `09_ONSURE_독립_ONAudit_ONEvidence_ONFinalGate_상세설계_공식정본_통합본.docx` | `e442bbe9503c73845414875a4c4797df0676920d39911228581c39011f3b13e8` | `BASELINE_CANDIDATE` |
| 설치·배포·운영·관제·DR | `10_ONSURE_설치_배포_운영_관제_DR_Runbook_설계_공식정본_통합본.docx` | `47f23bfe929b1be1719ae90c6d857abc9d1ab0051e9f0e1904d113c79d0b9c93` | `BASELINE_CANDIDATE` |
| 요구사항 추적·시험·수용·구현로드맵 | `11_ONSURE_요구사항_추적_시험_수용_구현로드맵_공식정본_통합본.docx` | `40f9682c5b82ecad3ad96705cfdb0e7399c75e78a214d759635aa3e927585d92` | `BASELINE_CANDIDATE` |

## 승인 필요

본 기준선은 설계책임자의 명시적 승인 전까지 공식 승인 완료로 간주하지 않는다.
