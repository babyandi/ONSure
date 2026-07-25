# ONSURE 프로그램 학습 방법론

## 1. 목적

이 문서는 ONSURE가 등록된 AI 프로그램을 추측이나 단순 문서 요약이 아니라 실제 소스·계약·실행·사용 결과에 근거해 학습하고, 검증된 후보만 활성 기억으로 승격하기 위한 표준 절차를 정의한다.

ONSURE의 학습 목표는 기반모델 재학습이나 기업 전체 지식 수집이 아니다. 대상 프로그램의 목적과 구조를 이해하고, 실제 행동과 실패 조건을 발견하며, 효과가 입증된 개선 경험을 재사용 가능한 형태로 축적하는 것이다.

## 2. 적용 범위와 경계

적용 대상:

- AI 응용프로그램, Agent, RAG, LLM 기반 업무 프로그램
- 코드, 프롬프트, 모델 설정, RAG, Tool Contract, 정책, 테스트, 실행 로그
- 디지털 UI, 디자인, 문서, 제품 경험 등 복합 도메인을 다루는 AI 프로그램
- 신규 등록, 기준선 변경, 실패 발생, 개선 완료 후의 재학습

금지 범위:

- 출처 권한이 확인되지 않은 자료의 무단 수집
- 검증 전 후보를 활성 기준선으로 사용
- 판매량·수상·홍보 문구를 품질 증명으로 단정
- 단일 성공 사례를 보편 규칙으로 일반화
- 고객 전용 정보나 개인정보를 범용 기억으로 승격
- 학습 엔진이 자기 후보를 단독 승인
- Source SHA가 바뀐 상태에서 이전 증거로 승격

## 3. 핵심 원칙

1. Authority before learning: 권위 원문과 사용 허가를 먼저 확인한다.
2. Learn before judging: 프로그램 목적·구조·제약을 이해한 뒤 검증한다.
3. Evidence over assertion: 홍보 주장보다 실제 바이트, 실행 결과, 사용자 결과를 우선한다.
4. Candidate first: 모든 신규 지식은 비활성 후보로 시작한다.
5. Domain-aware learning: 프로그램 유형에 맞는 전문 분류·산출물·성공 기준을 사용한다.
6. Preserve intent: 승인된 제품 목적과 정상 동작을 훼손하는 학습·보완을 금지한다.
7. Separate decision from execution: 설계 판단, 구현, 검증, 승격 책임을 분리한다.
8. Parent-hash lineage: 원문부터 활성 기억까지 각 단계가 부모 hash를 포함한다.
9. Fail closed: 충돌, 누락, PENDING, 독립성 부족 시 승격하지 않는다.
10. Reversible memory: 모든 승격은 버전, 만료, 폐기, 롤백이 가능해야 한다.

## 4. 학습 모델

ONSURE는 네 종류의 학습 모델을 분리한다.

| 모델 | 질문 | 주요 산출물 |
|---|---|---|
| Program Learning | 이 프로그램은 무엇을 왜 어떻게 수행하는가 | Program Profile, 책임·계약·데이터 흐름 |
| Behavior Learning | 실제로 어떤 입력에서 어떻게 행동하는가 | Behavior Profile, 변동·취약·실패 조건 |
| Domain Learning | 해당 분야에서 좋은 결과와 금지 조건은 무엇인가 | Domain Pack, 분야별 기준·사례·오라클 |
| Improvement Learning | 어떤 수정이 실제로 효과가 있었는가 | Improvement Memory, 적용 조건·부작용·효과 |

Domain Learning은 대상 프로그램의 개선에 필요한 범위로만 사용한다. 범용 도메인 지식 사업으로 확장하지 않는다.

## 5. 학습 단위

### 5.1 Authority Source

최소 필드:

- source_id, source_type, owner, license_or_permission
- locator, retrieval_time, actual_byte_hash
- version, language, market, valid_from, valid_to
- observation, external_claim, verified_fact 구분
- 개인정보·기밀·고객 전용 여부

URL 문자열이나 locator hash만으로 원문 봉인을 대신할 수 없다. 가능하면 실제 원문 바이트 또는 재현 가능한 스냅샷을 보존한다.

### 5.2 Program Experience Case

하나의 제품이나 기능은 화면 캡처 또는 성공 출력 하나가 아니라 다음 전체를 하나의 사례로 묶는다.

