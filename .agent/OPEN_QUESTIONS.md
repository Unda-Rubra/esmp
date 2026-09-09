# ESMP Open Questions

This is a gate-oriented index of `ARCHITECTURE.md` §§26–28, not a second architecture. Settled policies must not be reopened merely because their implementation is unfinished.

## Playable Settlement slice completed

The Java 25/Paper 26.2 plugin implements world identity, Settlement persistence, contiguous claiming, fixed-role and offer workflows, confirmed abandonment, immutable cache publication, historical/outbox records, protection adapters, and private borders.

The user has tested Settlements in game. Preserve the native checklist for regressions and deployment-specific integration checks, rather than treating first in-game verification as the next blocker. Chat ergonomics are deferred by explicit user preference.

Current boundaries: normal PvP, ground-item pickup and natural entity movement remain unchanged. Shared game time, migrated offer/border/throttle timers, Activity scoring/caps/hysteresis, pause intervals and status are implemented. Building/decay/repair/reset/production remain disabled.

## Next: Building discovery and stable identity

- Define automatic discovery, split/merge identity and claim-change reconciliation before implementing Building tracking.
- Future production must consume Activity's persisted pause intervals and define input/output/in-progress-job accounting; no catch-up from paused time.
- Activity balance testing and explicit migration of an installed Activity policy; initial formula, caps and thresholds are no longer unresolved.
- Leap/season boundaries and calendar presentation remain open. MAIN source, all-forward-jump counting, rewind re-anchoring, frozen time and normal restart are implemented; paired world/database restore remains separately gated.

## Before enabling Facilities and cultural capabilities

- Concrete four-stage upgrade criteria, cultural qualification and higher-stage unlocks; Village-or-certified-landmark basic teleport access is settled.
- Facility/Building linkage, workforce checks and Repair sharing, certificate scope/records/rechecks/reactivation, and ownership/claim-change handling.
- Shared production-capacity units, allocation authority and stage amounts; a Settlement-wide ceiling is settled.
- Recipes, eligible mined inputs, stale-state validity, inputs/output reservations and bounded offline accounting. Functional shared housing and affected-capability suspension are settled.

## Before enabling Economy and Transport

- Stable ledger operations/audit, issuance-budget allocation/replenishment and Owner-approved operating-budget controls.
- Physical depot/stall stock permissions, deposit/withdrawal and purchase/collection recovery, crash-safe item/payment settlement, and ownership changes.
- Exact NPC buy/sell catalogues including vanilla farm outputs, prices/spreads/quote behavior, quantities and replenishment. Global bounded bands, finite stock and separate lists are settled.
- Natural regional profiles/modifier values and exact seasonal effects; keep underlying regional identity independent from seasonal cosmetics.
- Route corridors/land permissions/targeted invalidation, bulk weights and throughput windows, mode/distance rates, reservations and payment recovery.
- Bounded destination storage, held-cargo recovery and original-recipient access after endpoint/ownership changes; no new departures after disruption and preserved existing shipment schedules are settled.
- Teleport cargo classification/portable-container treatment, fee presentation, safe destinations and payment recovery. Shared-storage bypasses are accepted.

## Before enabling native seasonal mutations

- Calendar reconciliation above and reversible loaded-area effects that preserve player builds, expected state, ownership, and Building baselines.
- Bounded scheduling and world/database reconciliation; offline production is not permission to simulate physical mutations in unloaded chunks.

## Before enabling automated Building mutation

- Automatic discovery algorithm, stable identity across split/merge and claim changes, and historical linkage during partial reclaim.
- Block-entity/inventory baseline representation and restoration safety; inherited structures use current-state baselines and authorized edits automatically revise targets.
- Durable journal/checkpoint implementation, world-save/database reconciliation, idempotent material accounting, and transactional outbox storage/retry scheduling.
- Activity/ruin aggregation and eligibility configuration; meaningful Visitor credit is very small, passersby earn none, and recovered activity stops further decay without healing.
- Repair/workforce integration and qualifying restoration-cycle rules for Era advancement.

## Before enabling Resource operations

- Resource reset interval, generation settings, pre-generation radius, evacuation destination, offline/respawn/portal handling, and retention policy. Shared cycles and advance preparation are settled.
- Coordinated world/database backup and restore verification, including recovery during Resource cutover.

## Deferred tuning and optional features

- Administrator exemptions, cooldowns, and remaining expansion policy.
- Activity weights/caps, decay curves/probabilities/material rules, vegetation, repair speed/material values, and workload budgets; configure safe eligibility before enabling affected features.
- Particle styles, command/Dialog presentation, localization, historical UI, rankings, and Era names.
- Alliance relationships.
- Full seasonal grass/foliage palette techniques and compatibility; explicitly deferred beyond the initial native-effects scope.

These details do not block the initial plugin or claim slice. Resolve them when their feature is implemented; do not enable an incomplete or unsafe feature to avoid its gate.
