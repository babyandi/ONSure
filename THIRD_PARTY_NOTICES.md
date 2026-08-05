# ONSure third-party notices

ONSure itself is proprietary software of ORUDA Labs. The following third-party runtime
components retain their own licenses; the ONSure proprietary notice does not replace or
restrict those license terms.

| Component family | Versions currently inventoried | License |
|---|---|---|
| Jackson annotations/core/databind/datatype | 2.18.9, 2.21, 3.1.5 | Apache-2.0 |
| Flyway core and PostgreSQL support | 12.11.0 | Apache-2.0 |
| PostgreSQL JDBC driver | 42.7.12 | BSD-2-Clause |

The complete Maven and VS Code dependency inventories, exact package URLs and integrity
digests are recorded under `assurance/dependencies/`. Dependency license declarations are
inventory evidence, not a substitute for the corresponding upstream license text or legal
review. Redistributors must retain all notices and license texts required by those upstream
licenses.

RHEL and Ubuntu runtime packages materialize the exact Apache-2.0 and PostgreSQL license texts
from the bundled runtime JARs under `/opt/onsure/legal/` and bind them to `SHA256SUMS`.

No third-party source code or external asset was copied into the ONSure repository according
to the inbound-rights declaration in `contracts/onsure-rights-declaration.v1.json`.
