UDA 원격 개발 환경

## 공식 작업 경로

- 원격 서버의 공식 저장소 경로는 `/workspace/ONSure`이다.
- `/home/babyandi/ONSure`, `/home/babyandi/aTops` 등에 동일 저장소를 다시 복제하지 않는다.
- 새 clone, 저장소 이동, 중복 worktree 생성은 사용자 승인 없이 하지 않는다.

## 실행 환경

- Python 가상환경: `/workspace/ONSure/.venv`
- 프로젝트 환경변수: `/workspace/ONSure/.env`
- 기본 렌더링 출력 경로: `/workspace/ONSure/rendered`

## Rootless Bubblewrap 요구 조건

전체 Core Gate는 fixture 실행 시 Bubblewrap의 user namespace와 network
namespace를 모두 사용한다. 실행 호스트는 일반 사용자가 새 user namespace를
만들고, 그 namespace 안에서 loopback 인터페이스를 구성할 수 있어야 한다.

2026-08-01 현재 원격 환경은 다음 호스트 정책 때문에 전체 Gate를 실행할 수
없다.

- `kernel.apparmor_restrict_unprivileged_userns = 1`
- 현재 프로세스는 AppArmor 기준 `unconfined`이지만 user namespace 생성 시
  `unprivileged_userns` 프로필로 전환되어 capability가 제거된다.
- `sudo -n true`는 암호를 요구하므로 작업 세션에서 호스트 정책을 변경할 수
  없다.

재현 명령:

```bash
unshare --user --map-root-user true
bwrap --unshare-user --uid 0 --gid 0 --unshare-net \
  --ro-bind / / --proc /proc --dev /dev true
```

관찰된 실패:

```text
unshare: write failed /proc/self/uid_map: Operation not permitted
bwrap: loopback: Failed RTM_NEWADDR: Operation not permitted
```

해결은 Bubblewrap의 격리 옵션을 제거하는 방식이 아니라, 별도 VM·컨테이너
또는 호스트 AppArmor 정책에서 rootless user/network namespace가 정상 작동하는
실행 환경을 제공하는 방식이어야 한다. 환경 변경 후 위 재현 명령 두 개가 먼저
통과해야 전체 Gate를 다시 실행할 수 있다.
