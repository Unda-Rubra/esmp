# Evolve: SMP — Architecture

> Status: Architecture baseline; implementation gates remain explicit  
> Project: **Evolve: SMP (ESMP)**  
> Platform direction: **Minecraft Java Edition + Paper plugin architecture**  
> Scope: This document records the architecture and gameplay design that has been settled so far.  
> It intentionally does **not** define the detailed Decay Engine algorithms yet.

## Current implementation

The runnable plugin consists of four Gradle modules and one deployable Paper JAR under `xdu.undarubra.esmp`.

- `esmp-core` owns world identity and a bounded serial SQLite worker, including database-only transaction callbacks for owning domains. Core schema version 1 remains independent from Civilization schema version 1.
- `esmp-civilization` now implements Settlement ownership, contiguous claims, roles, expiring offers, transfer, leave/removal, confirmed abandonment, rename, immutable views, and durable history/outbox records.
- `esmp-plugin` supplies `/settlement` commands, main-thread cache publication, action-specific protection, private temporary borders, and post-commit `SettlementCommittedEvent` notifications. `/esmp status` reports actual readiness and Settlement count.
- Configured Main World instances are claimable; Resource worlds remain unclaimable. Removed definitions remain historical only. Existing claims load before gameplay is admitted; persistence failure stops the server rather than leaving claims unprotected.
- Building discovery, Activity scoring, decay/repair, automatic release, and Resource resets are not enabled. The implemented Settlement event outbox is not a claim that future DB-to-block mutation jobs already exist.

The Factory Realm is removed from the current scope following the team's RPG-survival balance and performance review. It is not a scheduled milestone or dormant runtime module. This cut removes the dedicated realm and industrial systems; it does not add a redstone ban to the remaining worlds. Reintroduction requires a new explicit decision.

Legacy configuration migration backs up the unversioned/version-1 config to `config-v1.yml`, removes its obsolete `worlds.FACTORY` mapping, and writes version 2. It never deletes physical world folders or persisted historical identities.

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

Conceptual model:

```text
Settlement
├── settlement_id
├── world_instance_id
├── name
├── owner_uuid
├── created_at
└── status

SettlementMember
├── settlement_id
├── player_uuid
├── role = MANAGER | MEMBER
├── joined_at
└── left_at (optional)

SettlementChunk
├── settlement_id
├── world_instance_id
├── chunk_x
└── chunk_z
```

The database must enforce exclusive Main World ownership with a unique constraint equivalent to:

```text
UNIQUE (world_instance_id, chunk_x, chunk_z)
```

`Settlement.owner_uuid` is the single source of truth for the Owner. `SettlementMember` stores only Managers and Members and must be unique by `(settlement_id, player_uuid)`. Visitors are derived and have no membership row.

Activity, building condition, repair, Era, and history remain separate domain records rather than duplicated mutable fields on `Settlement`.

## 7.4 Ownership and teams

A player can:

- create and own multiple settlements;
- own adjacent settlements;
- invite players to a settlement team;
- assign team roles;
- transfer settlement ownership through an auditable operation;
- leave, abandon, or release a settlement according to settlement rules.

Every player has exactly one effective role relative to a Settlement:

```text
OWNER
├── exactly one while the Settlement is owned
└── identified by Settlement.owner_uuid

MANAGER
├── zero or more
└── persisted as SettlementMember

MEMBER
├── zero or more
└── persisted as SettlementMember

VISITOR
├── every other player
└── derived; never persisted as membership
```

These four roles are fixed. Settlements cannot create, rename, delete, or reorder roles. A player may hold different effective roles in different Settlements.

The initial capability matrix is:

| Action | Owner | Manager | Member | Visitor |
|---|---:|---:|---:|---:|
| Enter, move through, and observe | Yes | Yes | Yes | Yes |
| Safe public interaction | Yes | Yes | Yes | Yes |
| Build and break blocks | Yes | Yes | Yes | No |
| Full block interaction | Yes | Yes | Yes | No |
| Use containers and persistent inventory holders | Yes | Yes | Yes | No |
| Interact with protected entities or machines | Yes | Yes | Yes | No |
| Start repair work | Yes | Yes | Yes | No |
| Claim or unclaim chunks | Yes | Yes | No | No |
| Invite or remove Members | Yes | Yes | No | No |
| Change ordinary Settlement settings | Yes | Yes | No | No |
| Promote or demote Managers | Yes | No | No | No |
| Transfer ownership | Yes | No | No | No |
| Abandon or release the Settlement | Yes | No | No | No |
| Voluntarily leave the team | No | Yes | Yes | Not a team member |

