# ONSure Ubuntu 배포·PostgreSQL Migration 설계 v1

상태: `CANDIDATE_IMPLEMENTED / PREPARATION_ONLY / NONFINAL`

## 선택된 후보

- OS/topology: Ubuntu 24.04 LTS 단독 서버, systemd
- compatibility candidate: RHEL 계열 정의는 보존하되 선택된 운영 대상이 아님
- Java: 17
- API: ONSure Local API, `127.0.0.1:47311` 기본값
- DB: 같은 서버의 loopback PostgreSQL, `onsure` database/schema/user
- migration: Flyway 12.11.0 전용 Maven 모듈
- AI: OpenAI Responses API 전용 Provider 모듈, outbound HTTPS만 필요
- container: 사용하지 않음
- GitHub Actions: 사용하지 않음

구현 파일은 `deploy/rhel/`, `deploy/ubuntu/`, `modules/onsure-migration-postgresql`과
`modules/onsure-provider-openai`에 있다. 저장소가 부여하는 권한은 package/preflight/test까지다.
서버 install, migration 실행, API key 사용, 서비스 시작과 Production/Commercial GO는 포함하지 않는다.

## 런타임 경계

systemd unit은 `onsure` 전용 non-root 사용자, read-only `/opt/onsure`, writable
`/var/lib/onsure`·`/var/log/onsure`, capability 제거, no-new-privileges와 loopback API를 고정한다.
비밀값은 `/etc/onsure/onsure.env`에만 두며 repository 예시는 변수명과 비밀 아닌 기본값만 제공한다.
OpenAI 호출은 네트워크 승인, 전송 데이터 승인과 비용 한도가 모두 있어야 한 번만 실행된다.
모델 fallback과 provider 내부 retry는 하지 않는다.

## DB 경계

Flyway runner는 loopback PostgreSQL URL만 허용하고 URL 내 credentials를 거부한다. migration은
forward-only이며 schema history/transactional advisory lock을 사용한다. 현재 V1은 assurance event의
메타데이터와 SHA-256 binding만 저장하고 고객 원문은 저장하지 않는다.

`ONSURE_MIGRATION_AUTHORIZED` 기본값은 false다. 운영 migrate 전에는 PostgreSQL 버전/stream,
backup·restore proof, lock 경쟁 시험, 이전 application 호환성, retention과 signed receipt를 사람이
검토해야 한다. rollback은 이전 immutable application artifact와 forward-compatible schema 또는
승인된 DB restore로 처리하며 자동 destructive undo는 제공하지 않는다.

개발 호스트의 임시 loopback PostgreSQL 16.14에서는 V1 apply 1건, 재적용 0건, validate,
pending 0건, 두 동시 migration process의 실행 결과 1/0과 단일 history, 합성 event
`pg_dump`/`pg_restore`와 복원 schema 재검증이 통과했다. 증거는
`assurance/runtime/onsure-postgresql-flyway-rehearsal.v1.json`에 migration/package digest와 함께
결속된다. 이 결과는 Ubuntu 개발 호스트의 격리된 임시 cluster 리허설이며 Ubuntu 또는 RHEL 운영
backup/restore 승인, 배포판 package 조합 인증을 대신하지 않는다.

## 명령과 실제 실행 경계

```bash
mvn -B -ntp -q clean verify
mvn -B -ntp -q -f pom-modular.xml clean package
bash scripts/package_onsure_ubuntu.sh
python3 scripts/validate_onsure_ubuntu_package.py
python3 scripts/validate_onsure_operational_boundary.py
python3 scripts/onsure_deploy_migration_skeleton.py preflight
python3 scripts/rehearse_onsure_postgresql.py
```

위 명령은 build/package/preflight다. Ubuntu Production install, AppArmor·firewall 변경,
PostgreSQL 초기화·migrate, OpenAI 실호출, systemd enable/start, 운영 backup/restore와 rollback은
별도 운영 승인 전 `NOT_RUN`이다.
