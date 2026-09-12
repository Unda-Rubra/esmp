# Evolve: SMP — MVP v1.0 Requirements Freeze

> Status: **FROZEN**
>
> Freeze date: 2026-09-12
>
> Purpose: This document defines the implementation boundary for the first complete ESMP core MVP.  
> Local development agents MUST treat this file as a scope contract. New ideas belong to Post-MVP unless they fix a correctness, security, data-loss, migration, or acceptance-test defect in a frozen feature.

---

## 1. What “MVP v1.0” means

MVP v1.0 is the first complete **core vertical slice**, not the final production feature set.

It must prove this chain end to end:

```text
World / persistence
→ Settlement / Parcel / protection
→ currency + land acquisition
→ certification
→ meaningful Activity
→ spatial local Activity support
→ registered Building identity + baseline
→ Decay
→ Repair
→ History / Era
```

MVP v1.0 ends when this chain is implementable, restart-safe, testable, and playable.

The following are explicitly **not required to complete MVP v1.0**:

```text
automatic Claim release / Dynamic Settlement Cycle
Server Trade
Facility recognition/production
automatic Resource Realm reset orchestration
freight
public Settlement teleport
gameplay seasons
Cultural Standing
disasters / Epochs / chunk retirement
historical snapshots / replay
player ruins exported into Resource worlds
graves / memorials
NPC cities
advanced production / workforce / debt
```

Dynamic Settlement Cycle remains **MVP+1 and mandatory next**, but must not be implemented before MVP v1.0 acceptance.

---

## 2. Freeze policy

After this freeze:

- New gameplay ideas do not enter MVP v1.0.
- Balance coefficients may change through configuration without reopening architecture.
- Bug fixes, exploit fixes, migration fixes, data-loss prevention and performance safeguards are allowed.
- A frozen invariant may change only through an explicit architecture-change decision that names the affected acceptance tests.
- Local agents must not “helpfully” implement deferred systems while working on prerequisites.
- Do not create empty tables/modules/events for deferred features.

---

## 3. Frozen MVP systems

### 3.1 Runtime, world identity, persistence, GameClock

Keep the current architecture and implementation.

Required invariants:

- one modular Paper plugin lifecycle;
- infrastructure-only Core;
- SQLite with bounded writes and versioned migrations;
- `world_instance_id` on coordinate-sensitive application state;
- no arbitrary async world mutation;
- persisted GameClock drives gameplay time;
- downtime gives no catch-up;
- `advance_time=false` freezes time-driven progression;
- fatal protection/persistence startup failure fails closed.

No new work beyond defects and dependencies needed by later MVP phases.

---

### 3.2 Settlement, Claims, Parcel, protection

Keep the current target model:

```text
OWNER
GOVERNOR
CITIZEN
PUBLIC
BANNED negative override
HOLDER independent Parcel relation
```

Claims:

- full-height 16×16 chunks;
- four-directionally contiguous;
- exclusive per world instance;
- no automatic Settlement merging;
- ordinary unclaim may not split a Settlement;
- commands remain the complete canonical interface;
- Management Book / Parcel Wand remain convenience interfaces only.

Parcel remains:

- 8×8×8 aligned cells;
- non-overlapping;
- six-connected;
- BUILD / INTERACT / CONTAINER / AUTOMATION domains;
- assigned Parcel changes require governance + all current/new Holders;
- administrator dispute override remains audited.

No extra civic roles are added in MVP.

---

### 3.3 Currency and paid land acquisition

Keep one integer virtual ledger currency with Player Wallet and Settlement Treasury.

Frozen payer/assessment semantics:

```text
Found new Settlement:
  payer            = founder Player Wallet
  assessed player  = founder
  resulting owner  = founder

Expand existing Settlement:
  payer            = Settlement Treasury
  assessed player  = current Settlement Owner
  Governor may request expansion only through authorized treasury spending
```

This prevents Governor/alternate-account portfolio bypass.

Price structure remains:

```text
Expansion:
B_terrain × P_portfolio × S_settlement × A_activity × D_early

Founding:
B_terrain × P_portfolio × N_proximity × D_founding
```

Frozen rules:

- first-ever founding Claim costs exactly 25;
- later early-land discounts are personal and never reset/transfer;
- LandValueProfile is immutable/versioned;
- a paid Claim with missing required LandValueProfile is rejected;
- no passive land tax;
- no inactivity fee;
- no generic peer-transfer tax;
- no debt / negative balance;
- quote and commit revalidate the same price context;
- monetary debit + subsidy consumption + Claim/history state commit atomically in the application database transaction.

Multiplier values, tier boundaries and rounding scale are configuration/balance, not architecture. Use deterministic integer/fixed-point arithmetic; never floating-point account values.

MVP does **not** require a repeatable automatic money faucet. Starting funds plus administrator/public test grants are sufficient for the core MVP. Server Trade remains post-MVP.

---

### 3.4 Settlement certification and naming

Certification remains exactly:

```text
UNVERIFIED
VERIFIED
```