On successful ownership transfer, the former Owner becomes a Member, not a Manager or Visitor. Update `Settlement.owner_uuid`, remove any membership row for the new Owner, and persist the former Owner as `MEMBER` atomically. No intermediate state may expose two Owners or an ownerless owned Settlement.

Ownership transfer requires explicit acceptance by the nominated recipient. Initiating a transfer creates a pending offer and changes no ownership or permissions. Acceptance must revalidate that the initiating player is still the Owner and the offer is still valid before committing the atomic transfer. Only the nominated recipient can accept; an unaccepted offer never transfers ownership.

Ownership-transfer offers expire automatically after a configurable duration, defaulting to **30 minutes**. Persist `expires_at` at creation and reject acceptance at or after expiry, regardless of whether cleanup has run.

The nominated recipient must already be a Manager or Member of this Settlement when the offer is created and when it is accepted. Visitors are ineligible; they must join first. Acceptance must revalidate current membership and reject the transfer if the recipient is no longer a Manager or Member.

Settlement invitations require explicit acceptance by the invited player before they become a Member. A pending invitation grants no membership or additional permissions; the recipient retains their existing effective role until acceptance commits. Only the invited player may accept. Acceptance must validate that the invitation is still valid and the inviter still has authority to invite Members. It must not overwrite an existing Owner, Manager, or Member role.

Membership invitations expire automatically after a configurable duration, defaulting to **24 hours**. Persist an `expires_at` timestamp when creating each invitation and reject acceptance when the current time is at or after that timestamp. Expiry must be enforced by the acceptance check, even if background cleanup has not run.

Cancellation follows current role authority: the Owner or any Manager may cancel any pending membership invitation for their Settlement, regardless of who issued it. Only the current Owner may cancel a pending ownership-transfer offer. The nominated recipient may decline their own invitation or transfer offer. Cancellation and decline grant no access and change no existing ownership or membership. Acceptance, cancellation, and decline must atomically transition the pending offer so only one outcome can commit; cancelled, declined, expired, or already accepted offers cannot be accepted.

Members and Managers may voluntarily leave a Settlement without approval. Leaving ends their active membership and makes them Visitors of that Settlement when the change commits; it does not change claims or other players' roles. A Manager leaving is self-withdrawal, not authority to manage another Manager. Owners cannot use this leave operation: they must first complete an ownership transfer or abandon the Settlement under the applicable release rules. No leave operation may leave an owned Settlement without its Owner.

Managers are operational deputies. They can manage land and ordinary Members but cannot create or remove Managers, transfer ownership, or abandon/release the Settlement.

Authorized removals take effect when the membership change commits, without approval from the removed player. The Owner may remove Members or Managers; Managers may remove ordinary Members only. Removed players become Visitors. Neither role may remove the Owner through membership removal.

Only the Owner may offer Manager promotion to an existing Member. Promotion requires explicit acceptance by that Member; while pending, they retain Member permissions. Acceptance must revalidate the offer, the initiating player's current ownership, and the recipient's current Member role before atomically granting Manager permissions.

Manager promotion offers expire automatically after a configurable duration, defaulting to **24 hours**. Persist `expires_at` at creation and reject acceptance at or after expiry, independently of cleanup. Only the current Owner may cancel a pending promotion offer; only the nominated Member may decline it. Acceptance, cancellation, and decline compete for one atomic pending-state transition. Expired, cancelled, declined, or already accepted promotion offers cannot grant Manager permissions.

The Owner may demote a Manager to Member without that Manager's approval. The change takes effect on commit. Demotion does not remove membership, and Managers cannot demote other Managers.

Members are standard collaborators. They can build, break, interact, use containers, interact with protected entities or machines, and start repair work. They cannot change claims, membership, roles, or settings.

