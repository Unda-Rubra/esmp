# ESMP Current Phase Gates

## Authority and status

The [frozen architecture](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/ARCHITECTURE.md), [requirements freeze](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/MVP_REQUIREMENTS_FREEZE.md) and [strict roadmap](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/IMPLEMENTATION_ROADMAP.md) remain canonical and unchanged.

Phases 0–3 are implemented/verified. Certification uses actual credited Activity and administrator review; acquisition requires exact recipient fee consent plus administrator approval before atomic Wallet SINK/ownership change. Old immediate transfer is unreachable. Historical and phase-specific player checks remain pending.

## Phase 4 — next, not yet implemented

1. **Local Activity storage.** Add lazy Claim-chunk buckets beside aggregate Activity, using the frozen fields and shared GameClock. No scheduled writes to every inactive Claim.
2. **Contribution and support.** Preserve current permission/bans/revision/cap revalidation; attribute meaningful contributions spatially and apply bounded neighbor support.
3. **Derived ActivityRegion.** Calculate four-connected supported Claim components; do not persist, name or expose player-managed region entities.
4. **Acceptance and boundaries.** Verify spatial isolation, bounded support, time freeze/jumps/restart, migrations and real Paper inspection. No automatic Claim release or Building/Decay implementation in this phase.

## Resolved infrastructure requirements

- `db.changelog-baseline.xml` contains only the immutable Phase 0 includes. Legacy shape verification/changeLogSync target it; the full master applies later changes normally. New-schema projections load only after forward migrations finish.
- Phase 2 uses structured update/table/index changes plus three documented SQLite-native ALTER exceptions. Pinned Liquibase's rebuild path lost constraints/defaults and failed on unnamed legacy foreign keys; tests verify preservation and old-column removal. Do not expand this exception into raw gameplay SQL or new legacy migrations.
- Lifetime credit begins with actual post-migration awards. Old decaying scores and initial Activity do not reconstruct historic lifetime contributions. Existing explicitly approved/named Settlements were grandfathered VERIFIED by the migration.

## Later frozen work

Phase 4 adds local Activity and derived regions. Phase 5 explicitly registers bounded Buildings and active baselines. Phases 6–7 add registered-only Decay and material/time Repair with History/Era. Phase 8 hardens the entire loop and paired recovery before the MVP tag. Dynamic Settlement Cycle starts only afterward; no deferred-system placeholders now.
