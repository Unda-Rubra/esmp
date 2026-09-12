# Evolve: SMP — Strict MVP Implementation Roadmap

> Status: **EXECUTION CONTRACT**
>
> Date: **2026-09-12**
>
> Canonical design source: `ARCHITECTURE.md`
>
> Frozen requirements source: `MVP_REQUIREMENTS_FREEZE.md`
>
> A local development agent must execute phases in order. It must not start a later phase while the current phase has failed acceptance criteria.

---

# 0. Agent rules

## 0.1 Scope discipline

The agent MUST:

- implement only the current phase;
- preserve already passing behavior unless the phase explicitly migrates it;
- keep commits/builds green at every phase boundary;
- add/modify tests before declaring a phase complete;
- update generated jOOQ classes through the build, never by editing generated source;
- use public cross-module APIs and repository interfaces;
- stop and report an architecture conflict rather than inventing a new system.

The agent MUST NOT:

- implement Post-MVP systems;
- add speculative tables/events/modules;
- create new Settlement roles;
- add automatic Claim release;
- add Server Trade/Facilities/teleport/freight/seasons/disasters;
- add raw SQL/JDBC to application/gameplay code;
- bypass repository/domain APIs because "it is easier";
- force-load chunks for Decay/Repair;
- turn tuning questions into architecture rewrites.

## 0.2 Definition of phase completion

A phase is DONE only when:

1. project builds from clean checkout;
2. migrations succeed from empty DB;
3. migration succeeds from the required previous fixture;
4. unit + persistence integration tests pass;
5. relevant Paper scenario tests pass;
6. `check` / dependency/architecture tests pass;
7. no TODO/FIXME remains for a stated phase requirement;
8. operator/player-facing command help reflects the implemented behavior;
9. no forbidden Post-MVP surface was introduced.

---

# 1. Baseline snapshot

Assume these existing capabilities are present and should be preserved:

```text
esmp-core:
  world identity
  bounded SQLite executor
  persisted GameClock

esmp-civilization:
  Settlement roles/bans
  claims/topology
  Parcel cells/policies/Holders
  durable consent proposals
  aggregate Activity
  history/outbox

esmp-economy:
  integer wallets/treasuries
  typed movements
  idempotent receipts
  issuance budget
  starting funds
  Governor allowances

esmp-plugin:
  commands
  committed authorization snapshots
  protection
  Management Book / Parcel Wand
  land-value capture adapters

land:
  immutable generated Main chunk valuation capture
```

Paid Claims, frozen certification, acquisition workflow, Local Activity, Buildings, Decay, and Repair are not yet accepted complete.

---

# Phase 0 — Persistence modernization and build stabilization

## Goal

Replace future hand-written migration/repository SQL with Liquibase + generated jOOQ while preserving current gameplay behavior.

## Allowed modules

- root build logic
- `esmp-core`
- `esmp-economy`
- `esmp-civilization`
- `esmp-resource`
- tests
- minimal `esmp-plugin` bootstrap wiring only

## Work

1. Pin:
   - Paper API `26.2.build.121-stable`
   - Java toolchain 25
   - Gradle Wrapper 9.7.1
   - jOOQ 3.21.8
   - Liquibase Community 5.0.4
   - sqlite-jdbc 3.53.4.0
   - JUnit 6.1.2
2. Commit dependency lockfiles and Gradle dependency verification metadata.
3. Introduce structured Liquibase master changelog representing the current known schema.
4. Implement one-time legacy migration bridge:
   - existing legacy migrator reaches a known bridge schema;
   - verify expected shape;
   - mark matching Liquibase baseline;
   - all new schema changes use Liquibase.
5. Add temporary-SQLite schema preparation task:
   - create temp DB;
   - apply Liquibase;
   - run official jOOQ codegen;
   - generated source becomes compile input.
6. Replace direct gameplay repository JDBC/raw SQL with jOOQ incrementally until no application repository uses string SQL.
7. Add `UnitOfWork` abstraction backed by jOOQ transaction context.
8. Add ArchUnit dependency rules.
9. Keep existing observable gameplay identical.

## Hard constraints

- Do not change Claim prices or enable payments here.
- Do not change Settlement role semantics.
- Do not add new domain tables beyond what is required to represent current schema/migration metadata.
- Do not use deprecated jOOQ direct Liquibase metadata code generation.
- Do not use the Flyway Gradle plugin as an alternate migration path.

## Tests