Visitors use an **adventure-like authorization policy**. They may move through the Settlement and use a narrow safe-public-interaction set such as doors, trapdoors, fence gates, buttons, levers, and pressure plates. They cannot build, break, open protected containers, alter persistent inventories, interact with protected entities, or directly operate protected machine interfaces.

ESMP must not implement Visitor behavior by switching the player's actual game mode or cancelling every right-click event. Protection classifies the attempted action and permits the safe subset. An allowed button, lever, or pressure plate may trigger connected redstone as its normal consequence.

Permissions are settlement-local; a role in one Settlement grants nothing in another Settlement owned by the same player.

## 7.5 Claim protection

Claim protection is a required part of the Settlement system, not an optional integration.

All protected actions must pass through one authorization boundary:

```text
authorize(actor, action, target, context) → allow | deny
```

Protection must account for direct and indirect world changes, including:

- block placement and breaking;
- block and container interaction;
- entity and vehicle interaction or damage;
- explosions;
- fire and fluid spread;
- pistons, hoppers, and other mechanics crossing claim boundaries;
- system-initiated decay and repair mutations;
- explicit administrator overrides.

The exact action catalogue may grow, but new mutation paths must not bypass authorization accidentally. Decay and Repair use explicit system actors and must revalidate settlement ownership and building state immediately before changing the world.

Authorization is action-specific. Visitors are denied every protected action outside the safe-public-interaction allowlist; Owner, Manager, and Member permissions follow the fixed capability matrix. Public movement and safe interaction must not become a blanket right-click denial.

### Implemented protection and publication boundary

The Paper adapter checks permissions from a server-thread spatial index, never synchronous SQL in an event handler. An accepted mutation temporarily locks its affected Settlement and any reserved new chunk; after SQL commits, the server thread publishes the replacement view before announcing success. Obvious unauthorized commands are rejected before reserving territory. Revocation also closes no-longer-authorized container views.

The current event adapters cover direct placement/breaking and multi-block placement, block/item interactions (including buckets and ongoing archaeology brushing), signs/harvest, containers and inventory transfers, protected entities/vehicles/hanging objects, projectile/potion/fire effects, explosions, fluid and piston boundaries, dispensing, growth, and portal construction. Safe public controls remain permitted; personal inventory is not a protected world container.

Cross-region automatic block/item transfers are denied even between adjacent Settlements with one owner, while operations wholly within one Settlement remain usable. Unknown/unattributed destructive effects do not gain player permissions; public controls may still activate machines inside their own Settlement as previously specified.

This is spatial protection, not player invulnerability or permanent ownership of every moving entity: PvP, loose-item pickup, and natural entity movement keep their existing rules. Trusted console commands and other plugins that bypass events need their own authorization/integration. These boundaries must remain explicit in operator documentation.

Protection is guarded during startup. Fatal persistence/publication errors and hot unloading trigger a server stop rather than exposing an unprotected Main World. Use full restarts, not plugin reloads.

## 7.6 Claim operations and lookup

The Settlement API must support:

```text
Create settlement
Claim current chunk
Unclaim a chunk
Inspect ownership
Invite / remove member
Assign role
Transfer ownership
Abandon / release
Reclaim released land
```

Claim changes must update durable state atomically before the new permission decision is exposed. Runtime protection lookups should use a bounded cache or spatial index keyed by `(world_instance_id, chunk_x, chunk_z)`, with the database as the durable source of truth.

Claim topology must preserve these invariants:

- the first claimed chunk establishes the Settlement component;
- every added chunk must share an edge with that Settlement's existing component;
- removing a chunk must be rejected if it would split the remaining claimed chunks;
- ordinary unclaim operations must reject removal of the final chunk, including for the Owner; emptying the Settlement requires the Owner's explicit abandon operation, never an implicit abandon through unclaiming;
- two different Settlements may occupy edge-adjacent chunks without merging, even when they share an owner;
- permissions, activity, history, and lifecycle remain independent for adjacent Settlements;
- a configurable policy may impose `max_chunks_per_settlement`; a claim exceeding an enabled cap must fail atomically.

Abandonment requires a separate, explicit confirmation by the Owner. The confirmation must identify the Settlement and clearly warn that its claim protection will be removed. Initiating abandonment or displaying the warning changes no ownership or protection; dismissing it likewise changes nothing. Before applying a confirmed abandonment, revalidate the confirming player's current ownership and the target Settlement. Confirmation does not bypass other applicable abandonment rules.

