# ESMP Open Questions

This is a gate-oriented index of `ARCHITECTURE.md` §§28–30, not a second architecture. Settled policies must not be reopened merely because their implementation is unfinished.

## Before the first runnable plugin

1. Verify and pin a JDK 25 distribution/patch, compatible Gradle wrapper, and concrete Paper 26.2 API/server build.
2. Pin reproducible dependencies and establish actual build/start verification.

Java plugin source, Minecraft/Paper 26.2, JDK 25, Gradle Kotlin DSL, one modular plugin JAR, and SQLite are already selected. No project files or executable commands exist yet.

## Before enabling durable claims

- SQLite driver, migration tooling, initial domain schemas, and transaction/cache publication boundaries.
- Target-version protection mappings, including Settlement public interactions versus Factory movement-only Visitors.
- Ownership and historical-event persistence from the first committed changes.

## Before enabling automated Building mutation

- Automatic discovery algorithm, stable identity across split/merge and claim changes, and historical linkage during partial reclaim.
- Block-entity/inventory baseline representation and restoration safety; inherited structures use current-state baselines and authorized edits automatically revise targets.
- Durable journal/checkpoint implementation, world-save/database reconciliation, idempotent material accounting, and transactional outbox storage/retry scheduling.
- Activity/ruin aggregation and eligibility configuration; meaningful Visitor credit is very small, passersby earn none, and recovered activity stops further decay without healing.
- Repair/workforce integration and qualifying restoration-cycle rules for Era advancement.

## Before enabling Resource and Factory operations

- Resource reset interval, generation settings, pre-generation radius, evacuation destination, offline/respawn/portal handling, and retention policy. Shared cycles and advance preparation are settled.
- Coordinated world/database backup and restore verification, including recovery during Resource cutover.
- Factory Plot limits, costs, and release rules; fixed per-Plot roles and contiguous claiming are settled.
- Factory generator implementation and target-version redstone compatibility evaluation; ordinary Paper ticking and compatibility-first policy are settled.

## Deferred tuning and optional features

- Administrator exemptions, cooldowns, and remaining expansion policy.
- Activity weights/caps, decay curves/probabilities/material rules, vegetation, repair speed/material values, and workload budgets; configure safe eligibility before enabling affected features.
- Particle styles, command/Dialog presentation, localization, historical UI, rankings, and Era names.
- Alliance relationships and optional future offline Factory capacity.

These details do not block the initial plugin or claim slice. Resolve them when their feature is implemented; do not enable an incomplete or unsafe feature to avoid its gate.
