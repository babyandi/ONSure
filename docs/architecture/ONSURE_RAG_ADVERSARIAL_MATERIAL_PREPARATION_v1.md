# ONSURE 적대검증 RAG 학습자료 준비 v1

## 목적

ONSure 자기검증에서 발생한 실패 사례와 차단 결과를 나중에 RAG가 검색할 수 있는
학습자료 후보로 준비한다. 이 단계는 Index 생성, Embedding, 모델 학습, 승격 또는
적용이 아니다.

## 자료 유무 판정

- `COMPLETE`: 실패 재현부터 RCA, 차단 규칙, 수정, 재검증 증적과 Hash까지 모두 존재
- `PARTIAL`: 실패 패턴과 기대 판정은 있으나 RCA·수정·재검증 증적 중 일부가 없음
- `MISSING`: Source에 결속된 실패 자료가 없음
- `DUPLICATE`: 같은 Fixture와 의미 실패 키를 가진 자료가 둘 이상임
- `STALE`: Source Fixture 또는 통제 계약 Hash가 현재 바이트와 다름

`PARTIAL`, `MISSING`, `DUPLICATE`, `STALE` 자료는 RAG 투입 자격을 `HOLD`로 판정한다.

## 준비 단위

각 자료는 다음을 가져야 한다.

1. Material ID와 Fixture ID
2. 실패 패턴과 재현 조건
3. 기대 Decision과 Reason
4. 누락 원인과 차단 규칙
5. 수정 내용
6. 수정 전후 및 반복 재검증 증적
7. Source·Fixture·Policy·Receipt SHA-256
8. 중복 판정용 Semantic Key
9. 민감정보 및 Hidden-answer 비접근 확인
10. `PREPARED_NOT_INGESTED` 상태

## 현재 판정

기존 A01~A20은 실패 패턴과 기대 판정은 있으나 사례별 RCA·수정·재검증 증적이
Fixture 단위로 봉인되지 않았다. 따라서 20건 모두 `PARTIAL/HOLD`로 준비한다.

1,000회 반복 적대검증이 통과해도 자동으로 `COMPLETE`로 승격하지 않는다. 각
Fixture의 실행 Receipt와 결과 Hash가 Material ID에 결속되고, 독립 검토가 끝난
경우에만 별도 변경으로 승격할 수 있다.

## 금지 상태

- RAG Index 생성: `NOT_CREATED`
- Embedding: `NOT_RUN`
- 실제 학습 반영: `NOT_RUN`
- 적용 건수: `0`
- FinalLock·Production GO·Commercial GO: 허용하지 않음