Abandonment confirmations expire after a configurable duration, defaulting to **60 seconds**. At confirmation, reject requests at or after the recorded expiry. Bind the confirmation to the Settlement's ownership revision and claimed-chunk revision as shown in the warning. Any ownership or claimed-chunk change invalidates it, even if a later change restores the previous owner or chunk set. An expired or invalidated confirmation requires a fresh warning and confirmation; it must not remove protection. Validate expiry and both revisions atomically with the abandonment state change.

Claiming Settlement chunks is free in the initial design: no currency or material payment is required. Expansion is constrained by contiguity and `max_chunks_per_settlement`, not by a claim price. The configurable chunk cap is enabled by default, with `max_chunks_per_settlement = 64`, but may be changed or disabled by server policy. This is a per-Settlement limit, not a per-player total. A Settlement at the cap may contain 64 chunks; claiming a 65th chunk must fail while the default cap is enabled.

Cooldowns, administrator exemptions, and partial release/reclaim behavior remain settlement policy decisions. Any future partial operation must preserve contiguity or explicitly create separate Settlement identities.

## 7.7 Basic visualization

Claim visualization must work with an unmodified vanilla client.

The initial visualization should:

- show the current chunk and nearby settlement borders with temporary particles;
- identify the settlement, owner, and the player's effective role through ActionBar or chat components;
- appear automatically during claim edits and on an explicit view command;
- be visible only to the requesting player;
- avoid permanent marker blocks and full-area scans.

The exact particle style and command syntax remain provisional.

## 7.8 Spatial infrastructure and domain state

Core may supply world/chunk keys and other neutral infrastructure. Settlement owns claim authorization, membership, activity, and lifecycle semantics. Resource worlds remain unclaimed; do not introduce a universal gameplay aggregate controlled by `if world_type == ...` branches.

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

The preferred conceptual model is a score that combines:

- member presence in or near the settlement;
- construction;
- maintenance;
- meaningful interaction with the settlement;
- potentially other settlement-relevant actions.

Owner, Manager, and Member participation is the primary activity source. Meaningful Visitor interactions may contribute a very small amount relative to team participation. Merely passing through, entering, or crossing the Settlement contributes no Visitor activity. Exact eligible interactions, weights, and anti-farming limits remain tuning work; Visitor credit does not grant additional permissions.

The score naturally decays over time. Inactivity should not require a scheduled write to every settlement. Store a value and its calculation time, then evaluate elapsed-time decay when the score is read or receives a new contribution.

Conceptually:

```text
Activity Score
      ▲
      │       member builds / maintains / uses
      │                 │
      │                 ▼
      │             increases
      │
      │        elapsed-time decay
      │              ▼
      └──────────────────────────────► time
```

The exact contribution weights and decay formula remain unresolved.

## 8.3 Automation constraint

Machine operation should not indefinitely replace human activity.

A fully automated machine should not generate infinite settlement activity simply because it continues operating.

Activity therefore measures **meaningful player participation in a settlement**, not raw machine uptime.

## 8.4 Recovery

When meaningful participation restores sufficient activity, the Settlement returns to an active activity state and further decay stops. Merely logging in or generating a single event is not an automatic recovery trigger; the scoring thresholds remain deferred.

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

The selected direction is a hybrid **C-style approach**:

1. player modification records;
2. building structure analysis;
3. rule-based decay.

Buildings are discovered automatically by an algorithm operating within player-claimed Settlements. Players do not register buildings or approve candidates before tracking begins. Player modification records and bounded structure analysis supply discovery evidence; modification records alone are not the definition of a Building.

Each tracked Building has a stable `building_id`, a `world_instance_id`, one responsible `settlement_id`, a footprint, and a versioned restoration baseline. Its tracked footprint must lie entirely within that Settlement's claimed chunks. A physical structure spanning different Settlements cannot be one tracked Building; tracking must respect their separate permission and activity boundaries.

Discovery establishes the initial baseline. Authorized player building edits automatically revise the affected portions of the restoration baseline without a separate approval step. ESMP decay and repair operations change physical condition, not the intended baseline. Updating one edited location must not capture unrelated decay damage as the new repair target.

