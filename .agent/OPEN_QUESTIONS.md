# ESMP Open Questions

This is a gate-oriented index of `ARCHITECTURE.md` §§26–28, not a second architecture. Settled policies must not be reopened merely because their implementation is unfinished.

## Playable Settlement slice completed

The Java 25/Paper 26.2 plugin implements world identity, Settlement persistence, contiguous claiming, fixed-role and offer workflows, confirmed abandonment, immutable cache publication, historical/outbox records, protection adapters, and private borders.

The user has tested Settlements in game. Preserve the native checklist for regressions and deployment-specific integration checks, rather than treating first in-game verification as the next blocker. Chat ergonomics are deferred by explicit user preference.

Current implementation boundaries: normal PvP, ground-item pickup, and natural entity movement are not redefined; no Activity/Building/decay/repair/reset simulation is advertised.

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

These details do not block the initial plugin or claim slice. Resolve them when their feature is implemented; do not enable an incomplete or unsafe feature to avoid its gate.
