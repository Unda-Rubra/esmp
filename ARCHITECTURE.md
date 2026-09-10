# Evolve: SMP — Architecture

> Status: Architecture baseline; implementation gates remain explicit  
> Project: **Evolve: SMP (ESMP)**  
> Platform direction: **Minecraft Java Edition + Paper plugin architecture**  
> Scope: This document records the architecture and gameplay design that has been settled so far.  
> It intentionally does **not** define the detailed Decay Engine algorithms yet.

## Current implementation

The runnable plugin consists of four Gradle modules and one deployable Paper JAR under `xdu.undarubra.esmp`.

- `esmp-core` owns world identity, bounded SQLite execution and the neutral persisted `GameClock`. Core world schema remains 1; Civilization schema is 3 and configuration version is 4.
- `esmp-civilization` implements Owner/Governor/Citizen/Public, local bans, stable Settlement numbers, administrator naming certification, numeric claim pricing, Parcel geometry/policies/Holders, durable mutual-consent proposals, history/outbox and Parcel-aware Activity.
- `esmp-plugin` supplies complete Settlement/Parcel commands, committed main-thread snapshots, four-domain protection, replaceable Management Book/Parcel Wand tools and bounded temporary particle previews. `/esmp status` reports readiness and game time. Prices are numeric-only: no currency, wallet balance or items are deducted.
- Configured Main World instances are claimable; Resource worlds remain unclaimable. Removed definitions remain historical only. Existing claims load before gameplay is admitted; persistence failure stops the server rather than leaving claims unprotected.
- Building discovery, decay/repair, Resource resets, Facilities/economy/transport/culture and seasonal presentation remain disabled. Automatic inactivity-driven Settlement release is intentionally outside the MVP and belongs to the required post-MVP dynamic-cycle phase. Shared game time and Activity are implemented; their eligibility output does not itself mutate buildings or produce goods.

The Factory Realm is removed from the current scope following the team's RPG-survival balance and performance review. It is not a scheduled milestone or dormant runtime module. This cut removes the dedicated realm and industrial systems; it does not add a redstone ban to the remaining worlds. Reintroduction requires a new explicit decision.

Main World Facilities are accepted as bounded Settlement production, not a restoration of the Factory Realm, Plot aggregate, or removed industrial module. For the MVP, Settlements remain persistent; the longer Explore → Settle → Build → Produce → Trade → Develop → Prosper → Decline → Ruin → Reclaim cycle belongs to the required post-MVP dynamic-Settlement phase.

Legacy configuration migration retains the version-1 Factory retirement/backup and then upgrades to config version 3, backing up to `config-before-game-clock.yml` and converting former offer `*-seconds` values to `*-ticks` at 20 ticks per former second. Civilization migration converts existing absolute deadlines once from their remaining cutover lifetime; expired offers remain expired. It preserves IDs, claims, memberships and history. No physical worlds are deleted.

The Settlement/Parcel migration backs up configuration to `config-before-parcels.yml`, maps legacy Manager/Member to Governor/Citizen, preserves existing UUID keys/offers/history, assigns stable public numbers, and grandfathers existing names as certified official names. It renames equivalent persisted Activity policy vocabulary without resetting scores or caps; arbitrary policy-value changes still require explicit migration.

---

# 1. Project Overview

**Evolve: SMP (ESMP)** is a long-running Minecraft SMP gameplay model in which a persistent civilization environment evolves together with player settlements over time.

The core idea is not:

> Reset the world → start a new season → repeat.

Instead:

> Players build settlements → settlements become active → settlements may decline → abandoned construction becomes ruins → land is released → new players build on top of the old world.

The world is therefore persistent and historical. Player settlements leave traces even after their original owners and members are gone.

### 1.1 Core design philosophy

> **World provides space. Claims provide ownership. Activity provides life. Decay provides change. Repair provides continuity. History provides meaning.**

Chinese interpretation:

> **世界提供空间，领地提供所有权，活跃度提供生命，侵蚀提供变化，修复提供延续，历史赋予意义。**

ESMP is intended to be a long-term SMP model rather than a conventional “SMP with a decay plugin”.

---

# 2. Design Goals

## 2.1 Primary goals

- Long-term persistent civilization world.
- Player-created settlements and infrastructure.
- Settlement ownership at Chunk granularity.
- Settlement activity represented by more than simple login recency.
- Abandoned buildings gradually become ruins instead of simply disappearing.
- Ruined settlement land can eventually be released.
- New settlements can reclaim old land and inherit its history.
- Buildings can be repaired before complete ruin.
- Repairs contribute to a building's historical Era.
- Historical information is preserved independently from current ownership.
- Resource supply is separated from the civilization world.
- Vanilla-client-first experience.
- Server-side implementation through Paper plugins.
- High performance and long-term maintainability.
- Minecraft world data and ESMP application data remain separate.
- Architecture should support future Minecraft version upgrades.

## 2.2 Non-goals

The first implementation should **not** attempt to:

- simulate every block in the entire world continuously;
- perform full-world periodic block scans;
- reproduce Fabric/Forge client-side mod functionality;
- create a large RPG-style custom GUI system;
- implement unlimited offline chunk loading;
- define detailed decay probabilities before the architecture is stable.

---

# 3. Platform Strategy

## 3.1 Paper-first

The project is implemented as a **single modular Paper server plugin**, distributed as one JAR.

The accepted initial development baseline is:

| Component | Selection |
|---|---|
| Minecraft Java Edition / Paper target | 26.2 |
| Plugin source language | Java |
| Development and server runtime | JDK 25 |
| Build system and configuration | Gradle with Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`) |

Kotlin DSL is used for build configuration, not plugin implementation. Plugin code uses the Java 25 toolchain and `xdu.undarubra.esmp` package prefix. The foundation pins Gradle Wrapper 9.7.1, Paper server 26.2 build 121, Paper API `26.2.build.121-stable`, SQLite JDBC `3.53.4.0`, and JUnit Jupiter `5.12.2`. Dependency lockfiles are committed. Runtime verification used Temurin 25.0.4.1+1; other JDK 25 distributions must be validated by operators.

Official compatibility evidence checked on 2026-09-09: [Paper 26.2 metadata](https://fill.papermc.io/v3/projects/paper/versions/26.2) marks the version supported with Java 25 minimum; [Paper's project setup guide](https://docs.papermc.io/paper/dev/project-setup/) demonstrates Java source with Gradle Kotlin DSL. A development target is not production validation: ESMP still requires actual server scenarios before deployment.

The target player experience is:

- vanilla Minecraft Java client;
- no mandatory Fabric/Forge/NeoForge client mod;
- server-side mechanics wherever possible.

The required systems—claims, activity, decay, repair, history, world management, UI, and persistence—are all intended to be implemented server-side.

## 3.2 Why not a client/server mod architecture?

A client mod would create unnecessary deployment and compatibility costs for an SMP whose core mechanics do not fundamentally require client-side changes.

The preferred model is:

```text
Vanilla Minecraft Client
        │
        ▼
      Paper
        │
        ▼
    ESMP Plugin
```

## 3.3 Folia

Paper is the initial target.

Folia may be evaluated later if server scale becomes large enough to justify its regionized execution model.

Folia is **not** considered a drop-in replacement for Paper from an architecture perspective. Plugin scheduling, thread/region boundaries, and compatibility must therefore be treated as a later engineering concern.

---

# 4. High-Level Plugin Architecture

ESMP deploys as one Paper plugin JAR with one bootstrap and coordinated lifecycle. The following are internal modules, not independently installed plugins:

```text
Evolve: SMP
│
├── esmp-plugin
│   └── Paper Bootstrap / Configuration / Command Adapter
│
├── esmp-core
│   ├── Player Identity
│   ├── World Abstraction
│   ├── Database
│   ├── Scheduler
│   ├── Event Bus
│   ├── Configuration
│   ├── Localization
│   └── Public API
│
├── esmp-civilization
│   ├── Settlement / Claim
│   ├── Team / Protection
│   ├── Activity
│   ├── Decay
│   ├── Repair
│   ├── Villager / Artisan
│   └── History / Era
│
└── esmp-resource
    └── Renewable Resource Worlds / Cycles
```

The bootstrap composes Core and gameplay modules and coordinates database migrations, startup, and shutdown. Module boundaries remain explicit application APIs and committed domain events; sharing one artifact does not permit cross-domain business-state access. Independent plugin deployment, classloader boundaries, and cross-plugin version compatibility are not part of the initial design.

The accepted extension adds logical Economy, Transport, and Calendar/Seasons services alongside Civilization gameplay; §16A.7 assigns state ownership. These are architectural responsibilities, not newly implemented Gradle modules. Keep APIs and committed facts between them; choose package/module extraction when the corresponding feature is implemented, without adding empty modules. Core must not own currency issuance, Facility recipes, progression, route pricing, or season policy.

## 4.1 Module responsibility

### `esmp-core`

Infrastructure only.

Core should provide:

- player identity abstraction;
- world abstraction;
- persistence/database infrastructure;
- scheduling abstractions;
- internal event bus;
- configuration;
- localization;
- shared APIs.

Core should **not** understand gameplay concepts such as:

- ruins;
- eras;
- resource cycles;
- settlement activity.

The principle is:

> **Core provides infrastructure; gameplay modules provide meaning.**

### `esmp-civilization`

Owns the Main World's persistent civilization gameplay domain. In this architecture, **Civilization** names the overall environment and gameplay domain; it is not a player-owned aggregate or database entity.

The player-owned aggregate is a **Settlement**:

```text
Settlement Claim
  ↓
Activity
  ↓
Decay
  ↓
Ruin
  ↓
Release
  ↓
Reclaim
```

The module also owns:

- settlement ownership, membership, and roles;
- claim protection and visualization;
- building candidates;
- repair;
- villagers/artisans;
- eras;
- historical records.
- economic-stage and cultural capabilities on existing Settlements;
- Main World Facility qualification and certification, shared production-capacity allocation, and Activity eligibility exposed to Economy.

### `esmp-resource`

Owns:

- resource worlds;
- resource cycles;
- reset lifecycle;
- resource-world generation;
- controlled reopening of resource dimensions.

---

# 5. World Architecture

Worlds are treated as a first-class concept from the beginning.

The system must never assume that the server has only one Minecraft world.

## 5.1 Top-level realm model

```text
ESMP
│
├── Civilization Realm
│   └── Main World
│
└── Resource Realm
    ├── Resource Overworld
    ├── Resource Nether
    └── Resource End
