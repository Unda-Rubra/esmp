# ESMP Current Phase Gates

## Authority and status

The [frozen architecture](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/ARCHITECTURE.md), [requirements freeze](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/MVP_REQUIREMENTS_FREEZE.md) and [strict roadmap](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/IMPLEMENTATION_ROADMAP.md) remain canonical and unchanged.

Phases 0–6 are implemented/verified. Registered Buildings now decay through bounded WEATHER/DROP/CAVE_IN with durable intent, independent world/SQL recovery, damage/condition history and local Activity recovery. No station material Repair or Era advancement exists yet. Player acceptance remains pending.

## Phase 7 — next, not yet implemented

1. **Station/material contract.** Use one protected station per Building; derive required materials from current active-baseline damage and define real player delivery/reservations. Do not appraise labor or add villagers/artisans.
2. **Durable repair.** Persist job/material accounting and bounded GameClock progress; revalidate world, baseline, permissions, expected state and conflicts before naturally-loaded world batches. Exclude block entities and preserve player renovation.
3. **Interruption recovery.** Test before/after physical mutations and material consumption; stable identities must prevent duplicate consumption or side effects. Never assume world saves, inventories and SQL are atomic.
4. **History/Era completion.** Emit started/completed facts; RUINED → Repair → INTACT advances Era once per cycle/job, ordinary non-ruined repair does not. No rewards, historical replay, partial-wing UI or automatic release.

## Resolved infrastructure requirements

- `db.changelog-baseline.xml` contains only the immutable Phase 0 includes. Legacy shape verification/changeLogSync target it; the full master applies later changes normally. New-schema projections load only after forward migrations finish.
- Phase 2 uses structured update/table/index changes plus three documented SQLite-native ALTER exceptions. Pinned Liquibase's rebuild path lost constraints/defaults and failed on unnamed legacy foreign keys; tests verify preservation and old-column removal. Do not expand this exception into raw gameplay SQL or new legacy migrations.
- Lifetime credit begins with actual post-migration awards. Old decaying scores and initial Activity do not reconstruct historic lifetime contributions. Existing explicitly approved/named Settlements were grandfathered VERIFIED by the migration.

## Later frozen work

Phase 8 hardens the entire loop, performance and paired recovery before the MVP tag. Dynamic Settlement Cycle starts only afterward; no deferred-system placeholders now.