- empty DB → Liquibase latest;
- legacy fixture → legacy bridge → Liquibase baseline → latest;
- generated jOOQ schema compiles;
- startup detects migration mismatch and fails closed;
- representative current repositories survive close/reopen;
- ArchUnit forbids raw persistence leakage/cross-domain `.persistence` imports.

## Done when

`./gradlew clean check` can recreate the schema/codegen pipeline and no gameplay/application repository contains handwritten JDBC statements or SQL strings.

---

# Phase 1 — Paid Claims and personal land progression

## Goal

Make Claim founding/expansion actually debit money and consume personal subsidies atomically.

## Allowed modules

- `esmp-civilization`
- `esmp-economy` public API only where needed
- `esmp-plugin` commands/UI
- Liquibase migration
- tests

## Schema

Add/finalize `PlayerLandProgress` equivalent:

```text
player_uuid
first_claim_used
subsidized_claims_used
revision
```

Add any quote/revision metadata only if needed for deterministic commit validation.

## Work

1. Implement deterministic fixed-point/integer quote service.
2. New Settlement:
   - founder wallet pays;
   - assessed portfolio = founder;
   - first-ever founding Claim = 25.
3. Expansion:
   - Settlement Treasury pays;
   - assessed portfolio = current Owner;
   - Governor route must use authorized treasury spending.
4. Reject missing LandValueProfile.
5. Revalidate at commit:
   - payer;
   - permission;
   - topology;
   - ownership revision;
   - land profile/version;
   - portfolio/Settlement size;
   - Activity correction;
   - subsidy progression;
   - available balance.
6. In one DB transaction:
   - debit;
   - ledger receipt;
   - consume subsidy state;
   - insert Claim/founding state;
   - append History/outbox fact.
7. Publish committed Claim snapshot after commit.

## Tests

- fixed first Claim 25;
- insufficient funds no partial Claim;
- retry same transaction ID does not double debit;
- quote becomes stale after portfolio/ownership/topology change;
- subsidy does not reset after transfer/abandonment;
- Governor cannot use personal small portfolio to lower Settlement expansion assessment;
- missing LandValueProfile rejects;
- crash/restart after DB commit reconstructs correct protection.

## Done when

Numeric-only Claim behavior is removed from the player path and every paid Claim is auditable/idempotent.

## Forbidden

Certification, ownership acquisition, Local Activity, Building work.

---

# Phase 2 — Frozen Settlement certification and naming

## Goal

Implement complete UNVERIFIED/VERIFIED application/admin workflow.

## Allowed modules

- `esmp-civilization`
- `esmp-plugin`
- Liquibase migration
- tests

## Schema

Persist:

```text
settlement_id
status
eligible_since_game_tick?
applied_at_game_tick?
application_state?
proposed_name?
decision_by_uuid?
decision_at_game_tick?
decision_note?
```

Use the smallest model that preserves application/decision provenance.

## Work

1. New Settlement defaults UNVERIFIED.
2. Eligibility default:
   - age ≥ 3 game days;
   - current Activity not INACTIVE;
   - lifetime credited meaningful Activity ≥ 20.
3. Governance can apply only when eligible.
4. Admin approve/deny.
5. Approval sets VERIFIED; official name may be approved/assigned.
6. Denial keeps Settlement fully owned/protected.
7. No automatic revocation.
8. Migrate existing grandfathered official names explicitly.
9. Produce clean read model/export for future website use; do not build website.

## Tests

- cannot apply before eligibility;
- threshold crossing via GameClock/Activity;
- denial preserves everything;
- approve idempotent;
- concurrent rename/application does not corrupt state;
- official name unavailable to unverified new Settlement;
- legacy named Settlement migration deterministic.

## Done when

The old naming toggle is no longer an alternate behavior.

## Forbidden

Town/City levels, aesthetic score, automatic verification/revocation, website.

---

# Phase 3 — Administrator-approved ownership acquisition

## Goal

Replace legacy two-party transfer with frozen recipient + fee + admin approval flow.

## Allowed modules

- `esmp-civilization`
- `esmp-economy` API
- `esmp-plugin`
- Liquibase migration
- tests

## Work

1. Owner proposes recipient.
2. Recipient accepts.
3. Quote:
   `Fee = k × sum(BaseLandValue(current Claims))`
4. Admin approves.
5. Commit transaction:
   - revalidate parties/ownership/revisions/Claims/profiles;
   - debit recipient wallet;
   - SINK receipt;
   - change Owner;
   - new Owner membership row removed;
   - former Owner → Citizen;
   - treasury unchanged;
   - History/outbox appended.
6. Missing valuation rejects automatic acquisition.
7. Pending legacy offers receive explicit migration/cancellation rule.

## Tests