- 목적, 사용자, 환경, 입력 권위
- 구조, 구성요소, 책임, 계약, 상태
- 정상·경계·실패·적대 경로
- 선택한 설계와 기각한 대안
- 실행 결과와 실제 사용자·시장·운영 결과
- 수정 전후와 부작용
- 적용 가능 조건과 적용 금지 조건

### 5.3 Learning Atom

활성 검색과 추론에 사용되는 최소 단위다.

필수 내용:

- 하나의 명확한 주장
- 적용 도메인과 대상 프로그램
- 근거 Source와 Case의 hash
- 조건, 예외, 반례, 신뢰도
- 검증 시나리오와 오라클
- 프로젝트 전용 또는 범용 후보 구분
- 생성자, 검증자, 승격자
- 생성·검증·만료 시각

### 5.4 표준 학습자산 12종

ONSURE는 모든 대상 프로그램의 경험을 다음 12종으로 분리한다.

| 자산 | 내용 |
|---|---|
| Source | 원문, 코드, 요구사항, 외부 근거와 실제 byte hash |
| Constraint | 고정 규칙, 금지사항, 사용자 선호, 승인 경계 |
| Method | 수행 방법과 절차 |
| Decision | 선택 내용, 선택 이유, 대안, 기각 이유 |
| Case | 실제 입력부터 결과까지의 수행 사례 |
| Failure | 실패 결과, 재현 조건, 근본원인 |
| Correction | 수정 지시, 수정 전후, 영향 범위 |
| Pattern | 반복 검증된 재사용 가능 패턴 |
| Anti-pattern | 적용 금지 조건과 실패 패턴 |
| Golden | 독립 검증·재생성·일반화·사용자 승인을 통과한 기준 사례 |
| Evaluation | 사용자, OTester, OAudit, 실행 하네스의 평가 증적 |
| Lineage | Source부터 승격·롤백까지의 부모 hash 계보 |

최종 산출물만 저장하지 않는다. 선택하지 않은 대안과 기각 이유, 사용자의 명시적 승인·거부·수정 지시를 함께 보존한다. 무응답은 승인으로 해석하지 않는다.

### 5.5 다계층 경험 구조

하나의 경험은 다음 계층으로 분해하되 동일 parent_case_id로 결속한다.

1. Project/Program: 전체 목적, 사용자, 환경, 기준선
2. Stage/Capability: 분석·기획·생성·검증·개선 등 수행 단계
3. Unit: 페이지, 기능, 컴포넌트, 시나리오 또는 산출물 단위
4. Event: 개별 판단, 실패, 사용자 피드백, 수정, 재검증 사건

보고서 프로그램은 Project → 작성 단계 → 페이지 → 판단·수정 사건으로, 코드·Agent 프로그램은 Program → Capability → Function/Scenario → 판단·수정 사건으로 매핑한다.

## 6. 표준 학습 절차

### 단계 0. 학습 트리거 고정

신규 등록, Source SHA 변경, 정책 변경, 실패 발견, 개선 완료, 정기 재검증 중 하나를 기록한다. 트리거 없는 임의 학습은 금지한다.

### 단계 1. 권한·격리 판정

수집 권한, 라이선스, 개인정보, 기밀, 고객 소유권, 외부 전송 가능 범위를 판정한다. 판정 불가 자료는 QUARANTINED로 격리한다.

### 단계 2. Source Baseline 봉인

Branch, Commit SHA, Dirty 상태, 의존성 lock, 모델 ID·버전·설정, 프롬프트, RAG index, Tool Contract, 실행환경을 hash로 고정한다.

### 단계 3. Program Inventory 생성

파일 목록만 만들지 않고 목적, 기능, 모듈, 데이터 흐름, 외부 도구, 권한, 상태, 오류·복구, 테스트, 불확실성을 연결한다.

### 단계 4. 도메인 분류와 Coverage Map

대상 프로그램의 도메인과 산출물 유형을 분류한다. 복합 프로그램은 하나의 범주로 축소하지 않는다.

예시 도메인:

- 코드·API·Agent·RAG
- 업무·정책·보안
- UI·Interaction·Accessibility
- 시각·정보·브랜드
- 제품·산업·인간공학
- 광고·게임·공간·패키지
- 운영·지원·장기 사용