This choice intentionally treats authorized demolition as a baseline change: repair does not undo it. The baseline is a restoration target, not a general rollback backup. Decay and repair jobs reference a baseline revision and must revalidate it before mutation so queued work does not overwrite a later renovation.

Discovery and rediscovery use bounded, localized work triggered by relevant claim or world changes, not periodic scans of every block in every Settlement. Existing construction inside newly claimed land is eligible for discovery; discovery must not require every block to have a placement event recorded by ESMP. Paper world reads remain on the appropriate execution context; expensive analysis may use captured data asynchronously.

Building identity must survive ordinary rediscovery rather than being recreated on every analysis pass. Detection, split/merge identity rules, and reconciliation when claims change remain later design work. Claim changes must not leave an active tracked Building spanning multiple Settlements. Reclaiming establishes a new active restoration baseline from the inherited structure's current state while preserving its historical records; ordinary rediscovery within the same ownership context does not perform this reset.

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

# 10. Natural World Change

ESMP should not attempt to continuously simulate the entire natural world.

Natural change should be:

- low frequency;
- local;
- event-driven or scheduled;
- bounded in workload.

Potential future examples include:

- vegetation entering abandoned areas;
- small riverbank changes;
- gradual sand movement;
- water accumulation in abandoned structures/mines.

These are architectural examples, not finalized algorithms.

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

Possible command surface:

```text
/settlement create <name>
/settlement claim
/settlement unclaim
/settlement invite <player>
/settlement role <player> <role>
/settlement view
/settlement abandon
```

During claim editing, temporary particles can show chunk borders. ActionBar or chat components can show:

```text
Settlement: Oakfall
Owner: Alex
Role: Member
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

The initial persistence engine is **SQLite**, embedded in the single Paper server deployment. Core provides connection, transaction, and migration infrastructure; each domain owns its repositories, schema, and queries. Durable application state, jobs, and the event outbox share this database so domain changes and outbox records can commit together.

Coordinate writes through bounded scheduling suited to SQLite's single-writer model and keep transactions short. Database I/O must not block the Paper world execution context; world state must not be accessed from database workers. Required migrations complete before dependent gameplay operations are enabled.

Use versioned schema migrations. The initial implementation uses SQLite JDBC 3.53.4.0 with small transactional JDBC migrations tracked by `PRAGMA user_version`, rather than adding a migration framework for the initial schema. Repository boundaries support maintainability, not transparent database portability; a future backend change requires explicit migrations and verification.

Civilization maintains its own `civilization_schema` version 1 in the same database, with `settlements`, `settlement_members`, `settlement_chunks`, `settlement_offers`, and `settlement_events`. Gameplay changes and historical/outbox facts share a transaction. Released Settlement records and events remain; current claim and membership rows are removed.

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

Only naturally loaded chunks are eligible for Decay and Repair mutation. A job resumes through load-driven, budgeted scheduling; unloading before application returns affected work to pending rather than triggering a chunk load. Elapsed-time Activity evaluation continues independently, but unloaded-world mutation is not simulated as completed.

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

# 22. Proposed Core Domain Model

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
│   │   ├── SettlementMember
│   │   └── SettlementChunk
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

---

# 23. Settlement Lifecycle Model

Settlement ownership and physical condition are related but separate state dimensions.

## 23.1 Ownership lifecycle

```text
                    SettlementFoundedEvent
                              │
                              ▼
                          ┌───────┐
                          │ OWNED │
                          └───┬───┘
                              │ release conditions
                              ▼
                        ┌──────────┐
                        │ RELEASED │
                        └────┬─────┘
                             │ new owner claims released land
                             ▼
                          ┌───────┐
                          │ OWNED │
                          └───────┘
                    SettlementReclaimedEvent
