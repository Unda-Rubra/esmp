# ESMP Open Questions

This is a gate-oriented index of `ARCHITECTURE.md` §§26–28, not a second architecture. Settled policies must not be reopened merely because their implementation is unfinished.

## Runnable foundation completed

Java/JDK 25, Gradle Kotlin DSL, one modular JAR, the `xdu.undarubra.esmp` namespace, and SQLite are implemented and pinned in the build. World definitions, persistent physical-world identity, configuration validation, lifecycle, and status command have real build and Paper startup verification.

The next functional slice is Settlement persistence and protection, not another foundation scaffold. Domain gameplay and the future durable job/outbox systems are not implemented.

## Before enabling durable claims

- Owning-domain tables and migrations, claim/member application APIs, and transaction/cache publication boundaries; initial SQLite infrastructure is already implemented.
- Target-version Settlement protection mappings, including safe public interactions and indirect world changes.
- Ownership and historical-event persistence from the first committed changes.

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
