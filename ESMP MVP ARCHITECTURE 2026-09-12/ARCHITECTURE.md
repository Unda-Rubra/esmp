# Evolve: SMP — Canonical Architecture

> Status: **MVP v1.0 FROZEN / CANONICAL**
>
> Freeze date: **2026-09-12**
>
> Project: **Evolve: SMP (ESMP)**
>
> Package root: `xdu.undarubra.esmp`
>
> This document supersedes earlier architecture drafts for **MVP v1.0 implementation**. Earlier documents remain design history only. If an older document conflicts with this file or `MVP_REQUIREMENTS_FREEZE.md`, this file and the freeze contract win.

---

# 0. Scope and change control

MVP v1.0 proves this complete vertical slice:

```text
World / persistence / GameClock
→ Settlement / Parcel / Protection
→ Currency / LandValue / Paid Claims
→ Certification / Ownership Transfer
→ Settlement Activity + Local Activity
→ Registered Building + Baseline
→ Decay
→ Repair
→ History / Era
```

MVP v1.0 **does not** include automatic Claim release. The required next phase is MVP+1 Dynamic Settlement Cycle.

After freeze:

- new gameplay ideas go to Post-MVP;
- tuning constants remain configuration and do not reopen architecture;
- only correctness, security, data-loss, migration, performance, or acceptance-test defects may change frozen implementation details without a new architecture decision;
- deferred features must not receive placeholder tables, modules, events, commands, or empty abstractions.

The local implementation agent must treat `IMPLEMENTATION_ROADMAP.md` as the execution order and this file as the design authority.

---

# 1. Core design principles

ESMP is a long-running Minecraft SMP in which the persistent world and player civilization change each other.

> **World provides space. Claims provide ownership. Activity provides life. Decay provides change. Repair provides continuity. History provides meaning.**

The Main World is not reset to create seasons. Instead, players create Settlements, Buildings gain history, inactive construction becomes recognizable ruin, and later phases can gradually return unsupported land to the world.

Vanilla personal progression may finish; contribution to the persistent world has no universal finish line. ESMP therefore prefers horizontal civilization play over an endless RPG equipment ladder.

Additional principles:

- Vanilla-client-first.
- Paper server plugin; no mandatory client mod.
- Core supplies infrastructure; gameplay modules supply meaning.
- World mutation is bounded, recoverable, and performed only on the correct Paper execution context.
- No recurring full-world or full-Settlement block scans.
- State ownership is explicit; cross-domain mutation goes through APIs, never through another module's tables.
- History records committed facts, not speculative future event taxonomies.
- New complexity must earn itself through player value or a proven invariant.

---

# 2. Technology baseline

Verified baseline as of 2026-09-12:

| Component | Frozen selection |
|---|---|
| Minecraft / Paper | Paper `26.2`, pin stable API build `26.2.build.121-stable` |
| Java language/runtime | Java 25 LTS; production/reference runtime Eclipse Temurin `25.0.4.1+1` |
| Build | Gradle Wrapper `9.7.1`, Kotlin DSL |
| Plugin implementation | Java |
| Database | SQLite through Xerial `sqlite-jdbc 3.53.4.0` |
| Type-safe DB access | jOOQ OSS `3.21.8` + generated schema classes |
| Schema migration | Liquibase Community `5.0.4`, structured YAML/XML changelogs |
| Tests | JUnit `6.1.2`; ArchUnit for dependency rules |
| Packaging | one deployable Paper JAR |

Rules:

1. **No handwritten SQL in gameplay/application repositories.**
2. **No handwritten JDBC statement/result-set code in gameplay/application repositories.**
3. Persistence adapters use generated jOOQ table/record/field types and jOOQ transaction APIs.
4. Schema changes use Liquibase structured change types (`createTable`, `addColumn`, `addForeignKeyConstraint`, indexes, constraints, etc.).
5. Vendor SQL is an exception, not a convenience. It requires a comment explaining why Liquibase change types and jOOQ cannot express the required behavior, plus a focused integration test.
6. SQLite configuration uses driver APIs (`SQLiteConfig`/DataSource configuration) rather than ad-hoc runtime `PRAGMA ...` strings wherever possible.
7. Dynamic/latest dependency selectors are forbidden in committed builds. Pin exact versions and commit Gradle dependency lockfiles and verification metadata.
8. Snapshot, RC, milestone, or nightly dependencies are forbidden in MVP unless a separate architecture decision names the blocking bug.

