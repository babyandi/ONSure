# ONSure Phase A provenance investigation

Final status: `PHASE_A_PROVENANCE_HOLD`

The investigation searched all fetched reachable local and remote refs in ONSure and ORUDA, including commit messages, tree/path history, added/deleted/renamed paths, pickaxe content changes, historical blobs, receipts, and manifests. Candidate bytes were read with `git show '<commit>:<path>'`; no historical artifact was checked out or rewritten.

## Candidate findings

| Candidate | Exact identity | Semantic result | Classification |
|---|---|---|---|
| Non-blind preflight | blob `16c76a01`, 10,879 bytes, SHA-256 `632501e9…ebff6` | D01-D23 identifiers but zero semantic artifacts | `STATIC_MAPPING_ONLY` |
| Phase A semantic replay | blob `8eecbea6`, 21,775 bytes, SHA-256 `4165b471…7d44` | 23 semantic artifacts, D01-D23 | `POST_GOLDEN_REPLAY`, `NONBLIND_SEMANTIC_REPLAY` |
| Archetype-domain companion | blob `6df9455a`, 17,153 bytes, SHA-256 `d22bb682…345c` | 26 derived domain records, no D01-D22 artifact set | `DERIVED_COPY` |

The Golden Corpus first appeared at commit `41807242226b0656e7edb7605d27ac4b18eb8bd0`, blob `73669067`, before both the preflight and semantic replay commits. The replay also explicitly declares non-blind Golden-contaminated context.

The closest bundle receipt, blob `1f3cf845`, references filenames only. It binds neither SHA-256, Git blob, canonical payload plus exact-file digest, nor a signed parent digest chain. Its exact-byte binding result is `FAIL_FILENAME_REFERENCE_ONLY`.

## A01–A08

| Gate | Result |
|---|---|
| A01 Original D01-D22 output found | `FAIL` |
| A02 Original exact bytes recovered | `FAIL` |
| A03 Original SHA-256 recomputed | `UNPROVEN` |
| A04 Original blob and first commit confirmed | `UNPROVEN` |
| A05 Not a replay/copy/later modification | `FAIL` |
| A06 Frozen before Golden access | `FAIL` |
| A07 Qualifying freeze/execution receipt found | `FAIL` |
| A08 Receipt binds exact bytes | `FAIL` |

Therefore `PHASE_A_PROVENANCE_ACCEPTED` is forbidden. D23 remains `NOT_RUN_PHASE_A_ARTIFACT_UNRESOLVED`; Golden comparison and Phase A regeneration remain `NOT_RUN`. Merge, deploy, FinalLock, Production GO, and Commercial GO remain prohibited by this evidence record.

The machine-readable evidence and exact hashes are in `validation/phase_a/ONSURE_PHASE_A_PROVENANCE_INVESTIGATION_20260810.candidate.json`.
