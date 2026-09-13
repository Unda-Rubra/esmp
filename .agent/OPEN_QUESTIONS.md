# ESMP Current Phase Gates

## Authority and status

The [frozen architecture](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/ARCHITECTURE.md), [requirements freeze](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/MVP_REQUIREMENTS_FREEZE.md) and [strict roadmap](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/IMPLEMENTATION_ROADMAP.md) remain canonical and unchanged.

Phases 0–4 are implemented/verified. Local Activity now credits exact Claim buckets from admitted actions, supports bounded cardinal neighbors and exposes derived Regions/area-weighted support without changing aggregate behavior or releasing Claims. Historical and phase-specific player checks remain pending.

## Phase 5 — next, not yet implemented

1. **Bounded explicit registration.** Owner/Governor selects a hard-limited 3D volume wholly inside one Settlement; all touched chunks must already be naturally loaded. No automatic city-wide discovery or forced loads.
2. **Active baseline and geometry.** Establish revision 1 with occupied/exterior/interior air/openings/simple support relationships. Select bounded storage from real size/performance evidence, not speculative export.
3. **Authorized edits and retirement.** Legal construction/demolition updates affected baseline revisions, not Decay damage. Preserve identity through ordinary edits; permit retirement/re-registration for major reinterpretation without a perfect split/merge solver.
4. **Acceptance and boundaries.** Verify registration bounds, unloaded rejection, edits, restart/inspection and retained history. Include the frozen repair-station metadata hook, but no Repair/Decay mutations or automatic block-entity restoration.

## Resolved infrastructure requirements

- `db.changelog-baseline.xml` contains only the immutable Phase 0 includes. Legacy shape verification/changeLogSync target it; the full master applies later changes normally. New-schema projections load only after forward migrations finish.
- Phase 2 uses structured update/table/index changes plus three documented SQLite-native ALTER exceptions. Pinned Liquibase's rebuild path lost constraints/defaults and failed on unnamed legacy foreign keys; tests verify preservation and old-column removal. Do not expand this exception into raw gameplay SQL or new legacy migrations.
- Lifetime credit begins with actual post-migration awards. Old decaying scores and initial Activity do not reconstruct historic lifetime contributions. Existing explicitly approved/named Settlements were grandfathered VERIFIED by the migration.

## Later frozen work

Phases 6–7 add registered-only Decay and material/time Repair with History/Era. Phase 8 hardens the entire loop and paired recovery before the MVP tag. Dynamic Settlement Cycle starts only afterward; no deferred-system placeholders now.