Why this stack:

- Java 25 is LTS while Java 26/27 are non-LTS for this project horizon.
- Gradle 9.7.1 is the current stable patch, not a 9.8 RC/nightly.
- Paper's current project setup targets Java 25 and Paper 26.2.
- jOOQ provides compile-time generated identifiers and typed DSL instead of SQL strings.
- Liquibase gives declarative/versioned schema evolution and SQLite support without making handwritten DDL the default.
- SQLite remains appropriate for one Paper server and the existing single-writer architecture.

---

# 3. Build and repository layout

One repository, one deployable JAR, explicit internal modules:

```text
root
├── esmp-core
├── esmp-economy
├── esmp-civilization
├── esmp-resource
└── esmp-plugin
```

Do not add a new runtime module merely to hold future features.

Recommended source layering inside each gameplay module:

```text
<module>
└── src/main/java/xdu/undarubra/esmp/<domain>/
    ├── domain/          pure domain values/entities/invariants
    ├── application/     use cases, commands, ports, transaction orchestration
    ├── persistence/     repository adapters; jOOQ is allowed here only
    └── api/             deliberately exported cross-module interfaces/events
```

`esmp-plugin` owns Paper adapters, command registration, listeners, UI adapters, bootstrap/composition, and server lifecycle.

Generated jOOQ sources live in generated build directories and are not handwritten. Their packages are internal persistence implementation details.

---

# 4. Module dependency rules

Allowed compile-time dependency direction:

```text
esmp-core
   ▲
   ├──────── esmp-economy
   │              ▲
   │              │ explicit economy API only where required
   ├──────── esmp-civilization
   │
   └──────── esmp-resource

esmp-plugin
   ├── esmp-core
   ├── esmp-economy
   ├── esmp-civilization
   └── esmp-resource
```

Practical rule for paid Claims: Civilization may depend on the **public Economy application API**, not Economy persistence classes or generated tables.

Forbidden:

- Core importing Settlement, Activity, Building, Wallet, or other gameplay semantics.
- Economy importing Civilization persistence.
- Civilization directly updating wallet/ledger tables.
- Plugin listeners directly querying or mutating database tables.
- Resource importing Settlement logic.
- Any module importing another domain's `.persistence..` or generated jOOQ package.

Enforce these with ArchUnit tests.

Cross-domain synchronous invariants use explicit APIs. Cross-domain facts after commit use durable domain events.

---

# 5. Runtime and execution model

Paper is the runtime target.

## 5.1 Thread boundary

```text
Paper/world execution context
├── Bukkit/Paper world reads that require live world state
├── block/entity mutation
├── permission snapshot publication
└── player UI

bounded database executor
├── Liquibase only during startup/migration
├── jOOQ repository transactions
├── scoring / due-work queries
└── durable job/event persistence

pure worker computation (optional)
└── CPU-only planning using immutable snapshots
```

Rules:

- no blocking database I/O in hot Paper event handlers;
- no arbitrary async access to Bukkit world objects;
- no forced chunk loading just to advance Decay/Repair;
- world jobs wait for naturally loaded chunks;
- DB jobs are bounded and backpressure-aware;
- publication to live permission/spatial snapshots happens only after the owning DB transaction commits.

## 5.2 Startup fail-closed

Startup order:

```text
load config
→ identify configured worlds
→ open database
→ run legacy bridge if needed
→ run Liquibase migrations
→ verify schema/code compatibility
→ load authoritative domain state
→ build committed snapshots
→ register/admit gameplay
```

If claims/protection cannot be loaded safely, stop rather than run unprotected.

---

# 6. Persistence architecture

## 6.1 SQLite access

Use `SQLiteDataSource`/`SQLiteConfig` and one bounded database execution service. MVP does not need a generic connection pool.

Recommended driver settings:

- foreign keys enabled;
- WAL journal mode if validated by integration tests and backup procedure;
- bounded busy timeout;
- deterministic transaction boundaries;
- one serialized write lane.

Exact driver settings are configuration/operations concerns, but they must be set through stable driver APIs where available.

