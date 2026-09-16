# ESMP Current Scope and Execution Notes

## Authority

The frozen MVP remains implemented at `mvp-v1.0`. The next increment is the simplified Dynamic Settlement Cycle in [architecture §30](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/ARCHITECTURE.md). That section supersedes the accumulated edge-case questionnaire and earlier partial-building decisions.

## Next work

Implement WILD / CLAIMED classification and whole-Building transitions using the existing services, repositories and publication gate. WILD is wholly outside Settlements; CLAIMED is wholly inside one Settlement. Cross-boundary tracked Buildings are unsupported: reject invalid registration/boundary changes instead of adding a mixed state, fragments or split/merge logic.

Then connect the small Activity → notice/grace → safe peripheral release loop. Keep the selected Holder, container, historical Treasury and Repair-refund rules in §30. Do not reopen them as a sequence of design votes.

## Implementation choices, not new architecture gates

- Start from existing configurable Activity/Decay policies and bounded work budgets; tune through focused tests and actual server evidence.
- Reuse current permission, history, baseline, job and reconciliation patterns. Keep storage/API details local to the implementation.
- Reject unsupported cases safely. Do not add Civic Capacity, maintenance simulation, extra region models, arbitrary Building discovery or deferred systems.

## Retained migration constraints

- `db.changelog-baseline.xml` contains only immutable Phase 0 includes; verify the old shape before baseline synchronization, then apply the forward master.
- Retain the tested legacy bridge for supported development-data upgrades. Do not rewrite released changelogs or expand documented SQLite-specific exceptions into gameplay SQL.
- Historical Activity is not reconstructed from old scores. Existing certification grandfathering and financial histories remain intact.

Native-client acceptance and deployment-specific performance checks remain in `TEST_CHECKLIST.md`; they are not new feature-design questions.
