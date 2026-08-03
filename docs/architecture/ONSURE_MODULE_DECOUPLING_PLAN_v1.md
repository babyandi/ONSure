# ONSure module decoupling plan v1

상태: `PARTIAL / SPLIT_AND_CYCLE_REMOVED / SELF_VALIDATION_NONFINAL`

현재 Maven artifact graph, split package, Java package mutual cycle은 모두 0건이다. Java
namespace `io.onsure`, 기존 source path와 기존 238개 공개 class descriptor는 변경하지 않았다.
새 target-neutral `TargetEvidenceContributor` SPI와 ORUDA provider bridge 2개만 additive API로
추가해 public baseline을 240개로 명시적으로 갱신했다.

## 이번 단계에서 완료한 소유권 정리

1. `onsure-core`가 기존 `io.onsure.platform` 공개 호환 class를 단독 compile한다.
2. `onsure-adapter-oruda`는 `io.onsure.platform.oruda`만 compile하고 core에만 의존한다.
3. `OrudaTargetAdapter`는 ORUDA registry를 직접 import하지 않고 `ServiceLoader`로 정확히 하나의
   `TargetEvidenceContributor`를 요구한다. provider 누락·중복은 fail-closed다.
4. `onsure-cli`와 `onsure-local-api`는 core class를 중복 compile하지 않으며 각각 고유 package의
   module-owned main entrypoint와 executable manifest를 제공한다.
5. shared source root를 읽는 core/adapter compiler에 `implicit=none`을 강제해 include 밖 source의 암묵 재컴파일을 막고, 실제 8개 module JAR class inventory에서도 split package 0건을 확인했다.
6. `allowed_split_packages`와 `allowed_package_cycles` baseline을 빈 집합으로 낮췄다.

## 남은 단계

- `onsure-core`와 `onsure-adapter-oruda`가 같은 물리 `src/main/java` root를 서로 배타적인 include/exclude로
  읽는 transitional source-root 공유 2건은 남아 있다.
- 현재 Java 파일 경로 동결을 해제하는 별도 cutover 승인 후 `io.onsure.platform.oruda` source를
  adapter 전용 source directory로 이동하고, core source도 module root로 귀속한다.
- 이동 전후 canonical/API/modular/ORUDA E2E와 Manifest digest를 검증하고 shared source module count를
  2에서 0으로 낮춘다.

이 문서는 실제 ORUDA-Products 이동이나 namespace 변경을 승인하지 않는다. `pom.xml`은 계속 독립
release 후보 권위이고 `pom-modular.xml`은 compatibility gate다.
