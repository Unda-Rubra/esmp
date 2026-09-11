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

The accepted economy/razor addendum is now merged into this document. The target is a scarce integer ledger currency, immutable land valuation, paid Claims, two-state Settlement certification, and administrator-reviewed ownership acquisition. The current JAR still uses numeric-only area quotes, a direct naming-certification toggle, and the existing two-party transfer flow; this documentation merge does not implement the new ledger, fees or certification workflow.

The Factory Realm is removed from the current scope following the team's RPG-survival balance and performance review. It is not a scheduled milestone or dormant runtime module. This cut removes the dedicated realm and industrial systems; it does not add a redstone ban to the remaining worlds. Reintroduction requires a new explicit decision.

The protected core is Settlement/Parcel/Protection → Activity → Building identity/baseline → Decay/Repair/History → the required Dynamic Settlement Cycle. Currency and land pricing support this loop. Facility recognition starts as lightweight site identity; production, cultural ranks, freight, teleport and gameplay seasons are later, need-driven extensions rather than core prerequisites. The Factory Realm remains removed.

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
│   ├── Building / Settlement Certification
│   └── History / Era
│
└── esmp-resource
    └── Renewable Resource Worlds / Cycles
```

The bootstrap composes Core and gameplay modules and coordinates database migrations, startup, and shutdown. Module boundaries remain explicit application APIs and committed domain events; sharing one artifact does not permit cross-domain business-state access. Independent plugin deployment, classloader boundaries, and cross-plugin version compatibility are not part of the initial design.

The near-term extension adds logical Economy services alongside Civilization, not speculative new Gradle modules. Civilization owns land/certification/claim semantics; Economy owns currency, accounts, issuance and monetary transactions (§16A.7). Core remains infrastructure-only. Add packages/modules when their real implementation requires them; do not scaffold deferred Transport, Seasons or workforce systems.

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
- lightweight Era facts and historical records;
- UNVERIFIED/VERIFIED Settlement certification and direct capability qualification;
- immutable Main World LandValueProfiles, land-acquisition policy and Activity eligibility;
- lightweight Facility recognition when its post-core slice begins.

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

Settlement certification is separate from ownership and Activity:

```text
SettlementCertification
├── settlement_id
├── status = UNVERIFIED | VERIFIED
├── eligible_since_game_tick (optional)
├── verified_by_uuid (optional)
├── verified_at_game_tick (optional)
└── note / decision provenance (optional)
```

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

Target transfer flow: Owner requests → recipient accepts → simple land-based Acquisition Fee is computed → administrator reviews/approves → fee and ownership change commit atomically through the owning transaction workflow. Acceptance alone must not transfer ownership in the new flow. Revalidate participants, ownership, relevant revisions, administrator approval and available recipient funds at commit. The fee is a SINK, distinct from any private compensation (§16A.5). The current two-party implementation requires migration before this behavior is enabled.

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

Target Claim prices use immutable land base value, player-wide portfolio leverage, Settlement-size leverage and a light Activity/development adjustment, plus personal early-land subsidies. Founding a separate nearby Settlement also has a same-owner proximity surcharge; ordinary expansion does not. The first-ever founding Claim costs exactly **25**, overriding ordinary valuation. Detailed formulas and subsidy persistence are canonical in §§16A.3–16A.4. Claim payment must reject insufficient funds and commit once with the owning claim/history state. The existing area-only numeric quote remains a legacy runtime behavior until the ledger/valuation cutover, not an alternative target pricing model.

The Settlement API must support complete command-driven administration for creation, claims, unclaims, inspection, membership/governance, bans, Parcel management, permission policy, naming administration, transfer, abandonment, and later lifecycle operations. Commands are the canonical complete interface; convenience tools call the same application services and must not implement separate business rules.

Every newly founded Settlement begins **UNVERIFIED**, with only its stable number/generated label. It may claim land, build, use protection/governance and accumulate Activity. It cannot obtain an official public name or appear as a normal named Settlement on the public website/map; selected civic capabilities may explicitly require certification.

After a configurable Activity/meaningful-development gate, governance may apply for administrator certification. Review concerns civic quality and exploit resistance, not aesthetic scoring; a technically active but exploit-oriented enclosure may be declined. Denial leaves the Settlement functional and owned, in UNVERIFIED status. On approval it becomes **VERIFIED** and may receive a separately administrator-approved official name, public named listing and directly qualified future civic capabilities.

This is one two-state governance filter, not an economic level or rank ladder. Do not add VERIFIED_II or recreate Settlement/Village/Town/City stages. All later official renames remain administrator-mediated and historical; naming/rename fees are explicit service sinks where configured. Existing internal UUIDs/public numbers and historical names must be preserved through an explicit migration policy. The current boolean naming toggle and grandfathered names are implementation history, not proof of the new eligibility/application/provenance workflow.

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

The issued written book and stick use `/esmp tools management_book` and `/esmp tools parcel_wand`, available to players with `esmp.settlement`; status remains administrator-only. Existing item identities, duplicate checks and the 40-game-tick issuance cooldown are preserved. Player text describes the action or tool briefly in ordinary language. Tool lore and success messages omit implementation disclaimers; useful rejection reasons and internal audit facts remain. Existing tool descriptions refresh on join, hotbar selection and use.

The wand continuously highlights its target with a dense blue particle surface. A block hit within the player's interaction reach selects that block's containing 8×8×8 cell. Otherwise, a camera ray selects the first cell beyond the player's occupied camera/body cells in the facing direction. Ray tracing clips before unavailable chunks. Left/right clicks on blocks or air share this same target; no-coordinate `parcel pos1/pos2` commands do too. Right-click swing events are deduplicated so finishing a selection does not reset its first corner.

Valid previews use blue wire borders, existing Parcels use green exposed-face edges, and invalid or excluded selections use red borders. Invalid drafts remain visible and cannot commit. After the first corner, the preview follows aim until the second corner is set; confirmation clears the finished selection. Target changes are sampled every two server ticks, with immediate redraw on a changed target/draft and refresh every four ticks otherwise. The target surface has 386 unique grid points; the per-viewer overlay cap is 1536 particles per frame. Nearby geometry and validation are cached against position, selection and committed publication version. Parcel previews still expire after 1200 game ticks, remain session-local and are revalidated by authoritative transactions.

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

Activity policy is configurable before initialization and persisted as a contract. Changing an installed value policy requires an explicit migration. `/settlement activity [name]` and `info` show score, state, today's credit and recovery need in concise player text; the application API retains contribution timestamps, inactivity timing and pause intervals for consumers.

## 8.3 Automation constraint

Machine operation should not indefinitely replace human activity.

A fully automated machine should not generate infinite settlement activity simply because it continues operating.

Activity therefore measures **meaningful player participation in a settlement**, not raw machine uptime.

Automation, including any future production or transport system, grants no Activity. Existing game-time inactivity/pause-interval APIs may support later consumers, but production simulation is not an Activity or Repair prerequisite. Future offline accounting must remain bounded and must not invent progress while the shared game clock is frozen or the server is stopped.

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

Each tracked Building has a stable `building_id`, `world_instance_id`, one responsible `settlement_id`, a footprint and a versioned restoration baseline. The footprint lies entirely inside one Settlement. Ordinary expansion/contraction preserves identity when it is obvious. Major structural reinterpretation may retire the old identity and discover one or more new Buildings, keeping lightweight predecessor/history links when obvious. Preserve old history; a general graph-matching or perfect split/merge identity solver is not an MVP Decay/Repair gate.

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

Initial structural decay uses small material/profile and weathering/integrity sets with **DROP** and **CAVE_IN** as the required collapse vocabulary. TOPPLE is deferred until visual quality demonstrates a need. Full rigid-body physics, finite-element analysis, continuous rotations and sophisticated collision synthesis are not prerequisites for proving the ruin loop.

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
- a workshop repair point.

The MVP requires a repair point/station, the required physical materials and shared game time. It does not require live villagers, professions, pathfinding, entity survival or workforce simulation.

## 11.1 Repair as a process

Repair should generally take time.

Conceptually:

```text
Player supplies resources
        ↓
