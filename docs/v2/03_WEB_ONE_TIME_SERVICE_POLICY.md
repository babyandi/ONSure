# ONSure Web 1회성 서비스 정책

## 1. Service Case 정의

하나의 Service Case는 계약 시 확정한 대상 System, Program, Source Baseline, 서비스 유형, 학습량, 검증범위, 개선한도, 재검증횟수, 수행기간과 산출물에 대해 작업을 수행하고 종료하는 단일 서비스 건이다.

## 2. 1회의 경계

### Learn 1회
고정된 기준선과 범위에 대해 Program Profile 기준선을 생성하고 납품한다.

### Verify 1회
고정된 기준선과 검증범위에 대해 최초 검증을 수행하고 최종 검증보고서를 납품한다.

### Learn & Verify 1회
동일 기준선을 학습해 Program Profile을 만들고, 확인된 프로필을 근거로 검증을 수행하여 통합 결과를 납품한다.

### Improve & Re-verify 1회
선택한 Finding과 Improvement 한도 내에서 수정하고 계약된 횟수만큼 재검증하여 Before/After 결과를 납품한다.

내부 재시도, ONSure 장애에 따른 재실행, 비결정성 확인 반복은 별도 횟수로 세지 않는다.

## 3. 기준선 결속

Case는 다음 중 하나 이상에 결속한다.

- Git Commit SHA
- Release Tag
- Source Archive SHA-256
- Container Image Digest
- Artifact Hash

고객의 신규 기능 추가, 주요 구조 변경, Program 추가, Agent·RAG·Tool 변경은 변경계약 또는 신규 Case 판정 대상이다.

## 4. Preflight

```text
계정·조직 확인
→ 대상 연결 또는 업로드
→ 악성코드·비밀정보·권한 사전검사
→ System·Program 경계 판정
→ 학습량·실행가능성 산정
→ 서비스 적합성 판정
→ 견적·기간·제외범위 제시
```

판정:

- READY: 정액 또는 확정견적으로 진행
- NEEDS_BASELINE: Verify 전 기준 작성 필요
- RECOMMEND_LEARN_VERIFY: 자료 부족으로 통합서비스 권장
- CUSTOM_QUOTE: 대규모·고위험·폐쇄환경
- REJECT/HOLD: 불법·권한불명·악성코드·실행불가

## 5. 결제

소규모는 확정 견적 전액 선결제, 불확실한 대규모는 사전진단비와 본 서비스 차액의 2단계 결제를 지원한다.

고비용 실행은 예상 사용량을 OLicense에서 예약한다.

```text
Estimate
→ Customer Approval
→ Credit/Amount Reserve
→ Execute
→ Actual Commit
→ Unused Release
```

## 6. Case 상태

```text
DRAFT
→ PREFLIGHT
→ QUOTED
→ PAYMENT_PENDING
→ LICENSED
→ INTAKE
→ BASELINE_LOCKED
→ LEARNING / VERIFYING / IMPROVING
→ CUSTOMER_APPROVAL
→ REVERIFYING
→ DELIVERING
→ COMPLETED
```

예외 상태: `HOLD`, `CANCELLED`, `EXPIRED`, `REFUND_PENDING`, `FAILED_PLATFORM`, `FAILED_CUSTOMER_ENV`.

## 7. 포함·추가·신규 Case

동일 Case 포함:

- 합의된 자료의 제한적 보완
- 분석 정제와 내부 재실행
- ONSure 수정 Branch
- 계약된 재검증
- 보고서 오류·누락 정정

추가 결제 또는 신규 Case:

- System·Program 추가
- 기준선의 중대한 변경
- Learning Unit 한도 초과
- Verification Pack 추가
- Improvement Unit 초과
- 고객 변경본 재검증
- 재검증횟수 초과
- Case 유효기간 만료

## 8. 종료조건

- 최종 산출물 납품·확인
- 계약 한도 소진
- 기간 만료
- 고객 종료 요청
- 대상 권한 상실
- 기준선 무효화

완료 시 기준선, 범위, 실행결과, Finding, Patch, 잔여위험, 보고서와 Evidence를 봉인한다.

## 9. Result Warranty Window

납품 후 제한된 기간 동안 보고서 문의, ONSure 수정부분의 재현 오류, 누락 산출물을 처리한다. 고객의 추가개발, 모델·Prompt·RAG·환경 변경, 신규 결함은 보증범위가 아니다.

## 10. 데이터 처리

- 최소 권한 연결
- 고객별 격리 Workspace
- Secret 탐지·마스킹
- 보존기간과 삭제정책 명시
- 학습 산출물의 고객 소유권과 범용 익명 패턴 사용 동의 분리
- Enterprise 전용 격리·Offline 옵션
