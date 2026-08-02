# ONSure dependency assurance candidates

이 디렉터리는 공급망 Final PASS가 아니라 재현 가능한 비최종 dependency inventory를 보관한다.

- `onsure.cdx.json`: CycloneDX 1.6 SBOM
- `onsure-dependency-license-inventory.v1.json`: dependency license 선언과 사람 검토 경계
- 생성: `python3 scripts/onsure_supply_chain.py generate`
- 검증: `python3 scripts/onsure_supply_chain.py validate`

Dependency의 license 선언은 ONSure source의 저작권·재배포 권리나 NOTICE 의무에 대한 법적 승인이 아니다.