```

## 5.2 World abstraction

The model distinguishes a stable logical world role from a physical Minecraft world instance:

```text
WorldDefinition
├── world_definition_id
├── realm_type
├── dimension_role
└── configuration

WorldInstance
├── world_instance_id
├── world_definition_id
├── bukkit_world_uuid
├── lifecycle
├── resource_cycle_id (optional)
└── created_at
```

The stable definitions are `MAIN`, `RESOURCE_OVERWORLD`, `RESOURCE_NETHER`, and `RESOURCE_END`. Resetting a Resource world creates a new physical `WorldInstance` rather than reusing the old instance identity.

Gameplay modules own their policies for each world definition:

```text
MAIN
├── Settlement: enabled
├── Activity: enabled
├── Decay: enabled
├── History: enabled
└── Reset: never

RESOURCE_*
├── Settlement: disabled
├── Activity: disabled
├── Decay: disabled
└── Reset: rotating
```

Core identifies definitions and instances but does not own settlement, decay, or resource policy semantics.

Block- and coordinate-sensitive records must use `world_instance_id`. This prevents stale records from one Resource Cycle from targeting a later cycle that reuses the same configured world name.

## 5.3 Cross-realm player state and item transfer

ESMP intentionally allows players to carry items and normal player state freely between the Main and Resource realms.

The initial policy is:

- one shared player inventory across realms, including hotbar, armor, and offhand;
- one shared Ender Chest across realms;
- experience and normal player state are preserved during realm travel;
- ESMP does not filter, clear, copy, or maintain per-realm inventories;
- items gathered or produced in one realm may be used in another realm.

This policy is not a workaround for a Paper limitation. Paper normally retains a player's inventory and Ender Chest across worlds on the same server. The policy exists to make the intended economy boundary explicit and to prevent ESMP or third-party multi-inventory configuration from silently changing it.

Portal routing, travel permissions, and Resource reset evacuation remain world-lifecycle concerns. Evacuation must preserve the player's shared inventory and Ender Chest state.

---

# 6. Civilization World

The Main World is the permanent civilization and history environment. **Civilization** refers to this overall gameplay domain, not to an ownable team or persisted entity.

Properties:

- persistent;
- never globally reset;
- supports settlements and protected claims;
- supports settlement teams;
- supports activity;
- supports decay;
- supports repair;
- supports eras;
- supports history.

### 6.1 Settlement lifecycle

```text
Player creates a settlement
     │
     ▼
Settlement becomes active
     │
     ▼
Meaningful player activity decreases
     │
     ▼
Buildings begin to decay
     │
     ▼
Settlement becomes ruined
     │
     ▼
Settlement land is released
     │
     ▼
New player claims released land
     │
     ▼
New settlement develops on inherited ruins
```

The world itself does not need to be reset for this lifecycle to occur.

---

# 7. Settlement / Claim Architecture

## 7.1 Terminology and aggregate boundary

**Settlement** is the Main World aggregate for player ownership, membership, permissions, activity, and claimed land. A settlement is a collection of claimed chunks with one stable identity.

**Claim** describes the protected ownership relationship and the actions that add or remove chunks from a settlement. It is not a separate civilization entity.

**Civilization** remains the name of the overall persistent gameplay domain. ESMP does not create a `Civilization` owner record.

## 7.2 Claim granularity

The smallest claim unit is a **Chunk (16×16)**.

This intentionally avoids arbitrary polygons and simplifies:

- storage;
- lookup;
- permission checks;
- chunk-based processing;
- decay scheduling;
- visualization.

A settlement may contain many claimed chunks. While owned, its chunks must form one four-directionally contiguous component: every chunk must connect through shared north, south, east, or west edges. Diagonal contact alone does not establish continuity. Claims do not cross physical world instances.

## 7.3 Settlement data model

Conceptual MVP model:

```text
Settlement
├── settlement_id
├── world_instance_id
├── generated_label
├── official_name (optional)
├── owner_uuid
├── created_at
└── status

SettlementMember
├── settlement_id
├── player_uuid
├── role = GOVERNOR | CITIZEN
├── joined_at
└── left_at (optional)

SettlementBan
├── settlement_id
├── player_uuid
├── banned_by_uuid
├── created_at
└── note (optional)

SettlementChunk
├── settlement_id
├── world_instance_id
├── chunk_x
└── chunk_z

Parcel
├── parcel_id
├── settlement_id
├── name
└── permission_policy

ParcelHolder
├── parcel_id
└── player_uuid

ParcelCell
├── parcel_id
├── voxel_x
├── voxel_y
└── voxel_z
```

The database must enforce exclusive Main World ownership with a unique constraint equivalent to:

```text
UNIQUE (world_instance_id, chunk_x, chunk_z)
```

`Settlement.owner_uuid` is the single source of truth for the Owner. `SettlementMember` stores only Governors and Citizens and must be unique by `(settlement_id, player_uuid)`. Public users are derived and have no membership row. `BANNED` is a settlement-local negative authorization override, not an ordinary membership role; a player cannot use a ban entry to bypass or replace membership invariants.

Activity, building condition, repair, Era, history, Facility state, and future dynamic-cycle state remain separate domain records rather than duplicated mutable fields on `Settlement`.

## 7.4 Ownership, governance, public access, and bans

A player can own multiple Settlements. A Settlement has one Owner, zero or more Governors, zero or more Citizens, and any number of Public users.

The effective governance identities are:

```text
OWNER
├── exactly one while the Settlement is owned
└── identified by Settlement.owner_uuid

GOVERNOR
├── zero or more
└── persisted as SettlementMember

CITIZEN
├── zero or more
└── persisted as SettlementMember

PUBLIC
├── every other non-banned player
└── derived; never persisted as membership
```

`BANNED` is separate from this positive-role hierarchy. An Owner or Governor may ban a player from one Settlement unless that player is a server administrator. A banned player may still enter, move through, leave, and receive necessary informational feedback, but all Settlement- and Parcel-governed interactions are denied. Server administrators bypass Settlement bans. Ban and unban operations are auditable governance actions.

The implemented administrator check uses online `esmp.admin` permission and offline operator status. Ban exemption does not itself grant ordinary world-edit access; the existing explicit `/settlement bypass on` controls administrator protection bypass. Bans and Parcel changes refresh committed authorization snapshots and close newly unauthorized world inventories.

The MVP should not model resident, tenant, worker, merchant, or visitor as additional Settlement roles. A Public player may still use interactions explicitly allowed by the active Settlement or Parcel policy. Parcel ownership/holding is orthogonal to Settlement membership: a Public player can hold a Parcel without becoming a Citizen.

On successful ownership transfer, the former Owner becomes a Citizen by default unless a separately approved rule changes this behavior. Update `Settlement.owner_uuid`, remove any membership row for the new Owner, and persist the former Owner as `CITIZEN` atomically. No intermediate state may expose two Owners or an ownerless owned Settlement.

Ownership transfer, membership invitation, Governor promotion, removal, demotion, and voluntary leave retain the existing durable-offer and atomic-transition principles. Rename the old Manager semantics to Governor and Member semantics to Citizen; do not keep duplicate legacy role names in the new schema. Owners cannot leave without first transferring ownership or abandoning under the applicable rules.

Permissions are settlement-local. A governance role in one Settlement grants nothing in another Settlement owned by the same player.

## 7.5 Claim protection and authorization

Claim protection remains a required part of the Settlement system. All protected actions pass through one authorization boundary:

```text
authorize(actor, action, target, context) → allow | deny
```

Protection must account for direct and indirect world changes, including block placement/breaking, block and container interaction, entity or vehicle interaction, explosions, fire/fluid spread, pistons, hoppers, dispensing, system-initiated decay/repair, and explicit administrator overrides.

The MVP authorization resolution order is:

```text
administrator override
↓
Settlement BANNED override
↓
world / Settlement lookup
↓
Parcel lookup (if any)
↓
Parcel policy or fixed Settlement default policy
↓
actor relations: OWNER / GOVERNOR / CITIZEN / HOLDER / PUBLIC
↓
allow | deny
```

A Parcel policy overrides the ordinary Settlement default policy for positions inside that Parcel, but never grants Settlement governance powers. Actor relations are a set, not one highest role; for example a Citizen may also be a Holder. Permission succeeds when at least one applicable actor relation is allowed, unless an earlier negative override such as `BANNED` applies.

The MVP exposes four Parcel permission domains:

```text
BUILD       direct structural world modification
INTERACT    ordinary non-inventory interaction
CONTAINER   persistent world-inventory access
AUTOMATION  indirect mechanical transfer or mutation
```

The exact event catalogue remains an adapter concern. The Parcel model does not expose per-block ACLs in the MVP. If finer separation is needed, use another Parcel rather than configuring individual containers.

Unparcelled Settlement space uses one server-defined default policy in the MVP rather than a per-Settlement matrix. Public space should permit safe normal use while preventing strangers from altering structures, persistent inventories, or automation. Exact default action membership is a tunable policy, not a reason to add more roles.

Implemented defaults: unparcelled space grants normal team access and safe Public controls only. New Parcel policies allow OWNER/GOVERNOR/CITIZEN/HOLDER in all four domains and no PUBLIC relations until explicitly configured. Multiple relations combine by OR; ownership is not an implicit Parcel-policy bypass. Unattributed automation inside an assigned Parcel uses its HOLDER automation grant (OWNER for unassigned Parcels). Boundary-crossing unattributed automation requires PUBLIC AUTOMATION on both protected sides; one-sided permission is insufficient.

Cross-Parcel and cross-Settlement automation must not bypass protection. Operations such as hoppers and pistons that affect both a source and a destination must satisfy authorization for every protected side of the transfer or mutation.

### Implemented protection and publication boundary

The Paper adapter checks permissions from a server-thread spatial index, never synchronous SQL in an event handler. Accepted durable mutations publish replacement views only after their database transaction commits. The current event coverage remains useful, but its role checks must migrate from the legacy fixed Visitor/Member matrix to the Settlement/Parcel authorization model above.

Protection is guarded during startup. Fatal persistence/publication errors and hot unloading trigger a server stop rather than exposing an unprotected Main World. Use full restarts, not plugin reloads.

## 7.6 Claim operations, cost, naming, and lookup

Settlement claims remain full vertical chunk columns. The minimum claim unit is still one 16×16 chunk from world minimum height through world maximum height. The first chunk establishes the Settlement component; additions must share an edge with the existing component; removals that split the component are rejected; ordinary unclaim cannot remove the final chunk; separate Settlements may remain edge-adjacent without merging.

The current user-approved stage implements **numeric claim pricing only**, with no real currency or item consumption. The configured next-claim price is `claim-base-price + claim-price-step * current claimed chunks`, initially 100 + 25×area. Creation quotes the area-zero price; successful create/claim records the numeric price and `charged=false` in its committed history. `/settlement price [settlement]` exposes the quote. No mock balance or wallet is created and unclaim has no refund because nothing was charged. Real currency integration is deferred until the user chooses a system; its debit/recovery contract will be a later change. Detailed land markets, rent, taxes and geography-sensitive prices remain deferred.

The Settlement API must support complete command-driven administration for creation, claims, unclaims, inspection, membership/governance, bans, Parcel management, permission policy, naming administration, transfer, abandonment, and later lifecycle operations. Commands are the canonical complete interface; convenience tools call the same application services and must not implement separate business rules.

Initial Settlements do not require an official player-chosen name. They use a stable numeric identity and may display a generated geographical label. `official_name` remains null until the Settlement reaches a server-approved certification threshold and an administrator approves the name. Naming and every later rename are administrator-mediated operations and may charge Settlement currency. Naming and rename events are historical facts; previous names are not erased from history.

Implemented identity uses immutable existing UUID keys internally plus a stable global numeric ID accepted by commands; new names display as `S#<number>` until naming approval. `/settlement certify <settlement> true|false` records administrator-reviewed naming eligibility; `/settlement rename` requires that eligibility and administrator authority. It does not claim to implement automatic economic-stage certification.

