# ESMP Current Phase Gates

## Authority and status

The [architecture](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/ARCHITECTURE.md), [requirements freeze](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/MVP_REQUIREMENTS_FREEZE.md) and [strict roadmap](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/IMPLEMENTATION_ROADMAP.md) remain canonical. Frozen MVP behavior is unchanged; accepted post-MVP decisions are recorded separately in architecture §30.

Phases 0–8 core server gates are implemented/verified; `mvp-v1.0` marks the frozen milestone. Native-client/user acceptance and deployment-specific capacity checks remain explicit in the checklist. `RELEASE.md` records the tested cold upgrade/restore and Resource maintenance procedure.

## MVP+1 — freeze design before implementation

1. Define gradual peripheral/boundary-only release using Local Activity, without internal holes or broken Settlement contiguity. Specify thresholds/hysteresis and whether Civic Capacity is actually necessary.
2. Define unassigned-Parcel handling, registered-Building lifecycle transition and pending-job/material reconciliation. Holder consent (§30.1), whole-RUINED Building candidacy (§30.2) and in-place land-local container contents after notice/grace (§30.3) are settled. Define notice delivery/duration/revalidation, cross-boundary storage (including double chests), other property/entities and explicit administrator dispute handling.
3. Define historical-core preservation, reclaim/current-baseline semantics and the migration/acceptance matrix. No restored predecessor permissions, free repairs or Era rewards.
4. Keep Server Trade, Facility, freight, seasons and other deferred systems out of this phase. No placeholder schema or code until the Dynamic Settlement Cycle design is approved separately.

## Resolved infrastructure requirements

- `db.changelog-baseline.xml` contains only the immutable Phase 0 includes. Legacy shape verification/changeLogSync target it; the full master applies later changes normally. New-schema projections load only after forward migrations finish.
- Phase 2 uses structured update/table/index changes plus three documented SQLite-native ALTER exceptions. Pinned Liquibase's rebuild path lost constraints/defaults and failed on unnamed legacy foreign keys; tests verify preservation and old-column removal. Do not expand this exception into raw gameplay SQL or new legacy migrations.
- Lifetime credit begins with actual post-migration awards. Old decaying scores and initial Activity do not reconstruct historic lifetime contributions. Existing explicitly approved/named Settlements were grandfathered VERIFIED by the migration.

## Later frozen work

Architecture §§30.1–30.3 fix assigned Holder consent, whole registered ruin candidacy and land-local container disposition for MVP+1. Other release rules remain unresolved; do not infer implementation details from the pre-freeze architecture or treat unaccepted discussion proposals as approved.
