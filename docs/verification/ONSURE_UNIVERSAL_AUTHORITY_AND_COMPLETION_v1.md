# ONSure 범용 검증 권위와 완료 기준 v1

이 문서는 범용 검증 구현의 존재와 실제 검증 완료를 분리하는 machine-readable 상태의 해석
기준이다. 현재 상태는 `HOLD`이며 Final PASS, Production GO 또는 범용성 완료를 주장하지 않는다.

## 1. 네 단계 실행 모델

모든 범용 실행은 다음 네 단계를 독립적으로 보고해야 한다.

1. `STRUCTURE_STATIC`: 환경 사전검증, 구조·정적 검사, 검증기 메타검증
2. `COMPONENT_AND_NEGATIVE`: 단위·통합, 정상·실패·재시도·차단 경로
3. `END_TO_END_LINEAGE`: 실제 요청, 산출, read-back, tester, audit, 노출 판정과 계보
4. `OPERATIONAL_RESILIENCE`: 중단, 재개, rollback, 재실행, 복구

단계가 존재하거나 테스트 코드가 있다는 사실은 실행 증거가 아니다. 실행하지 않은 단계는
`NOT_RUN`, 환경이나 권위가 부족하면 `BLOCKED`, 증적 결속이 깨지면 `FAIL` 또는
`INVALID_EVIDENCE`로 유지한다.

## 2. 최소 실제 대상

범용성 완료 후보는 ONSure 자체(`self`), 독립 Python 프로그램(`python`), 독립 Node 프로그램
(`node`) 세 대상 모두가 `REAL_REPOSITORY` provenance를 가져야 한다. Fixture 저장소 내부 경로,
합성 snapshot, provenance가 불명확한 디렉터리는 이 분모에 포함하지 않는다. 각 대상은 같은
commit과 실행 snapshot manifest에 결속된 네 단계 receipt가 필요하다. 현재 HEAD에 결속된 세
대상의 실행 receipt가 없으므로 세 대상 모두 `NOT_RUN`이다.

## 3. 자동 추론 권위

정적 구조와 OpenAPI에서 생성한 업무 의미·흐름은 검토 후보일 뿐이다. 검토와 별도 승인을
거치기 전 자동 실행하지 않으며, 추론 자체는 점수나 PASS 증거가 아니다. 합성 loopback E2E는
승인된 후보의 실행 가능성을 보조하지만 실제 고객 업무의 완전한 E2E를 증명하지 않는다.

## 4. 증적 binding

실제 대상 증거는 `ONSURE_TARGET_PROVENANCE_V1`과
`ONSURE_TARGET_PROVENANCE_RUN_BINDING_V1`을 포함해야 한다. repository identity, commit,
scope, clean 상태, 등록 source digest, 실행 snapshot digest와 manifest를 실행 전후 확인하고,
receipt·report·evidence가 같은 provenance 및 receipt hash를 가리켜야 한다. dirty target,
commit·snapshot·manifest 불일치, `FIXTURE`·`SYNTHETIC_SNAPSHOT`·`UNKNOWN` 분류는 실제 대상
범용성 집계를 차단한다. provenance만으로 PASS를 만들 수 없다.

## 5. 완료 조건

다음 조건을 모두 충족하기 전 machine-readable decision은 `HOLD`다.

- 세 실제 대상 각각 네 단계가 `PASS_NONFINAL`이고 `NOT_RUN`·`BLOCKED`·`INCONCLUSIVE`가 없음
- 각 receipt의 provenance binding 상태가 `VERIFIED_BEFORE_AND_AFTER`
- 각 대상의 `real_target_universality_evidence_eligible=true`
- 자동 추론 후보가 검토·승인·실행 receipt에 결속되고 추론 자체가 PASS로 집계되지 않음
- receipt·report·evidence 독립 read-back 검산 성공
- 현재 source commit에 결속된 반복 실행 증거 존재

이 조건은 self-validation 완료 기준이며 독립 OTester, OAudit 또는 사람의 Final 승인 권위를
대체하지 않는다.
