# ESMP Current Phase Gates

## Authority and status

The [frozen architecture](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/ARCHITECTURE.md), [requirements freeze](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/MVP_REQUIREMENTS_FREEZE.md) and [strict roadmap](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/IMPLEMENTATION_ROADMAP.md) remain canonical and unchanged.

Phase 0 infrastructure is implemented and verified. It added Liquibase 5.0.4 structured changelogs/validated legacy bridge, generated jOOQ schema, opaque UnitOfWork, domain Repository boundaries, SQLiteConfig/DataSource, JUnit 6.1.2, ArchUnit 1.5.0 and dependency verification metadata. It did not change gameplay. Historical player checks remain pending; phase execution evidence does not substitute for user acceptance.

## Phase 1 — next, not yet complete

1. **Treasury payer cutover.** All expansion must debit Settlement Treasury, assessed against current Owner, including Owner-initiated expansion. The current Owner-wallet expansion is intentionally preserved from the pre-freeze baseline. Founding stays founder Wallet. Governor spending remains authorization/allowance-limited.
2. **Quote/progression acceptance.** Preserve paid receipts and subsidies. Pending quotes must not retain obsolete wallet payer semantics after the cutover. Add/finalize progression revision or equivalently validated context as required by the roadmap; verify stale portfolio/ownership/topology/Activity/profile/subsidy and insufficient treasury/allowance cases.
3. **Atomic publication.** Continue one UnitOfWork transaction for debit, receipt, progress, Claim/history, then publish protection through the existing coordinator. Re-test crash/restart after committed paid purchase and same-ID replay. No free fallback for uncaptured land.
4. **Scope.** Only Phase 1 modules/APIs/migrations/tests/command feedback. No certification, acquisition, Local Activity or Building work before Phase 1 acceptance. Coefficients are configuration, not an architecture questionnaire.

## Later frozen dependencies

- Phase 2 needs durable lifetime credited meaningful Activity for the default certification gate (age 3 days, non-INACTIVE, lifetime credit 20), plus explicit old-name migration. Current score is not lifetime history. Do not move Phase 4 local buckets into Phase 2.
- Phase 3 replaces two-party transfer with recipient acceptance, fee and admin approval; treasury identity and former-Owner Citizen transition remain.
- Phase 4 supplies local-support API and derived regions. Phase 5 is bounded explicit Building registration/active baseline. Phases 6–7 add registered-only Decay and recoverable material/time Repair with deterministic History/Era.
- Phase 8 proves the whole loop, performance and paired backup/restore before MVP tag. Dynamic Settlement Cycle is mandatory MVP+1 only afterward. No placeholders for deferred systems.

## Operational notes retained

- Canonical schemas are `schema/db/changelog/*.xml`. Old SQL resources and `.persistence.legacy` adapters are compatibility-only. New migrations must not extend the old chain.
- SQLite CHECK/composite/partial-index baseline exceptions are documented in changelogs and covered by native schema comparison/constraint tests; they do not authorize new handwritten gameplay SQL.
- Maven's Java TLS transport intermittently failed in this environment. Final clean/no-cache validation used a temporary loopback transport that fetched unchanged Maven Central artifacts over verified HTTPS; committed repositories were not changed and SHA-256 verification remained enforced. See HANDOFF for exact commands.
- The bundle references SVGs but supplies equivalent DOT/PNG; this non-blocking asset-link issue is not a missing implementation prerequisite.
