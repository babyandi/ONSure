# ONSure OBuilder 준비 경계

이 디렉터리는 향후 `products/onsure/.obuilder/` 편입을 위한 비최종 build metadata 후보다.

- 현재 독립 build 권위와 미래 module compatibility gate만 선언한다.
- OBuilder 실행 승인, 배포 승인, FinalLock 또는 Production GO를 부여하지 않는다.
- 배포·DB migration은 `contracts/onsure-operational-boundary.v1.json`의 `DESIGN_ONLY_NONFINAL` 경계를 검증할 뿐 실행하지 않는다.
- GitHub Actions는 사용하거나 요구하지 않는다.
- ORUDA-Products의 최종 schema가 확정되면 `product-build.yaml`을 변환하고 다시 검증해야 한다.
