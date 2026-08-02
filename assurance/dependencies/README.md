# ONSure dependency assurance candidates

이 디렉터리는 공급망 Final PASS가 아니라 재현 가능한 비최종 dependency inventory를 보관한다.

- `onsure.cdx.json`: CycloneDX 1.6 SBOM
- `onsure-dependency-license-inventory.v1.json`: dependency license 선언과 사람 검토 경계
- `onsure-vulnerability-scan.v1.json`: SBOM digest에 결속된 취약점 scanner evidence. 현재 scanner DB가 없어 `NOT_RUN`이다.
- `onsure-npm-audit.v1.json`: VS Code package-lock digest에 결속된 npm audit evidence
- `contracts/onsure-supply-chain-policy.v1.json`: unique purl, component SHA-256, license review, critical/high zero release gate
- 생성: `python3 scripts/onsure_supply_chain.py generate`
- 검증: `python3 scripts/onsure_supply_chain.py validate`

Dependency의 license 선언은 ONSure source의 저작권·재배포 권리나 NOTICE 의무에 대한 법적 승인이 아니다.

Air-gap 후보는 network를 사용하지 않고 명시적으로 지정한 local Maven repository의 JAR/POM을 SBOM SHA-256에 결속한다. npm payload와 외부 서명은 별도 cache export/signer가 필요하므로 현재 `NOT_RUN`이다.

```bash
python3 scripts/onsure_airgap_pack.py plan --maven-repository /explicit/path/to/maven-repository
```
