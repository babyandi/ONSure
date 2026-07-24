# ONSure RAG Ingest Guide

## Ingest Target

Ingest this pack into ONSure, not ORUDA.

ONSure uses the material to answer:

- Which program boundary owns a failure?
- Which contract was violated?
- Which cause class applies?
- Which memory store should receive a candidate?
- What remediation pattern should be proposed?

## Files To Ingest

Primary ingestion file:

- `rag/chunks/oruda_report_chain.jsonl`

Reference files:

- `docs/rag/rag_source_pack.md`
- `docs/rag/onsure_common_management_design.md`
- `rag/manifests/oruda_report_chain.manifest.json`

The JSONL file is the retrieval unit. Markdown files are human-readable source
packs and should be retained for audit and re-chunking.

## Metadata

Preserve these fields for every chunk:

- `chunk_id`
- `title`
- `programs`
- `category`
- `memory_kind`
- `source`
- `promotion_status`

Recommended filters:

- `programs`
- `category`
- `memory_kind`
- `promotion_status`

## Promotion Flow

1. Ingest chunks as `ready_candidate`.
2. Run ONSure harness against the target profile and run receipt.
3. If a finding appears, compare it with retrieved chunks.
4. Run the same verification for 2 or 3 loops.
5. Promote only when decision, cause codes, remediation targets, memory kinds,
   and evidence projection hash remain stable.
6. Require regression proof before moving from candidate to active memory.

## Retrieval Examples

Question: "OUI field_manifest is missing. Who owns the fix?"

Expected retrieval:

- `oui-contract-001`
- `cause-field-001`
- `loop-rule-001`

Expected answer:

- Owner: OUI
- Cause: REQUIRED_OUTPUT_FIELD_MISSING
- Memory kind: failure_memory
- Required action: promote field_manifest into OUI contract and bind it into
  render receipt

Question: "ODesign generated output but skipped detailed croquis."

Expected retrieval:

- `odesign-contract-001`
- `cause-procedure-001`
- `loop-rule-001`

Expected answer:

- Owner: ODesign
- Cause: FORMAL_PROCEDURE_MISSING
- Memory kind: program_learning
- Required action: require ordered formal procedure receipts before OUI
  generation

Question: "OTester is PENDING but the report was marked complete."

Expected retrieval:

- `final-gate-001`
- `cause-final-gate-001`

Expected answer:

- Owner: ONSure final gate policy or target finalization boundary
- Cause: FINAL_GATE_NOT_PASS
- Memory kind: improvement_memory
- Required action: block final completion unless OTester and OAudit are PASS
  and receipt-bound

## Anti-Patterns

Do not ingest raw chat logs as direct RAG chunks.

Do not promote a failure pattern after one observation.

Do not let ORUDA become the owner of ONSure verification memory.

Do not mix temporary PR status with reusable verification rules.
