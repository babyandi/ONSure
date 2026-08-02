CREATE TABLE assurance_event (
  event_id TEXT PRIMARY KEY,
  observed_at TEXT NOT NULL,
  evidence_sha256 TEXT NOT NULL CHECK (length(evidence_sha256) = 64)
);
