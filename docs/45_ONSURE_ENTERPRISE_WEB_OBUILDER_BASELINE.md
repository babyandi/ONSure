# ONSure Enterprise Web — OBuilder W00~W10 Baseline

- Status: DESIGN_BASELINE_NONFINAL
- Issue: #94
- Branch: `feature/onsure-enterprise-web-springboot`
- Implementation authority: ONSure product requirements and target architecture
- Method reference: ORUDA OBuilder / Website Program Asset Domain
- FinalLock / Production GO / Commercial GO: false

## W00 Intake

ONSure requires an Enterprise Web surface in addition to VS Code, CLI and SDK. The Web surface MUST share the same Core API, Policy Gateway, state machine and Evidence Ledger. It MUST NOT create a second business authority.

## W01 Purpose & Outcome

Primary outcome: provide portfolio-level AI Assurance control, approvals, findings, evidence, audit and operational visibility through a browser while preserving the same Project/Session/Task/Approval/Evidence identities used by the other ONSure surfaces.

Success is not measured by page existence. Success requires connected state, authorization, evidence lineage and runtime validation.

## W02 User / Role / Context

Primary roles:
- AI / Model Owner
- Product Owner
- Developer
- Data Owner
- Security Reviewer
- Compliance / Legal
- Independent Validator
- Auditor
- Release Manager
- CISO / Risk Committee
- Customer / Tenant Administrator

Role separation and SoD remain authoritative. A Web role MUST NOT gain authority forbidden to the same principal in Core policy.

## W03 Job / Journey / Scenario

Critical journeys:
1. Portfolio risk overview → project → target → assurance state → evidence.
2. Finding triage → RCA → controlled improvement → regression → independent validation.
3. Approval inbox → evidence readback → approve/reject/hold.
4. Verification run → receipt → failure → replay/recovery.
5. Audit pack → restricted/shareable evidence export.
6. Session continuation between VS Code and Web with identity/state readback.

Exception journeys include expired approval, stale evidence, tenant mismatch, SoD violation, unavailable execution node and UNKNOWN/NOT_RUN evidence.

## W04 Content / Data / Evidence

Authoritative content classes:
- Project, Target, Asset, Profile
- Session, Task Graph, Checkpoint
- Verification Plan, Case, Run, Receipt
- Finding, Diagnosis, RCA, Improvement
- Approval, Decision, Risk Acceptance
- Evidence, Parent Hash, Pack
- Delivery, Branch, Commit, Build, Package
- Audit / Independent Verification

Truth state MUST remain distinguishable from display state. UNKNOWN, NOT_RUN, INCONCLUSIVE and HOLD must never be rendered as PASS-like success.

## W05 Functional Architecture

Enterprise Web domains:
1. Dashboard / Portfolio
2. Projects / Targets / Inventory
3. Workbench / Sessions / Tasks
4. Verification
5. Findings / RCA
6. Improvement
7. Evidence
8. Approvals
9. Learning / Profiles
10. Delivery
11. Audit
12. Administration

Web controllers are adapters. Core domain services remain the authority.

## W06 IA / Navigation / Task / State

Top navigation:
- Home
- Projects
- Workbench
- Verification
- Findings
- Evidence
- Approvals
- Delivery
- Audit
- Administration

The assurance state model is a first-class visual primitive:
`DECLARED → DESIGNED → IMPLEMENTED → CONNECTED → TESTED → EVIDENCED → INDEPENDENTLY_VERIFIED → OPERATING_EFFECTIVELY`.

Higher state must not be inferred from a lower state.

## W07 Policy / Permission / Security

Mandatory controls:
- authenticated browser session
- CSRF protection for state-changing browser requests
- tenant isolation
- RBAC + ABAC
- SoD / four-eyes enforcement
- approval expiry and replay protection
- server-side resource resolution
- no caller-supplied authority claims
- audit event for privileged actions
- secure headers and same-origin policy
- no exposure of the existing loopback-only `LocalAuthenticatedApiServer`

Browser requests MUST use a dedicated Spring Boot Web API/BFF boundary.

## W08 UX / Interaction

Principles:
- one screen, one primary decision
- risk/hold/fail states before decorative analytics
- evidence reachable from every decision
- destructive/privileged actions require explicit review context
- loading/error/empty/permission-denied states are designed states, not afterthoughts
- portfolio → project → target → receipt drill-down must preserve context

Representative first slice:
Dashboard → Project Detail → Assurance State → Evidence Readback.

## W09 Visual Design

Initial design system:
- enterprise financial-control visual language
- dense but readable data tables
- restrained status palette
- no color-only state semantics
- responsive desktop-first layout
- WCAG-oriented contrast and keyboard navigation
- provenance/evidence identifiers visibly copyable

Visual styling is subordinate to state correctness and authorization clarity.

## W10 Technical Architecture

### Runtime
- Java 17
- Spring Boot 4.1.1
- Spring MVC
- Spring Security
- Thymeleaf for the initial server-rendered surface
- Actuator health/readiness
- PostgreSQL + Flyway

### Module boundary

```text
apps/onsure-web/
  pom.xml
  src/main/java/kr/co/oruda/onsure/web/
  src/main/resources/

Existing ONSure Core
  ↓
ONSure Web application/service adapters
  ↓
Spring MVC controllers
  ↓
Thymeleaf / JSON read models
```

The first slice intentionally avoids a Node/SPA runtime. A future React or other client MAY be introduced behind the same Web API contract after the server-side state and policy boundary is proven.

### Initial endpoints
- `GET /` dashboard shell
- `GET /healthz` application health projection
- `GET /api/web/v1/portfolio` read-only portfolio projection (next slice)
- `GET /api/web/v1/projects/{id}` project projection (next slice)

### Build and evidence
W11 implementation must be followed by W12 compile/unit/integration/runtime/security evidence. Two-run reproducibility is required before promotion claims.

## W11 Entry Gate

Implementation is allowed only for the bounded vertical slice defined above. It MUST NOT:
- change frozen PR #73 subject
- weaken LocalAuthenticatedApiServer restrictions
- duplicate ONSure domain authority
- create auto-approval or final decision shortcuts

## Traceability

- FR-FIN-12 Evidence / Independent Validation
- FR-FIN-15 Unified Workbench
- FR-FIN-18 Orchestration / Memory
- FR-FIN-22 Unified Evidence
- Target Architecture §4 ONWorkbench / ONGateway

## Current decision

`W00_W10_DESIGNED_NONFINAL`

This document authorizes only the initial Spring Boot Enterprise Web vertical slice. It is not implementation, test, audit or release evidence.
