# OReport 읽기 전용 검증 관찰 — 2026-08-04

## 범위와 경계

- 대상은 외부 저장소의 commit `0c928a0839bf772241216955891fdd75e3013c81`에서 만든
  3,752-file `git archive` 불변 스냅샷이다.
- 대상 저장소의 dirty/untracked 파일은 스냅샷에서 제외했으며 대상 저장소를 수정하지 않았다.
- 실행 전후 대상 HEAD와 `git status --porcelain` SHA-256
  `1be83e81edd37e17f61667969d55aa1d75d36cddb448cb79c28ef90f306fbeb0`는 동일했다.
- ONSure와 대상 제품 사이에 runtime/import/source 의존성을 추가하지 않았다.
- 결과는 self-validation 비최종 관찰이며 Production GO, Final PASS 또는 독립 OTester/OAudit를
  주장하지 않는다.

## 7개 검증군 결과

| 순서 | 검증군 | 결과 | 근거 |
|---:|---|---|---|
| 1 | 환경·의존성 | `BLOCKED` | `package-lock.json`에는 renderer/font dependency 3개가 있으나 `package.json` dependency 집합은 비어 있음 |
| 2 | 구조 | `NOT_RUN` | 환경 필수 gate 미통과 |
| 3 | 검증기 메타검증 | `NOT_RUN` | 구조 gate 미실행 |
| 4 | 단계별 기능 | `NOT_RUN` | 앞선 필수 gate 미통과 |
| 5 | 실제 연결 E2E | `NOT_RUN` | 앞선 필수 gate 미통과 |
| 6 | 증적·판정 | `NOT_RUN` | 실행 PASS step이 없고 연결 E2E 미실행 |
| 7 | 운영·복구 | `NOT_RUN` | 증적 gate와 합성 DB 운영 profile 미실행 |

전체 판정은 `BLOCKED`이다. 차단 상태를 우회해 하위 단계를 PASS로 올리지 않았다.

## 발견된 실행 경로와 차이

읽기 전용 소스 조사에서는 다음 경로가 서로 다른 권위를 가진 것으로 확인됐다.

- reviewed v2 entrypoint는 CanonicalScene 이후 `AWAITING_OFLOW_GENERATION_PERMIT`에서
  의도적으로 정지한다.
- legacy local E2E는 Permit 소비와 16-page render를 시도하지만 반환 receipt의 OTester와
  OAudit 상태가 `PENDING`이며 제품 문서가 이 경로를 `LEGACY_NONAUTHORITATIVE`로 규정한다.
- v1.5 authoritative case runtime은 PageSpec, styled design/Croquis, materialized scene,
  PostgreSQL-backed Permit issue/consume, renderer, read-back, OTester, OAudit, exposure 및
  recovery runner를 정의한다. 다만 해당 외부 대상의 자체 문서는 실제 운영 E2E를 아직
  수행하지 않은 `NO_GO`로 기록한다.

따라서 source symbol 또는 regression test의 존재만으로
Story Flow → Croquis → Permit → Renderer → read-back → OTester → OAudit → exposure 연결을
실행 완료로 판정할 수 없다.

## 증적

- ONSure source/snapshot digest:
  `b879abba9df85604c7a40facdc5405ae5f992c1da9f2f1877928579d0a06a876`
- environment digest:
  `b1b0f3a751e610bc6cf537b2a34abfdc5f3cb17892d56d7ddac42ac777ca4958`
- path-free observation receipt:
  `assurance/runtime/oreport-readonly-validation-observation.v1.json`
- observation receipt SHA-256:
  `c3095ea7c7652d53b93c738790ce1517e92c4393c9d077df737c4bc01e6daea8`

봉인기는 21개 step log, 환경 digest, 실행 시각 순서, source/snapshot 동일성,
source mutation=false 및 final authority=false를 실제 파일 read-back으로 검증한 뒤 절대경로를
제거했다.

## 다음 실행을 위한 차단 해소 조건

1. 외부 대상 소유자가 `package.json`과 `package-lock.json` dependency 집합을 일치시킨다.
2. renderer, 한글 font, ClamAV, LibreOffice/Poppler, signing fixture와 실행 권한을 동일
   불변 스냅샷 환경에서 다시 preflight한다.
3. ONSure 1번 gate가 통과한 새 source commit에 대해 2번부터 순서대로 실행한다.
4. authoritative E2E는 합성 PostgreSQL과 서로 분리된 OTester/OAudit runner/signing identity를
   사용하고, 모든 산출물을 read-back한다.
5. 중단·재개·rollback·rerun을 완료하기 전에는 4차 보증을 PASS로 올리지 않는다.

ONSure 저장소에서 외부 대상의 dependency 파일을 대신 수정하지 않는다.
