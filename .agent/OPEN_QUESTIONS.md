# ESMP Current Phase Gates

## Authority and status

The [frozen architecture](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/ARCHITECTURE.md), [requirements freeze](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/MVP_REQUIREMENTS_FREEZE.md) and [strict roadmap](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/IMPLEMENTATION_ROADMAP.md) remain canonical and unchanged.

Phases 0–2 are implemented/verified. Certification now uses actual lifetime credited Activity, the frozen eligibility gate, governance application and administrator approval/denial. Old naming flags/names migrate explicitly; the boolean toggle/API/column are removed. No automatic revocation or website was added. Historical and phase-specific player checks remain pending.

## Phase 3 — next, not yet implemented

1. **Frozen transfer workflow.** Owner proposes → recipient accepts → current land-based acquisition fee quote → administrator approves → atomic Wallet SINK plus ownership transition. Acceptance alone must no longer transfer ownership.
2. **Price and stale context.** Fee is configurable k × sum of immutable base values for current Claims, with deterministic integer rounding. Missing required profiles reject automatic acquisition. Revalidate participants, ownership/claim/profile context and recipient funds at commit; no labor/Building/cultural valuation or peer-receiving-capacity system.
3. **Atomic state and history.** Preserve Treasury identity/balance/history, remove the incoming Owner's membership row, make the former Owner Citizen and record the receipt/transfer/outbox fact together. No two-owner or ownerless committed intermediate state; retries cannot double debit or transfer again.
4. **Pending-offer migration.** Define an explicit forward migration/cancellation policy for old pending two-party transfer offers. Preserve historical accepted offers and completed monetary/certification records; no silent reuse of stale consent under the new workflow.
5. **Acceptance and scope.** Empty/current-previous/legacy-bridge migrations, acceptance-without-approval refusal, insufficient funds, stale context, replay, Treasury preservation and real Paper restart/protection. No Local Activity or Building work before this phase passes.

## Resolved infrastructure requirements

- `db.changelog-baseline.xml` contains only the immutable Phase 0 includes. Legacy shape verification/changeLogSync target it; the full master applies later changes normally. New-schema projections load only after forward migrations finish.
- Phase 2 uses structured update/table/index changes plus three documented SQLite-native ALTER exceptions. Pinned Liquibase's rebuild path lost constraints/defaults and failed on unnamed legacy foreign keys; tests verify preservation and old-column removal. Do not expand this exception into raw gameplay SQL or new legacy migrations.
- Lifetime credit begins with actual post-migration awards. Old decaying scores and initial Activity do not reconstruct historic lifetime contributions. Existing explicitly approved/named Settlements were grandfathered VERIFIED by the migration.

## Later frozen work

Phase 4 adds local Activity and derived regions. Phase 5 explicitly registers bounded Buildings and active baselines. Phases 6–7 add registered-only Decay and material/time Repair with History/Era. Phase 8 hardens the entire loop and paired recovery before the MVP tag. Dynamic Settlement Cycle starts only afterward; no deferred-system placeholders now.
