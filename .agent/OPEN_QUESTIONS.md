# ESMP Current Phase Gates

## Authority and status

The [frozen architecture](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/ARCHITECTURE.md), [requirements freeze](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/MVP_REQUIREMENTS_FREEZE.md) and [strict roadmap](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/IMPLEMENTATION_ROADMAP.md) remain canonical and unchanged.

Phases 0 and 1 are implemented/verified. All expansion is Treasury-funded and current-Owner-assessed; founding stays founder Wallet. Existing purchase count is the monotonic progression revision, and versioned quote context rejects old unpurchased consent while paid receipts replay. No extra schema column or released-baseline edits were needed. Historical and phase-specific player checks remain pending sign-off.

## Phase 2 — next, not yet implemented

1. **Durable lifetime meaningful credit.** Add actual accepted credit accumulation alongside the current decaying score and daily caps. Do not reconstruct lifetime contributions from current score or silently invent credit. This is a Phase 2 certification dependency, not permission to implement Phase 4 local buckets.
2. **Frozen gate and state machine.** Defaults: age ≥3 game days, aggregate Activity not INACTIVE, lifetime credited meaningful Activity ≥20. Eligibility permits governance application only; admin approves/denies. Exactly UNVERIFIED/VERIFIED, no automatic approval/revocation/aesthetic scoring.
3. **Naming migration.** Preserve existing names/IDs/history through an explicit migration of grandfathered flags/names. Remove the old direct naming toggle as an alternate behavior. Pending proposals/reviews and repeated approval must remain deterministic/idempotent; denial retains Claims/protection.
4. **Forward migration safety.** Before the first post-baseline schema change, restrict the legacy bridge's reference verification and changelogSync to the known Phase 0 baseline, then execute newer changeSets normally. Current bridge uses the full master because Phase 0/1 required no later schema change; do not accidentally mark Phase 2 changes applied or compare an old bridge to a future schema. Cover that infrastructure correctness adjustment with old-bridge→new-latest tests rather than adding legacy SQL migrations or rewriting released baseline files.
5. **Acceptance/scope.** Clean generation/check, empty and previous-version fixtures, gate thresholds/GameClock behavior, denial preservation, approval idempotency, naming concurrency and relevant Paper tests. No acquisition fees, Local Activity, Building or website implementation in this phase; expose only clean status/name read/export data.

## Later frozen work

Phase 3 replaces two-party transfer with recipient acceptance, fee and admin approval. Phase 4 adds local Activity and derived regions. Phase 5 registers bounded Buildings/active baselines; Phases 6–7 add registered-only Decay and material/time Repair with History/Era. Phase 8 hardens the complete loop and paired recovery before tagging MVP. Dynamic Settlement Cycle follows only afterward, with no deferred-system placeholders now.

## Operational notes

- Canonical schema: `schema/db/changelog/*.xml`; old SQL resources and `.persistence.legacy` adapters are compatibility-only. All normal repositories use generated jOOQ through the opaque UnitOfWork.
- SQLite baseline constraint exceptions are documented and covered by native schema comparison/negative-data tests; they are not a license for raw gameplay SQL.
- The Phase 1 clean/no-cache build ran successfully using ordinary committed repositories, without the temporary TLS transport used in Phase 0.
- The bundle references SVGs but supplies equivalent DOT/PNG; this remains a non-blocking navigation issue.