```

`Founded` and `Reclaimed` are events, not permanent settlement states. Each ownership change creates an auditable ownership epoch. Reclaiming restores claim protection under the new owner; it does not automatically restore ruined buildings.

Release has two distinct entry paths. The Owner may voluntarily abandon an intact or active Settlement through the confirmed abandon operation; inactivity and ruin are not prerequisites for that path. Automatic release requires both inactivity and ruin conditions. Their numerical thresholds remain deferred.

Both paths remove claim protection only when the release commits and preserve historical records. Abandonment does not itself damage or restore buildings. Until release completes, an owned Settlement remains protected.

### Reclaiming released land

Released land is reclaimed through normal chunk claiming into a new or existing Settlement. Players may claim only part of a former Settlement's footprint; they need not reclaim it as a whole. Ordinary contiguity, exclusivity, world-instance, and configured size-cap rules apply.

The claiming Settlement's current team and permissions govern reclaimed chunks. Former memberships, roles, and settings are not restored. A released Settlement's identity is not automatically revived as the current owning aggregate; historical ownership remains independently traceable.

Reclaiming preserves Building and site history but does not reuse the former intact restoration baseline as the active repair target. Establish a new versioned baseline from the structure's current state in the reclaimed area. This baseline reset changes application state only: reclaiming neither restores blocks nor advances Era. Subsequent authorized edits update the new baseline normally.

Old baseline records, if retained for historical purposes, are not active repair targets. Work queued under the former ownership or baseline must not apply to the reclaimed Building without revalidation against its new ownership context and baseline.

Partial reclaim does not override the one-Settlement-per-Building invariant. Active tracking must respect the claiming Settlement's boundaries while retaining historical links to the former structure; exact split/merge bookkeeping remains deferred.

## 23.2 Activity recovery and physical condition

```text
Activity:
ACTIVE ── insufficient activity ──► DECLINING
ACTIVE ◄── sufficient recovery ─── DECLINING