No Town/City ladder.

MVP eligibility is deliberately simple and configurable. Default gate:

```text
Settlement age >= 3 game days
AND current Settlement Activity is not INACTIVE
AND lifetime credited meaningful Activity >= 20
```

Eligibility only permits application. It does not auto-verify.

Frozen flow:

```text
UNVERIFIED
→ eligible
→ governance submits application, optionally with proposed official name
→ administrator approves / denies
→ VERIFIED on approval
→ approved official name may be assigned
```

Rules:

- admin review is authoritative;
- denial does not remove Claims or protection;
- no automatic certification revocation in MVP;
- official rename remains admin-mediated and historical;
- naming/rename fee may be configured as a sink;
- no aesthetic score is computed by the plugin;
- exploit-oriented but technically active Settlements may be denied manually.

Website/map publication integration itself is outside plugin MVP; the plugin only needs durable status/name/history data and a clean read/export boundary.

---

### 3.5 Ownership transfer

Target MVP flow is frozen as:

```text
Owner proposes
→ recipient accepts
→ acquisition fee quote
→ administrator approves
→ fee + ownership transition commit atomically
```

Rules:

- recipient pays from personal Wallet;
- treasury remains attached to the Settlement;
- former Owner becomes Citizen by default;
- private compensation is outside the system;
- no automatic Building/labor/cultural appraisal;
- no peer-receiving-capacity system.

Acquisition fee structure remains:

```text
Fee = k × Σ BaseLandValue(claimed chunks)
```

`k` and integer rounding are configurable balance values. If required valuation data is missing, automatic transfer is rejected rather than inventing a value; administrator migration/override is a separate operation.

---

### 3.6 Activity: Settlement aggregate + spatial local support

This is the final MVP Activity model.

Settlement Activity remains the aggregate signal used for:

- certification eligibility;
- player-facing Settlement status;
- global Activity history/debugging.

MVP additionally introduces **Local Activity Buckets** at Claim-chunk granularity.

Conceptual state:

```text
LocalActivityBucket
├── settlement_id
├── world_instance_id
├── chunk_x
├── chunk_z
├── score_at_anchor
└── anchor_game_tick
```

Rules:

- meaningful authorized actions credit both the Settlement aggregate and the action's local bucket;
- local buckets use lazy time decay;
- default half-life remains seven game days;
- automation, idle presence, walking and login grant no credit;
- role/category caps remain;
- PUBLIC/HOLDER credit stays small and bounded;
- local support may include a small configurable contribution from neighboring buckets so players are not forced to “check in” every chunk;
- `ActivityRegion` is **derived**, not persisted or player-managed;
- derived Regions are four-connected components of locally supported claimed chunks;
- no `/activityregion create`, naming, ownership or administrative district system exists.

For Building decay eligibility, use the area-weighted local support of the Building footprint rather than only the Settlement-wide score.

MVP does **not** automatically release Claims from local inactivity.

This data exists so MVP+1 can later implement peripheral decline without redesigning Activity storage.

---

### 3.7 Building identity and discovery — MVP razor cut

Fully automatic city-wide Building discovery is **not an MVP requirement**.

MVP Building creation is explicit and bounded:

```text
authorized Owner/Governor
→ selects a bounded 3D building region
→ requests Building registration
→ system validates region
→ localized scan establishes geometry + baseline
→ stable building_id is created
```

Requirements:

- region lies wholly inside one Settlement;
- every touched chunk must be naturally loaded for registration;
- scan volume has a configurable hard upper bound;
- very large structures may be split into multiple Buildings;
- existing pre-plugin/pre-claim structures can therefore still be registered;
- no full-Settlement or full-world scan is required;
- automatic discovery is a later enhancement.

MVP geometry stores/derives enough for:

```text
occupied baseline blocks
exterior surface
interior air volume
openings
simple structural/support relations
```

Baseline rules:

- registration establishes baseline revision 1;
- authorized player edits update affected active baseline portions;
- ESMP Decay/Repair does not rewrite intended design;
- player demolition is a baseline change, not damage;
- obvious local edits keep the same building_id;
- complex reinterpretation may retire the old Building and register a new one;
- perfect split/merge lineage is not required;
- predecessor/history links are best effort.

Block entities inside ordinary Building baseline are excluded from automatic Decay/Repair mutation in MVP.

---

### 3.8 Decay — minimal frozen scope

Decay applies only to registered Buildings.

Eligibility requires:

```text
Building local Activity support below inactive threshold
for configured grace duration
AND Building not already protected by a current conflicting condition
```

Recovery above the configured recovery threshold stops future ordinary decay but does not undo damage.

MVP effect vocabulary:

```text
WEATHER
DROP
CAVE_IN
```

`WEATHER`:

- only explicit whitelisted material substitutions;
- examples may include stone-brick cracked/mossy variants;
- no generic “wood becomes mud” rule;
- no natural-terrain mutation;
- no block-entity mutation.

`DROP`:

- bounded vertical structural failure;
- no collectible item drops;
- deterministic/retry-safe operation planning.

