# ONSure Enterprise Web — UI/UX Design Gate

- Status: DESIGN_GATE_NONFINAL
- Issue: #94
- Branch: `feature/onsure-enterprise-web-springboot`
- W12: NOT_RUN
- GitHub Actions: not used as product validation authority.

## 1. Design set

The detailed UI/UX design set is:

1. `45_ONSURE_ENTERPRISE_WEB_OBUILDER_BASELINE.md` — original W00~W10 baseline.
2. `46_ONSURE_ENTERPRISE_WEB_UIUX_FOUNDATION.md` — UI-01~UI-09 principles/IA/state/error rules.
3. `47_ONSURE_ENTERPRISE_WEB_CORE_WIREFRAMES.md` — P01~P04 detailed wireframes.
4. `48_ONSURE_ENTERPRISE_WEB_READ_MODEL_CONTRACT.md` — Core/Web read boundary and absence semantics.
5. `49_ONSURE_ENTERPRISE_WEB_VISUAL_COMPONENT_SYSTEM.md` — UI-11/UI-12 visual tokens and domain components.

These documents refine W06/W08/W09/W10. They do not replace the normative product requirements or target architecture.

## 2. Corrected stage interpretation

The earlier aggregate `W00_W10_DESIGNED_NONFINAL` remains a broad baseline label, but detailed status is now tracked separately:

| Stage | Detailed status | Note |
|---|---|---|
| W00 Intake | DESIGNED_NONFINAL | unchanged |
| W01 Purpose | DESIGNED_NONFINAL | unchanged |
| W02 User/Role | DESIGNED_NONFINAL | persona-to-screen emphasis refined |
| W03 Journey | DESIGNED_NONFINAL | first read journey narrowed |
| W04 Content/Data | DESIGNED_NONFINAL | read objects refined |
| W05 Functional Architecture | DESIGNED_NONFINAL | Web remains adapter/surface |
| W06 IA/Navigation | DETAILED_DESIGNED_NONFINAL | reduced initial top navigation and context model |
| W07 Policy/Security | DESIGNED_NONFINAL | UI permission/policy/SoD semantics refined |
| W08 UX/Interaction | DETAILED_DESIGNED_NONFINAL | 3s/10s/30s, state/why/blocker/evidence contract |
| W09 Visual Design | DETAILED_DESIGNED_NONFINAL | tokens/components defined, no visual mock validation yet |
| W10 Technical Architecture | DESIGNED_NONFINAL | Read Model contract added; Core adapter implementation pending |
| W11 Implementation | INITIAL_VERTICAL_SLICE_PRESENT | existing hard-coded/non-authoritative shell only |
| W12 Test/Validation/Evidence | NOT_RUN | no authorized W12 execution evidence claimed |

No stage is promoted to final, evidenced, independently verified or operating effectively by this design work.

## 3. Negative review findings resolved by the detailed design

### Removed/reduced
- dashboard-first KPI/card proliferation;
- mandatory full 8-stage journey in permanent chrome;
- Project and Target as equal top-level navigation in first slice;
- default global lineage graph;
- portfolio assurance percentage/score;
- Web-side `Next Best Action` inference;
- Web-side `Why this state?` rule reconstruction;
- universal Tenant/Project/Environment dropdown bar;
- global search as first-slice requirement;
- raw audit stream as home activity feed;
- premature advanced data-grid features;
- color-heavy PASS-like completion treatment;
- large authenticated-page hero region.

### Preserved
- Core authority only;
- explicit NONFINAL/UNKNOWN/NOT_RUN semantics;
- evidence drill-down;
- tenant/SoD fail-closed behavior;
- decision revision safety;
- evidence lineage/provenance;
- desktop-first enterprise density;
- accessible non-color-only status semantics.

## 4. First implementation slice after design

The existing Dashboard is a replaceable shell. The first design-conformant live slice is:

```text
P01 Workspace (authoritative read only)
  -> P02 Project Detail / Target List
  -> P03 Target Detail
  -> P04 Evidence Receipt Detail
```

Required implementation order:

1. Introduce Core/application read ports and DTOs matching `48_...READ_MODEL_CONTRACT.md` semantics.
2. Replace hard-coded assurance-state/dashboard data with explicit unavailable state until Core readback exists.
3. Implement P02 Project/Target read flow.
4. Implement P03 AssuranceSnapshot + blockers + requirements.
5. Implement P04 Evidence receipt readback.
6. Only after read path is proven, design/implement Finding and Decision write flows.

## 5. Hard gates before write/approval UX

Approval/Decision writes MUST NOT begin until the read path demonstrates:
- principal/tenant-scoped resource resolution;
- canonical Core revision readback;
- explicit absence semantics;
- evidence lineage readback;
- no browser route through LocalAuthenticatedApiServer;
- no Web-side assurance calculation;
- permission-denied vs policy-blocked distinction.

## 6. Visual mock gate

Before the current HTML/CSS is treated as UI baseline, visual mockups must cover at least:
- P01 normal + NONFINAL/unavailable;
- P02 populated + empty;
- P03 blocked + unblocked/read-only;
- P04 fresh + stale evidence;
- 1366x768 first viewport;
- keyboard focus/state semantics.

A polished mockup is design evidence only. It cannot satisfy compile/runtime/W12 gates.

## 7. Current decision

`UIUX_DETAILED_DESIGN_SET_PRESENT_NONFINAL`

`CORE_READ_MODEL_IMPLEMENTATION_PENDING`

`W12_NOT_RUN`

This is the handoff point from broad design to bounded implementation. The current Dashboard shell may be changed or removed to conform to these documents.