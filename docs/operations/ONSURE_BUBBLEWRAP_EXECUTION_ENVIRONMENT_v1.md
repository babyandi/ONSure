# ONSure bubblewrap 실행환경 계약 v1

상태: `ENVIRONMENT_SETUP_GUIDE / NONFINAL`

## 목적

ONSure fixture sandbox는 `ROOTLESS_BWRAP`만 허용한다. source read-only, private network namespace, capability drop, 제한된 환경변수와 resource limit을 검증하기 때문에 bubblewrap을 제거하거나 host network로 우회해서는 안 된다. GitHub Actions는 이 계약의 필수 실행환경이 아니며 사용하지 않는다.

## 진단

```bash
python3 scripts/onsure_bubblewrap_diagnostics.py
```

현재 Codex host에서 확인된 reason은 `BWRAP_LOOPBACK_PERMISSION_DENIED`다.

```text
bwrap: loopback: Failed RTM_NEWADDR: Operation not permitted
```

이는 Java/Maven 코드 실패가 아니다. 외부 container/runtime가 새 network namespace 안의 loopback 설정을 거부한 것이다. 같은 source의 canonical `mvn clean verify`는 sandbox 밖에서 통과한다.

2026-08-04 재현 환경은 Linux `6.8.0-136-generic`, bubblewrap `0.9.0`이며
`kernel.unprivileged_userns_clone=1`, user/network namespace 한도는 각각 `30865`였다.
namespace 생성 한도는 열려 있지만 outer runtime이 private network namespace의 loopback
주소 설정을 거부한다. 최신 full core gate는 이 때문에 sandbox fixture를 사용하는 9개
Java 시험이 실패했고, 동일 HEAD의 sandbox 비강제 canonical clean verify는 282/282를
2회 연속 통과했다.

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

## 권장 실행 위치

전용 Linux 개발 runner 또는 user/network namespace를 허용한 격리 VM에서 실행한다. 기존 공유 container에 광범위한 `--privileged` 권한을 추가하는 방식은 권장하지 않는다. runner 자체의 권한 변경은 운영자 승인과 별도 threat review가 필요하다.

환경 진단이 `PASS_NONFINAL`이면 다음을 실행한다.

```bash
bash scripts/onsure-local-gate.sh --mode full --profile core
```

## 금지되는 우회

- `--unshare-net` 제거
- host network 사용
- bubblewrap 대신 검증되지 않은 backend 사용
- sandbox 실패를 테스트 skip이나 PASS로 변환
- source write bind 또는 host HOME/secret 전달
- 로컬 실행 결과를 Final PASS나 Production GO로 승격

환경이 지원되지 않으면 결과는 `BLOCKED_ENVIRONMENT`로 기록한다. 실행하지 않은 독립 검토와 운영 승인은 계속 `NOT_RUN`이다.
