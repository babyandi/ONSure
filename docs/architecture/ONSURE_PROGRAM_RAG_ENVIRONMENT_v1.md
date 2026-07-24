# ONSure 프로그램별 RAG 준비 환경 v1

## 소유권

- ONSure는 자신의 검증·학습 실행에서 발생한 RAG 후보와 판정 Receipt를 ONSure
  실행 저장소의 `rag-preparation/`에서 관리한다.
- 검증 대상 프로그램에 RAG 자료 준비가 필요하면 ONSure는 필요성을 판정한다.
- 실제 프로그램 자료와 준비 환경은 대상 프로그램의
  `.onsure/rag-preparation/`에서 대상 프로그램이 관리한다.

## 실행 분리

1. 검증 완료 후 ONSure 자체 후보를 항상 기록한다.
2. 재사용 가능한 Failure Mode·RCA가 있으면 `RAG_READY`, 추가 검토가 필요하면
   `RAG_REVIEW_REQUIRED`, 가치가 없으면 `LOCAL_ONLY`로 판정한다.
3. 대상 프로그램 환경 생성은 검증과 분리된 명시적 Bootstrap 승인 뒤에만 한다.
4. Bootstrap은 `source/source_pack.md`, `chunks/chunks.jsonl`, `manifest.json`,
   `ingest_guide.md`, `candidates/`, `quarantine/`, `receipts/`를 만든다.
5. 기존 파일은 덮어쓰지 않으며 소유 대상 경로가 다르면 Fail-Closed한다.

## 금지

- 검증 실행 중 대상 프로그램 소스를 자동 변경하지 않는다.
- ONSure가 대상 프로그램의 실제 학습자료를 중앙 소유하지 않는다.
- Bootstrap을 실제 적재·Index 생성·Embedding·파인튜닝·적용으로 표시하지 않는다.
- `LOCAL_ONLY` 판정에는 불필요한 환경을 만들지 않는다.
