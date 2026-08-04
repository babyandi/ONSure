# ONSure bubblewrap 실행환경 계약 v1

상태: `ENVIRONMENT_SETUP_GUIDE / NONFINAL`

## 목적

ONSure sandbox는 `AUTO`, `ROOTLESS_BWRAP`, `OCI_DOCKER`를 허용한다. `AUTO`는 먼저 rootless
bubblewrap 실제 probe를 실행하고 실패하면 로컬 OCI image를 immutable `sha256:` ID로 해석할 수
있을 때만 OCI를 선택한다. 둘 다 불가능하면 `BLOCKED_ENVIRONMENT`로 닫힌다. GitHub Actions는
사용하지 않는다. OCI는 검증 snapshot/fixture 실행 backend일 뿐 Ubuntu/RHEL systemd 단독 서버
배포 topology를 변경하지 않는다.

## 진단

```bash
python3 scripts/onsure_bubblewrap_diagnostics.py
python3 scripts/onsure_sandbox_diagnostics.py
```

현재 Codex host에서 확인된 reason은 `BWRAP_LOOPBACK_PERMISSION_DENIED`다.

```text
bwrap: loopback: Failed RTM_NEWADDR: Operation not permitted
```

이는 Java/Maven 코드 실패가 아니다. 외부 container/runtime가 새 network namespace 안의 loopback 설정을 거부한 것이다. 같은 source의 canonical `mvn clean verify`는 sandbox 밖에서 통과한다.

2026-08-04 재현 환경은 Linux `6.8.0-136-generic`, bubblewrap `0.9.0`이며
`kernel.unprivileged_userns_clone=1`, user/network namespace 한도는 각각 `30865`였다.
namespace 생성 한도는 열려 있지만 outer runtime이 private network namespace의 loopback
주소 설정을 거부한다. rootless bubblewrap 자체는 계속 차단되며 이를 PASS로 바꾸거나
`--unshare-net`을 제거하지 않는다.

현재 호스트에서는 Docker daemon의 AppArmor·builtin seccomp가 활성화되어 있고 로컬 검증 image가
있어 OCI backend가 선택된다. source read-only, `/tmp` write, network egress, filesystem/symlink
격리, orphan process, CPU/memory/PID/file/time 제한, capability와 환경 필터 경계 probe가 통과했다.
범용 validation에는 Runner가 복사한 writable snapshot만 mount하며 외부 원본은 mount하지 않는다.
실행 후 원본 digest를 다시 확인한다.

Ubuntu 24.04의 `kernel.apparmor_restrict_unprivileged_userns=1`을 전역으로 끄지 않고
`/usr/bin/bwrap`에 필요한 `userns,`만 부여하는 검토 후보는
`deploy/apparmor/usr.bin.bwrap-onsure`다. 운영자가 내용을 검토한 뒤 다음처럼 설치한다.

```bash
sudo install -o root -g root -m 0644 \
  deploy/apparmor/usr.bin.bwrap-onsure \
  /etc/apparmor.d/usr.bin.bwrap-onsure
sudo apparmor_parser -r /etc/apparmor.d/usr.bin.bwrap-onsure
python3 scripts/onsure_bubblewrap_diagnostics.py
```

진단이 계속 실패하면 Profile은 유지하고 audit/kernel log를 조사한다. 전역 AppArmor
disable, `kernel.apparmor_restrict_unprivileged_userns=0`, privileged container는 권장하지 않는다.
Rollback은 `sudo apparmor_parser -R /etc/apparmor.d/usr.bin.bwrap-onsure` 후 설치 파일을
제거하는 명시적 운영 작업이다.

## 필수 환경

- Linux host에서 unprivileged user namespace 생성이 허용되어야 한다.
- user 및 network namespace 한도가 0보다 커야 한다.
- outer container seccomp/LSM 정책이 nested user/network namespace와 내부 loopback 설정을 허용해야 한다.
- `bwrap`, `prlimit`, `timeout`, `bash`, JDK 17+, Maven, Node와 npm이 설치되어야 한다.
- fixture source는 read-only bind, 임시 쓰기는 sandbox `/tmp`만 허용한다.
- sandbox capability는 전부 drop하고 host 환경변수는 allowlist만 전달한다.

진단기는 다음 값을 함께 출력한다.

- bubblewrap version
- kernel release와 architecture
- `kernel.unprivileged_userns_clone`
- `user.max_user_namespaces`
- `user.max_net_namespaces`
- 실제 rootless user/network namespace probe 결과

## OCI 검증 backend 필수 경계

- `--network none`, `--read-only`, `--cap-drop ALL`, `no-new-privileges`
- Docker builtin seccomp와 `docker-default` AppArmor
- local image only, immutable image ID, `--pull never`
- Docker socket·host HOME·원본 source mount 금지
- snapshot 외 rootfs read-only, `/tmp`만 tmpfs
- PID·CPU·memory·nofile·fsize·wall-clock limit과 종료 시 container 강제 제거
- host 환경은 전달하지 않고 build cache와 `ONSURE_FIXTURE_*` allowlist만 사용

기본 offline image 이름은 `onsure-validation-runtime:java17-node20-v1`이다. 이 image는
`scripts/build-onsure-validation-image.sh`가 로컬 base image를 immutable ID로 결속해 만들며,
ONSure 자체 Python 검증에 필요한 고정 PyYAML/jsonschema 패키지를 포함한다. 다른 image는
`ONSURE_VALIDATION_OCI_IMAGE`로 지정할 수 있지만 immutable local ID로 해석되지 않으면 거부한다.

## 권장 실행 위치

전용 Linux 개발 runner 또는 user/network namespace를 허용한 격리 VM에서 실행한다. 기존 공유 container에 광범위한 `--privileged` 권한을 추가하는 방식은 권장하지 않는다. runner 자체의 권한 변경은 운영자 승인과 별도 threat review가 필요하다.

환경 진단이 `PASS_NONFINAL`이면 다음을 실행한다.

```bash
bash scripts/onsure-local-gate.sh --mode full --profile core
```

## 금지되는 우회

- `--unshare-net` 제거
- host network 사용
- 계약과 공격 경계 시험을 통과하지 않은 backend 사용
- OCI image pull, mutable tag 직접 실행, host network, Docker socket mount
- sandbox 실패를 테스트 skip이나 PASS로 변환
- source write bind 또는 host HOME/secret 전달
- 로컬 실행 결과를 Final PASS나 Production GO로 승격

어느 허용 backend도 지원되지 않으면 `BLOCKED_ENVIRONMENT`다. rootless bubblewrap 차단은 OCI
성공과 별도로 기록한다. 실행하지 않은 독립 검토와 운영 승인은 계속 `NOT_RUN`이다.