- acceptance alone cannot transfer;
- admin approval required;
- recipient insufficient funds;
- stale Claim/profile/Owner revisions reject;
- retry is idempotent;
- treasury stays;
- former Owner citizen;
- no two-owner/ownerless intermediate committed state.

## Done when

Legacy two-party immediate transfer is unreachable.

## Forbidden

Building/cultural appraisal, receiving-capacity model, private sale contract engine.

---

# Phase 4 — Local Activity Buckets

## Goal

Add spatial Activity support without automatic land release.

## Allowed modules

- `esmp-civilization`
- `esmp-plugin` inspection/UI
- Liquibase migration
- tests

## Schema

```text
settlement_local_activity
  settlement_id
  world_instance_id
  chunk_x
  chunk_z
  score_at_anchor
  anchor_game_tick
  policy_version
```

Indexes support Settlement + chunk lookups and due/read aggregation.

## Work

1. Existing accepted meaningful activity observation credits:
   - aggregate Activity;
   - exact local Claim bucket.
2. Use same GameClock lazy decay semantics.
3. Implement configurable neighbor contribution kernel.
4. Add service:
   `localSupport(settlement, footprint/chunks, gameTick)`.
5. Add derived four-connected supported-component view (`ActivityRegion`) without persistence.
6. Add admin/debug command/view for local support; no player management commands.
7. Preserve existing aggregate Activity behavior.

## Tests

- local action affects correct bucket;
- neighboring influence bounded/configurable;
- no activity from walking/login/automation;
- freeze time freezes decay;
- server downtime no catch-up;
- derived Regions split/merge based on support;
- no Claim is released.

## Done when

Building subsystem can consume a stable local-support API.

## Forbidden

Automatic Claim release, district naming/governance, high-Activity buffs.

---

# Phase 5 — Explicit Building registration and active baseline

## Goal

Create stable registered Buildings without solving global automatic discovery.

## Allowed modules

- `esmp-civilization`
- `esmp-plugin`
- Liquibase migration
- tests

## Schema

Minimum conceptual state:

```text
buildings
building_baseline_revisions
building_baseline_blocks/fragments
building_geometry_summary
building_predecessor_link?   // optional/best effort
```

Choose storage representation based on bounded size/performance tests, not on speculative future export.

## Work

1. Add Owner/Governor bounded 3D selection and registration command/UI.
2. Require all selected volume inside one Settlement.
3. Require touched chunks naturally loaded at registration.
4. Hard volume limit.
5. Scan once and build:
   - occupied baseline;
   - exterior;
   - interior air;
   - openings;
   - simple support relationships.
6. Establish revision 1.
7. Authorized player edits:
   - update affected active baseline;
   - increment revision;
   - do not create Decay damage.
8. Complex reinterpretation can retire old Building; new registration possible.
9. Exclude block entities from automatic mutable baseline operations.
10. Add repair-station placement metadata hook but do not implement Repair yet.

## Tests

- region outside Settlement rejected;
- unloaded selection rejected without force load;
- size cap;
- baseline survives restart;
- authorized demolition changes baseline;
- renovation does not become damage;
- retired Building history preserved;
- no global scan path exists.

## Done when

A player can register, inspect, edit, restart, and re-inspect a stable Building baseline.

## Forbidden

Automatic city-wide discovery, perfect split/merge matcher, Decay/Repair world mutation.

---

# Phase 6 — Durable Decay

## Goal

Turn locally unsupported registered Buildings into bounded recognizable damage.

## Allowed modules

- `esmp-civilization`
- `esmp-plugin`
- Liquibase migration
- tests

## Schema

Persist durable jobs/operations with:

```text
job_id
building_id
baseline_revision
expected activity/ownership/security revision
state
progress
operation identity
expected source
intended target
```

## Work

1. Eligibility from Building footprint local support + grace.
2. Candidate selection indexed/due, never global block scan.
3. Implement WEATHER whitelist.
4. Implement DROP.
5. Implement CAVE_IN as bounded patch using safe primitive.
6. No ordinary item drops.
7. Persist intent before physical mutation.
8. Wait for naturally loaded chunks.
9. World-thread revalidate:
   - world instance;
   - Building/baseline revision;
   - ownership/security;
   - local Activity eligibility;
   - current block state.
10. Apply bounded batch.
11. Persist outcome.
12. Stop future decay on Activity recovery.
13. Compute INTACT/DAMAGED/RUINED condition and emit structured facts.

## Tests

