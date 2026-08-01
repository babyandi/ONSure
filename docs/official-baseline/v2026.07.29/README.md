# ONSure 공식 설계 기준선 v2026.07.29

- 기준 브랜치: `audit/onsure-meta-omission-remediation-20260728`
- 기준 Commit: `dffdb6b1051b9ecb61c51ebd601fdd7f5fe2c62c`
- 승인상태: `BASELINE_CANDIDATE / NONFINAL / HOLD`
- FinalLock: `false`

## 정본 원칙

- 기존 185페이지 본문은 삭제·축약하지 않고 저장소 설계 근거와 충돌 해결 부록을 추가했다.
- DOCX는 사람 승인용 규범 정본, MD·JSON·YAML은 실행·검증용 동반 계약이다.
- ONSure는 ORUDA와 독립 실행하며 ORUDA는 선택형 교차검증 Adapter로만 사용한다.
- ONTester·ONAudit·ONFinalGate는 제품 내부의 분리된 신뢰영역이다.
- PENDING·UNKNOWN·INCONCLUSIVE·BLOCKED·NOT_RUN·HOLD 중 하나라도 있으면 Final 금지다.
- Validator는 예외 대신 구조화 FAIL을 생성하고, Aggregate는 각 Step Receipt를 재검산해야 한다.
- 현재 상태는 `DESIGN_SPECIFIED / IMPLEMENTATION_NOT_PROVEN / PROMOTION_BLOCKED`다.

## 문서 목록

| 문서 | 파일 | SHA-256 | 상태 |
|---|---|---|---|
| 설계 정본 통합대장 | `00_ONSURE_설계정본_통합대장_v2026.07.29.docx` | `ddc83cbab8957e8b750d2621a768048c46bffc130272fb8108e7e5aa944e2ac0` | `BASELINE_CANDIDATE` |
| 산출물 통합대장·정본관리 | `00_ONSURE_산출물_통합대장_및_정본관리계획_공식정본_통합본.docx` | `a3cdce8e879afcb025f12869910ab93da719d7754e659603a4e38756527afe25` | `BASELINE_CANDIDATE` |
| 제품·업무 요구사항·수용기준 | `01_ONSURE_제품요구사항_업무요구_수용기준_공식정본_통합본.docx` | `4520149d476fe274cedb0b8603e9b07f038a821b13fb8196dd604b0b98bbd6df` | `BASELINE_CANDIDATE` |
| 최종 목표 아키텍처 | `02_ONSURE_최종목표_아키텍처_상세설계_공식정본_통합본.docx` | `c11bf48581bb72d6e030aed10b52d5fd0c453b4fb1317b73322ea142c6309fd9` | `BASELINE_CANDIDATE` |
| AI Agent·Session·Orchestration·Memory | `03_ONSURE_AI_Agent_Session_Orchestration_Memory_설계_공식정본_통합본.docx` | `dfc8975e899848f970737541f3505f8752d3ae529d2f0cb11b92f9457a3eeeb4` | `BASELINE_CANDIDATE` |
| Developer·Git·Package·Delivery | `04_ONSURE_Developer_Git_Package_Delivery_상세설계_공식정본_통합본.docx` | `bdf2206c7c1e11d83067eda8189ef229b241f56ed355b51b622907a5f0171883` | `BASELINE_CANDIDATE` |
| Work·Artifact·Research·Connector·Automation | `05_ONSURE_Work_Artifact_Research_Connector_Automation_설계_공식정본_통합본.docx` | `a8080579e21803f2b55020802d5e0e8dc279ec1d2068207fd824cd81c7a29861` | `BASELINE_CANDIDATE` |
| 금융권 보안·IAM·데이터·망·공급망 | `06_ONSURE_금융권_보안_IAM_데이터_망_공급망_설계_공식정본_통합본.docx` | `bb90542d4e8387bc627f375070a44c3b6392e7563d9f59b8ff8f31f0ecd76539` | `BASELINE_CANDIDATE` |
| 데이터모델·API·Event·Receipt | `07_ONSURE_데이터모델_API_Event_Receipt_계약설계_공식정본_통합본.docx` | `7ae2628dbeaa794e3210950beaf8e0ddf150374a4a719e821dc774ba451c68bc` | `BASELINE_CANDIDATE` |
| 독립 ONTester | `08_ONSURE_독립_ONTester_상세설계_공식정본_통합본.docx` | `2f4f6ba5163ffd73d3a16b8b3b5712967eb281ed724d59d9fc7c4c3e7843dbc0` | `BASELINE_CANDIDATE` |
| 독립 ONAudit·ONEvidence·ONFinalGate | `09_ONSURE_독립_ONAudit_ONEvidence_ONFinalGate_상세설계_공식정본_통합본.docx` | `ffaea0ca1d667ea3a9e121a8b6d8f8879aeff96fc26616874878e346b8eca264` | `BASELINE_CANDIDATE` |
| 설치·배포·운영·관제·DR | `10_ONSURE_설치_배포_운영_관제_DR_Runbook_설계_공식정본_통합본.docx` | `a089e9dcb26c1aaee06ba4d443b66f0bba1d2dd4ecb2f111abcec599262f4efb` | `BASELINE_CANDIDATE` |
| 요구사항 추적·시험·수용·구현로드맵 | `11_ONSURE_요구사항_추적_시험_수용_구현로드맵_공식정본_통합본.docx` | `2185dd2222ba05a21d49c33d07392d61600465f2186ca3db7b277da1d747a472` | `BASELINE_CANDIDATE` |

## 승인 필요

본 기준선은 설계책임자의 명시적 승인 전까지 공식 승인 완료로 간주하지 않는다.
