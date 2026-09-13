# ESMP Current Phase Gates

## Authority and status

The [frozen architecture](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/ARCHITECTURE.md), [requirements freeze](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/MVP_REQUIREMENTS_FREEZE.md) and [strict roadmap](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/IMPLEMENTATION_ROADMAP.md) remain canonical and unchanged.

Phases 0–5 are implemented/verified. Explicit bounded Building registration, geometry/baseline history, authorized-edit journals, resizing/retirement and station metadata are implemented. No Decay/Repair world mutation exists yet. Historical and phase-specific player acceptance remains pending.

## Phase 6 — next, not yet implemented

1. **Eligibility and selection.** Use Building footprint local support plus grace; index due candidates instead of scanning all blocks. Activity recovery must stop future decay without repairing existing damage.
2. **Safe bounded mutations.** Implement whitelisted WEATHER, vertical DROP and bounded CAVE_IN. Exclude block entities/stations, unregistered natural terrain, ordinary collectible drops and forced chunk loads.
3. **Durable intent/apply/outcome.** Persist operations first, then revalidate world, active Building/baseline, ownership/security, local support and expected source immediately before world-thread mutation. Reconcile world saves independently from SQL progress and preserve player conflicts.
4. **Condition and acceptance.** Compute INTACT/DAMAGED/RUINED and structured facts; test activity recovery, conflicts, restart around all mutation stages, idempotence, no drops and no force loads. No Repair/Era, TOPPLE, horizontal physics, disaster simulation or Claim release.

## Resolved infrastructure requirements

- `db.changelog-baseline.xml` contains only the immutable Phase 0 includes. Legacy shape verification/changeLogSync target it; the full master applies later changes normally. New-schema projections load only after forward migrations finish.
- Phase 2 uses structured update/table/index changes plus three documented SQLite-native ALTER exceptions. Pinned Liquibase's rebuild path lost constraints/defaults and failed on unnamed legacy foreign keys; tests verify preservation and old-column removal. Do not expand this exception into raw gameplay SQL or new legacy migrations.
- Lifetime credit begins with actual post-migration awards. Old decaying scores and initial Activity do not reconstruct historic lifetime contributions. Existing explicitly approved/named Settlements were grandfathered VERIFIED by the migration.

## Later frozen work

Phase 7 adds material/time Repair with History/Era. Phase 8 hardens the entire loop and paired recovery before the MVP tag. Dynamic Settlement Cycle starts only afterward; no deferred-system placeholders now.