## 7.7 Parcel model

A Parcel is a lightweight three-dimensional usage and permission subdivision inside exactly one Settlement. It does not create independent territorial sovereignty and it does not replace chunk claims.

Parcel geometry uses a world-aligned **8×8×8 block voxel** as its minimum unit. A Parcel is a set of these cells with the following MVP invariants:

```text
all cells lie inside the owning Settlement
cells do not overlap another Parcel
cells are six-directionally face-connected
Parcel names are unique within one Settlement
```

A Parcel can have zero, one, or multiple Holders. Holder status is a Parcel relationship rather than a Settlement role. A Holder may be a Citizen or a Public player.

A Parcel with no Holders is fully governable by the Settlement's Owner/Governors and is suitable for temporary construction zones, municipal buildings, warehouses, or other public subdivisions. Once one or more Holders exist, the governance side may not unilaterally revoke the Parcel, remove Holders, or reshape the assigned Parcel, and Holders may not unilaterally expand or reshape it. Revocation or boundary changes require agreement between the governance side and all current Holders. The server does not model price, rent, contract duration, renewal, debt, or private contractual terms in the MVP. Disputes or inaccessible Holders are handled by server administrators.

Implemented changes to assigned geometry, Holders, policies and names use durable proposals requiring governance and all current/new Holders. Proposer approval is recorded, other participants approve or reject explicitly, and proposals expire after 24000 game ticks. Parcel, ownership, claim and security revisions guard acceptance; changed authorization/geometry requires a fresh proposal. Server administrators can resolve directly with an audited override. Parent unclaim/abandonment cannot circumvent consent: affected Parcels must first be removed through this workflow.

The MVP Parcel permission policy controls only `BUILD`, `INTERACT`, `CONTAINER`, and `AUTOMATION` using allowed relation sets drawn from OWNER, GOVERNOR, CITIZEN, HOLDER, and PUBLIC.

Initial bounds are 4096 cells per Parcel/selection, 128 Parcels per Settlement, 32 Holders and 64 pending proposals per Settlement. These are safety limits, not a population/economy model.

## 7.8 Management tools and visualization

Every Settlement and Parcel operation must have complete command support. Special items are optional convenience frontends so ordinary players do not need to memorize the full command grammar.

The MVP uses two convenience tools:

```text
Management Book
→ Settlement status, claims, roles, bans, Parcel ownership and permission management

Parcel Wand
→ three-dimensional Parcel geometry selection and editing
```

Tools are server-issued, disposable, and replaceable. Players can request them through commands; issuance may enforce one item of each tool type in inventory plus a short cooldown to prevent litter. Tool identity is marked with plugin-owned persistent item data. Authority belongs to the player using the tool, not to the item; another player picking it up gains no permissions they do not already possess. Tool items should not retain valuable vanilla utility. If a normally useful/damageable item is chosen as a tool, its ordinary gameplay use should be disabled; a plain stick needs no artificial durability model.

The Parcel Wand is used with a named Name Tag in the off hand. Two selected aligned voxel positions define a cuboid; every currently unparcelled cell in that cuboid is proposed for addition to the named Parcel. If the name refers to an existing Parcel, the selection extends that Parcel. New cells must yield a valid six-connected result. Conflicts that would produce a disconnected result reject the whole operation rather than partially applying an unpredictable shape.

Parcel removal is also spatially constrained. A single cell can be removed only when it is on the Parcel surface and the remaining cells stay six-connected. A whole unassigned Parcel may be removed by governance in one operation; assigned Parcels follow the mutual-revocation rule above.

Selection state is transient session state, not item state. Geometry edits preview before commit and apply atomically. Temporary particles provide the primary spatial visualization: current voxel, proposed selection, existing Parcel surface, and conflicts should be visually distinct. ActionBar/chat feedback supplies coordinates, voxel counts, mode, costs, and validation messages. Settlement claim editing similarly uses temporary chunk-border particles and concise text feedback.

The issued items are a PDC-marked written book and plain stick. `/settlement tools book|stick` allows one of each in inventory and a 40-game-tick issuance cooldown. Book pages provide live information and command links; crafting/anvil/grindstone conversion of tools is blocked. Stick left/right clicks select aligned cells with a named off-hand Name Tag; command-based `parcel pos1/pos2` is equivalent. Add/remove previews require `parcel commit`, expire after 1200 game ticks and reject changed snapshots. Session selections carry no authority and are discarded on logout/restart. Current, selected, existing and conflicting cells use cyan, green, blue and red corner particles; the Parcel renderer caps each viewer at 256 particles per update and uses bounded nearby cell lookups without loading chunks.

## 7.9 Spatial infrastructure and domain state

Core may supply world/chunk/voxel keys and neutral infrastructure. Settlement owns claim authorization, governance, bans, Parcel semantics, Activity, and lifecycle semantics. Resource worlds remain unclaimed; do not introduce a universal gameplay aggregate controlled by `if world_type == ...` branches.

---

# 8. Activity System

Activity determines whether a **settlement** is alive and should eventually influence its buildings' decay.

Activity is settlement-local. Activity performed in one settlement does not automatically preserve another settlement, even when both have the same owner.

## 8.1 Activity is not login recency

The system must not use:

```text
last_login < threshold
```

as the complete definition of settlement activity.

Otherwise a player could log in briefly every few weeks and permanently prevent meaningful decay.

## 8.2 Activity score

Activity credits successful authorized building and meaningful interactions in the actual Settlement. The initial adapters cover block place/break, permitted physical inventory opening, and deliberate right-click use of safe public controls. Login, walking/pressure-plate passage, idle presence and automation grant no credit. Real Repair contributions can be added when Repair exists.

Owner, Governor and Citizen actions are primary. Meaningful authorized Holder/Public interactions may receive smaller bounded credit without granting additional permissions. The SQL service revalidates owned identity, world/chunk and ownership/chunk revisions, plus current governance/authorization context before credit; stale observations are discarded. The exact Public/Holder weights are part of the MVP policy migration and remain tunable.

The score naturally decays over elapsed shared game time (§16A.6), not wall time. Store a value and its game-clock anchor, then evaluate decay lazily when read or credited; inactivity must not require scheduled writes to every Settlement. With `advance_time=false`, no time-driven Activity loss occurs. Server downtime contributes no elapsed game time.

Conceptually:

```text
Activity Score
      ▲
      │       local meaningful builds / maintains / uses
      │                 │
      │                 ▼
      │             increases
      │
      │        elapsed-time decay
      │              ▼
      └──────────────────────────────► time
```

Implemented formula: `score(t) = score(anchor) * 0.5 ^ ((t-anchor)/(halfLifeDays*24000))`, maximum 100, initial 30, half-life seven game days, inactive below 20 and recovery at 30. Owner/Governor/Citizen BUILD credit is 1 and INTERACTION credit 0.25. Public actors, including non-Citizen Holders, receive 0.02 for authorized contributions and a daily cap of 0.25; holding a Parcel does not confer team credit. Per-player/category cooldown remains 200 game ticks, team player cap 5 and Settlement cap 20. Observations include exact block positions, action domain and security revision; SQL revalidates bans, membership, Parcel relation policy and claim/ownership/security revisions before credit. Existing scores/caps are preserved. The `public-credit` configuration replaces the legacy `visitor-credit` key; equivalent persisted policy text migrates without changing values.

New Settlements anchor at founding game time; legacy Settlements start at migration time. A first visit does not reset their age. Transfers preserve activity, released identities reject new credit, and reclaiming a new identity starts fresh. Pure status reads project state without writing decayed scores. Inactivity begins at the calculated threshold-crossing tick, not the next inspection.

The Paper collector coalesces by Settlement/player/category, keeps at most 4096 observations, and submits at most 256 per batch through the existing bounded SQLite worker. Uncommitted/coalesced observations are best-effort, not an audit log of every click. Grace defaults to 72000 game ticks; closed pause intervals persist on recovery so future production can exclude them. No production is implemented.

