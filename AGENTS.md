# ONSure Product Agent Instructions

이 파일은 독립 저장소의 현재 개발과 향후 `products/onsure/` 이관 준비에 모두 적용한다.

## 제품 경계

- ONSure는 검증, 증적, RCA, 개선 증명과 비최종 assurance 후보 생성을 소유한다.
- 현재 저장소는 독립적으로 clone, build, test, package할 수 있어야 한다.
- 현재 Java namespace `io.onsure`와 기존 공개 API를 호환성 승인 없이 변경하지 않는다.
- 미래 namespace `kr.co.oruda.products.onsure`는 실제 cutover 전까지 후보로만 기록한다.
- ORUDA, aTops, AsterDB 또는 ORUDA-Products의 source를 직접 참조하거나 수정하지 않는다.

## Build 권위

- 독립 release 후보 검증의 권위 build는 `mvn -B -ntp -q clean verify`다.
- `pom-modular.xml`은 미래 물리 모듈 분해를 검증하는 compatibility build이며 release 권위가 아니다.
- Java 변경은 권위 build와 `mvn -B -ntp -q -f pom-modular.xml clean package`를 모두 검증한다.
- 권위 build 후 `python3 scripts/onsure_java_api_baseline.py validate`로 전체 public binary descriptor 호환성을 검증한다.
- dependency 변경은 CycloneDX SBOM과 license inventory를 재생성하고 `python3 scripts/onsure_supply_chain.py validate`를 실행한다.
- Python 변경은 `python3 -m unittest discover -s tests`를 검증한다.
- 실행하지 않은 검증은 `NOT_RUN`으로 기록하고 로컬 self-validation으로 Final PASS를 주장하지 않는다.

## 모듈·의존성 경계

- `io.onsure.common`은 `io.onsure.platform`이나 `io.onsure.rag`를 import하지 않는다.
- `io.onsure.platform`은 `io.onsure.rag`를 import하지 않는다.
- 기존 RAG 공개 API 호환을 위한 `io.onsure.rag -> io.onsure.platform` 간선만 임시 허용한다.
- Core 소스는 ORUDA package 또는 `OrudaTargetAdapter`를 직접 import하지 않는다.
- ORUDA 전용 구현은 `onsure-adapter-oruda`가 소유하고 artifact 의존 방향은 adapter에서 core로만 향한다.
- 공유 source root와 `io.onsure.platform` split package는 제거되었다. module-owned source root와 0건 baseline을 `scripts/validate_onsure_build_boundary.py`로 유지한다.

## 제품 루트와 출력 경계

- 제품 루트의 단일 명시적 override는 절대경로 `ONSURE_PRODUCT_ROOT`다.
- override가 없으면 제품 스크립트 위치를 기준으로 현재 독립 저장소 root를 계산한다.
- 제품 출력과 변경은 제품 root 밖으로 나가면 안 된다.
- 외부 workspace 절대경로나 다른 제품 source가 독립 build의 입력이 되면 실패 처리한다.

## Git·이관 안전

- 작업은 전용 `codex/` 브랜치와 Draft PR에서 수행한다.
- `main` 직접 수정·병합, 실제 배포, Production/Commercial GO, Final PASS를 수행하지 않는다.
- 실제 ORUDA-Products 파일 이동·복사·병합은 별도 승인과 immutable cutover SHA 없이는 수행하지 않는다.
- cutover 전 격리된 임시 `products/onsure/` 리허설에서 Manifest digest, rollback, canonical/modular build를 검증한다.
- 열린 PR overlap 판정이 `HOLD_MERGE_ORDER_REQUIRED`이면 권한 보유 여부와 무관하게 자동 병합하지 않는다.
- Manifest는 자동 민감정보 검사 결과와 사람의 라이선스·소유권·고객 데이터 확인을 구분한다.