Building condition:
INTACT ── eligible decay ──► DAMAGED ── eligible decay ──► RUINED
```

Activity and physical condition are separate. Activity recovery stops further decay but leaves existing damage unchanged; restoration requires repair. Era advancement remains tied to completed qualifying restoration cycles, not recovered activity.

Automatic release evaluates inactivity and ruin together, not a single overloaded status. Exact thresholds and how Building conditions aggregate into Settlement-level ruin remain unresolved.

An owned ruined settlement remains protected until a valid release operation completes.

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

This table is a design boundary, not yet a final implementation schema.

---

# 25. Current Decisions

The following decisions are considered settled enough to serve as architectural constraints:

1. Project name: **Evolve: SMP (ESMP)**.
2. **Civilization** names the overall persistent gameplay environment/domain; it is not a player-owned aggregate.
3. **Settlement** is the Main World aggregate for ownership, teams, permissions, activity, and claimed chunks.
4. The Main World is permanent and is the civilization/history world.
5. Resource Overworld, Nether, and End reset together in one shared cycle. All replacements are generated and pre-generated before closing the old cycle; entry reopens only after all three switch together.
6. Resource worlds have no settlements or claims.
7. Claims use Chunk granularity and do not cross physical world instances.
8. Each owned Settlement is one non-empty, four-directionally contiguous chunk component; diagonal contact alone is insufficient. Ordinary unclaim cannot remove its last chunk; the Owner must explicitly abandon instead.
9. Separate Settlements may occupy directly adjacent chunks and do not merge automatically, including Settlements with the same owner.
10. A player may own multiple settlements.
11. Settlement chunk claiming is initially free. The configurable per-Settlement maximum is enabled by default at 64 chunks and may be changed or disabled by server policy.
12. Settlements use exactly four fixed effective roles: one Owner, zero or more Managers, zero or more Members, and every other player as a derived Visitor; custom roles are not supported.
13. Managers are operational deputies: they may change claims, manage ordinary Members and settings, build, interact, and repair, but may not manage Managers, transfer ownership, or abandon/release the Settlement.
14. Members are standard collaborators: they may build, interact, use containers, use protected entities or machines, and repair, but may not change claims, membership, roles, or settings.
15. Visitors use action-specific, adventure-like protection: movement and safe public controls are allowed, while building, containers, protected entities, and direct machine interfaces are denied.
16. Claims protect their blocks and relevant interactions against unauthorized direct and indirect changes.
17. Protection must classify actions rather than switch Visitor game mode or cancel every right-click event.
18. Settlement claims have basic vanilla-client visualization using temporary particles and text UI.
19. Stable `WorldDefinition` identity is separate from physical `WorldInstance` identity.
20. Player inventory, equipment, Ender Chest, experience, and normal player state are shared across realms.
21. ESMP does not create per-realm inventories or item-transfer restrictions.
22. Settlement activity is more than last-login time and is evaluated per settlement.
23. Activity uses a decaying score driven primarily by team participation. Meaningful Visitor interactions contribute very little; passersby contribute nothing.
24. Elapsed-time decay should not require periodic writes to every settlement.
25. Automation should not provide unlimited activity credit.
26. Decay preserves building shape and turns abandoned construction into ruins.
27. Buildings are automatically discovered within claimed Settlements, with one Settlement per tracked Building. Authorized player building edits automatically revise restoration baselines; ESMP decay and repair do not redefine them.
28. Decay and Repair use durable, budgeted jobs and wait for naturally loaded chunks. Conflicting edits trigger replanning of affected work without overwriting player changes or stopping otherwise valid work.
29. Natural-world change is low-frequency and localized.
30. Buildings can be repaired before complete ruin.
31. Repair is a process rather than an instant operation, and villagers/artisans may perform it.
32. A completed qualifying repair advances a building's Era once.
33. Era represents historical restoration cycles.
34. History survives reclaim, but former team permissions and active repair targets do not. Reclaimed chunks use the claiming Settlement's team and normal claim rules; inherited structures receive a baseline from their current state without restoration or Era advancement.
35. Ownership, activity, and physical condition are separate. Recovered activity stops further decay without repairing damage or advancing Era. Voluntary abandonment may release intact Settlements; automatic release requires inactivity and ruin.
36. `Founded` and `Reclaimed` are events rather than durable settlement states.
37. The initial target is Minecraft Java Edition/Paper 26.2, with Java plugin source, JDK 25, and Gradle Kotlin DSL build configuration. Vanilla client compatibility remains a core goal.
38. Folia is a possible future scale target, not the initial platform.
39. ESMP should not initially implement its own fake-player/chunk-loader system.
40. Minecraft world data and ESMP database data are migrated independently.
41. Initial application persistence uses embedded SQLite with versioned migrations, module-owned repositories, short coordinated writes, and off-world-thread database I/O.
42. Application APIs enforce synchronous decisions. Durable domain events use a transactional outbox with at-least-once delivery and idempotent consumers; world mutations use recoverable jobs, not a cross-store atomicity assumption.
43. ESMP deploys as one modular Paper plugin JAR with one bootstrap and coordinated lifecycle. Core remains gameplay-agnostic.
44. The first UI favors commands, ActionBar, BossBar, chat, containers, books/signs, native dialogs, and temporary particles over large custom GUIs.

---

# 26. Intentionally Unresolved

The major domain boundaries are settled enough to plan implementation. The remaining items below are not all prerequisites for starting the project.

| Gate | Required decisions or evidence |
|---|---|
| Runnable foundation (implemented) | Pinned wrapper and dependencies, Java 25 compilation, single-JAR packaging, SQLite world registry, status command, and actual Paper boot/restart verification. |
| Playable Settlements (implemented) | Transactional owning tables, contiguous claims, fixed-role workflows, committed cache publication, history/outbox, Paper protection adapters, and private visualization; verify the target server and other-plugin integrations before production use. |
| Before enabling decay, repair, or automatic release | Define Building split/merge and historical linkage, block-entity/inventory restoration safety, journal/checkpoint recovery, material accounting, and configured activity/ruin eligibility. |
| Before enabling Resource resets | Finish evacuation/retired-location handling and coordinated cutover/backup/restore checks. |

Weights, probabilities, generation appearance, UI polish, and optional features may remain deferred until their implementation slice. This does not permit enabling unsafe or incomplete behavior: satisfy each gate before exposing its feature, rather than blocking all work on every unresolved detail.

The following areas are explicitly **not finalized yet**:

### Settlement

- remaining creation, expansion, and cooldown rules; chunk claiming is initially free;
- administrator exemptions from the default-enabled, configurable 64-chunk cap;
- exact version-specific catalogue of blocks and events classified as safe Visitor interaction;
- alliance relationships between settlements;
- exact Activity Score formula and decay curve;
- exact settlement condition thresholds;
- automatic release thresholds, Settlement-level ruin aggregation, and partial-reclaim behavior while preserving contiguity; voluntary abandonment does not require inactivity or ruin;

### Building / Decay

- discovery and rediscovery algorithms, Building split/merge identity, and claim-change reconciliation; reclaimed structures use their current state as the new active baseline while retaining history;
- exact decay stages;
- block-level selection and probabilities;
- material-specific and structural dependency rules;
- vegetation/weathering rules;
- decay scheduling frequency and prioritization;
- journal/checkpoint implementation and reconciliation of world-save versus database durability; durable recovery, expected-state validation, and affected-work replanning are settled.

### Repair

- exact repair station implementation;
- material valuation;
- villager job implementation;
- repair speed and failure model;
- exact relationship between repair and building state;
- qualification threshold for advancing Era.

### History / Era

- exact historical record and ownership-epoch schema;
- Era naming;
- historical UI;
- public rankings/statistics.

### Resource

- reset interval;
- generation seeds and pre-generation radius;
- archival policy;
- exact player evacuation behavior;
- exact cycle management schema;
- portal routing and travel-access rules.

### Runtime / Persistence

- future dependency upgrades and owning-domain SQLite schema migrations; current tooling, JDBC driver, and foundation schema are implemented;
- transactional outbox storage and retry scheduling; post-commit at-least-once delivery and idempotent consumers are settled;
- coordinated world/database backup procedure;

### UI

- final command syntax;
- final Dialog designs;
- exact ActionBar/BossBar information;
- exact particle visualization style;
- localization structure.

Resolve these at the relevant implementation gate. Default to established technical conventions where choices do not change accepted gameplay; ask the user in batches only for material tradeoffs.

---

# 27. Recommended Development Order

The architecture suggests the following implementation sequence:

```text
Phase 1
  Runtime/build baseline + single-plugin bootstrap
    ↓
  esmp-core
    ↓
  WorldDefinition / WorldInstance registry
    ↓
  SQLite schema migrations (foundation version 1 implemented)
    ↓
  Database + migrations
    ↓
  Event / Scheduler infrastructure