## 6.2 jOOQ contract

Application code sees repository interfaces and domain values, never SQL strings.

Persistence implementations:

- receive a transaction/session abstraction owned by Core;
- use generated jOOQ schema identifiers;
- return domain/application DTOs, not jOOQ `Record` types;
- do not expose `DSLContext`, `Table`, `Field`, or generated records across module/API boundaries.

A Core `UnitOfWork` supplies a scoped jOOQ transaction context for coordinated database-only work. Paid Claim + currency debit and acquisition fee + ownership transfer use this boundary.

Minecraft world state is never part of that SQL atomic transaction.

## 6.3 Liquibase migration contract

Canonical schema source is structured Liquibase changelog:

```text
db/changelog/
├── db.changelog-master.yaml
├── core/
├── economy/
├── civilization/
└── resource/
```

Prefer portable change types. Raw `<sql>` / `sqlFile` is forbidden unless documented as a reviewed SQLite-specific exception.

Every migration is:

- immutable after release;
- deterministic;
- forward-only for production;
- tested against an empty DB and a previous-version fixture;
- validated before gameplay admission.

## 6.4 Legacy migration bridge

Existing development databases currently use the previous custom schema/version machinery. Do **not** rewrite them in place with an untested big-bang conversion.

One-time cutover:

```text
legacy database
→ existing legacy migrator brings DB to one known bridge version
→ verify expected tables/constraints
→ Liquibase changelogSync/baseline records the matching baseline
→ Liquibase owns all later schema evolution
```

The legacy migrator becomes compatibility-only and receives no new migrations after cutover.

## 6.5 jOOQ code generation

Do not use deprecated direct Liquibase metadata generation.

Build process:

```text
Liquibase changelog
→ create temporary SQLite database
→ apply all migrations
→ jOOQ codegen inspects real SQLite schema
→ generate Java schema classes
→ compile repositories against generated classes
```

Generated output is reproducible and disposable. CI must fail if migrations cannot construct a schema suitable for jOOQ generation.

---

# 7. World model

Stable definitions:

```text
MAIN
RESOURCE_OVERWORLD
RESOURCE_NETHER
RESOURCE_END
```

A logical definition can have physical `WorldInstance`s.

Coordinate-sensitive ESMP records use `world_instance_id`.

Main World:

- permanent;
- Claim/Settlement enabled;
- Activity enabled;
- registered Buildings enabled;
- Decay/Repair/History enabled;
- never globally reset.

Resource worlds:

- no Settlements/Claims;
- no Building decay/repair;
- renewable;
- shared player inventory and Ender Chest with Main World.

Automatic Resource reset orchestration is Post-MVP. Operator-driven reset is acceptable for MVP.

---

# 8. GameClock

One neutral persisted GameClock supplies gameplay time.

Rules:

- game time advances from Main World's forward time deltas;
- downtime does not catch up;
- `advance_time=false` freezes time-driven progression;
- rewinds do not reverse completed work;
- Resource reset does not restart chronology;
- gameplay uses ticks/days, not wall-clock time.

Calendar/season presentation is not part of MVP mechanics.

---

# 9. Settlement and Claim model

Settlement is the player-owned governance aggregate.

Core roles:

```text
OWNER       exactly one
GOVERNOR    zero or more
CITIZEN     zero or more
PUBLIC      derived
BANNED      negative Settlement-local override
```

One player may own multiple Settlements.

Claims:

- Main World only;
- one full-height 16×16 chunk;
- exclusive by `(world_instance_id, chunk_x, chunk_z)`;
- all owned chunks form one four-directionally connected component;
- diagonal contact is not connectivity;
- ordinary unclaim cannot split the component;
- separate Settlements do not auto-merge.

Commands remain complete/canonical; tools are convenience adapters.

---

# 10. Parcel and authorization

Parcel is a lightweight 3D subdivision inside one Settlement.

Geometry:

- aligned 8×8×8 cells;
- all cells inside parent Settlement;
- no overlap;
- six-directionally connected;
- unique name within Settlement.

Holder is orthogonal to membership.

Assigned Parcel geometry/Holder changes require governance plus all affected Holders. Admin dispute resolution is audited.

Permission domains:

```text
BUILD
INTERACT
CONTAINER
AUTOMATION
```