Activity policy is configurable before initialization and persisted as a contract. Changing an installed policy currently fails startup rather than retroactively reinterpreting scores; live policy migration is not implemented. `/settlement activity [name]` and `info` show actual score, state, recent credited category, inactivity tick, daily credit and recovery need.

## 8.3 Automation constraint

Machine operation should not indefinitely replace human activity.

A fully automated machine should not generate infinite settlement activity simply because it continues operating.

Activity therefore measures **meaningful player participation in a settlement**, not raw machine uptime.

Facility output, offline accrual, and automated trade/transport processing grant no Activity. Economy consumes Settlement Activity eligibility but cannot sustain it through its own production events. After insufficient Activity exceeds a configurable grace period, production pauses even when unloaded buildings show no decay; recovery cannot turn paused time into catch-up output (§16A.2).

## 8.4 Recovery

When meaningful participation reaches the configured recovery threshold (default 30), the Settlement returns to ACTIVE eligibility. Login or a single event does not automatically recover it. Below the inactivity threshold (default 20), it becomes INACTIVE; the gap supplies hysteresis. Future decay must consume this eligibility before mutation.

Existing physical damage persists until repaired. Activity recovery alone neither restores blocks nor advances a Building's Era. Queued decay work must revalidate current activity eligibility immediately before world mutation and must not apply after recovery makes it ineligible.

---

# 9. Decay Architecture

The detailed decay algorithm is intentionally deferred.

The architecture is already defined around the following principles.

## 9.1 Purpose

Decay is not intended to delete abandoned buildings.

Its purpose is:

> **Turn abandoned construction into recognizable ruins while preserving its original architectural footprint.**

The desired outcome is:

```text
Intact Building
      ↓
Weathered Building
      ↓
Damaged Building
      ↓
Ruin
```

rather than:

```text
Building
   ↓
Air
   ↓
Original terrain
```

## 9.2 Hybrid design

The selected direction is a hybrid approach combining player-modification evidence, bounded structure analysis, and rule-based decay.

Buildings are discovered automatically inside claimed Settlements. Player modification records are strong evidence but are not the definition of a Building; existing construction inside newly claimed land must still be discoverable.

Each tracked Building has a stable `building_id`, `world_instance_id`, one responsible `settlement_id`, a footprint, and a versioned restoration baseline. The footprint must lie entirely inside one Settlement. Building identity should survive ordinary rediscovery.

Discovery establishes the initial baseline. Authorized player edits revise only the affected active baseline portions. ESMP decay and repair change physical condition, not intended design. Authorized demolition therefore changes the baseline rather than becoming damage that Repair later reverses.

The MVP Building geometry model should remain deliberately small but must include enough spatial semantics for safe restoration:

```text
BuildingGeometry
├── occupied volume
├── exterior surface
├── interior volume
├── openings
└── structural components / support relations
```

Exterior/interior classification is required because block-level conflict checks alone are insufficient. If a ruined tower is later enclosed by a newly built hall, Repair must not restore obsolete exterior walls through that new interior merely because the exact target blocks are currently air. Localized rediscovery therefore compares baseline geometry with current authorized player geometry and can mark old restoration fragments as conflicted or retired by renovation while preserving their historical record.

The active restoration model conceptually distinguishes:

```text
ACTIVE baseline fragment
DAMAGED fragment
CONFLICTED_BY_RENOVATION
RETIRED_BY_RENOVATION
HISTORICAL_ONLY
```

The exact persistence representation may be simpler than this vocabulary, but Repair must be able to stop targeting obsolete portions without erasing their history.

Decay and Repair jobs reference a baseline revision and revalidate immediately before mutation. A conflicting player edit invalidates/replans only affected work; valid work elsewhere in the Building may continue.

Block entities are a special category. The MVP does not define complex inventory restoration, migration, condemnation, or content-state simulation. Decay/Repair must not treat block entities as ordinary blocks until an explicit safe policy exists.

Wild construction does not receive the full persistent Building model by default. Ordinary wild structures use occasional localized scans and coarse decay planning when naturally loaded and due. Roads, railways, and similar infrastructure may use specialized lightweight linear/segment models. Only structures important enough to justify persistent identity should be promoted to richer tracking.

The first structural-decay implementation should prefer a small material/profile set and a small failure-mode set. Weathering and structural integrity are separate concerns. Initial collapse synthesis may support simple `DROP`, `TOPPLE`, and `CAVE_IN` behavior without attempting full rigid-body physics, finite-element analysis, or complex continuous rotations.

Discovery and rediscovery remain bounded, localized, and event-driven. Do not introduce recurring scans of every block in every Settlement.

## 9.3 Event-driven architecture

Do not periodically scan every block in every settlement.

Instead:

```text
Player Event
    ↓
Record relevant change
    ↓
Building / Settlement state
    ↓
Activity update
    ↓
Potential decay candidate
    ↓
Decay Queue
    ↓
Small batch processing
    ↓
World mutation
```

## 9.4 Decay Queue

Decay work should be represented as queued, schedulable work.

The queue allows the server to:

- spread work over time;
- cap world mutations;
- prioritize eligible ruins;
- reduce work when TPS/load is high;
- increase work when the server is idle.

Decay and Repair jobs are durable pending work. They wait for chunks to load naturally; ESMP does not load or retain chunks solely to advance these jobs. Restarting the server does not discard pending work. A remote Building may therefore remain physically unchanged until its chunks load again.

## 9.5 Thread boundary

Database operations and computationally expensive candidate selection may run asynchronously.

Actual Minecraft world/block mutations must execute on the correct Paper scheduler / world execution context.

The architecture must never rely on arbitrary asynchronous block mutation.

---

# 10. Natural World Change and Deferred World Evolution

Natural-world change remains accepted only at a high architectural level for the MVP: it must be low-frequency, local, event-driven or scheduled, and bounded in workload. ESMP must not continuously simulate the entire world.

A future world-evolution design may preserve significant player-created landforms without recording every changed block. The accepted direction is coarse **Anthropogenic Landform** representation: small surface edits and incidental trenches can be ignored, Buildings remain Building data rather than terrain data, while large excavations, artificial lakes, major fills, artificial hills, canals, and comparable terrain-scale changes can be represented using coarse material/shape voxels. A fixed 8×8×8 voxel is the preferred first implementation candidate; adaptive refinement to 4×4×4 or 2×2×2 is deferred until a demonstrated need exists.

The following are design directions, not current MVP commitments:

- natural erosion/evolution of those coarse landforms;
- chunk retirement and regeneration from current historical terrain state;
- disaster operators/fields, sea-level shifts, global catastrophes, and Epoch transitions;
- large-scale volcanic, tectonic, glacial, flood, ash, snow, or other world-shaping events.

Natural disasters in particular remain open design. They are not a prerequisite for the next dynamic-cycle phase and must not be assumed to define Settlement ownership or release rules.

---

# 11. Repair System

A building should be repairable before it becomes completely ruined.

The intended interaction is not:

> Find every damaged block and replace it manually.

Instead, a player interacts with a building through a dedicated repair point such as:

- a cellar entrance;
- a repair station;
- an artisan/workshop point.

The player provides materials or hires villagers/artisans.

## 11.1 Repair as a process

Repair should generally take time.

Conceptually:

```text
Player supplies resources
        ↓
Repair job created
        ↓
Villager / artisan works
        ↓
Building gradually restored
        ↓
Repair completed
        ↓
Era advances
```

Instant restoration is not the preferred model.

The MVP exposes Repair primarily as a whole-Building operation, even if the implementation applies bounded local batches internally. Repair restores only still-valid damage against the current active baseline. Authorized renovations, newly created interior space, and retired baseline fragments are preserved rather than overwritten. More detailed player-facing scopes such as “repair only the east wing” are deferred until needed.

System decay must not create ordinary collectible block drops, and repair/material accounting must be recoverable rather than assuming atomicity between SQLite and Minecraft world state.

---

# 12. Villager / Artisan System

Villagers can serve as a settlement's repair workforce.

The system may allow:

- assigning villagers to repair jobs;
- consuming repair materials;
- running repair work over game time;
- restoring eligible building components progressively.

The detailed villager job architecture is not yet finalized.

---

# 13. Era System

Every completed repair cycle can advance a building's **Era**.

Era is not simply an age measured in years.

It represents:

> A completed historical cycle of construction → decline → restoration.

Example:

```text
Era 1
  Build
   ↓
  Decay
   ↓
  Repair
   ↓
Era 2
   ↓
  Decay
   ↓
  Repair
   ↓
Era 3
```

This makes Era a historical/cultural value rather than merely a timestamp.

Possible future rankings include:

- oldest buildings;
- highest Era;
- most repairs;
- longest continuously owned settlements;
- most historically persistent settlement sites.

---

# 14. History System

History should be treated as an independent domain concept, not merely as a property of Repair.

Potential historical information:

- original builder/founder;
- construction time;
- repair count;
- current Era;
- ownership history;
- important settlement events.

The historical record should survive changes in current ownership where appropriate.

This allows a future player to inhabit a location without erasing its historical identity.

---

# 15. Resource Realm

The Resource Realm exists primarily as a renewable resource supplier.

It is intentionally separated from the permanent civilization world.

## 15.1 Resource world properties

- no settlement claims;
- no settlement activity;
- no building decay;
- no repair/history mechanics;
- periodic reset;
- resource-focused gameplay.

The Resource Realm contains three dimensions:

```text
Resource Overworld
Resource Nether
Resource End
```

These are separate Minecraft worlds/dimensions, but share the same Resource System.

## 15.2 Resource Cycle

Each reset creates one shared logical cycle for Resource Overworld, Resource Nether, and Resource End. All three advance together; independently active dimension generations are not supported.

Conceptual record:

```text
ResourceCycle
├── cycle_id
├── cycle_number
├── overworld_instance_id
├── nether_instance_id
├── end_instance_id
├── created_at
└── retired_at
```

Each dimension instance belongs to this cycle and uses a fresh physical world identity. Generation settings and seeds are recorded with the relevant generation data; their exact schema is deferred. The active cycle selects all three instances together for player entry and portal routing.

