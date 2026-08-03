#!/usr/bin/env bash
set -euo pipefail

product_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output="${1:-${product_root}/target/onsure-rhel-candidate.tar.gz}"
case "${output}" in
  "${product_root}"/target/*) ;;
  *) echo "OUTPUT_MUST_BE_UNDER_PRODUCT_TARGET" >&2; exit 2 ;;
esac

mvn -B -ntp -q -f "${product_root}/pom-modular.xml" clean install
mkdir -p "${product_root}/target"
stage="$(mktemp -d "${product_root}/target/onsure-rhel-stage.XXXXXX")"
trap 'rm -rf -- "${stage}"' EXIT
mkdir -p "${stage}/opt/onsure/app" "${stage}/opt/onsure/migration" "${stage}/opt/onsure/lib"
mkdir -p "${stage}/etc/onsure" "${stage}/usr/lib/systemd/system"
mkdir -p "${stage}/usr/lib/sysusers.d" "${stage}/usr/lib/tmpfiles.d"

cp "${product_root}/modules/onsure-core/target/onsure-core-0.1.0-SNAPSHOT.jar" "${stage}/opt/onsure/app/"
cp "${product_root}/modules/onsure-local-api/target/onsure-local-api-0.1.0-SNAPSHOT.jar" "${stage}/opt/onsure/app/"
cp "${product_root}/modules/onsure-provider-spi/target/onsure-provider-spi-0.1.0-SNAPSHOT.jar" "${stage}/opt/onsure/app/"
cp "${product_root}/modules/onsure-provider-openai/target/onsure-provider-openai-0.1.0-SNAPSHOT.jar" "${stage}/opt/onsure/app/"
cp "${product_root}/modules/onsure-migration-postgresql/target/onsure-migration-postgresql-0.1.0-SNAPSHOT.jar" "${stage}/opt/onsure/migration/"

mvn -B -ntp -q -f "${product_root}/pom-modular.xml" \
  -pl modules/onsure-local-api,modules/onsure-provider-openai,modules/onsure-migration-postgresql -am \
  dependency:copy-dependencies -DincludeScope=runtime -DexcludeGroupIds=io.onsure \
  -DoutputDirectory="${stage}/opt/onsure/lib"

cp "${product_root}/deploy/rhel/onsure.service" "${stage}/usr/lib/systemd/system/"
cp "${product_root}/deploy/rhel/onsure-migrate.service" "${stage}/usr/lib/systemd/system/"
cp "${product_root}/deploy/rhel/onsure.sysusers.conf" "${stage}/usr/lib/sysusers.d/"
cp "${product_root}/deploy/rhel/onsure.tmpfiles.conf" "${stage}/usr/lib/tmpfiles.d/"
cp "${product_root}/deploy/rhel/onsure.env.example" "${stage}/etc/onsure/"
cp "${product_root}/deploy/rhel/README.md" "${stage}/opt/onsure/README.md"

(cd "${stage}" && find . -type f ! -name SHA256SUMS -print0 | sort -z \
  | xargs -0 sha256sum > SHA256SUMS)
tar --sort=name --mtime='2000-01-01T00:00:00Z' --owner=0 --group=0 --numeric-owner \
  -C "${stage}" -czf "${output}" .
sha256sum "${output}"