Resolution:

```text
administrator bypass
→ BANNED
→ Settlement lookup
→ Parcel lookup
→ Parcel/default policy
→ OWNER/GOVERNOR/CITIZEN/HOLDER/PUBLIC relation set
→ allow/deny
```

Cross-boundary automation must satisfy every protected side.

---

# 11. Currency, accounts, and audit

One server-wide virtual ledger currency.

Accounts:

```text
PlayerWallet
SettlementTreasury
```

Movement types:

```text
TRANSFER
ISSUANCE
SINK
```

Rules:

- integer amounts only;
- balances non-negative;
- no formal debt;
- stable transaction IDs;
- retries are idempotent;
- ledger entries are immutable audit facts;
- refunds are new movements, never history rewrite.

Starting funds remain budgeted and one-time.

MVP does not need Server Trade. Admin/test/public grants are sufficient to test the frozen core.

---

# 12. Land valuation and paid Claims

Each newly generated Main chunk receives immutable `LandValueProfile`:

```text
world_instance_id
chunk_x
chunk_z
valuation_version
base_value
valuation_class?  // optional display/debug
```

Player modification does not change base value.

Paid Claim formulas:

```text
Expansion:
B_terrain × P_portfolio × S_settlement × A_activity × D_early

Founding:
B_terrain × P_portfolio × N_proximity × D_founding
```

Frozen payer rules:

```text
new Settlement:
  payer = founder PlayerWallet
  assessed player = founder

expand Settlement:
  payer = SettlementTreasury
  assessed player = current Owner
```

This prevents Governor/alt account portfolio bypass.

Rules:

- first-ever founding Claim = exactly 25;
- early subsidy belongs to player;
- subsidy never resets/transfers;
- missing required LandValueProfile rejects purchase;
- price context is revalidated at commit;
- debit + subsidy use + Claim + history commit together;
- multiplier values are config/balance, not architecture.

---

# 13. Settlement certification

Exactly:

```text
UNVERIFIED
VERIFIED
```

Default MVP application gate:

- Settlement age ≥ 3 game days;
- current aggregate Activity is not INACTIVE;
- lifetime credited meaningful Activity ≥ 20.

These defaults are configuration, but the two-stage semantics are frozen.

Flow:

```text
UNVERIFIED
→ eligible
→ governance applies
→ admin approves/denies
→ VERIFIED on approval
```

Admin review is authoritative. Denial preserves Claims/governance. No automatic revocation in MVP.

Official naming/public publication eligibility requires VERIFIED. Website implementation is outside plugin MVP; durable status/name/export data is inside.

---

# 14. Settlement ownership transfer

Frozen flow:

```text
Owner proposes
→ recipient accepts
→ fee quote
→ administrator approves
→ fee + ownership mutation commit atomically
```

Acquisition fee:

```text
Fee = k × sum(BaseLandValue of current Claims)
```

Recipient pays from personal Wallet. Treasury remains with Settlement. Former Owner becomes Citizen by default. No automatic Building/cultural appraisal.

Missing required valuation rejects automatic transfer.

---

# 15. Activity model

Two simultaneous views.

## 15.1 Aggregate Settlement Activity

Used for:

- certification;
- overall status/UI;
- aggregate statistics.

Meaningful authorized human actions credit Activity. Automation/login/walking/idle do not.

Existing lazy exponential decay remains:

```text
score(t) = score(anchor) × 0.5 ^ ((t-anchor)/(halfLifeDays×24000))
```

Default half-life remains seven game days. Thresholds/caps are configuration.

## 15.2 Local Activity Buckets

Persist Claim-chunk-level local support:

```text
LocalActivityBucket
├── settlement_id
├── world_instance_id
├── chunk_x
├── chunk_z
├── score_at_anchor
└── anchor_game_tick
```

Meaningful actions credit both aggregate and local bucket.

A small configurable neighbor kernel may let nearby real activity support adjacent claimed chunks. This prevents per-chunk "check-in" gameplay.

`ActivityRegion` is derived:

```text
locally supported buckets
→ four-connected components
→ ActivityRegion view
```

It is not persisted, named, owned, or player-managed in MVP.

Registered Building decay eligibility uses footprint-weighted local support, not only global Settlement Activity.