## 15.3 Reset lifecycle

Replacement worlds are prepared before closing the old cycle. Resource worlds must not be deleted live while players are using them.

```text
Create pending cycle with three new world instances
  ↓
Generate and pre-generate all replacement dimensions
  │ old cycle remains accessible; replacements are not public
  ↓
Verify all three replacements are ready
  ↓
Close Resource entry and freeze the old cycle
  ↓
Evacuate players and verify all old dimensions are empty
  ↓
Unload old instances
  ↓
Activate the new shared cycle and switch routing
  ↓
Reopen Resource access
  ↓
Archive or delete retired instances according to retention policy
```

Preparation uses bounded generation work and the appropriate Paper execution context. This deliberately requires temporary disk capacity for both cycles and generation headroom while the old cycle is serving players. Failure during preparation does not close the old cycle or expose incomplete replacements.

Cutover keeps entry closed until all three new instances and their routes are ready; never expose a mix of old and new dimensions. Persist lifecycle progress so a restart reconciles physical world readiness with the active-cycle record before admitting players. Database activation alone is not proof that the world switch completed.

Evacuation preserves shared player state and must move players outside retiring instances. Offline login, respawn, and saved travel references must not route into retired worlds or silently reinterpret old coordinates in the new cycle. Exact destinations and portal UX remain deferred.

Old instances remain unavailable after cutover. Archive/delete is post-cutover work, never a prerequisite for preparing the replacement cycle; retention and backup procedures remain operator-policy decisions.

## 15.4 Nether and End

The Resource Nether and Resource End remain conventional Minecraft dimensions in gameplay terms.

The End can therefore support repeated Ender Dragon fights.

The Nether can regenerate its resource structures between cycles.

No special alternative gameplay model is required for these dimensions.

---

# 16. UI / Player Experience

The design target is:

> **Vanilla-feeling interaction, not plugin-server UI overload.**

## 16.1 Preferred interaction hierarchy

Use existing Minecraft interaction patterns whenever possible:

1. commands;
2. ActionBar;
3. BossBar;
4. chat Text Components;
5. vanilla containers;
6. books/signs;
7. modern native Minecraft Dialogs where appropriate.

## 16.2 Settlement and claim example

Current primary command surface (complete details in README and in-game help):

```text
/settlement create
/settlement claim <settlement>
/settlement unclaim
/settlement invite <settlement> <player>
/settlement promote|demote <settlement> <player>
/settlement view [settlement]
/settlement abandon <settlement>
```

During claim editing, temporary particles can show chunk borders. ActionBar or chat components can show:

```text
Settlement: Oakfall
Owner: Alex
Role: Citizen
Status: Prosperous
```

Exact command names and presentation are provisional.

## 16.3 Repair example

A repair station/cellar can use a vanilla container interface to show:

- required materials;
- repair status;
- assigned artisans;
- progress.

The first version should avoid a large custom RPG-style menu hierarchy.

---
# 16A. Economy, Transport, and Seasons

Accepted on 2026-09-09 after review of the gameplay extension. These systems support useful settlements, visible infrastructure, and history rather than compressed villager captivity or large redstone farms as the economic core. They are separate feature slices, not one milestone. The shared reckoning clock and Activity eligibility are implemented; production, markets, transport, cultural progression and seasonal presentation still require their §26 gates.

## 16A.1 Progression, culture, and certification

Economic stages are I **Settlement**, II **Village**, III **Town**, and IV **City**, attached to the existing Settlement identity. Housing, public space, industry, trade, transport, Activity, and history are candidate criteria, not selected numerical requirements. Ordinary vanilla crafting remains available at every stage; progression gates Facility tiers and automated throughput, not normal recipes.

Cultural standing is a parallel capability track: architecture, history, and landmarks can grant public significance to nonproductive castles, ruins, and monuments. It does not independently raise production capacity or redefine Building Era. Reclaim still uses current ruins as the active baseline while retaining history; neither progression nor the calendar advances Era. Exact cultural ranks and qualification criteria remain open.

A Facility combines a functional place, its Settlement, and villager workforce to provide goods or services. Examples include farms, lumber yards, quarries/stone works, smithies, bakeries, ports, and railway stations; examples are not a finalized recipe catalogue.

Basic production requires automatic checks for usable work area, valid workstations, reachable housing, and reasonable worker density. Housing may be in another building within the same Settlement. Higher tiers require administrator certification for function, exploit resistance, and consistent standards—not aesthetic scoring.

Relevant changes trigger revalidation and suspend affected capabilities only. Invalid higher-tier qualification does not stop basic production if basic checks still pass; failure of basic requirements also blocks basic production. Material scope changes or exploits require staff review, not every decorative edit. Preserve stored goods and unused inputs. Certificate scope, records, triggers, type-specific requirements, and reactivation rules must be resolved before enablement.

## 16A.2 Bounded Facility production and physical depots

Each economic stage supplies one shared Settlement production-capacity budget allocated among Facilities. Combined allocations cannot exceed it; adding villagers or Facilities does not create additional budget. Facility tiers remain bounded by economic capability and certification. Exact units, allocations, authority, and stage amounts remain open.

Production is hybrid: basic renewables use capacity/workers/time; processed goods consume inputs. Market category does not determine production method: basic glass can still require sand/fuel. Metal-producing Facilities consume supplied ore or other eligible mined inputs; time and capacity cannot create metal. This does not approve an item-provenance tracker or a ban on vanilla farms.

Bounded offline accrual uses saved valid Facility state without loading chunks or pretending villagers continue ticking. Available inputs, output capacity, workforce eligibility, and the shared production budget bound it. Insufficient Activity beyond a configurable grace period pauses new production until meaningful participation recovers, independently of physical ruin. Stored goods and unused inputs remain; paused time never yields catch-up production. Production grants no Activity. Automatic loss of earned progression or culture is not selected by this operating-state rule.

Here, offline means players offline or chunks unloaded while the shared game clock advances. Server downtime and frozen game time produce no accrual. Production grace periods, input/output accounting, and Activity eligibility all use that same clock; they must not substitute wall-clock catch-up.

Inputs and outputs use bounded ledger stock at a physical Facility depot. Deposits remove physical items into stock; withdrawals return physical items. The ledger is not a duplicate snapshot of items still in a chest. Use vanilla-client-compatible interfaces. Permission checks, interrupted deposit/withdrawal recovery, in-progress jobs, capacity reservations, and stale saved-state validity must be defined before enabling production.

## 16A.3 Goods and regional specialization

Basic construction/survival supply includes timber, stone, food, clay, glass, and basic metal. Processed building materials, masonry/bricks, food, tools, and components support industry chains. Rare goods such as Elytra, Dragon Egg, Wind Burst III, special equipment/materials, event rewards, and historical relics belong to exploration/events/history rather than unlimited ordinary Facility production. Exact catalogues remain open.

Stable natural biome/terrain profiles give efficiency advantages, not hard availability locks. Seasonal modifiers apply separately; cosmetic biome changes must not redefine regional identity or create specialization exploits. Mountain/forest/plain/coast mappings and multipliers remain illustrative until configured.

## 16A.4 Currency and markets

Currency is ledger-based with distinct player wallets and Settlement treasuries. Budgeted server purchases and exploration/event rewards create currency. Player trades transfer currency. Teleport and market service fees destroy currency; freight separates operator revenue (transfer) from a disclosed system fee (destruction). No recurring wages or membership taxes are introduced.

Owner controls treasury withdrawals and operating budgets. Governors may make audited operational payments within Owner-approved budgets; Citizens cannot spend treasury funds. This is bounded delegated trust, not a guarantee against self-dealing, and does not create custom Settlement roles. Budget allocation/replenishment and detailed spending controls remain open.

Server Trade is a bounded basic-commodity backstop, including selected simple processed essentials—not an unlimited quest-money shop. It has global Cheap/Normal/Expensive price bands adjusted at coarse configurable intervals from stock and demand, within fixed price bounds. Stock is finite: purchases consume it, eligible sales and controlled replenishment restore it. Temporary shortages are accepted.

NPC buying and selling use separate eligibility lists. Selling an essential to players does not imply buying it from them; eligibility of vanilla farm outputs remains open. Rare and most specialized goods belong primarily to player markets. Exact prices, spreads, quote/bulk behavior, quantities, replenishment, and issuance budgets remain feature gates.

Initial Player Trade uses physical market stalls with stocked goods and posted prices; buyers visit to purchase and collect. Global instant delivery, orders, procurement, and bulk fulfillment are not implicitly included. Stocking, purchase/collection, permissions, and crash-safe item/payment settlement must be resolved before enabling stalls.

## 16A.5 Freight and public teleport

Trade Routes connect physical depots over real roads, rails, or port connections. Qualification combines automatic endpoint/basic checks with initial administrator review of the physical connection. Relevant edits and invalid endpoints trigger targeted rechecks, not periodic whole-route scans. Registered corridors, land permissions, and invalidation coverage remain design work.

Freight uses timed ledger shipments, not travelling cargo entities. Dispatch reserves cargo and route throughput; arrival follows an abstract travel time. Stack-equivalent bulk units include nested cargo contents rather than counting a filled portable container as one empty item. Mode and distance inform quoted cost and travel time. Exact weights, throughput windows, rates, and payment timing remain open.

Route disruption blocks new departures but does not destroy or automatically pause already-dispatched shipments; they retain their arrival schedule. An unavailable destination holds cargo for the explicitly identified original recipient, not whoever later owns its coordinates. Ownership changes, recipient recovery access, destination capacity, and payment recovery must not duplicate cargo, redirect ownership, or turn held shipments into unlimited storage.

Public Settlement Teleport is distinct from ordinary movement and freight. Village economic stage qualifies for basic teleport infrastructure; an independently certified cultural landmark also qualifies without increasing production capacity. Higher economic stages can support stronger transport capabilities, with exact unlocks unresolved.

Teleport costs base plus carried-cargo surcharge to favor people using teleport and bulk goods using routes. Shared inventories and Ender Chests remain unchanged; manual/global-storage bypasses are accepted under this soft incentive, not a promise of strict freight separation. Cargo classification, portable-container treatment, fee presentation, and safe destination/payment handling remain feature gates.