Repair job created
        ↓
Budgeted repair work advances with game time
        ↓
Building gradually restored
        ↓
Repair completed
        ↓
Qualifying restoration records lightweight Era/history progress
```

Instant restoration is not the preferred model.

The MVP exposes Repair primarily as a whole-Building operation, even if the implementation applies bounded local batches internally. Repair restores only still-valid damage against the current active baseline. Authorized renovations, newly created interior space, and retired baseline fragments are preserved rather than overwritten. More detailed player-facing scopes such as “repair only the east wing” are deferred until needed.

System decay must not create ordinary collectible block drops, and repair/material accounting must be recoverable rather than assuming atomicity between SQLite and Minecraft world state.

---

# 12. Optional Villager / Artisan Extension

Villagers/artisans are deferred enhancements for workforce flavor, repair efficiency, lightweight Facility integration or cultural meaning. Entity lifecycle, pathfinding, profession, death and chunk loading must not block MVP Repair. Add those concerns only when their gameplay benefit justifies them; materials plus game time are sufficient for the initial repair process.

---

# 13. Lightweight Era Semantics

Era is a Building historical counter/state advanced by qualifying completed construction → decline → restoration cycles and recorded in History. It is not elapsed calendar age, a Settlement economic level or a new progression subsystem.

MVP implementation needs only the counter/state, qualifying-cycle rule and durable historical fact. Era-specific abilities, rankings, names and dedicated UI are later presentation/content work, not prerequisites for the core loop.

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

Initial operation may use administrator-triggered preparation/activation commands or a verified maintenance procedure. The world roles, fresh instance identities and safe cutover invariants still apply; fully automatic cycle orchestration is a mature implementation target, not a blocker for other core systems.

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

## 15.3 Safe reset lifecycle

The sequence below describes the mature lifecycle and the safety obligations of operator-driven resets. Initial implementation may execute it through administrative operations or a documented maintenance window instead of an automatic scheduler. Replacement worlds must be ready before public cutover, and populated worlds must never be deleted live. Full automated generation/evacuation/activation/archive/recovery follows only when reset frequency justifies it.

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
- remaining game-time work;
- progress.

The first version should avoid a large custom RPG-style menu hierarchy.

---
# 16A. Economy, Currency, and Land

The accepted economy addendum and its final razor review supersede provisional economy scope. This is the target design, not a claim that the currently numeric-only runtime already has accounts, monetary issuance, land valuation or the new transfer/certification workflow.

## 16A.1 Economic purpose and currency

Use one scarce, server-wide **virtual ledger currency** with low integer denominations. Currency supports exchange, land, civic/public services, certification and administration; ordinary survival, exploration, mining, crafting and building remain available without money. Physical materials remain physical requirements rather than being replaced by currency.

Persist Player Wallet and Settlement Treasury accounts. Diamonds, emeralds, notes or custom coin items are not authoritative money. Physical vouchers/bearer tokens and multiple-currency gameplay are deferred.

Keep a stable internal `currency_id` separate from configurable `singular_name`, `plural_name`, `symbol` and `format`. Changing display names or symbols must not redefine account identity or migrate balances. Store balances, prices and transaction amounts in integer units (`long` or equivalent), never `double`; specify checked arithmetic and deterministic integer rounding before implementation.

Operating-scale targets: starting funds approximately 100; ordinary balances in the hundreds; wealthy balances in low thousands; very wealthy balances in several thousands; periodic issuance budgets ordinarily at most tens of thousands. These are tuning targets, not hard wealth caps. One unit should matter, ten be noticeable, one hundred significant and one thousand substantial.

## 16A.2 Ledger invariants, issuance and sinks

Wallets and treasuries remain non-negative. Reject any payment that would overdraw, including Claims, acquisition, naming/certification and other system services. Debt is not a negative balance. Informal promises stay social; formal loans, invoices, interest, collateral, default and bankruptcy require a separate future domain.

Distinguish monetary movement types:

| Type | Meaning | Circulating supply |
|---|---|---|
| TRANSFER | Move existing money between authorized accounts | Unchanged |
| ISSUANCE | Create server-authorized money | Increases |
| SINK | Destroy money for a system charge | Decreases |

`M(next) = M(now) + issuance - sinks`, where circulating supply includes player wallets and Settlement treasuries. Peer trade/donations redistribute money; they do not mint it. The server is not an ordinary cash account whose buys/sales must balance.

Every durable movement has a stable `transaction_id`, affected account(s), integer amount, explicit reason, game-tick/timestamp provenance and optional metadata. Retry cannot commit the same transaction twice. Monetary state and its durable audit result must agree; pure ledger/claim transactions use coordinated owning-domain APIs, not an asynchronous event as retroactive admission control.

Initial reasons include STARTING_FUNDS, PLAYER_TRANSFER, SETTLEMENT_DONATION, CLAIM_PURCHASE, SETTLEMENT_FOUNDING, SETTLEMENT_ACQUISITION_FEE, NAMING_FEE, RENAME_FEE, EVENT_REWARD, EXPLORATION_REWARD, ADMIN_GRANT and REFUND. SERVER_TRADE_PURCHASE/SALE distinguish the later trade paths; add Facility/market/transport reasons when their real services exist. Reason names and buy/sell direction must be explicit, not a generic money-change event.

All server issuance is constrained by a periodic **Server Monetary Budget**: `actual issuance <= configured budget`. It is a ceiling, not a distribution target; unused capacity need not be paid out. Accepted channels are starting funds, controlled administrative/public rewards, exploration/event rewards and later Server Trade purchases from players. Repetitive block breaking, ordinary mob kills, walking, online time and generic jobs loops must not automatically mint money.

Prefer coarse, configurable active-player budget tiers over a complex formula (illustrative bands: 1–5, 6–15, 16–30, 30+ genuinely active players). Do not scale linearly with online/alt/AFK count. Define period/eligibility/missed-period handling before enabling issuance. The global budget and idempotent one-time starting grant must exist before the first issuance channel is exposed; trade-specific quotas come with Server Trade.

Preferred sinks are Claim creation/expansion, acquisition fees, official naming/rename, Facility certification and explicit system services. Market, teleport or freight fees apply only if those deferred services are introduced. Direct peer transfers have no generic movement tax. Avoid passive land/maintenance taxes, automatic inactivity charges, confiscation and hard balance caps. Future old-city preservation spending is deliberate investment, not offline punishment.

Treasury access remains governed by Settlement authorization and audit: Owner-controlled withdrawals and minimal approved Governor spending do not require advanced appropriation law. A treasury remains Settlement property after ownership transfer; it never silently becomes the incoming Owner's personal wallet.

## 16A.3 Immutable generated-land valuation

Every Main World chunk receives its **LandValueProfile at first generation**, before player terrain/building changes can manipulate the assessed base. Minimal persisted record:

```text
LandValueProfile
├── world_instance_id
├── chunk_x
├── chunk_z
├── valuation_version
├── base_value
└── valuation_class (optional, UI/debug)
```

Coarse natural factors may include terrain class, slope/buildability, biome/environment desirability, river/coast access and special natural features. Coefficients are configurable and explainable, not a real-estate simulator. Sample factors to compute the immutable base; discard intermediate features unless an actual diagnostic consumer justifies retaining them.

Flattening hills, excavating, filling coastlines, roads or cities cannot retroactively change the stored base value. Preserve valuation versions; revaluation requires an explicit approved operation/migration. Existing pre-feature chunks need a backfill policy that does not pretend player-modified terrain is pristine. Generation capture, persistence/recovery and legacy backfill are implementation gates, not permission for live global scans or arbitrary async Paper access.

## 16A.4 Claim price and personal subsidies

For ordinary expansion:

```text
C_expand = B_terrain × P_portfolio × S_settlement × A_activity × D_early
```

For founding a separate Settlement:

```text
C_found = B_terrain × P_portfolio × N_proximity × D_founding
```

- `B_terrain` comes from the immutable/versioned LandValueProfile.
- `P_portfolio` reflects the assessed player's total owned Settlement count and total claimed area. Configurable tiers can combine both dimensions so neither one giant city nor many tiny Settlements bypass leverage.
- `S_settlement` rises with the area of the Settlement being expanded.
- `A_activity` is a light Activity/meaningful-development correction, not a dominant discount, punishment for activity or incentive to farm Activity. It does not depend on a removed economic-stage ladder.
- `N_proximity` applies only to CREATE_NEW_SETTLEMENT, based on distance to that player's existing Settlements. Nearby separate founding should usually cost more than normal expansion; expansion receives no founding proximity surcharge.

Example proximity bands (0–3 chunks ×2, 4–7 ×1.6, 8–15 ×1.3, 16–31 ×1.1, 32+ ×1), portfolio tiers and size coefficients are illustrative tuning, not hard-coded balance decisions. Define distance measurement, the assessed player/payer when governance acts on behalf of a Settlement, integer rounding and quote revision checks before payment implementation.

**First-ever founding Claim: fixed price 25**, overriding normal valuation. Subsequent personal early-land discounts may diminish (illustrative second through fifth Claims: ×0.50, ×0.65, ×0.80, ×0.90; later ×1). Those later coefficients remain configurable.

Persist personal `PlayerLandProgress` with `first_claim_used` and `subsidized_claims_used` or equivalent. Selling/transferring, abandoning or founding another Settlement does not reset the subsidy, and prior ownership's subsidy history never transfers to a new owner. Legacy acquisition history requires an explicit migration policy.

Quotes explain the final integer price and major causes—base land, portfolio, Settlement scale, light Activity correction and any own-nearby-founding surcharge—without dumping every coefficient. Revalidate price context, subsidy state, topology, ownership/permission and sufficient funds at commit; debit, consumed subsidy and Claim/history changes must not partially commit.

## 16A.5 Peer transfers and Settlement acquisition

Direct peer payments move existing currency and remain atomic. A short `/pay` interface may use configurable per-transaction or newcomer-period limits and operator-visible large-transfer audit if needed. Settlement-size-based Economic Receiving Capacity is **deferred**, not an MVP prerequisite or a hard cap on wealth.

If later evidence warrants receiving capacity, it would constrain peer-origin inflows rather than legitimate Server Trade income, rewards, starting funds or other self-earned/server-issued money. An over-limit transfer must reject in full, not partially fill to the ceiling, and report what is receivable. Such a future model must cover player→player, player→recipient-controlled treasury, treasury→player and treasury→treasury routing; treasury capacity need not equal wallet capacity. Do not implement that model merely because land tiers are available.

Settlement succession remains supported. Initial target flow is Owner request → nominated recipient acceptance → simple land-based Acquisition Fee quote → administrator review/approval → atomic fee/ownership transfer. Existing membership eligibility, single-Owner and former-Owner-to-Citizen rules remain. Pending acceptance/approval changes no ownership. The current runtime's two-party transfer must be explicitly migrated before this new contract is advertised.

The incoming Owner pays a **SINK** Acquisition Fee derived from immutable land values under current configured acquisition rules; a suitable initial form is `k × sum(BaseLandValue_i)` with configurable `k`. No predecessor early-land subsidy is inherited. Do not anchor the fee to historical cheap Claim purchase prices or appraise Buildings, labor, stock, railways, Facility or cultural value.

Private compensation in money, items, services or none is a social agreement; ESMP does not enforce a canonical sale price. The treasury stays attached to the Settlement and outgoing money still requires treasury authorization and ledger audit. Repeated-transfer cooldowns are optional later controls. A fully automatic fee `f(land profiles, recipient portfolio, size, recipient proximity)` remains a future automation option justified by transfer volume, not an additional MVP formula.

## 16A.6 GameClock and optional calendar

Keep the implemented shared GameClock for Activity, jobs, cooldowns, monetary periods and lifecycle work. Gameplay timing uses game ticks/days, not real-life elapsed time. `advance_time=false` freezes time-driven progression; stopped-server time gives no catch-up. Changing tick rate changes real-world pacing, not results per elapsed game day.

The loaded MAIN world's `getFullTime()` supplies forward deltas, including sleep and forward time commands. Rewinds re-anchor without subtracting reckoning time or reversing completed work. Initial installation starts at tick 0; boot resumes persisted reckoning time and re-anchors to the loaded source. Source UUID replacement fails rather than silently resetting chronology; Resource resets do not drive the clock.

Game-timed domain writes checkpoint with their transaction; otherwise clock-only checkpoints occur every 200 game ticks and at normal shutdown. Uncheckpointed time/observations may be lost on abrupt failure; this does not replace paired world/database restoration. Old wall timestamps remain diagnostic/legacy provenance, not gameplay inputs.

Optional calendar presentation retains one Minecraft day per calendar day, 365/366-day years starting at 0 A.D.; leap/season presentation details remain separate. **GameClock is retained infrastructure; calendar presentation is optional; gameplay Seasons are deferred content.** Seasonal production/demand, reversible snow/vegetation and other seasonal presentation do not block the core or Dynamic Settlement Cycle. Full seasonal palette techniques remain later design.

## 16A.7 State ownership and recovery boundaries

| Logical owner | Authoritative responsibilities |
|---|---|
| Civilization | Settlement/Parcel governance, certification, Activity, immutable land profiles, personal land-subsidy progression, Claim/ownership acquisition and Building/history semantics. |
| Economy | Stable currency identity, wallet/treasury ledger, typed movements, issuance budget, monetary audit; later Server Trade stock/quotas/quotes. |
| Core / Paper adapter | Neutral world identity, GameClock/persistence/scheduling infrastructure; world-generation capture, item interaction and physical mutation on the proper execution context. |

These are responsibilities inside one plugin lifecycle, not pre-created module/table/event classes. Use explicit owning APIs and short coordinated SQLite transactions for ledger/Claim state; stable transaction IDs and committed facts provide retry/audit semantics. Never authorize first and try to fix an overdraw with a later event.

Physical item/material exchange cannot share one atomic transaction with Minecraft world state. Its prepare/apply/reconcile protocol, permissions, capacity checks and backup/restore safety are gates for Repair and later Server Trade. Generation-time valuation likewise needs recoverable capture/persistence. Do not add arbitrary async world reads, force-load chunks or scan all inactive Settlements.

## 16A.8 Server Trade — post-core backstop

Server Trade is a bounded commodity backstop/price anchor after the core Dynamic Settlement Cycle, not the primary player market. It offers controlled conversion of basic goods into scarce currency and limited emergency goods access.

Normalize narrowly whitelisted item forms into commodity units and whole trade lots, not generic decomposition of every item into its supposed wood/metal contents. Example categories include TIMBER, STONE, FOOD, CLAY, GLASS and BASIC_METAL. Illustrative conversions such as one log or four planks per timber unit, or lots of 32 stone/16 logs, require validation; crafting/conversion loops must not increase value. Exact forms, lot sizes and purchase/sale eligibility remain configuration.

Finite category stock behaves as follows:

| Player action | Items/stock | Money movement |
|---|---|---|
| Sells eligible goods to server | Player goods decrease; server stock increases | Player wallet receives ISSUANCE within budget/quota |
| Buys goods from server | Server stock decreases; player receives goods | Player wallet pays a SINK |

The server is not a normal cash account. Limited essential-goods restocking may be explicitly configured, small relative to player production and visible as server policy. It must not masquerade as player-sourced stock or turn Server Trade into the main supplier.

Category parameters include base/target/min/max price or stock values as applicable: `base_price`, `target_stock`, `min_price`, `max_price`, `spread`, `current_stock`, `trade_lot_size`, `period_buy_quota` and optional per-player quota. Purchases from players are simultaneously limited by global remaining issuance, category buy quota, optional player quota and stock-demand/maximum-stock thresholds. Quote the currently purchasable quantity before taking items; revalidate/reserve all constraints at commit.

MVP pricing uses **discrete stock bands with fixed multipliers**, for example VERY_LOW_STOCK / LOW_STOCK / NORMAL / HIGH_STOCK / VERY_HIGH_STOCK. Reprice only at coarse economic periods of one or several game days, with stable prices inside the period. Low stock can raise prices, high stock lower them or pause server purchases. Player-sell receipts and player-buy costs have a fixed spread, integer quotes and min/max bounds; rounding must preserve their distinction so player trade can beat both sides.

Continuous stock pressure is only a deferred tuning alternative: `P_mid = clamp(P0 × (1 - alpha × (S-T)/T), P_min, P_max)`, with player-sell/player-buy quotes around it by spread. Hysteresis is also deferred unless observed band flapping warrants it; neither is required by the MVP.

## 16A.9 Economic feedback and observation

Use transient, event-driven feedback: small money changes through ActionBar/light sound; important income/expenses through Title/Subtitle/sound; milestones through short sound/Title sequences and limited particles. Optional internal MINOR/NORMAL/MAJOR/MILESTONE categories help avoid fatigue. A first starting-funds grant is a one-time onboarding moment with configured amount (target approximately 100), currency name and final balance, not a hidden mutation.

Management Book/commands aggregate wallet, treasury, recent transactions, Claims, governance/bans, Parcels, Activity and history; recognized Facilities appear when implemented. A short `/balance` command is appropriate. BossBar can show real process progress; optional Scoreboard/HUD views must not become a mandatory permanent status block. Keep player text concise and explain the action.

MVP monitoring is observational: wallet supply, treasury supply, total supply, issuance/sinks per period, median wallet, top-balance distribution and Server Trade volume/stock when available. Operators tune from real data. Money per genuinely active player may be informative but is not the sole signal. Commodity-basket purchasing-power indexes and automatic central-bank/budget reactions are deferred; do not introduce hard wealth caps or confiscation through analytics.

## 16A.10 Lightweight recognition and deferred extensions

The first Facility model is a recognized site/capability record: `facility_id`, `settlement_id`, `building_id` or bounded region, `type`, and enabled/certified state. Farm, Workshop, Market, Station and Port are examples. Recognition does not require workers/housing, shared production-capacity budgets, offline accrual, depot ledgers or production chains; add them only when actual gameplay need justifies a later extension.

The four-level Settlement/Village/Town/City economic ladder is removed. Capabilities use direct feature-specific qualification; UNVERIFIED/VERIFIED is only the separate governance/publication gate in §7.6. Cultural Standing is deferred; History, lightweight Era and landmark/site flags preserve meaning without another score or rank.

Abstract freight is deferred in full, including routes-as-cargo-services, reservations, throughput, nested cargo accounting and destination recovery. Public Settlement teleport is also deferred and, if later needed, must be a separate service independent from freight or removed stages. Walking, roads, rails, boats and terrain remain the default geographic experience; infrastructure may be recognized for history/maintenance without shipment simulation.

Advanced production, formal credit/debt, interest/bankruptcy, physical bearer currency, multiple primary currencies, securities/complex markets, enforced private property-sale contracts, passive taxes, advanced treasury appropriation law, commodity futures/orders, gameplay seasons and automatic monetary policy remain outside the core MVP. None may become a dependency of required Dynamic Settlement Cycles.

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

Accepted near-term state adds currency definition, wallet/treasury accounts, typed monetary transactions and issuance budgets; minimal immutable LandValueProfiles; personal land-subsidy usage; two-state certification/application provenance; and administrator-approved acquisition-fee work. Server Trade stock/quotas and lightweight Facility identity follow after the core cycle. Detailed workforce/production, freight, cultural ranks and seasonal state are deferred, not initial tables to scaffold. §16A.7 defines logical ownership.

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

Physical material and Server Trade item exchange must use recoverable DB-to-world workflows; pure monetary balances/fees can share the owning SQLite transaction with claim or ownership changes through explicit domain APIs. Future stock/transport/season features have their own gates and are not prerequisites for these core transactions. The existing Settlement outbox alone does not establish safe inventory/material recovery.

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

Create domain events only for real durable consumers or historical facts. Preserve stable event IDs, transactional outbox storage and post-commit delivery, but do not predeclare a catalogue of speculative event classes for deferred systems. Lightweight Era or monetary history can be represented by the simplest durable fact that satisfies the actual consumer.

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

If later Facility production or other optional simulation is introduced, it must use bounded due work and preserve the separation between ledger accounting and physical world mutation. Those systems are not prerequisites for the core MVP or Dynamic Settlement Cycle; all Paper world access remains on the proper execution context.

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

The target adds Settlement certification, LandValueProfile and PlayerLandProgress alongside a logical Economy owner for currency/account/transaction state (§16A.7). Facility recognition remains lightweight and later. These are gameplay responsibilities, not additions to infrastructure-only `esmp-core`; deferred domains do not require empty runtime modules.

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
| Settlement certification / recognized sites | Settlement-local | No Settlement sites |
| LandValueProfile | Immutable generated-chunk valuation | Not a Claim pricing source |
| Player currency | Shared player wallet | Same wallet; no per-realm balance |
| GameClock | Retained MAIN source | Resource resets do not restart reckoning |

This table is a design boundary, not yet a final implementation schema.

---

# 25. Current Decisions

The following summarize the current target; implementation status is recorded at the start of this document.

1. Civilization is the persistent Main World gameplay domain; Settlement owns governance/Claims/Activity. Main World is permanent and Resource instances are renewable; player inventory and Ender Chest remain shared.
2. Claims remain full-height 16×16 chunks, four-face contiguous within one world instance. Adjacent Settlements never merge automatically; one player may own multiple Settlements.
3. Owner/Governor/Citizen/derived Public and local BANNED remain. Owner identity is unique; Holder is an independent Parcel relationship, not a governance role.
4. Parcels use world-aligned 8×8×8 non-overlapping, six-connected cells within one Settlement. Assigned changes preserve governance/all-Holder consent and administrator dispute handling.
5. Authorization retains administrator/ban precedence and BUILD/INTERACT/CONTAINER/AUTOMATION relation sets, with both protected sides checked for indirect transfers. Commands are complete; book/wand items carry no authority.
6. Currency is one scarce, configurable-display ledger unit with integer wallet/treasury balances. No physical money store, floating-point amounts, negative accounts, formal debt or hard wealth caps in MVP.
7. Monetary TRANSFER / ISSUANCE / SINK are distinct, with stable transaction IDs, explicit reasons and game-time provenance. Peer transfers conserve supply; retries cannot commit twice.
8. All issuance—including starting funds (target approximately 100), administrative/public rewards and later Server Trade purchases—is budgeted. No per-block/mob/walking/online jobs mint. Budgets are ceilings, not payout targets.
9. Development/service costs are sinks; no generic peer-transfer tax, passive land tax, inactivity charge or wealth confiscation.
10. Main World chunks receive minimal immutable/versioned LandValueProfiles at first generation. Player terrain/building changes do not alter base value; approved migrations govern revaluation.
11. Claim cost uses land base, player portfolio, Settlement size, light Activity and personal early subsidies. Separate nearby founding adds same-owner proximity leverage; ordinary expansion does not.
12. The first-ever founding Claim costs 25. Early subsidy usage belongs to the player and neither resets nor transfers through ownership changes, abandonment or new Settlements.
13. Settlement certification is only UNVERIFIED / VERIFIED. Activity/development eligibility permits an application; administrator approval unlocks official-name/publication eligibility and explicitly qualified civic capabilities. Denial preserves Claims and normal governance.
14. Settlement/Village/Town/City economic stages are removed. Cultural Standing is deferred; History, lightweight Era and landmark/site flags carry near-term meaning.
15. Initial ownership transfer requires recipient acceptance, administrator approval and a simple land-based Acquisition Fee paid by the incoming Owner. No inherited subsidy, historical-price anchoring or automatic Building/labor appraisal. Treasury stays with the Settlement; private compensation remains social.
16. Activity stays local and meaningful, independent of login recency. Automation grants no Activity. GameClock is retained; calendar presentation is optional and gameplay Seasons are deferred.
17. Building identity survives obvious expansion/contraction. Complex reinterpretation may retire/rediscover identities with lightweight predecessor links; preserve old history rather than demand perfect graph matching.
18. Renovation-safe geometry/baseline fragments, block-entity safeguards, durable budgeted work, natural chunk loading and expected-state revalidation remain core. System decay creates no ordinary collectible drops.
19. MVP collapse modes are DROP and CAVE_IN. TOPPLE/full physics are deferred.
20. MVP Repair requires a station/point, materials and shared game time. Villager/artisan simulation is optional later work. Era is a lightweight qualifying-restoration counter/state in History.
21. No automatic inactivity/ruin-driven ownership release in MVP. The Dynamic Settlement Cycle is the required next major phase immediately after the core MVP.
22. Server Trade and lightweight Facility recognition follow the core cycle. Trade uses finite category stock, narrow unit/lot normalization, budget/quota admission, stable coarse-period discrete stock bands and spread.
23. Facility starts as identity/type/region/enabled-certified state, not workforce/housing/offline production/depot/capacity simulation.
24. Monitoring is observational and feedback transient. Automatic monetary policy, basket indexes and a mandatory permanent HUD are deferred.
25. Peer receiving capacity and fully automatic portfolio-based ownership revaluation are deferred; simple transfer controls may be added if needed. Any future receiving cap must address treasury routing and distinguish self-earned money.
26. Safe operator-driven Resource preparation/activation may precede full reset automation. Automated orchestration must earn its complexity through actual reset frequency.
27. Freight, public teleport, advanced production, gameplay seasons, cultural ranks and optional analytics cannot block the core cycle. Later world evolution/disasters/Epoch systems retain their independent design gates.
28. Keep one modular Paper lifecycle, infrastructure-only Core, explicit owning APIs and durable outbox semantics. Add actual events/consumers, not speculative event catalogues; never scan every Settlement block globally.

---

# 26. Deferred and Open Design

## 26.1 Dynamic Settlement Cycle — required immediately after core MVP

The core MVP ends after Settlement/Parcel/economic land foundations, Building identity/geometry, and durable Decay/Repair/History. The next required phase is Dynamic Settlement Cycles, before Server Trade, Facility recognition, reset automation and optional production/transport/seasons.

Candidate mechanisms remain Civic Capacity/maintenance demand from meaningful local Activity and demonstrated development, gradual peripheral decline/release, mature-city versus new-Settlement incentives, selective preservation and history-preserving reclaim. No removed economic-stage ladder is required. Resolve formulas, UI, ownership/Holder guarantees, timing, contiguity and protection of historic cores before automatic ownership loss. MVP Settlements remain persistent until explicit authorized lifecycle action.

## 26.2 Ledger, valuation and certification cutover gates

- Choose the stable currency identifier/display configuration, account/repository model, checked integer amount/rounding rules, transaction IDs/reasons, authorization and atomic/recoverable movement semantics. Non-negative accounts and no duplicate commits are hard invariants.
- Define monetary periods, genuinely active-player budget tiers, budget reservation/carry-over/forward-clock-jump handling and legacy-player starting-funds eligibility. Budget enforcement precedes any issuance, including onboarding.
- Capture LandValueProfile from initial generation safely and durably; resolve pre-existing-chunk backfill, valuation versions/revaluation authority and absent-profile behavior before paid Claims. Do not assess edited terrain as immutable natural evidence without an approved policy.
- Define player portfolio/size tiers, light Activity correction, distance metric/proximity bands, deterministic integer rounding, payer/assessed-player identity for Governor actions, quote revisions and personal subsidy-history migration. First-ever founding price 25 is settled; later coefficient examples are tuning.
- Add Activity/development eligibility, certification application/review/provenance and official-name/map-publication gates. Resolve legacy naming-toggle/name migration and any later revocation rules without silently deleting protected Settlements.
- Migrate two-party transfer offers to recipient acceptance plus fee quote/admin approval and atomic acquisition. Define fee coefficient/rounding, pending-offer migration, treasury authorization and revalidation of changed claim/profile/participant state. Full automatic recipient portfolio revaluation and permanent receiving capacity are not MVP dependencies.

## 26.3 Building / Decay / Repair gates

Resolve bounded discovery and obvious-identity preservation, retire/rediscover rules with lightweight lineage, safe geometry/baselines, renovation conflict retirement, block-entity treatment, journal/checkpoint/material recovery and loaded-chunk scheduling. Perfect split/merge matching is not required. Start collapse with DROP/CAVE_IN and Repair with station + materials + game time; qualifying Era thresholds stay lightweight. TOPPLE, live workforce, detailed repair scopes and ranking/UI systems are later refinements.

## 26.4 Post-core Server Trade and Facility gates

After Dynamic Settlement Cycles, implement narrow commodity normalization/lots, finite stock and optional safety-net restocking, global/category/player/stock-demand purchase admission, discrete period pricing/spread/min-max rules and recoverable item exchange. Operators observe supply/issuance/sinks/distribution/stock/volume and tune manually. Continuous pressure pricing, hysteresis, basket indexes and automatic monetary policy require evidence first.

Recognized Facility identity/type/region/enabled-certified state does not require production, villagers, housing, worker-density, shared capacity or depot accounting. Detailed production and advanced player-market/order workflows remain need-driven extensions.

## 26.5 Resource operations and optional content

Retain renewable roles, fresh world instances and safe evacuation/cutover/restore obligations. Operator-triggered commands or a documented maintenance procedure may provide initial resets; full automation follows when justified. Optional calendar presentation, gameplay seasons, freight, public teleport, Cultural Standing and workforce simulation are not core-cycle blockers.

## 26.6 Further world evolution and disasters

Anthropogenic Landforms remain a coarse later representation of significant player terrain modification, distinct from immutable natural LandValueProfiles. Significance/material rules, erosion, adaptive refinement and Building/infrastructure interaction remain open. Chunk retirement/regeneration needs explicit protection for Parcels, Buildings, entities, POI, history and valuation records. Disasters, sea-level changes and Epoch transitions remain optional/open and are not prerequisites for Dynamic Settlement Cycles.

---

# 27. Recommended Development Order

| Phase | Prerequisites and deliverable |
|---|---|
| 1. Runtime / world identity / persistence — implemented | Existing modular runtime, world registry, SQLite execution and migration/startup verification. |
| 2. Settlement foundation / GameClock / Activity — implemented baseline | Preserve governance, Parcel protection, consent, tools, historical facts and shared game-time behavior. |
| 3. Settlement/Parcel economic land foundations — next | Extend the implemented baseline with one integer currency ledger, budget-safe issuance and starting funds, LandValueProfile generation/backfill, paid Claim pricing/personal subsidies, UNVERIFIED/VERIFIED certification, and administrator-approved acquisition fees. Numeric-only runtime quotes and naming toggles are not completed target economics. |
| 4. Building identity / baseline / geometry | Bounded discovery, preserve obvious identities, retire/rediscover complex changes with lightweight history links, exterior/interior/support semantics and renovation-safe active baselines. |
| 5. Durable Decay / Repair / History — core MVP completion | Recoverable materials/world work, block-entity safety, DROP/CAVE_IN, station + materials + game-time Repair, and lightweight Era/history. No villager simulation or perfect topology solver prerequisite. |
| 6. Dynamic Settlement Cycle — required immediately after core MVP | Civic Capacity/maintenance demand or equivalent, gradual peripheral decline/release, mature/new-city incentives and history-preserving reclaim with ownership/Holder guarantees. |
| 7. Server Trade + lightweight Facility recognition | Finite normalized lots/stock, budget/quota admission, discrete period bands/spread, recoverable exchange and minimal observation; Facility identity/type/region/enabled-certified state. |
| 8. Resource reset automation | Automate verified operator procedures when reset frequency justifies generation/evacuation/activation/archive/recovery orchestration. Safe manual operations may precede this phase. |
| 9. Optional advanced economy / production / transport / seasons | Add only demonstrated needs; no global economic levels, speculative workforce or mandatory freight/teleport/season dependencies. |
| 10. World evolution / disasters / Epoch systems | Separate later design/safety gates for landforms, regeneration and optional large world-history changes. |

The ordering is dependency-aware. Budget enforcement belongs with the first issuance even though Server Trade-specific quotas/pricing arrive later. Dynamic Settlement Cycles must not wait for phases 7–10.

---

# 28. Immediate Architecture and Execution Plan

The addendum selects the currency model; the previous “wait for currency selection” gate is closed. Current runtime behavior remains unchanged until a tested implementation cutover.

1. Define currency identity/display and integer Wallet/Treasury persistence, then implement non-negative TRANSFER/ISSUANCE/SINK transactions with stable IDs, audit reasons and budget enforcement.
2. Add idempotent starting funds (target approximately 100), `/balance`, minimal peer/treasury movement and short event-driven feedback. Resolve legacy account/grant eligibility and period semantics first; avoid jobs/reward-system expansion.
3. Capture minimal immutable LandValueProfiles at new Main chunk generation and define safe legacy backfill/versioning.
4. Replace area-only quotes with land/portfolio/size/light-Activity pricing, fixed first-ever founding cost 25, persistent personal early subsidies and founding-only own-nearby surcharge. Ship actual charging only with its atomicity, permission, retry and quote tests.
5. Add Activity/development-gated UNVERIFIED/VERIFIED applications/admin review and naming/publication eligibility; migrate the current naming toggle explicitly.
6. Add recipient acceptance + simple land-based Acquisition Fee + administrator approval before atomic ownership transfer, preserving treasury and history. Strong receiving capacity and fully autonomous property valuation remain deferred.
7. Proceed to bounded Building geometry and obvious-identity/retire-rediscover rules, then durable station/material/time Repair and DROP/CAVE_IN decay with lightweight history.
8. Move directly to the required Dynamic Settlement Cycle. Server Trade/recognition, Resource automation and optional advanced systems follow in the order above.

No code, database migration, account grant or new fee is executed by this document merge. Keep current implementation acceptance checks separate from future ledger/certification/acquisition checks.

---

# Appendix A — Architectural Vocabulary

| Term | Meaning |
|---|---|
| ESMP | Evolve: SMP |
| Civilization | Persistent Main World gameplay domain, not a player-owned aggregate |
| Settlement / Claim | Governance/ownership identity / protected full-height chunk relationship and operation |
| Parcel / Holder | 8×8×8-cell permission subdivision / independent holding relationship |
| SettlementCertification | UNVERIFIED or VERIFIED governance/publication status, not an economic level |
| World Definition / Instance | Stable configured role / physical incarnation used for coordinates |
| Activity | Meaningful local participation measured against shared game time |
| Building | Stable-where-obvious structural identity with versioned active baseline and preserved history |
| Decay / Ruin | Bounded change into recognizable damaged construction / its remnant |
| Repair | Station/material/game-time restoration of still-valid damage, preserving renovations |
| Era / History | Lightweight qualifying-restoration counter or state / persistent committed historical facts |
| Release / Reclaim | Explicit MVP ownership release (automatic peripheral release only after its post-MVP gate) / new control without reviving obsolete baselines or resetting personal subsidies |
| Currency / Wallet / Treasury | One stable ledger unit / player account / Settlement-owned account |
| TRANSFER / ISSUANCE / SINK | Supply-preserving movement / money creation / money destruction |
| LandValueProfile | Minimal immutable/versioned generated-chunk base valuation, distinct from player-modification history |
| PlayerLandProgress | Persistent personal first/early-Claim subsidy usage |
| Acquisition Fee | Incoming Owner's system land charge, separate from private compensation |
| Facility | Lightweight recognized site identity/type/region and enabled/certified state |
| Resource Cycle | Shared generation/lifetime of renewable Overworld/Nether/End instances |
| GameClock | Retained shared gameplay ticks/days; frozen time/downtime never accrues progress |
| Anthropogenic Landform | Deferred coarse record of significant player terrain modification, not a mutable land-price profile |

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