- no mutation while active;
- recovery cancels future eligible work;
- damage persists after recovery;
- player conflict wins;
- crash/restart around intent/apply/outcome;
- already-applied target does not duplicate side effects;
- no block entities changed;
- no natural terrain outside registered Building changed;
- no drops;
- no force load.

## Done when

A registered Building can decay across restart safely under controlled local inactivity.

## Forbidden

TOPPLE, horizontal physics, raids, water/snow simulation, Claim release.

---

# Phase 7 — Repair, History completion, Era

## Goal

Close the MVP gameplay loop by restoring valid damage with real materials and durable history.

## Allowed modules

- `esmp-civilization`
- `esmp-plugin`
- Liquibase migration
- tests

## Work

1. One Repair Station/point per Building.
2. Damage inspection computes required material map.
3. Player supplies materials through defined interaction.
4. Create durable Repair job/reservations.
5. GameClock-driven bounded progress.
6. Revalidate before each world batch.
7. Restore only current active-baseline damage.
8. Player renovation/conflict wins.
9. Block entities excluded.
10. Station protected from ordinary decay.
11. Recover item/material accounting on interruption.
12. Emit Repair started/completed.
13. Era:
   - starts 0;
   - RUINED → Repair → INTACT increments once;
   - idempotent by cycle/job identity.
14. Finalize structured MVP History catalog.

## Tests

- insufficient materials;
- duplicate/retry cannot consume twice;
- crash before/after physical batch;
- player edit during repair preserved;
- old retired baseline not restored;
- full repair returns INTACT;
- Era advances exactly once per ruin-restoration cycle;
- non-ruined ordinary repair does not falsely advance Era.

## Done when

The vertical loop `Activity ↓ → Decay → RUINED → materials/time → Repair → History/Era` works through restart.

## Forbidden

Artisans/villagers, partial-wing repair UI, historical screenshots/replay, loot rewards.

---

# Phase 8 — MVP hardening and release gate

## Goal

Do not add features. Prove frozen MVP quality.

## Work

1. Run full acceptance matrix.
2. Performance/profile:
   - protection hot paths;
   - Activity observation load;
   - Building registration max volume;
   - Decay/Repair batch budgets;
   - SQLite queue pressure.
3. Validate world + DB backup/restore together.
4. Test actual Paper server upgrade/restart path.
5. Validate migration from real current development DB copy.
6. Verify all player-facing commands/help.
7. Remove obsolete legacy runtime paths after migration window if safe.
8. Generate release notes and operator migration guide.
9. Confirm forbidden-system grep/architecture checks.

## Mandatory acceptance list

- clean install;
- legacy migration;
- restart persistence;
- fail-closed protection startup;
- paid Claim idempotency;
- certification state machine;
- acquisition state machine;
- local Activity decay/recovery;
- Building registration/baseline;
- Decay restart/conflict;
- Repair material/restart/conflict;
- History/Era exactly-once;
- no SQL strings/JDBC statements in gameplay repositories;
- no sync DB on hot Paper paths;
- no recurring full-world/full-Settlement block scan;
- no forced chunk loading for Decay/Repair.

## Done when

All acceptance tests pass and a clean server can demonstrate the entire MVP loop.

At this point tag **MVP v1.0**.

---

# MVP+1 — Dynamic Settlement Cycle (DO NOT IMPLEMENT BEFORE MVP TAG)

This is already selected as the next major feature.

Use Local Activity/derived Activity Regions to answer spatial support.

Design must be frozen separately around:

- peripheral/boundary-only release;
- no internal Swiss-cheese holes;
- Civic Capacity/maintenance demand only if needed;
- assigned Parcel/Holder rights;
- container/property disposition;
- topology and historical-core preservation;
- reclaim/history;
- gradual city spatial migration.

Do not begin implementation from this roadmap.

---

# Appendix A — Required Gradle gates

Recommended root tasks:

```text
./gradlew clean check
./gradlew dependencyUpdates       // informational only; never auto-upgrade
./gradlew generateJooq
./gradlew test
./gradlew integrationTest
./gradlew architectureTest
```

`check` should depend on generated-schema validation, unit tests, persistence integration tests, and architecture tests.

Dependency updates require deliberate review and exact repinning.

---

# Appendix B — Agent phase report format

At the end of every phase, the local agent should output:

```text
PHASE:
STATUS: DONE | BLOCKED

Changed modules:
Schema migrations:
Public API changes:
Commands/UI changes:

Acceptance tests:
- PASS ...
- PASS ...

Known non-blocking issues:
Deferred ideas explicitly not implemented:

Architecture deviations:
- none
```

If `Architecture deviations` is not `none`, the phase is not complete until reviewed.