`CAVE_IN`:

- small bounded connected structural patch using the same vertical-failure primitive;
- no rigid-body physics;
- no horizontal sliding/rotation.

Explicitly excluded from MVP:

```text
TOPPLE
raids/invasions as decay
water accumulation
snow burial
vegetation simulation beyond explicit WEATHER substitutions
claim-wide random corruption
natural mountain/cave/tree erosion
```

All Decay jobs are durable, bounded, naturally-loaded-chunk only, expected-state checked and restart-reconcilable.

---

### 3.9 Repair — minimal frozen scope

Repair is whole-Building in player-facing semantics.

Each registered Building may have one plugin-recognized Repair Station/point.

Frozen process:

```text
inspect damage
→ compute required original materials
→ materials supplied to Repair Station
→ durable repair job
→ bounded GameClock-driven batches
→ restore only still-valid active-baseline damage
→ complete
```

Rules:

- repair restores baseline, never obsolete renovation;
- conflicts retire/replan only affected work;
- ordinary baseline block entities are not restored in MVP;
- Repair Station itself is protected from ordinary Building decay;
- physical item consumption and world mutation use a recoverable intent/apply/reconcile workflow;
- no instant free repair;
- no villager/artisan dependency;
- no “east wing only” player-facing repair scope in MVP.

Exact material mapping is normally one original block item per restored baseline block, with explicit exceptions only where Minecraft item/block identity requires a mapping table.

---

### 3.10 Building condition, History and Era

Minimal Building condition:

```text
INTACT
DAMAGED
RUINED
```

Exact percentages are configurable, but state changes must be deterministic from current active-baseline condition.

MVP History records only structured committed facts needed by the core loop:

```text
Settlement founded
Settlement certification approved/denied
official name / rename
Claim acquired/released explicitly
ownership transfer
Building registered / retired
Decay started
Building became RUINED
Repair started / completed
Era advanced
```

Do not persist raw chat as History.

Era is a lightweight Building counter:

```text
era = 0 initially
RUINED
→ later fully restored to INTACT through Repair
→ era += 1 exactly once for that ruin/restoration cycle
```

No Era abilities, rankings, loot multipliers or special RPG progression exist in MVP.

---

## 4. Explicitly frozen out of MVP

The following may be designed later but MUST NOT appear in MVP implementation work:

- automatic Claim release;
- Civic Capacity / maintenance-demand ownership loss;
- player-managed districts;
- high-Activity prosperity buffs;
- Server Trade;
- Facility production;
- freight;
- public teleport;
- seasons;
- Cultural Standing;
- disaster systems;
- settlement warfare systems;
- chunk deletion/regeneration;
- Anthropogenic Landform simulation;
- player ruins exported into Resource Realm generation;
- history screenshots/replay;
- permanent chat journals;
- graves/AI epitaphs;
- NPC cities;
- automatic Building discovery across Settlements;
- advanced workforce/villagers;
- formal debt/loans;
- physical currency;
- complex market orders.

---

## 5. Resource Realm in MVP

MVP keeps Resource world identity and the rule that Resource worlds are unclaimable.

MVP does not require automatic reset orchestration.

A documented administrator maintenance procedure/manual reset is acceptable for the first release of the core MVP. Automated prepare/pregenerate/freeze/evacuate/cutover/archive logic remains post-MVP.

---

## 6. MVP acceptance boundary

MVP v1.0 is complete only when all frozen systems above pass:

- clean-install migration/startup;
- restart persistence;
- permission/protection tests;
- monetary idempotency/insufficient-funds tests;
- paid Claim quote/commit/subsidy tests;
- certification and transfer state-machine tests;
- local Activity decay/recovery tests;
- Building registration/baseline-edit tests;
- Decay expected-state/conflict/restart tests;
- Repair material/conflict/restart tests;
- History/Era exactly-once tests;
- no synchronous SQL in hot Paper event paths;
- no full-world/full-Settlement recurring block scans;
- no forced chunk loading for Decay/Repair.

Only after these pass does development move to **MVP+1 Dynamic Settlement Cycle**.

---

## 7. First post-MVP feature: Dynamic Settlement Cycle

The first feature after MVP is already selected and does not need to compete with other ideas.

It will consume the MVP Local Activity Buckets / derived Activity Regions to determine spatial support.

Target direction:

```text
local Activity support
+ Settlement-wide capacity/maintenance constraints if still needed
+ Building/infrastructure demand
+ topology / Parcel-holder guarantees
→ peripheral decline
→ gradual boundary Claim release
→ history-preserving reclaim
```

The expected emergent behavior includes:

- unused pre-claimed peripheral land returning first;
- active new districts surviving while inactive old districts decline;
- large cities having multiple active areas;
- cities gradually shifting geographically over time;
- no internal “Swiss-cheese” release;
- assigned Parcel/Holder rights cannot be bypassed by automatic release.

The exact release algorithm is intentionally **not part of MVP v1.0** and will be frozen separately after MVP acceptance.