## 16A.6 Calendar and seasons

One Minecraft day equals one calendar day; a year contains 365 days or 366 in a leap year. Start at **0 A.D.**, followed by 1 A.D., 2 A.D., and onward. At normal 20-minute days, a non-leap year is five real days plus one hour forty minutes—not a fixed five-real-day year. Sleeping and time changes can affect pacing.

All ESMP gameplay timing uses one shared reckoning clock measured in game ticks/days, including Activity loss, decay/repair progression, production, grace periods, freight, cooldowns, offer expiry, market intervals, and Resource schedules. It is not tied to the real-life clock. When `advance_time=false`, the shared clock and time-driven mechanisms stop advancing. Server downtime adds no game time and creates no catch-up. Changing tick rate alters real-world pacing only: the same elapsed game days must produce the same time-based Activity/decay result.

Gameplay consumes one clock supplied by the configured, loaded MAIN world's `getFullTime()`. First installation begins at reckoning tick 0; 24000 ticks are one game day. All positive source deltas count, including sleep and explicit forward time commands; negative changes re-anchor without subtracting reckoning time or reversing completed work. `advance_time=false` re-anchors without crediting deltas; Activity contributions are also disabled while frozen. Claim protection and explicit commands remain available.

The clock persists source UUID, raw source position and reckoning ticks. Boot resumes persisted reckoning time and re-anchors to the loaded source without inferring downtime from either wall time or a mismatched world save. A changed MAIN source UUID fails startup instead of silently resetting chronology. Resource worlds do not drive this source.

Game-timed domain writes checkpoint the clock in their transaction; idle clock checkpoints occur every 200 game ticks and normal shutdown flushes. Abrupt failure may lose uncheckpointed clock-only progress/coalesced observations, but committed score/offer anchors are not ahead of the durable clock. This is not a coordinated world-save/DB restore protocol; restore paired data, never mix unrelated MAIN and database snapshots.

Offer expiry, border duration and protection-message throttling now use game ticks. Existing pending offers retain their remaining lifetime via a one-time legacy conversion; new expiry has no wall-clock fallback. New history includes explicit game ticks; original wall timestamps remain diagnostics/legacy provenance, not gameplay inputs.

Leap-year and season-boundary presentation remains deferred. The implemented clock supplies ticks/days, not a completed seasonal rendering or full calendar UI.

Spring favors growth/building, summer production, autumn harvest/storage/trade, and winter reduced agriculture plus crafts/trade. Exact effects remain tuning work. Seasons modify production/demand rather than owning another economy; decay eligibility remains independent. They provide rhythm, not compulsory daily tasks, punitive survival, or intricate stock management.

Initial presentation uses bounded native weather/particles and limited reversible snow/vegetation in eligible loaded areas, preserving builds and restoration baselines. Mutations require the same permissions, world-instance validation, expected-state checks, and recovery discipline as other world work. Full seasonal grass/foliage palette changes remain a deferred future feature; no resource-pack/client-biome technique or compatibility promise is selected.

## 16A.7 Ownership, recovery, and scope boundaries

| Logical owner | Authoritative state and integration |
|---|---|
| Civilization | Settlement identity/roles, economic and cultural capabilities, Facility qualification/certification, shared production allocation, and Activity eligibility. |
| Economy | Wallet/treasury ledger, issuance and operating budgets, depot/stall stock, production accounting, market prices/quotes and settlement. Consumes Civilization eligibility, never writes Activity. |
| Transport | Route qualification/capacity, shipment lifecycle and recipient, teleport access/requests. Uses Civilization authorization and Economy stock/payment APIs. |
| Calendar/Seasons | Shared gameplay reckoning clock and calendar state, regional/seasonal modifiers, and bounded presentation plans. Supplies time and modifiers, never duplicates Economy balances or changes Building Era. |
| Core / Paper adapter | Neutral persistence/scheduling/world identity in Core; world observations, inventory interaction, and mutations on the proper Paper execution context. No gameplay policy in Core. |

This is a logical ownership contract, not a finalized SQL schema or new Gradle-module layout. All domains remain within one plugin lifecycle. Facility/depot/route/landmark coordinates use `world_instance_id`; stable roles use `world_definition_id`. Settlement ownership or claim changes require dependent eligibility and access to be revalidated rather than inherited by coordinate alone.

Economy commits pure ledger state through short owning-domain transactions and stable operation identities. Cross-domain stock/payment/capacity changes require an explicit recoverable command workflow; committed events report results and must not be relied on to retroactively enforce admission invariants. Physical item exchange cannot be atomic with SQL; §17.4 recovery and coordinated world/database restore apply. Exact reservation, refund, reconciliation, and durable audit designs are enablement gates, not implemented guarantees.

Use event-driven invalidation, saved-state accounting, due-work indexes, and bounded queues. Never scan every Facility, route, or claimed block on every tick; do not schedule writes to all inactive Settlements. Unloaded physical changes are not simulated as complete. Offline economic accrual is a separate bounded ledger calculation, not an exception allowing chunk loading or off-thread Paper access.

Initial scope spans progression/certification, basic production and commodity categories, Server Trade and physical stalls, regional modifiers, lightweight freight, public teleport, native seasonal effects/basic modifiers, and cultural/landmark capabilities. Exclude detailed population simulation, wages/happiness/taxes, full physical logistics, unlimited automation, complex finance, faction warfare, forced seasonal chores, and complex NPC-city AI. The Factory Realm remains removed; full seasonal palettes remain deferred.

---


# 17. Persistence and Database Architecture

Minecraft world state and ESMP application state must be strictly separated.

## 17.1 Minecraft world data

Managed by Minecraft/Paper:

```text
Minecraft World
├── chunks
├── entities
├── POI
├── structures
└── level data
```

## 17.2 ESMP data

Managed by ESMP:

```text
ESMP Database
├── players
├── world_definitions
├── world_instances
├── settlements
├── settlement_members
├── settlement_chunks
├── settlement_activity
├── buildings
├── decay_jobs
├── repairs
├── eras
├── history
└── resource_cycles
```

Exact table names and schema are not finalized.

Accepted future state also includes progression/cultural capabilities, Facility certificates and allocations, production checkpoints, depot/stall stock, wallet/treasury entries and budgets, market quotes, route reservations/shipments, and calendar/modifier state. §16A.7 defines logical ownership; these are not existing tables or migrations.

The initial persistence engine is **SQLite**, embedded in the single Paper server deployment. Core provides connection, transaction, and migration infrastructure; each domain owns its repositories, schema, and queries. Durable application state, jobs, and the event outbox share this database so domain changes and outbox records can commit together.

Coordinate writes through bounded scheduling suited to SQLite's single-writer model and keep transactions short. Database I/O must not block the Paper world execution context; world state must not be accessed from database workers. Required migrations complete before dependent gameplay operations are enabled.

Use versioned schema migrations. The initial implementation uses SQLite JDBC 3.53.4.0 with small transactional JDBC migrations tracked by `PRAGMA user_version`, rather than adding a migration framework for the initial schema. Repository boundaries support maintainability, not transparent database portability; a future backend change requires explicit migrations and verification.

Civilization schema version 3 retains founding/history game ticks and tick-based offer deadlines, migrates membership role constraints, and adds Settlement details/numbers/bans plus Parcel cells, Holders, policies, proposals and approvals. Activity owns its policy, score, actor-limit and pause-interval tables; `game_clock` remains independent of the Core world schema. Released identity/history remains; affected Parcels must be explicitly removed before parent claim release.

## 17.3 World identity requirement

Every block- or coordinate-sensitive ESMP entity must be scoped by `world_instance_id`. Configuration and realm policy may reference the stable `world_definition_id`.

This is mandatory for preventing cross-world leakage and stale Resource Cycle records.

## 17.4 Recoverable DB-to-world work

SQL state and Minecraft world state cannot participate in one atomic transaction. Decay and Repair use persistent, recoverable jobs rather than claiming exactly-once world mutation.

```text
Persist intent and expected state
    ↓
Wait for naturally loaded chunks
    ↓
Revalidate on the world execution context
    ↓
Apply a bounded operation batch
    ↓
Persist outcomes and committed domain events
```

Jobs and operations need stable identities and durable progress sufficient to reconcile an interrupted batch after restart. Each operation records its world instance, Building/ownership context, baseline revision, expected source state, and intended target.

Before application, revalidate world instance, loaded-chunk availability, ownership, permission, baseline, current world state, and activity eligibility where applicable. Never overwrite a conflicting player edit. Outdated operations are discarded and replanned from current authorized state; unaffected valid work may continue. If ownership or another shared prerequisite changed, invalidate all work affected by that change, not merely one block.

On retry, distinguish an unapplied operation, an already achieved target, and a conflicting state using the durable operation record plus current state. An already achieved target is not permission to repeat material charges, history updates, or Era advancement. Those durable side effects must be idempotent by operation or job identity.

World mutation success does not mean Minecraft has durably saved the affected chunks. Recovery and coordinated restore must account for world-save/DB-checkpoint divergence; ambiguous outcomes must be reconciled rather than blindly replayed over newer state. Detailed journal storage, checkpoint protocols, and material accounting are implementation work.

The same cross-store boundary applies to depot/stall item exchange, charged teleport effects, and seasonal block changes. Pure ledger production does not access Paper state from database workers. Enabling any of these features requires its specific recovery protocol, including item/payment ambiguity and stale qualification; the existing Settlement outbox alone does not satisfy that gate.

---

# 18. Version Migration

Long-term operation requires two independent migration paths.

## 18.1 Minecraft world migration

Minecraft/Paper handles Minecraft world-data upgrades.

ESMP should not reimplement Minecraft's chunk/world migration logic.

## 18.2 ESMP database migration

ESMP owns its own schema migrations.

The database must have a schema version:

```text
schema_version
```

Example:

```text
V6 → V7
```

Each application release can ship deterministic migrations.

## 18.3 Upgrade procedure

Do not target support for a new Minecraft version immediately on release.

