# ONSure Enterprise Web — Core Read Model Contract

- Status: DESIGNED_NONFINAL / IMPLEMENTATION_REQUIRED
- Issue: #94
- Purpose: prevent the Web surface from becoming a second assurance authority.
- Normative sources: `05_PRODUCT_REQUIREMENTS_AND_ACCEPTANCE.md`, `41_ONSURE_FINAL_TARGET_ARCHITECTURE.md`, `46_ONSURE_ENTERPRISE_WEB_UIUX_FOUNDATION.md`.

## 1. Boundary rule

ONSure Web may format, localize, sort and paginate data. It MUST NOT derive new assurance truth from lower-level facts.

Forbidden examples:

```java
if (evidenceCount > 3) state = EVIDENCED;
if (failedTests == 0) projectStatus = PASS;
if (targets.stream().allMatch(...)) portfolioScore = 100;
```

The Core/application boundary must expose the semantic facts necessary for the Web to explain state without re-implementing policy.

## 2. Minimum read contracts

### ProjectSummary

```text
projectId
name
tenantId/context reference
owner/reference if authorized
materiality/criticality if defined
targetCount: known integer or explicit unavailable
coreRevision
lastChangedAt
```

`targetCount` MUST distinguish known zero from unavailable.

### TargetSummary

```text
targetId
projectContextId
name
type
criticality/materiality if defined
assurance: AssuranceSnapshot
lastVerifiedAt
coreRevision
```

### AssuranceSnapshot

```text
subjectType
subjectId
canonicalState
progressionStatus
satisfiedRequirements[]
unresolvedRequirements[]
blockingConditions[]
sourceAuthority
coreRevision
observedAt
```

`canonicalState` is authoritative and may be unavailable/unknown according to Core semantics. Web never chooses it.

### RequirementStatus

```text
requirementId
status
reasonCode
relatedEvidenceIds[]
relatedFindingIds[]
relatedDecisionIds[]
```

The Web can localize a `reasonCode`, but cannot invent requirement satisfaction.

### BlockingCondition

```text
blockerId
category
code
severity if domain-defined
human-safe parameters
relatedObjectRefs[]
policyReference if authorized
```

Initial categories include Evidence Missing, Verification Failed, Approval Required, Policy Denied, Authority Unavailable, Stale Evidence, SoD Conflict and unresolved UNKNOWN/NOT_RUN/INCONCLUSIVE conditions.

### EvidenceSummary

```text
evidenceId
evidenceType
humanLabel
targetId
verificationId
scopedResult
freshness
executedAt
sourceRevision
coreRevision
```

### EvidenceReceipt

```text
evidenceId
evidenceType
humanLabel
subject/target reference
verification reference
source identity + revision/hash
environment identity
executor identity if authorized
executedAt + timezone/absolute timestamp
scopedResult
freshness
parentHash / receipt hash if authorized
linkedFindings[]
linkedDecisions[]
linkedRequirements[]
technicalDetails / artifact references subject to policy
coreRevision
```

## 3. Explicit absence model

Every field that can legitimately be absent needs semantic absence, not accidental Java null handling.

Recommended contract semantics:

```text
KNOWN(value)
KNOWN_EMPTY
NOT_AVAILABLE
NOT_AUTHORIZED
NOT_APPLICABLE
UNKNOWN
```

The transport representation may differ, but the semantic distinction MUST survive to the UI.

Rules:
- `NOT_AVAILABLE` is never converted to `0`.
- `UNKNOWN` is never converted to blank/neutral success.
- `NOT_AUTHORIZED` must not disclose protected value existence.
- `NOT_APPLICABLE` requires Core-side basis where required by policy.

## 4. Authority envelope

Each page-level read should carry an authority envelope:

```text
authority = ONSURE_CORE
coreRevision
readAt
projectionState = LIVE | CACHED | STALE | UNAVAILABLE
projectionSourceRevision when cached
```

A Web-generated timestamp is not evidence freshness and does not replace Core revision.

## 5. Project aggregation rules

Unless Core defines an explicit authoritative project assurance object, Web MUST NOT produce:
- average assurance stage;
- weighted assurance score;
- project PASS percentage;
- arbitrary red/amber/green project status.

Web may present factual distributions returned from Core, for example:

```text
12 targets
3 independently verified
5 evidenced
2 tested
2 blocked
```

Only if each count is authoritative for a defined denominator/scope.

## 6. Next unresolved requirement

The Web may display `Next unresolved requirement` only from Core-provided unresolved requirements. It may choose presentation ordering only when the ordering rule is explicitly non-semantic, such as Core-provided priority then stable ID.

The Web MUST NOT decide that one blocker is more important based on UI heuristics and present it as authoritative.

## 7. Why-this-state contract

`Why this state?` requires Core to provide satisfied and unresolved conditions that support the current snapshot.

Web responsibility:
- localization;
- grouping;
- progressive disclosure;
- links to evidence/finding/decision.

Core responsibility:
- requirement applicability;
- satisfaction;
- blocking semantics;
- state transition truth.

## 8. Freshness

Freshness is authoritative only when Core can compare evidence lineage with the current subject/source/policy/environment identity.

Suggested semantics:

```text
FRESH
STALE
UNKNOWN
NOT_APPLICABLE
```

The Web MUST NOT infer freshness only from age. A two-hour-old receipt can be stale if source changed; a thirty-day-old receipt can still be current if Core policy allows and inputs are unchanged.

## 9. Pagination and filtering

Initial lists support server-side:
- stable sort;
- simple state/result filters;
- project/target scope;
- pagination.

Offset pagination is acceptable for the first bounded slice if volumes are bounded. Evidence/Audit high-volume endpoints SHOULD preserve a path to cursor pagination without changing object identity or authority semantics.

## 10. Route-to-contract mapping

```text
GET /api/web/v1/projects
  -> ProjectSummary[]

GET /api/web/v1/projects/{projectId}
  -> Project detail + TargetSummary[]

GET /api/web/v1/projects/{projectId}/targets/{targetId}
  -> Target detail + AssuranceSnapshot + EvidenceSummary[]

GET /api/web/v1/evidence/{evidenceId}
  -> EvidenceReceipt
```

Exact endpoint form may change during implementation, but Web browser controllers and JSON/BFF endpoints MUST consume the same application read service semantics.

## 11. Security requirements

- resource resolution is server-side;
- tenant/project scope derives from authenticated principal/policy, not trusted caller fields;
- cross-tenant not-found/denied behavior avoids resource enumeration;
- fields may be redacted by policy without creating false empty values;
- privileged technical evidence fields remain policy-controlled;
- no browser access path is created through the loopback-only LocalAuthenticatedApiServer.

## 12. Revision and decision safety

Read-only screens may show the latest Core revision.

Future decision screens MUST bind to a reviewed revision. If the revision changes before submission, Core must reject/revalidate the decision; Web presents a restart/review-changed state rather than silently submitting against newer data.

## 13. Implementation gate

Before replacing the current hard-coded Dashboard model with live data, implementation must demonstrate:
- a Core/application read port exists;
- controller depends on read port, not repository/policy duplication;
- unavailable data has explicit semantics;
- state and why/blockers arrive from Core contract;
- Evidence IDs link to authoritative receipt reads;
- no portfolio/project assurance score is generated in Web.

This contract is a design artifact, not evidence that Core integration already exists.