각 도메인마다 현재 근거, 미확인 영역, 필요한 전문 검증, 성공 지표를 기록한다.

### 단계 5. Program Profile 후보 생성

목적, 책임 경계, 입력·출력 계약, 불변 조건, 허용 변경, 금지 변경, 불확실성을 작성한다. 문서와 코드가 충돌하면 자동 선택하지 않고 CONFLICT로 남긴다.

### 단계 6. 행동 관찰

정상·경계·실패·적대 입력을 실제 실행한다. 입력, 출력, 도구 호출, 상태 전이, 지연, 비용, 변동, 오류·복구를 기록한다. AI 결과는 동일 입력 반복 실행으로 비결정성을 측정한다.

### 단계 7. 후보 지식 추출

원리, 패턴, 실패 조건, 개선 가설을 Learning Atom으로 만든다. 유사 문장 생성량이 아니라 새로운 근거·조건·반례의 증가량을 측정한다.

### 단계 7A. 피드백 사건 구조화

대화나 운영 로그를 통째로 학습하지 않고 요청 → 결과 → 사용자/운영 평가 → 원인 → 수정 → 승인 여부의 사건으로 추출한다. 성공·실패·개선·검증 사례를 분리하되 동일 case_id와 parent_hash로 연결한다.

### 단계 7B. 2~3중 수렴 하네스

각 회차는 후보 생성 → 중복·충돌 제거 → OTester 검증 → OAudit 감사 → 미통과 원인 보완 순서로 실행한다.

- 최소 2회, 불안정·신규 패턴 발견 시 3회 실행
- 회차마다 신규 Source, 신규 Learning Atom, 신규 실패 유형, 미해결 충돌, 검증 결과를 기록
- 마지막 2회 연속 신규 유효 패턴 0건, 미해결 충돌 0건, 결과 hash 안정일 때만 포화 후보
- 최대 3회 도달 후에도 신규 패턴이나 충돌이 남으면 완료로 간주하지 않고 HOLD와 다음 재개 조건을 기록
- 문장 변형이나 조합 증가는 신규 학습자료로 계산하지 않음

### 단계 8. 반례·충돌·오염 검사

다음을 검사한다.

- 기존 활성 기억과 의미 충돌
- 같은 사실의 중복 표현
- 유명 사례 모방 또는 최신 유행 과잉 일반화
- 홍보 주장과 실제 결과 혼동
- 상관관계와 인과관계 혼동
- 프로젝트 전용 정보의 범용 기억 유출
- 저작권·특허·상표·라이선스 위험
- 실제 제품과 콘셉트, 양산과 렌더, 관찰과 주장의 혼동

### 단계 9. 검증 시나리오 결속

각 후보에는 최소 하나의 재현 가능한 시나리오와 판정 오라클을 연결한다. 실행할 수 없는 후보는 REFERENCE_ONLY를 넘을 수 없다.

### 단계 10. 독립 검증

후보 생성자와 다른 책임 주체가 출처, 의미, 재현성, 적용 조건, 부작용을 검증한다. 자체 스크립트 하나의 PASS만으로 독립 검증을 대체할 수 없다.

- OTester: 기능·사실·구조·재현·회귀·변형 입력·미학습 과제 적용을 검증한다.
- OAudit: Source 권리, 실제 byte hash, parent lineage, 책임 분리, Receipt, 승격 조건을 감사한다.
- User/Owner Gate: 실제 사용자의 명시적 승인 또는 정의된 업무 오라클을 확인한다.

세 판정 중 하나라도 FAIL·PENDING·NOT_RUN이면 Golden 또는 ACTIVE로 승격하지 않는다.

### 단계 11. 제한적 적용과 A/B 비교

활성 기준선을 직접 교체하지 않고 격리된 실행에서 후보 적용 전후를 동일 Fixture·환경으로 비교한다. 개선 지표와 기존 정상 기능의 회귀를 함께 측정한다.

### 단계 12. 승격·감시·롤백

모든 Gate가 PASS이면 프로젝트 전용 또는 범용 활성 기억으로 승격한다. 승격 후에도 drift, Source 만료, 반례, 회귀를 감시하며 조건 충족 시 이전 버전으로 롤백한다.

## 7. ODesign–OUI와 같은 복합 프로그램 적용법