Preferred process:

```text
New Minecraft version
        ↓
Paper test server
        ↓
World upgrade test
        ↓
ESMP plugin compatibility test
        ↓
Database migration test
        ↓
Performance test
        ↓
Backup verification
        ↓
Production upgrade
```

Minecraft world migration and ESMP DB migration must remain conceptually separate.

---

# 19. Event Architecture

Modules should communicate through explicit application APIs and domain events rather than direct implementation coupling.

Use APIs or commands when a caller needs a synchronous decision or invariant. Use events to report facts that have already committed.

Example domain events:

```text
SettlementFoundedEvent
SettlementChunkClaimedEvent
SettlementOwnershipTransferredEvent
SettlementBecameRuinedEvent
SettlementReleasedEvent
SettlementReclaimedEvent
PlayerRepairBuildingEvent
RepairCompletedEvent
EraAdvancedEvent
```

A statistics/ranking module can listen to these events without Decay or Repair knowing about that module.

Durable domain events are recorded in an outbox in the same database transaction as the owning domain-state change, then delivered after commit. Delivery is at least once: events have stable IDs, and consumers deduplicate durable effects. A crash between commit and delivery must not lose historical events, while retrying delivery must not advance Era or duplicate history entries. Core supplies delivery infrastructure; gameplay domains own event semantics. Exact storage and retry scheduling remain implementation details.

---

# 20. Scheduling and Performance

Performance is a first-class architectural requirement.

## 20.1 Core principle

Never do:

```text
for every settlement
    for every block
        inspect block
```

on a regular global schedule.

## 20.2 Preferred model

```text
Player / World Event
        ↓
Relevant state update
        ↓
Candidate generation
        ↓
Persistent queue
        ↓
Budgeted processing
        ↓
World mutation
```

## 20.3 Adaptive workload

Decay processing should have a configurable workload budget.

Conceptually:

```text
Server busy
    ↓
reduce decay work

Server idle
    ↓
increase decay work
```

This allows long-term background processing without making decay compete aggressively with player gameplay.

## 20.4 Async vs world thread

Safe separation:

```text
ASYNC
├── database reads/writes
├── scoring calculations
├── candidate selection
└── queue preparation

WORLD EXECUTION CONTEXT
├── block changes
├── entity/world mutation
└── other operations requiring Minecraft world access
```

The exact scheduler abstraction must be designed so the same domain code can later accommodate Paper and potentially Folia execution models.

## 20.5 Loaded-chunk work and conflict replanning

Only naturally loaded chunks are eligible for Decay and Repair mutation. A job resumes through load-driven, budgeted scheduling; unloading before application returns affected work to pending rather than triggering a chunk load. Activity evaluation uses elapsed shared game time independently of chunk loading, but unloaded-world mutation is not simulated as completed.

Offline Facility accrual (§16A.2) is bounded ledger accounting, not physical world simulation. Use targeted invalidation and budgeted due work for Facilities, routes, and seasons. World observations and native effects remain on the proper execution context and must not force chunk loads.

When a player edit invalidates planned work, preserve that edit and replan the affected operations. Do not require a manual restart of the whole Building job. Replanning uses the current baseline and permissions and cannot reuse obsolete ownership or activity eligibility.

---

# 21. Architectural Boundaries

The following boundaries are considered important.

## 21.1 Core vs gameplay

```text
Core
  ↓ infrastructure
Gameplay Modules
  ↓ semantics
World
```

Core must not become a “god module”.

## 21.2 Civilization domain vs Resource

Settlement ownership, activity, decay, repair, and building history do not exist in Resource worlds.

Resource worlds exist to supply renewable resources. Player inventory and Ender Chest state remain shared with other realms.

## 21.3 Repair vs History

Repair produces historical events.

History records and interprets those events.

History should not be reduced to a field on Repair.

---

# 22. Conceptual Domain Model

The following is the current conceptual model, not a finalized Java class design:

```text
ESMP
│
├── Player
│
├── World Registry
│   ├── WorldDefinition
│   └── WorldInstance
│
├── Civilization Domain (not an owner entity)
│   ├── Settlement
│   │   ├── SettlementMember (Governor/Citizen)
│   │   ├── SettlementBan
│   │   ├── SettlementChunk
│   │   └── Parcel / ParcelHolder / ParcelCell
│   ├── Activity
│   ├── Building
│   ├── Decay
│   ├── Repair
│   ├── Era
│   └── History
│
└── Resource Domain
    └── ResourceCycle
```

`Civilization Domain` is an architectural grouping. It must not become a `Civilization` table or aggregate unless a future, separately approved gameplay requirement introduces one.

The accepted extension adds Facility/progression/cultural state to Civilization and logical Economy, Transport, and Calendar/Seasons services as assigned in §16A.7. These are gameplay domains, not additions to infrastructure-only `esmp-core`; the diagram above describes the original civilization/resource loop.

---

# 23. Settlement Lifecycle Model

Settlement ownership and physical condition are separate state dimensions.

## 23.1 MVP ownership lifecycle

The MVP does **not** automatically release Settlements because of inactivity, ruin, age, or low Activity. A Settlement remains owned and protected indefinitely unless an authorized explicit lifecycle operation changes that state.

```text
SettlementFoundedEvent
        │
        ▼
     OWNED
        │
        │ explicit Owner abandonment / approved administrative operation
        ▼
    RELEASED
```

Voluntary abandonment remains an explicit confirmed Owner action. It removes claim protection only when the durable release commits and preserves historical records. Abandonment does not itself damage or restore Buildings.

Released land may later be claimed according to ordinary exclusivity, contiguity, world-instance, size-cap, and claim-cost rules. Historical Settlement/Building records remain independently traceable. If a ruined structure is later brought into a different active Settlement, its current inherited state becomes the basis for any new active restoration baseline; an old intact baseline is not silently revived.

Automatic inactivity-driven/peripheral release belongs to the required post-MVP dynamic Settlement-cycle phase in §26.1 and must not be enabled by the MVP Activity/Decay implementation.

## 23.2 Activity and physical condition

```text
Activity:
ACTIVE ── insufficient activity ──► DECLINING
ACTIVE ◄── sufficient recovery ─── DECLINING

Building condition:
INTACT ── eligible decay ──► DAMAGED ── eligible decay ──► RUINED
```

Activity and physical condition remain separate. Activity recovery can stop further ordinary decay but leaves existing damage unchanged; restoration requires Repair. A Settlement may therefore contain ruined Buildings while its ownership and protection continue unchanged.

The post-MVP dynamic-cycle phase may later use Civic Capacity/maintenance demand and gradual peripheral release to convert prolonged under-maintenance into reusable land, but that lifecycle is intentionally absent from the MVP.

---

# 24. Data Ownership Matrix

| System | Main World | Resource World |
|---|---:|---:|
| Settlement claims | Yes | No |
| Claim protection | Settlement policy | No |
| Team membership | Settlement teams | No |
| Claim visualization | Yes | No |
| Activity | Yes | No |
| Decay | Yes | No |
| Repair | Yes | No |
| Era | Yes | No |
| History | Yes | Cycle history |
| Periodic reset | No | Yes |
| Shared player inventory | Yes | Yes |
| Shared Ender Chest | Yes | Yes |
| Facility / progression / cultural sites | Settlement-local | No Settlement sites |
| Depot / stall / Settlement transport infrastructure | Main World sites | No Settlement sites |
| Player currency | Shared player account | Same account; no per-realm balance |
| Calendar / seasonal policy | §16A.6 | Reset/source reconciliation must be resolved |

This table is a design boundary, not yet a final implementation schema.

---

# 25. Current Decisions

The following decisions are settled enough to guide the MVP and immediate implementation planning:

1. **Civilization** is the persistent Main World gameplay domain, not an owner aggregate. **Settlement** is the Main World ownership/governance/Activity/claim aggregate.
2. Main World Settlement claims use full vertical 16×16 chunks, remain four-directionally contiguous, and do not cross world instances. Separate Settlements may be adjacent and one player may own multiple Settlements.
3. The user selected numeric-only claim pricing for the current stage: record the increasing configured area price, but do not deduct currency/items or invent balances. Real currency charging is deferred until the user selects the system. Detailed land markets, rent, taxes and geography pricing remain deferred.
4. MVP Settlement governance identities are one Owner, zero or more Governors, zero or more Citizens, and derived Public users. Legacy Manager/Member/Visitor naming is replaced by Governor/Citizen/Public in the target model.
5. `BANNED` is a Settlement-local negative authorization override, not a positive membership role. Owner/Governor governance may ban non-administrator players from Settlement/Parcel interactions; server administrators bypass Settlement bans.
6. Public users are not forced into an adventure-like spectator relationship. They may use interactions allowed by the applicable Settlement or Parcel policy while unauthorized building, inventories, and automation remain protected.
7. Parcel is a lightweight 3D subdivision inside one Settlement using world-aligned 8×8×8 cells. Parcel cells cannot overlap another Parcel, must remain six-directionally connected, and must stay within the owning Settlement.
8. A Parcel may have zero, one, or multiple Holders. Holder is orthogonal to Settlement membership. Unassigned Parcels are governable by Owner/Governors; assigned Parcels cannot be unilaterally revoked or reshaped by either side. Governance plus all Holders must agree, otherwise a server administrator resolves the dispute.
9. The MVP does not model Parcel rent, contract duration, renewal, debt, or private agreement terms.
10. Parcel permissions expose only `BUILD`, `INTERACT`, `CONTAINER`, and `AUTOMATION`, evaluated against OWNER/GOVERNOR/CITIZEN/HOLDER/PUBLIC relations. Per-block/container ACLs are deferred; finer separation should normally use a separate Parcel.
11. Every Settlement/Parcel operation has complete command support. A replaceable Management Book provides convenience governance access and a replaceable Parcel Wand provides spatial editing. Tools contain identification only; authority belongs to the user, not the item.
12. Parcel Wand geometry editing uses a named off-hand Name Tag plus two selected 8×8×8 voxel endpoints. Proposed cuboids add only unparcelled cells, must leave the Parcel connected, and preview through particles/text before atomic commit. Cell deletion is allowed only from the surface and only if the remainder stays connected.
13. Initial Settlements need no official player-chosen name. They use a stable number and optionally a generated geographic label. Official naming and all later renaming require administrator approval and may charge Settlement currency; rename history is preserved.
14. Activity remains Settlement-local and based on meaningful participation rather than login time. Public/Holder participation may contribute under bounded anti-abuse rules; exact weights remain tunable.
15. Buildings are automatically discovered within claimed Settlements and have stable identity plus versioned restoration baselines. Authorized player edits update intended design; decay/repair do not redefine it.
16. Building geometry must include enough exterior/interior and support information to avoid restoring obsolete external structures through newly created player interiors. Renovation may retire only affected restoration fragments while preserving history.
17. Block entities are special and do not receive a complex inventory-decay/restoration design in the MVP. Unsafe ordinary-block treatment is prohibited until a dedicated policy is defined.
18. Decay/Repair use durable, budgeted jobs, natural chunk loading, expected-state revalidation, and affected-work replanning. System decay does not create ordinary collectible block drops.
19. Initial structural decay should use coarse material profiles plus simple weathering/integrity and a limited collapse set such as DROP, TOPPLE, and CAVE_IN rather than full physics.
20. Ordinary wild structures use coarse, occasional localized decay analysis rather than persistent full structural graphs. Roads/railways and similar infrastructure may use specialized lightweight segment models.
21. Facility implementation starts simple: a bounded set of Facility types, basic automatic functional checks, and administrator certification for higher tiers. Avoid detailed social/workforce simulation in the MVP.
22. Significant player terrain modification may later be represented as coarse Anthropogenic Landforms, initially favoring fixed 8×8×8 material/shape voxels. This direction is accepted, but advanced natural-evolution behavior is not part of the MVP.
23. Resource worlds remain separate renewable worlds without Settlements/claims, and the Main World remains the persistent civilization/history world.
24. Performance remains a first-class constraint: no recurring full-world/full-Settlement block scans; candidate generation, persistent queues, bounded processing, and correct world-thread mutation remain required.
25. The MVP intentionally does **not** implement dynamic Settlement cycles. MVP Settlements are persistent unless explicitly abandoned or changed by administrative/approved lifecycle operations.

