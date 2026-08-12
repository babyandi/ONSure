# 119 Exact Design Artifact Population 및 Digest Governance

Status: `EXECUTED_PARTIAL / NON_FINAL`

## 1. Authoritative population classes
Design Baseline에 포함할 artifact class:
- docs/master/00~08A authoritative parent set
- docs/master/semantic-assurance canonical companion set
- contracts design/trace/operation/policy registries
- machine schema candidates explicitly referenced by design baseline

제외 또는 별도 class:
- evidence execution results
- temporary notes
- superseded/deprecated aliases
- generated render/export copies

## 2. Identity fields
각 artifact는 최소 다음을 가진다.
- path
- canonical_document_id 또는 contract_id
- git_blob_sha
- content_sha256
- byte_size
- authority_class
- lifecycle_state: ACTIVE_DESIGN|ALIAS|SUPERSEDED|DEPRECATED|CANDIDATE
- supersedes/superseded_by

## 3. 현재 확보된 Git identity
Frozen input commit `abe122ef9438f41e8b074f03b8017b6d30cbc389`에서 parent master blob identity와 semantic-assurance subtree Git tree identity `44a290f0369ef6991fcaf363c452a1b24f2842a2`를 확인했다.

그 이후 branch에는 108~119 및 machine execution 후보가 추가되었으므로 **현재 head를 lock input으로 사용할 경우 새로운 frozen commit을 다시 잡아야 한다.**

## 4. Git SHA와 content SHA-256 분리
Git blob SHA는 Git object identity이고 content SHA-256은 ONSure baseline canonical content identity다. 두 값을 동일시하지 않는다.

현재 Git API는 blob SHA를 제공하지만 전체 authoritative file population의 raw bytes를 일괄 SHA-256으로 계산한 manifest는 아직 없다.

따라서:
- Git population identity: PARTIAL_PROVEN
- content SHA-256 inventory: NOT_PROVEN

## 5. Population digest
Canonical rows를 path 순으로 정렬하고 다음 tuple을 해시한다.
`(canonical_id,path,content_sha256,authority_class,lifecycle_state)`

Git tree SHA만으로 ONSure Design Population Digest를 대체하지 않는다.

## 6. Task 상태
- 19 exact design population taxonomy: DONE_DESIGN
- 20 content SHA-256: PENDING_EXECUTION
- 21 artifact inventory: PARTIAL_GIT_IDENTITY
- 22 artifact population digest: PENDING_SHA256