복합 프로그램은 같은 사례를 프로그램별로 복제하지 않고 하나의 Authority Case를 공유하면서 판단과 실행을 분리한다.

| 구분 | ODesign형 프로그램 | OUI형 프로그램 |
|---|---|---|
| 직접 학습 | 목적, 사용자, 경험 원칙, 전문 설계, 후보 비교, 선택·기각 근거 | Component, Geometry, State, Event, Transition, Error/Recovery, Accessibility, Runtime |
| 공통 참조 | Unified Design Intent, Domain Spec, Asset, 제약, 검증 기준 | 동일 원본의 승인된 실행 계약 |
| 금지 | 근거 없이 구현 세부 확정 | 상위 의도를 임의 재해석·재설계 |
| 충돌 처리 | 새 Design Intent 버전 발급 | DESIGN_CONFLICT로 역반환 |

필수 계보:

Authority Source → Product/Service Brief → Unified Design Intent → Domain Design Spec → Alternative Decision Record → Detailed Design Contract → Canonical Scene → Runtime → Actual Render/Usage → Verification Receipt → Audit Receipt → Memory Promotion

화면 좌표, 제품 공차, 게임 규칙, 캠페인 채널을 하나의 Scene 스키마로 억지 통합하지 않는다. 공통 Intent와 분야별 계약을 분리한다.

## 8. 후보 상태와 승격 Gate

상태:

DISCOVERED → RIGHTS_CHECKED → MATERIALIZED → PROFILE_CANDIDATE → EVIDENCE_BOUND → VERIFIED → SHADOW_APPLIED → PROMOTION_CANDIDATE → ACTIVE

예외 상태:

CONFLICT, QUARANTINED, REFERENCE_ONLY, REJECTED, STALE, ROLLED_BACK, HOLD

ACTIVE 승격 필수 조건:

- 실제 Source byte hash 존재
- 권한·기밀·개인정보 판정 PASS
- 적용 범위와 금지 조건 명시
- 반례 또는 경계 조건 존재
- 실행 가능한 Scenario·Oracle 존재
- 생성자와 독립된 검증 Receipt PASS
- 제한 적용 Before/After 개선 입증
- 기존 정상 기능 회귀 0건
- 부모 hash 계보 완전
- PENDING Receipt 0건
- 활성 기억 중복·충돌 0건
- 롤백 포인터 존재

한 항목이라도 충족하지 않으면 ACTIVE 금지다.

## 9. 기억 분리

### Project Memory

대상 저장소의 코드, 고객 정책, 비공개 데이터, 제품별 실패·개선 이력. 다른 프로젝트로 이동하지 않는다.

### Reusable Pattern Memory

다수의 독립 사례에서 재현되고 권리·개인정보 검사를 통과한 일반 패턴. 원문을 복제하지 않고 조건·반례·증거 참조를 보존한다.

Project Memory를 Reusable Pattern Memory로 승격하려면 최소 두 개의 독립 프로젝트 또는 별도 공인 근거에서 재현되어야 한다. 단, 보안상 민감한 실패 패턴은 내용 비식별화와 별도 승인이 필요하다.

## 10. 품질 지표

학습량은 문서 수나 벡터 수만으로 보고하지 않는다.

| 지표 | 의미 |
|---|---|
| Authority Coverage | 필수 권위 원문의 실제 바이트 확보율 |
| Contract Coverage | 입력·출력·상태·오류·권한 계약 확인율 |
| Behavior Coverage | 정상·경계·실패·적대 실행 범위 |
| Domain Coverage | 필요한 전문영역별 증거 확보율 |
| Evidence Completeness | 후보의 부모 hash와 Receipt 완전성 |
| Contradiction Rate | 활성 기억 내 미해결 충돌 비율 |
| Reproduction Rate | 동일 조건에서 행동·실패 재현율 |
| Improvement Proof Rate | 후보 적용 후 유의미 개선 입증률 |
| Regression Escape Rate | 승격 후 발견된 기존 기능 회귀율 |
| Rollback Success Rate | 기억·Patch의 복구 성공률 |
| Staleness | Source 변경 후 미재검증 기억 비율 |

최종 학습 완료는 Coverage 100퍼센트라는 표현만으로 주장하지 않는다. 대상 범위, 미확인 영역, NOT_RUN, HOLD를 함께 보고한다.