---

# 26. Deferred and Open Design

## 26.1 Next phase: dynamic Settlement cycles — required after MVP

Dynamic cycles are not an optional someday feature; they are the required major design/implementation phase immediately after the MVP is stable. The MVP nevertheless treats Settlements as persistent so the permission, Parcel, Building, Activity, and recovery foundations can be proven first.

The next phase must solve how long-running active players are encouraged to found and develop new Settlements while older cities can gradually lose maintained territory without a blunt wipe. Current candidate concepts include:

- a **Civic Capacity / maintenance capacity** derived from meaningful local Activity and possibly Settlement stage;
- maintenance demand that grows with claimed area, Buildings, Facilities, and infrastructure;
- selective preservation rather than “high Activity makes every Building immune”;
- mature/legacy-city incentives that make new Settlement development attractive without making old cities worthless;
- gradual peripheral claim decay/release rather than sudden full-Settlement deletion;
- rules that allow old cores, landmarks, and history to survive while under-maintained outskirts return to reusable land.

Exact formulas, UI, ownership guarantees, release timing, reclaim behavior, and interactions with Parcel Holders remain unresolved and must be designed before enabling this phase.

## 26.2 Natural disasters / Epoch transitions — continue discussion

Large-scale disasters, forced global cycle transitions, pre/post-disaster ownership, sea-level changes, Disaster Operators/Fields, and RPG-style world catastrophes remain intentionally open. They may later enrich world history but are not currently required to make dynamic Settlement cycles work.

## 26.3 World evolution / Anthropogenic Landforms — continue discussion

The coarse Anthropogenic Landform direction is accepted, but exact significance scoring, material categories, simulation frequency, erosion rules, adaptive voxel refinement, and interaction with Buildings/infrastructure remain open.

Chunk retirement/regeneration is deferred due to its cross-cutting risk across Building, Parcel, entities, POI, infrastructure, history, and player terrain changes.

## 26.4 Building / Decay / Repair remaining gates

Before enabling physical decay/repair, resolve and verify Building split/merge/history linkage, safe baseline representation, block-entity policy, journal/checkpoint recovery, material accounting, deterministic conflict preservation, and loaded-chunk scheduling. Exact decay probabilities, material values, weathering palettes, repair speed, villager implementation, and Era advancement thresholds remain tunable.

## 26.5 Economy / Facilities / Transport / Resource remaining gates

Keep the existing accounting/recovery gates for physical stock, currency, markets, transport, production, Facility certification, resource-world cutover, and coordinated backup/restore. Avoid expanding these systems merely to support the MVP permission/Parcel work.

---

# 27. Recommended Development Order

| Phase | Prerequisites and deliverable |
|---|---|
| 1. Runtime / world identity — implemented | Existing modular runtime, world registry, SQLite execution, migrations, startup/restart verification. |
| 2. Settlement foundation / GameClock / Activity — implemented | Preserve claim topology, durable offers/history, game-time semantics and bounded Activity while evolving policy. |
| 3. **Settlement/Parcel revision — implemented with numeric-only prices** | Governor/Citizen/Public+BANNED, Parcel geometry/authorization/Holders and consent, complete commands, Management Book/Parcel Wand and official-name administration. Currency selection/charging remains explicitly deferred; no funds/items consumed. |
| 4. **MVP Building identity and geometry** | Discovery, stable ID/baseline, split-merge linkage, exterior/interior spatial semantics, localized player-edit reconciliation; simple Facility linkage/qualification may begin here. |
| 5. **MVP durable world mutation / Decay / Repair** | Recoverable material/world operations, special block-entity safety gate, simple weathering/integrity, limited structural failure modes, Repair respecting renovation conflicts, coarse wild-structure decay. |
| 6. Economy / Facilities / production / markets / transport | Implement accepted gameplay extensions in bounded slices with their existing accounting and recovery gates; keep Facility qualification initially simple. |
| 7. Resource lifecycle / seasonal presentation | Proceed independently when evacuation, backup/restore, calendar, and safe mutation gates are ready. |
| 8. **Dynamic Settlement cycles — required next major phase after MVP** | Design and implement Civic Capacity/maintenance demand or equivalent, mature/new-city incentives, and safe gradual peripheral decline/release while protecting history and avoiding abrupt wipes. |
| 9. Further world evolution | Anthropogenic Landforms, richer natural change, possible chunk retirement/regeneration after explicit safety design. |
| 10. Natural disasters / Epoch systems | Optional later world-history/RPG layer; design remains open and must not be assumed by earlier phases. |

This order is dependency-aware rather than strictly serial. The key product boundary is that the MVP stops before automatic dynamic Settlement decline, while Phase 8 is a committed next-stage requirement rather than an indefinitely deferred idea.

---

# 28. Immediate Architecture Work

The Settlement/Parcel slice is implemented with user-approved numeric-only prices. Next define and evaluate Building identity and geometry, preserving the new authorization/Parcel boundaries. Real currency integration remains deferred to the user's currency choice rather than blocking this slice.

Completed foundation and next dependencies:

```text
numeric-only claim pricing (implemented; real charging deferred)
↓
Governor/Citizen/Public + Settlement bans (implemented)
↓
Parcel geometry, policies, Holder consent and command API (implemented)
↓
Management Book / Parcel Wand / naming administration (implemented)
↓
Building geometry evaluation and identity design (next)
↓
Building identity + exterior/interior geometry
↓
durable Decay/Repair safety gates
```

Do not implement automatic Settlement decay/release in the MVP. Record the required dynamic-cycle phase and its open questions, then return to it after the MVP foundations are stable.

---

# Appendix A — Architectural Vocabulary

| Term | Meaning |
|---|---|
| ESMP | Evolve: SMP |
| Civilization | The overall persistent Main World gameplay environment/domain; not a player-owned entity |
| Civilization World | Permanent Main World where settlements and history exist |
| Settlement | Main World ownership, team, permission, activity, and claim aggregate |
| Claim | Protected chunk ownership relationship and the operation that changes it |
| Settlement Chunk | One claimed Main World chunk belonging to a Settlement |
| Resource Realm | Renewable resource worlds that periodically reset |
| World Definition | Stable configured world role, such as `MAIN` or `RESOURCE_NETHER` |
| World Instance | One physical Minecraft world incarnation with a Bukkit world UUID |
| Activity | Quantified measure of meaningful participation in one Settlement |
| Decay | Controlled transformation of abandoned construction into ruins |
| Ruin | A decayed but recognizable remnant of previous construction |
| Release | Removing claim protection through confirmed Owner abandonment or automatic inactivity-and-ruin eligibility |
| Reclaim | Establishing a new ownership epoch over previously occupied land |
| Repair | Process that restores a building during decline |
| Era | Historical restoration-cycle count/state |
| History | Persistent record of buildings, Settlements, and ownership epochs |
| Resource Cycle | One generation/lifetime of resettable Resource world instances |
| Economic stage | Settlement/Village/Town/City capabilities on the existing Settlement, not Building Era |
| Cultural standing | Independent public/landmark capability track, without production-capacity increases |
| Facility | Main World functional production/service site with Settlement qualification and villager workforce |
| Depot stock | Bounded ledger inventory exchanged for physical items at a real Facility interface |
| Treasury | Settlement currency account with Owner-controlled budgets and audited Governor operations |
| Trade Route / Shipment | Qualified physical connection / recipient-bound timed ledger cargo movement |
| Calendar / reckoning clock | Shared gameplay ticks/days; 365/366-day calendar years from 0 A.D.; freezes with `advance_time=false`, with no real-time or downtime progression |

---

# Appendix B — Guiding Principle

The project should preserve one central distinction:

```text
Traditional SMP
Player → World

ESMP
Player ↔ World
```

In a traditional SMP, the world is mostly a persistent backdrop.

In ESMP, player actions change the world's future, while the world's accumulated history changes what future players inherit.

That feedback loop is the defining architectural and gameplay characteristic of Evolve: SMP.