MVP does **not** automatically release Claims.

---

# 16. Building registration, identity, and baseline

Automatic whole-city Building discovery is outside MVP.

MVP registration:

```text
Owner/Governor selects bounded 3D region
→ validate entirely inside Settlement
→ require relevant chunks naturally loaded
→ bounded localized scan
→ compute geometry + baseline
→ create stable building_id
```

Hard configurable registration size limits prevent unbounded scans. Very large construction may be split.

Geometry must support:

- occupied baseline blocks;
- exterior surface;
- interior air volume;
- openings;
- simple support/structural relations.

Baseline semantics:

- revision 1 at registration;
- authorized player edits revise only affected active baseline;
- player demolition is intended-design change, not damage;
- Decay/Repair never silently rewrite intended design;
- obvious local expansion/contraction can retain identity;
- complex reinterpretation may retire old Building and register a new one;
- perfect split/merge graph matching is not required;
- predecessor/history links are best effort.

Block entities are excluded from ordinary automatic Decay/Repair mutation in MVP.

---

# 17. Decay

Only registered Buildings decay in MVP.

Eligibility:

```text
footprint local support below inactive threshold
for configured grace duration
AND no current invalidating condition
```

Recovery stops future ordinary decay but does not heal damage.

Effect vocabulary:

```text
WEATHER
DROP
CAVE_IN
```

WEATHER:

- explicit material whitelist/mapping only;
- visually recognizable weathering;
- no arbitrary category conversion;
- no natural terrain mutation;
- no block entities.

DROP:

- bounded vertical failure;
- no collectible ordinary drops.

CAVE_IN:

- small bounded connected patch built from the same safe vertical-failure primitive.

Not MVP:

- TOPPLE;
- horizontal sliding/rotation;
- rigid-body/FEA;
- raids as decay;
- water/snow simulation;
- Claim-wide random corruption;
- natural mountains/caves/trees erosion.

Jobs are durable, bounded, naturally-loaded-chunk-only, and expected-state checked immediately before mutation.

---

# 18. Repair

Player-facing repair scope is whole Building.

Each Building can have one plugin-recognized Repair Station/point.

Flow:

```text
inspect damage
→ calculate material requirement
→ supply materials
→ persist repair intent/job
→ advance bounded work by GameClock
→ world-context revalidation/mutation
→ persist progress/outcome
```

Rules:

- restore only current active-baseline damage;
- renovations win over old baseline;
- conflict invalidates/replans only affected work;
- no automatic block-entity restoration;
- Repair Station is excluded from ordinary Building decay;
- no villager/artisan dependency;
- no free instant repair;
- default material model is one corresponding original block item per restored block, with explicit mapping exceptions.

Physical item consumption + world mutation cannot be one database transaction. Use recoverable prepare/apply/reconcile semantics with stable job/operation IDs.

---

# 19. Building condition, History, Era

Condition:

```text
INTACT
DAMAGED
RUINED
```

Threshold percentages are configuration; transition is deterministic from active-baseline condition.

MVP structured History facts include:

- Settlement founded;
- certification approved/denied;
- official name/rename;
- paid Claim acquisition and explicit release;
- ownership transfer;
- Building registered/retired;
- Decay started;
- Building became RUINED;
- Repair started/completed;
- Era advanced.

No raw chat archival.

Era:

```text
initial era = 0

RUINED
→ fully restored to INTACT through Repair
→ era += 1 once for that ruin/restoration cycle
```

No Era power, rank, loot, or economic bonus.

---

# 20. Resource Realm MVP boundary

Resource world identities exist and remain unclaimable.

Shared player inventory/Ender Chest remain normal across worlds.

For MVP:

- operator-driven reset/maintenance is acceptable;
- safe documented world replacement procedure is required;
- automatic pregeneration/evacuation/cutover/archive orchestration is Post-MVP.

---

# 21. UI and commands

Vanilla-feeling hierarchy:

1. commands;
2. ActionBar;
3. BossBar for real process progress;
4. chat text components;
5. books/signs;
6. vanilla containers/dialogs when useful.

No mandatory MMO HUD.

Commands are the full interface. Management Book/Parcel Wand are replaceable frontends.

Economic feedback is transient. Repair may use BossBar/container presentation.

