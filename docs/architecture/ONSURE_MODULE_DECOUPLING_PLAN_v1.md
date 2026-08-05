# ONSure module decoupling plan v1

상태: `IMPLEMENTED / SHARED_ROOT_SPLIT_AND_CYCLE_REMOVED / SELF_VALIDATION_NONFINAL`

현재 Maven artifact graph, split package, Java package mutual cycle은 모두 0건이다. Java
namespace `io.onsure`와 공개 descriptor는 변경하지 않았다. source는 module-owned root로 이동했다.
새 target-neutral `TargetEvidenceContributor` SPI와 ORUDA provider bridge 2개만 additive API로
추가해 public baseline을 240개로 명시적으로 갱신했다.

## 이번 단계에서 완료한 소유권 정리

1. `onsure-core`가 기존 `io.onsure.platform` 공개 호환 class를 단독 compile한다.
2. `onsure-adapter-oruda`는 `io.onsure.platform.oruda`만 compile하고 core에만 의존한다.
3. `OrudaTargetAdapter`는 ORUDA registry를 직접 import하지 않고 `ServiceLoader`로 정확히 하나의
   `TargetEvidenceContributor`를 요구한다. provider 누락·중복은 fail-closed다.
4. `onsure-cli`와 `onsure-local-api`는 core class를 중복 compile하지 않으며 각각 고유 package의
   module-owned main entrypoint와 executable manifest를 제공한다.
5. core와 adapter가 각각 전용 source root를 소유하며, compiler `implicit=none`과 module JAR inventory로 split package 0건을 확인한다.
6. `allowed_split_packages`와 `allowed_package_cycles` baseline을 빈 집합으로 낮췄다.

## 남은 단계

- 독립 clone과 nested migration rehearsal에서 module-owned 경로와 Manifest digest를 반복 검증한다.
- 실제 cutover 전 immutable SHA, 상위 build owner와 adapter version owner를 승인한다.

이 문서는 실제 ORUDA-Products 이동이나 namespace 변경을 승인하지 않는다. `pom.xml`은 계속 독립
release 후보 권위이고 `pom-modular.xml`은 compatibility gate다.
