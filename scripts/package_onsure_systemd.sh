#!/usr/bin/env bash
set -euo pipefail

product_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
platform="${1:?PLATFORM_REQUIRED}"
output="${2:-${product_root}/target/onsure-${platform}-candidate.tar.gz}"
command -v unzip >/dev/null 2>&1 || { echo "MISSING_COMMAND:unzip" >&2; exit 69; }

case "${platform}" in
  rhel|ubuntu) ;;
  *) echo "UNSUPPORTED_SYSTEMD_PLATFORM:${platform}" >&2; exit 2 ;;
esac
case "${output}" in
  "${product_root}"/target/*) ;;
  *) echo "OUTPUT_MUST_BE_UNDER_PRODUCT_TARGET" >&2; exit 2 ;;
esac

other_platform="rhel"
if [[ "${platform}" == "rhel" ]]; then
  other_platform="ubuntu"
fi
preserve_dir="$(mktemp -d)"
cleanup() {
  rm -rf -- "${preserve_dir}"
  if [[ -n "${stage:-}" ]]; then
    rm -rf -- "${stage}"
  fi
}
trap cleanup EXIT
other_package="${product_root}/target/onsure-${other_platform}-candidate.tar.gz"
if [[ -f "${other_package}" ]]; then
  cp "${other_package}" "${preserve_dir}/"
fi

mvn -B -ntp -q -f "${product_root}/pom-modular.xml" clean install
mkdir -p "${product_root}/target"
if [[ -f "${preserve_dir}/onsure-${other_platform}-candidate.tar.gz" ]]; then
  cp "${preserve_dir}/onsure-${other_platform}-candidate.tar.gz" "${other_package}"
fi
stage="$(mktemp -d "${product_root}/target/onsure-${platform}-stage.XXXXXX")"
mkdir -p "${stage}/opt/onsure/app" "${stage}/opt/onsure/migration" "${stage}/opt/onsure/lib"
mkdir -p "${stage}/opt/onsure/legal"
mkdir -p "${stage}/etc/onsure" "${stage}/usr/lib/systemd/system"
mkdir -p "${stage}/usr/lib/sysusers.d" "${stage}/usr/lib/tmpfiles.d"

cp "${product_root}/modules/onsure-core/target/onsure-core-0.1.0-SNAPSHOT.jar" "${stage}/opt/onsure/app/"
cp "${product_root}/modules/onsure-local-api/target/onsure-local-api-0.1.0-SNAPSHOT.jar" "${stage}/opt/onsure/app/"
cp "${product_root}/modules/onsure-provider-spi/target/onsure-provider-spi-0.1.0-SNAPSHOT.jar" "${stage}/opt/onsure/app/"
cp "${product_root}/modules/onsure-provider-local-mock/target/onsure-provider-local-mock-0.1.0-SNAPSHOT.jar" "${stage}/opt/onsure/app/"
cp "${product_root}/modules/onsure-provider-openai/target/onsure-provider-openai-0.1.0-SNAPSHOT.jar" "${stage}/opt/onsure/app/"
cp "${product_root}/modules/onsure-llm-gateway/target/onsure-llm-gateway-0.1.0-SNAPSHOT.jar" "${stage}/opt/onsure/app/"
cp "${product_root}/modules/onsure-migration-postgresql/target/onsure-migration-postgresql-0.1.0-SNAPSHOT.jar" "${stage}/opt/onsure/migration/"

mvn -B -ntp -q -f "${product_root}/pom-modular.xml" \
  -pl modules/onsure-local-api,modules/onsure-llm-gateway,modules/onsure-migration-postgresql -am \
  dependency:copy-dependencies -DincludeScope=runtime -DexcludeGroupIds=io.onsure \
  -DoutputDirectory="${stage}/opt/onsure/lib"

# Materialize the exact upstream license texts from bundled runtime JARs.
unzip -p "${stage}/opt/onsure/lib/jackson-annotations-2.18.9.jar" META-INF/LICENSE \
  > "${stage}/opt/onsure/legal/APACHE-2.0.txt"
unzip -p "${stage}/opt/onsure/lib/postgresql-42.7.12.jar" META-INF/LICENSE \
  > "${stage}/opt/onsure/legal/POSTGRESQL-LICENSE.txt"

# These distribution-neutral units remain at deploy/rhel for path compatibility.
cp "${product_root}/deploy/rhel/onsure.service" "${stage}/usr/lib/systemd/system/"
cp "${product_root}/deploy/rhel/onsure-llm-gateway.service" "${stage}/usr/lib/systemd/system/"
cp "${product_root}/deploy/rhel/onsure-migrate.service" "${stage}/usr/lib/systemd/system/"
cp "${product_root}/deploy/rhel/onsure.sysusers.conf" "${stage}/usr/lib/sysusers.d/"
cp "${product_root}/deploy/rhel/onsure.tmpfiles.conf" "${stage}/usr/lib/tmpfiles.d/"
cp "${product_root}/deploy/rhel/onsure.env.example" "${stage}/etc/onsure/"
cp "${product_root}/deploy/${platform}/README.md" "${stage}/opt/onsure/README.md"
cp "${product_root}/LICENSE" "${stage}/opt/onsure/legal/LICENSE"
cp "${product_root}/NOTICE" "${stage}/opt/onsure/legal/NOTICE"
cp "${product_root}/THIRD_PARTY_NOTICES.md" "${stage}/opt/onsure/legal/THIRD_PARTY_NOTICES.md"
chmod 0640 "${stage}/etc/onsure/onsure.env.example"

(cd "${stage}" && find . -type f ! -name SHA256SUMS -print0 | sort -z \
  | xargs -0 sha256sum > SHA256SUMS)
tar --sort=name --mtime='2000-01-01T00:00:00Z' --owner=0 --group=0 --numeric-owner \
  -C "${stage}" -czf "${output}" .
sha256sum "${output}"