---

# 22. Events and outbox

Use synchronous APIs for decisions/invariants; events report committed facts.

Durable events:

- stable event ID;
- written in same database transaction as owning domain state;
- post-commit delivery;
- at-least-once delivery;
- durable consumers deduplicate.

Do not predeclare events for deferred systems.

---

# 23. Recoverable DB-to-world jobs

SQL/application state and Minecraft world state cannot commit atomically.

All Decay/Repair physical work follows:

```text
persist intent + expected state
→ wait for naturally loaded chunk
→ revalidate on Paper world context
→ apply bounded operation
→ persist outcome/progress/events
```

On retry distinguish:

- not applied;
- already reached target;
- conflicting/newer player state.

Never replay blindly over a newer player edit. Never double-charge materials or double-advance History/Era.

---

# 24. Configuration policy

Config separates **architecture invariants** from **balance values**.

Configurable examples:

- Activity half-life/thresholds/caps;
- local neighbor contribution;
- Claim multiplier tiers;
- early subsidies after first Claim;
- acquisition coefficient;
- certification numeric gate defaults;
- Building registration volume limits;
- Decay grace/rate/material mappings;
- Repair speed;
- condition thresholds;
- queue/batch/work budgets.

Not configurable without architecture change:

- Claim unit is chunk;
- Parcel unit is 8×8×8 cell;
- certification has exactly two states;
- BANNED precedence;
- currency uses integer authoritative ledger;
- ActivityRegion is derived in MVP;
- no automatic Claim release in MVP;
- no block-entity automatic Decay/Repair in MVP.

Installed policy values that affect durable interpretation should be versioned/persisted where changing them would otherwise reinterpret old records.

---

# 25. Performance invariants

Never:

```text
for every settlement
  for every block
    scan
```

Prefer:

```text
player/world event
→ relevant state update
→ indexed candidate
→ durable queue
→ budgeted work
→ loaded-chunk world mutation
```

Requirements:

- O(1)/indexed Claim/Parcel authorization lookup on Paper hot paths;
- no synchronous DB query per protection event;
- bounded activity observation queues;
- bounded Building registration volume;
- bounded mutation batches;
- no forced-loaded decay/repair chunks;
- adaptive background work budget may reduce work under server load.

---

# 26. Security and authorization

Every use case revalidates authority at commit, not only when UI/command starts.

Security revisions/ownership revisions protect stale proposals/jobs.

Admins:

- explicit bypass, never implicit accidental power;
- audited governance overrides;
- admin inspection is not wallet confiscation permission.

Do not trust item ownership as authority. Tool items only identify UI mode; actor permissions remain server state.

---

# 27. Testing architecture

Test layers:

## 27.1 Pure domain tests

Fast JUnit tests for:

- topology;
- Parcel connectivity;
- authorization;
- price math;
- certification state machine;
- Activity decay;
- Building baseline transitions;
- condition/Era rules.

## 27.2 Persistence integration tests

Use temporary SQLite files.

Each test fixture:

```text
empty temp DB
→ Liquibase migrate
→ jOOQ repositories
→ use case
→ close/reopen
→ verify durable result
```

Also test migration from the frozen legacy bridge fixture.

## 27.3 Architecture tests

ArchUnit verifies:

- Core does not depend on gameplay;
- no cross-domain persistence access;
- Paper adapter classes do not appear in domain packages;
- jOOQ generated/persistence types do not leak into public domain APIs.

## 27.4 Paper integration / scenario tests

Use a real Paper test server or controlled integration harness for behaviors that depend on actual Paper events/chunks/world saving. Do not make a mock library authoritative for world-mutation correctness.

Required MVP scenarios include restart during queued Decay/Repair and protection behavior across Parcel/Settlement boundaries.

---

# 28. Current implementation snapshot

Already implemented baseline from the previous architecture:

- modular one-JAR runtime;
- world identity + SQLite execution;
- persisted GameClock;
- Owner/Governor/Citizen/Public + BANNED;
- Claims, Parcel geometry/policies/Holders/proposals;
- command/UI tools and committed protection snapshots;
- aggregate Activity;
- history/outbox infrastructure;
- integer wallet/treasury ledger;
- budgeted starting funds;
- immutable generated-land capture/inspection.

