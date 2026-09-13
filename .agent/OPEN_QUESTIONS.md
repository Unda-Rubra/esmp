# ESMP Current Phase Gates

## Authority and status

The [frozen architecture](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/ARCHITECTURE.md), [requirements freeze](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/MVP_REQUIREMENTS_FREEZE.md) and [strict roadmap](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/IMPLEMENTATION_ROADMAP.md) remain canonical and unchanged.

Phases 0–7 are implemented/verified. Registered Buildings support bounded Decay and material/time Repair, independent inventory/world/SQL reconciliation, structured History and qualifying-cycle Era advancement. Player acceptance remains pending.

## Phase 8 — next, not yet accepted

1. Run the full frozen acceptance matrix, including clean install, real development-copy migration, fail-closed startup and all earlier gameplay contracts.
2. Profile protection hot paths, Activity load, maximum Building capture, Decay/Repair budgets and SQLite queue pressure.
3. Verify coordinated world/player-data/database backup and restoration plus real Paper upgrade/restart; individual Phase 7 crash cuts are not the entire release gate.
4. Verify command/help surfaces and produce release/operator migration instructions before an MVP tag. No new features or post-MVP systems.

## Resolved infrastructure requirements

- `db.changelog-baseline.xml` contains only the immutable Phase 0 includes. Legacy shape verification/changeLogSync target it; the full master applies later changes normally. New-schema projections load only after forward migrations finish.
- Phase 2 uses structured update/table/index changes plus three documented SQLite-native ALTER exceptions. Pinned Liquibase's rebuild path lost constraints/defaults and failed on unnamed legacy foreign keys; tests verify preservation and old-column removal. Do not expand this exception into raw gameplay SQL or new legacy migrations.
- Lifetime credit begins with actual post-migration awards. Old decaying scores and initial Activity do not reconstruct historic lifetime contributions. Existing explicitly approved/named Settlements were grandfathered VERIFIED by the migration.

## Later frozen work

Phase 8 hardens the entire loop, performance and paired recovery before the MVP tag. Dynamic Settlement Cycle starts only afterward; no deferred-system placeholders now.