Phase 2
  Settlement ownership + team roles
    ↓
  Ownership events + minimal historical persistence
    ↓
  Claim storage + protection
    ↓
  Claim visualization
    ↓
  Activity
    ↓
  Building tracking
    ↓
  Decay Queue

Phase 3
  Decay Engine
    ↓
  Ruin states
    ↓
  Release / Reclaim

Phase 4
  Repair
    ↓
  Villager / Artisan
    ↓
  Era
    ↓
  History presentation / rankings

Phase 5
  Resource Realm
    ↓
  Resource Cycle
    ↓
  Safe reset lifecycle
```

This order makes claim protection usable before automated world mutation. Persist ownership and historical facts from the first owning-state changes; later History work adds presentation and rankings, not retroactive reconstruction of missing events. Each feature must satisfy its gate in §26 before enablement. Detailed optimization and optional automation stay deferred.

---

# 28. Next Architecture Work

The architecture baseline is ready for implementation planning, not a claim that the full plugin specification or implementation is complete.

## Immediate next action

Run the documented native Minecraft 26.2 verification loop with two accounts: create/expand a Settlement, check Visitor versus Member actions, inspect private borders, and restart to confirm persistence. Keep the tested plugin artifact and full-restart workflow; do not enable unfinished automated mutation systems.

## First functional slice

Settlement persistence, membership/ownership workflows, claim protection, basic visualization, and committed history are implemented on the verified world/SQLite foundation. The next feature work is Building discovery and Activity, after resolving their remaining gates. Durable block mutation/recovery and material accounting still precede decay/repair enablement.

## Later feature gates

- Building discovery must resolve split/merge historical linkage and safe baseline representation before mutation depends on it.
- Decay/Repair must prove idempotent recovery, edit-conflict preservation, material accounting, and loaded-chunk scheduling before enablement.
- Resource cutover must prove evacuation, backup/restore safety, and restart-safe routing before use.

Detailed scoring, decay probabilities, particle styles, and generator aesthetics belong to those implementation slices. They are not reasons to continue a broad yes/no architecture questionnaire.

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