Not yet accepted as frozen MVP complete:

- Liquibase/jOOQ persistence modernization;
- actual paid Claim debit/subsidy flow;
- frozen certification workflow;
- admin-approved acquisition fee transfer;
- Local Activity Buckets;
- explicit Building registration/baseline;
- Decay;
- Repair;
- frozen History/Era completion rules;
- full MVP acceptance suite.

---

# 29. MVP boundary

MVP includes:

```text
Runtime / World identity / GameClock
Settlement / Claim / Parcel / Protection
Currency / Treasury / LandValue / Paid Claims
Certification / Ownership transfer
Aggregate + Local Activity
Registered Building / Baseline / Geometry
Decay
Repair
History / Era
```

MVP explicitly excludes:

```text
automatic Claim release / Dynamic Settlement Cycle
Server Trade
Facilities
freight
public teleport
seasons
Cultural Standing
disasters / Epochs
chunk retirement/regeneration
historical snapshots/replay
player-ruin export into Resource worlds
graves / AI epitaphs
NPC cities
advanced production/workforce
formal debt/physical currency
automatic Building discovery
```

See `ARCHITECTURE_BOUNDARY.svg`.

---

# 30. First post-MVP system

**MVP+1: Dynamic Settlement Cycle** is mandatory next.

It consumes Local Activity data and may add Civic Capacity/maintenance demand if real design still needs them.

Target behavior:

```text
local support
+ demand/capacity
+ topology
+ Holder/property guarantees
→ peripheral decline
→ boundary-only gradual release
→ reclaim/history inheritance
```

Desired emergent result: unused pre-claimed outskirts disappear first; active new districts can survive while old districts decline; a city may gradually shift geographically.

MVP+1 must be frozen separately after MVP v1.0 acceptance.

## 30.1 Accepted MVP+1 boundary — Holder consent (2026-09-14)

Automatic release must preserve the existing consent rights of assigned Parcel Holders. Low Activity, long absence, a notice or an expired grace period does not constitute consent.

- An assigned Parcel cannot automatically lose protection. Its revocation, Holder removal or boundary change continues to require governance and all applicable current/new Holders under the existing consent workflow.
- A Claim intersecting such a Parcel cannot be automatically released while that protection remains. Automatic parent unclaim or Settlement termination must not indirectly bypass the Parcel's rights.
- An offline-Holder dispute requires explicit, audited administrator handling through the existing override path; a scheduled timeout is not an administrative resolution.
- Such protected land may indefinitely constrain boundary retreat. Existing Settlement contiguity requirements remain; the system must not sever the Settlement to work around a protected Parcel.

This is a partial post-MVP design decision, not an implementation authorization or a change to the frozen MVP behavior. Registered-Building candidacy is specified in §30.2, land-local container disposition in §30.3 and unassigned Parcels in §30.4. Remaining property cases, release timing, final-Claim rules and whether Civic Capacity is needed remain unresolved.

## 30.2 Accepted MVP+1 boundary — whole registered ruins (2026-09-15)

A registered Building may enter the automatic-release candidate process only as a whole RUINED Building, rather than being permanently exempt solely because it is registered.

- RUINED is the existing damage condition, not the ACTIVE/RETIRED registration lifecycle. It is necessary but not sufficient for release; remaining eligibility and unresolved property cases still have to be frozen.
- A proposed release touching a registered Building must include all Claims intersecting its registered footprint in one safe whole-Building release group. It must not release that Building piecemeal by chunk. Every registered Building touched by that group must satisfy the same whole-RUINED requirement.
- The group must satisfy boundary-only release and Settlement topology safeguards, without internal holes or disconnection. If a safe whole group cannot be formed, the Building remains protected.
- Holder consent under §30.1 remains mandatory. Ruin, including a Building overlapping an assigned Parcel, cannot override that boundary.
- Release must not automatically demolish the physical structure or erase its history. RUINED condition, group completeness, topology and rights must be revalidated before any eventual release commits.

This approves candidate eligibility, not immediate release or an implementation. Land-local container contents follow §30.3 and unassigned Parcels follow §30.4; other property cases, the registration-lifecycle transition and pending-job/material reconciliation still need explicit rules. No new repair, restored former permissions or Era benefit follows merely from release/reclaim.

