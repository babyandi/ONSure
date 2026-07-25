# ONSure Common RAG Pack

This pack defines how ONSure should manage shared verification knowledge for
OUI, ODesign, OReport, and ODocument.

Core decision:

- ONSure owns common verification knowledge, RAG material, failure memory, and
  remediation patterns.
- ORUDA owns target program implementation.
- ONSure findings may create ORUDA remediation PRs, but ORUDA must not become
  the source of truth for verifier memory.

Files:

- `onsure_common_management_design.md`: target architecture and governance.
- `rag_source_pack.md`: curated source text for RAG.
- `rag_ingest_guide.md`: ingestion, promotion, and retrieval rules.
- `../../rag/manifests/oruda_report_chain.manifest.json`: version, taxonomy, and filtering metadata.
- `../../rag/chunks/oruda_report_chain.jsonl`: ingestion-ready chunks.