## 11. 실행 주기

- 최초 학습: 등록 직후 전체 기준선
- 증분 학습: Source SHA, 프롬프트, RAG, Tool Contract, 모델, 정책 변경 시
- 사건 기반 학습: 실패, 보안 사건, 사용자 불만, 개선 완료 시
- 정기 재검증: 활성 기억의 유효기간과 위험도에 따라
- 긴급 폐기: 권리 문제, 개인정보 노출, 치명적 회귀, 위조 증거 발견 시

정기 실행은 완료된 범위를 무조건 재수집하지 않는다. 변경·위험·미확인 영역을 우선한다.

## 12. 책임 분리

| 책임 | 수행 내용 | 단독 수행 금지 |
|---|---|---|
| Intake | 권한·Source·환경 봉인 | 최종 승격 |
| Learning | Profile·Atom 후보 생성 | 자기 후보 승인 |
| Scenario | Fixture·Oracle 생성 | 단독 PASS |
| Verification | 재현·의미·회귀 검증 | Patch 작성과 최종 승인 겸임 |
| Improvement | 보완안 생성·적용 | 자기 효과 판정 |
| Memory Gate | 승격·폐기·롤백 | 불완전 Receipt 무시 |
| Release Gate | 전체 독립성·계보 확인 | PENDING 상태의 Final |

## 13. 필수 산출물

- Source Manifest
- Rights and Data Classification Receipt
- Program Inventory
- Program Profile
- Domain Coverage Map
- Behavior Profile
- Learning Atom Set
- Conflict and Contamination Report
- Scenario and Oracle Pack
- Verification Receipt
- Before/After Proof
- Promotion Receipt
- Memory Version Manifest
- Rollback Pointer
- RAG 준비물: source_pack.md, chunks.jsonl, manifest.json, ingest_guide.md

RAG 준비물은 RAG_READY 승인 후보까지만 만든다. 실제 embedding, Vector DB 적재, fine-tuning은 별도 승인·실행·검증이 없으면 수행하거나 완료로 표현하지 않는다.

모든 산출물은 SourceBaseline, parent_hashes, actor, tool/model version, created_at, decision을 포함한다.

## 14. 수용 시험

방법론 구현은 다음 실제 시험을 모두 통과해야 한다.

1. 코드·프롬프트·RAG·Tool Contract가 있는 AI 프로그램 최초 학습
2. Source SHA 변경 후 변경분만 재학습하고 이전 기억을 STALE 처리
3. 문서와 코드 충돌 시 자동 선택하지 않고 HOLD
4. 동일 입력 반복으로 비결정성 기록
5. 실패 후보를 재현 시나리오와 결속
6. 개선 전후 동일 Fixture 비교
7. 정상 기능 회귀 발생 시 승격 차단
8. 고객 전용 기억의 다른 프로젝트 검색 차단
9. 실제 Source byte 변조 시 전체 계보 실패
10. PENDING Receipt가 하나라도 있으면 Final 차단
11. 활성 기억 롤백 후 이전 결과 재현
12. 복합 도메인 사례에서 판단 계약과 실행 계약 분리
13. 두 개의 실제 대상 프로그램에서 Full-Chain 2회 연속 성공
14. 사용자 피드백 사건의 요청·결과·지적·수정·승인 계보 재현
15. 12종 학습자산의 필수 필드와 상호 parent hash 결속
16. 2~3중 하네스에서 마지막 2회 연속 신규 유효 패턴 0건과 안정 hash 확인
17. 미학습 프로그램 또는 변경 입력에서 재사용 조건과 금지 조건 검증

## 15. 최종 판정 규칙

학습 완료는 자료를 읽었거나 문서를 생성한 상태가 아니다.

학습 완료는 승인된 SourceBaseline에 대해 Program·Behavior·Domain·Improvement 후보가 증거와 시나리오에 결속되고, 독립 검증과 제한 적용에서 개선이 입증되며, 회귀 없이 활성 기억으로 승격되고 롤백 가능함이 확인된 상태다.

구현과 실제 Full-Chain 시험 전까지 이 문서와 계약의 상태는 DESIGN BASELINE이며, RUNTIME PASS 또는 PRODUCTION READY를 의미하지 않는다.
