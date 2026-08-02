# ONSure module decoupling plan v1

상태: `STAGED / PARTIAL / SELF_VALIDATION_NONFINAL`

현재 artifact graph는 순환 0건이며 신규 `onsure-provider-spi`, `onsure-provider-local-mock`,
`onsure-sdk`는 독립 source root를 사용한다. 기존 `onsure-core`, `onsure-cli`,
`onsure-local-api`, `onsure-adapter-oruda`는 공개 `io.onsure` API와 canonical root build를
보존하기 위해 아직 `src/main/java`를 compiler include로 분할 소유한다.

## 단계적 제거 순서

1. CLI와 Local API source를 각 module root로 이동하되 package는 `io.onsure.platform`으로 유지한다.
2. `OrudaTargetAdapter`와 `io.onsure.platform.oruda`를 adapter source root로 함께 이동한다.
3. ORUDA evidence persistence hook를 target-neutral SPI로 역전해
   `io.onsure.platform -> io.onsure.platform.oruda` import를 제거한다.
4. root API baseline, CLI, Local API와 ORUDA E2E가 동일함을 검증한 뒤
   `allowed_split_packages`와 `allowed_package_cycles`를 0건으로 낮춘다.

이번 변경에서는 Java package, canonical source path와 기존 root public baseline을 바꾸지 않았다.
따라서 split package 1건과 package cycle 1건을 제거 완료로 주장하지 않으며, 물리 이동은 별도
호환성 승인과 immutable baseline commit 뒤에 수행해야 한다.