## 30.3 Accepted MVP+1 boundary — containers remain in place (2026-09-15)

Before eligible land or whole ruins lose protection, provide advance notice and a relocation grace period. Land-local containers and their contents remain physically in place; nonempty contents alone do not veto release.

- Release does not automatically move, delete, recreate or convert the containers or their contents into dropped items. It does not create an automatic storage/escrow service for abandoned contents.
- Existing protection remains until the actual authorized release commits, not merely when land becomes a candidate or notice begins. After release, access follows ordinary unclaimed-land rules; after reclaim, it follows the new Settlement/Parcel permissions.
- The accepted consequence is that items not removed during the grace period may be taken by others. Previous ownership does not retain a separate container-access lock solely because those items were left behind.
- This applies only to containers left on the released land. Player inventories and personal Ender Chest contents are unaffected. It does not authorize spending a Settlement Treasury or refunding/unreserving pending Repair materials.
- Assigned Holder consent under §30.1 and whole-Building safeguards under §30.2 still apply. Notice or nonempty-container policy cannot waive those constraints.

Notice delivery, grace duration/revalidation, cross-boundary storage such as double chests, other property/entity cases and pending-job accounting remain explicit design gates. This decision neither authorizes exposing a still-protected neighboring region nor implements automatic release.

## 30.4 Accepted MVP+1 boundary — whole unassigned Parcels (2026-09-15)

An unassigned Parcel may automatically end its permission subdivision as part of an otherwise eligible land-release group, without a separate manual deletion by governance. Its history must be retained.

- Every cell of the Parcel must lie within the same safe release group. Do not split, truncate or partially release the Parcel; if the whole Parcel cannot be included safely, it remains protected.
- The Parcel must still have zero Holders at commit. Candidate selection or notice does not freeze the Holder set or authorize ignoring later assignments. If a Holder is added, the release must not proceed on the old unassigned assumption; §30.1 consent applies again.
- End the subdivision only with the actual coordinated parent land release, not at candidate selection or notice. Do not erase the historical record or leave a live Parcel outside its Settlement's Claims.
- This does not waive low-support/notice requirements, boundary/topology constraints, whole-Building safeguards or container disposition rules. Empty Holder membership is not proof that the land is otherwise releasable.

This is a post-MVP lifecycle decision, not a change to ordinary MVP unclaim or an implementation. Commit/proposal reconciliation details, remaining property and job accounting, release timing and final-Claim behavior still need design.

---

# Appendix A — Persistence coding standard

Allowed repository example:

```java
return ctx.select(SETTLEMENTS.SETTLEMENT_ID, SETTLEMENTS.OWNER_UUID)
          .from(SETTLEMENTS)
          .where(SETTLEMENTS.SETTLEMENT_ID.eq(id.value()))
          .fetchOptional(mapper);
```

Forbidden application persistence:

```java
connection.prepareStatement("SELECT ...");
statement.execute("UPDATE ...");
ctx.fetch("SELECT ...");
```

String SQL is not accepted as "using jOOQ".

Repository methods should be intention-revealing domain operations, not generic CRUD bags.

---

# Appendix B — Technology verification references

Verified on 2026-09-12:

- Paper project setup: https://docs.papermc.io/paper/dev/project-setup/
- Gradle releases: https://gradle.org/releases/
- Java SE support roadmap: https://www.oracle.com/java/technologies/java-se-support-roadmap.html
- Eclipse Temurin releases/support: https://adoptium.net/
- jOOQ release notes/download: https://www.jooq.org/notes and https://www.jooq.org/download/versions
- jOOQ Gradle code generation: https://www.jooq.org/doc/latest/manual/code-generation/codegen-execution/codegen-gradle/
- Liquibase Community download: https://www.liquibase.com/download-community
- Liquibase SQLite docs: https://docs.liquibase.com/
- SQLite JDBC releases: https://github.com/xerial/sqlite-jdbc/releases
- JUnit framework releases: https://github.com/junit-team/junit-framework/releases

---

# Appendix C — Guiding distinction

```text
Traditional SMP
Player → World

ESMP
Player ↔ World
```

Players alter what later players inherit; accumulated world history alters what later players can build, repair, reclaim, and remember.
