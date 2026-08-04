# ONSURE 요청 범위 완료 점검표 v1

## 구현 상태

- [ ] ONSURE 제품 핵심부 — `PARTIAL`
- [ ] 범용 검증 엔진 — `PARTIAL`; 4개 보증 수준·7개 순차 검증군·Pack SPI·격리 Runner와
  검증 전용 OCI fallback 및 독립 Gradle 대상 실제 실행 완료, 독립 감사 미완료
- [ ] 검증 대상 등록소와 대상 어댑터 — `PARTIAL`; 등록 source의 `validation_mode=UNIVERSAL`과 Manifest 비의존 언어 탐지 구현, binary/package intake 미완료
- [x] 일반 프로그램 종단간 시험 대상 — 중립 Java·Python·Node·Gradle 대상과 portable lineage
  read-back을 OCI sandbox에서 전 7개 검증군 `PASS_NONFINAL`, artifact 변조 차단 `FAIL`
- [ ] AI 프로그램 종단간 시험 대상 — `NOT_RUN_REAL_TARGET`
- [ ] 실패 유형·근본원인분석·개선 계획 — `PARTIAL`; 인과 재현과 실제 Patch 전후 입증 미완료
- [x] 시험 데이터·하네스·오라클 등록 골격
- [ ] 회귀 잠금과 재검증 비교 — `PARTIAL`
- [ ] 독립 검증·감사 영수증 — `NOT_RUN`
- [x] 범용 검증 축 30개 계약
- [x] 시험 데이터 유형 7개 계약
- [x] 실행 전 점검과 개발 관문 골격
- [x] 학습 엔진·검증 엔진 분리 설계
- [ ] 학습 후보 실제 적용 파이프라인 — `PARTIAL`

외부 제품은 ONSURE의 필수 연계 대상, 제품 구성요소 또는 완료 조건이 아니다.
기존 선택형 Adapter가 남아 있더라도 독립 Core의 범용성 증거로 사용하지 않는다.

## 정적 통합 완료

- [x] 제품 핵심부와 범용 하네스의 단일 기준선 결속
- [x] 실행 명령 허용 목록과 경로 이탈 차단
- [x] 시간 제한·출력 제한·종료 코드 증적
- [x] 영수증·증적 SHA-256 목록
- [x] 실패 시 `RCA_PENDING`
- [x] 독립 회귀검증 2회 관문
- [x] 서로 다른 운영자의 독립 실행 2회 조건
- [x] `NOT_RUN`·`BLOCKED` 최종 후보 차단
- [x] 자동 최종 잠금 금지

## 실제 실행 필요

- [x] JDK 17 확인
- [x] Maven 확인
- [x] `mvn -B -ntp -q clean verify` 2회
- [x] `mvn -B -ntp -q -f pom-modular.xml clean package`
- [x] Public Java API 265개; 기존 259개 변경·삭제 0, 표준 Pack API 6개 추가
- [x] Java 347개 회귀 테스트(조건부 11개 skip)
- [x] Python 202개 회귀 테스트
- [x] Gradle 표준 Pack의 offline build, 부정·재시도·차단, 연결 E2E, 운영 복구 convention 탐지와
  독립 외부 Gradle 대상 20개 필수 Step 실제 실행 (`PASS_NONFINAL`, 원본 변경 0건)
- [x] OpenAPI 3.1 Local API 16개·LLM Gateway 4개 경로 계약
- [x] portable Workflow lineage 계약·실제 artifact/schema/permit digest read-back과 변조 차단
- [ ] rootless bubblewrap private network namespace (`BWRAP_LOOPBACK_PERMISSION_DENIED` 유지)
- [x] 검증 전용 OCI sandbox: image pull 금지·immutable ID·network none·read-only rootfs·capability 0,
  12개 경계 probe, Java·Maven·Node·npm·ClamAV·Noto CJK capability 및 중립 Node 4차 검증
  `PASS_NONFINAL`
- [ ] 제품 플랫폼 종단간 시험 2회
- [ ] 범용 하네스 독립 실행 2회
- [x] ONSURE 자체 보증 2회 — 동일 source digest, 각 26개 필수 Step과 안정 의미 판정 일치
- [ ] 실패 발생 시 근본원인분석·수정·전체 회귀검증
- [ ] 개발 관문 `PASS`
- [ ] 증적 SHA-256 읽기 전용 재검증 — 범용 Runner의 PASS log·환경 digest 검증 구현,
  외부 signer·불변 저장소·독립 감사 영수증 검증은 `NOT_RUN`

## 최종 후보 조건

- [ ] `NOT_RUN=0`
- [ ] `BLOCKED=0`
- [ ] 미해결 `Critical/Major=0`
- [ ] 독립 실행 운영자 분리
- [ ] 동일 환경·소스·정책·시험 데이터·오라클
- [ ] 정규화 결과 해시 동일
- [ ] 필요한 근본원인분석과 회귀검증 완료

## 최종 잠금

최종 후보 조건을 충족해도 최종 잠금은 자동 허용하지 않는다. 별도 승인·독립 감사·최종 영수증 검증이 필요하다.

```text
현재 개발 상태      PARTIAL_LOCAL_OCI_SANDBOX_PASS
개발 관문           HOLD
최종 후보           BLOCKED
최종 잠금           NOT_ALLOWED
```
