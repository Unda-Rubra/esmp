# ESMP Architecture Decisions

This is a dated rationale log, not a competing specification. The current `ARCHITECTURE.md` takes precedence; older entries describe historical decisions where superseded. In particular, legacy free claims, Manager/Member/Visitor access, player-controlled naming, blanket ownership capabilities and automatic-release scheduling below are not the new MVP target.

## 2026-09-10 — Settlement/Parcel implemented with numeric-only claim pricing

The user explicitly postponed real currency: use numbers now and do not consume currency/items until a currency system is selected. This supersedes the earlier immediate real-ledger requirement. Claim price is configurable base + step×current area (initially 100 + 25×area); committed history records the value and `charged=false`. No mock wallet, funding, deduction or refund is introduced.

- Migrated active roles/schema to Owner/Governor/Citizen/Public while preserving UUIDs, offers, history, scores and caps. Civilization schema is 3, config 4. Added stable public numbers and S#number labels; existing names are grandfathered, new names require administrator naming certification and approval.
- Implemented local bans, relation-set Parcel policy, world-aligned 8×8×8 cells, six-face connectivity, exclusion/height checks and immutable committed spatial publication. Current bounds: 4096 cells, 128 Parcels/Settlement, 32 Holders and 64 pending proposals/Settlement.
- Holder is independent from membership. Assigned geometry, Holder, policy and name changes require governance plus all current/new Holders; proposals survive restart, expire at 24000 game ticks and validate Parcel/ownership/chunk/security revisions. Administrator overrides are auditable. Parent claim release cannot bypass Parcel consent.
- New Parcel defaults grant Owner/Governor/Citizen/Holder all four domains and Public none. Unparcelled Public access remains safe controls. Unattributed internal automation uses Holder grant when assigned or Owner grant otherwise; crossing distinct protected boundaries requires Public automation permission on both sides. Player/container access is separately authorized; bans revoke open protected inventory access.
- Issued PDC book/stick tools carry no authority. Book pages link current command services. Stick+named off-hand Name Tag or equivalent position commands produces bounded 1200-tick previews, then commits through the same service. Tool issuance is limited to one of each in inventory plus a short game-time cooldown; crafting/anvil/grindstone conversion is blocked.
- Activity now captures block coordinates/action/security revision and rechecks bans and Parcel relations. Non-Citizen Holder/Public credit remains small (.02, daily cap .25) rather than becoming team credit; existing balances/caps remain intact and equivalent `visitorCredit` policy nomenclature migrates to `publicCredit`.

Reason: implement usable spatial governance and safe consent now without pretending a real economy exists. Preserve the user's currency decision for a later integration. Six new domain regressions and isolated live Paper workflows covered geometry, consent, stale/expired approvals, migrations, real tool interactions, Public Holder access, bans, role/naming persistence and two-sided hopper authorization. Native client visual layout remains a manual acceptance check because the older protocol test client cannot reliably decode current particles.

## 2026-09-10 — User-authored MVP revision supersedes the previous immediate plan

Source: the user's revised `ARCHITECTURE.md` §§7–11 and 23–28. This synchronization changes plans and notes only; the existing JAR still uses its implemented legacy roles/free claims/naming. Do not advertise an unimplemented migration.

- **Next priority:** paid claim transactions and pricing, governance migration/bans, Parcel storage/authorization/commands, and convenience tools/naming administration precede Building identity/geometry. Build only the necessary real currency/accounting dependency for paid claims, not the full market/Facility economy. Payer, initial funding and exact price/recovery policy remain implementation gates.
- **Claims and naming:** creation/expansion consume currency with rising marginal area cost; full-height chunks and contiguity remain. Stable numeric identity/generated labels precede optional official names. Certification and administrator approval control naming; every later rename is also administered and recorded. Existing UUID/name/history migration requires an explicit policy.
- **Governance and bans:** Owner/Governor/Citizen/derived Public replaces legacy Manager/Member/Visitor in the target. Owner stays solely in `owner_uuid`, Governors/Citizens in membership; former Owner becomes Citizen on transfer. BANNED is a local negative override; Owner/Governor can ban non-administrators from governed interactions, not movement/information. Server administrators bypass bans.
- **Authorization:** administrator override → ban → world/Settlement → Parcel → applicable policy → relation set. A player can be both Citizen and Holder; any allowed applicable relation can authorize unless an earlier negative override denies. Parcel policy replaces ordinary default policy, never grants governance. Only BUILD/INTERACT/CONTAINER/AUTOMATION are exposed; both sides of cross-boundary automation must authorize.
- **Parcels:** world-aligned 8×8×8 cells within one Settlement, no overlap, six-face connectivity, unique local names. Zero/multiple Holders are valid and Public can hold. Assigned revocation, Holder removal and reshaping require governance plus all current Holders; administrators resolve disputes. No rent/contracts/debt subsystem.
- **Management:** command-complete application APIs are authoritative. Disposable/replaceable Management Book and Parcel Wand are frontends, never authority tokens. Wand plus named off-hand Name Tag proposes unparcelled cuboid cells, previews before atomic commit, and must preserve connectivity. Surface-cell removal also preserves connectivity; assigned geometry requires consent.
- **Building and Repair:** geometry includes occupied/exterior/interior volumes, openings and support relationships. Local renovation may conflict/retire restoration fragments without erasing history; air at an old wall position is not sufficient permission to restore through a new interior. MVP Repair is whole-Building externally and bounded internally. Block entities require special safety policy; system decay yields no ordinary collectible drops. Coarse profiles and DROP/TOPPLE/CAVE_IN are initial candidates, not full physics.
- **Wild/natural evolution:** ordinary wild construction uses coarse, localized due work; important structures alone justify rich persistent tracking. Roads/rails may use lightweight segments. Coarse Anthropogenic Landforms are an accepted later direction, with 8×8×8 preferred initially; adaptive refinement, regeneration and disaster/Epoch systems remain deferred/open.
- **MVP boundary:** no inactivity/ruin-driven automatic Settlement release. Owned protection persists until explicit authorized lifecycle action. Dynamic cycles are the required next major phase immediately after stable MVP, not optional: Civic Capacity/maintenance demand, old/new-city incentives and gradual peripheral release are candidates whose formulas, Holder guarantees and reclamation behavior remain unresolved.

Reason stated by the revised architecture: prove permissions, Parcels, Building geometry, Activity and recovery first; preserve history and player renovations, keep MVP mechanics bounded, and defer dynamic ownership loss without abandoning the long-term civilization cycle. Existing detector research remains useful for the later geometry/evaluation slice, not the immediate implementation priority.

## 2026-09-09 — Civilization and Settlement vocabulary

- **Civilization** means the persistent Main World gameplay environment/domain.
- Civilization is not a player-owned aggregate or planned database table.
- **Settlement** is the Main World aggregate for ownership, team membership, permissions, activity, and claimed chunks.
- A solo owner is valid; a separate one-member Civilization entity is not needed.

Reason: the project models player claims and historical settlements, not nation-like Civilization membership.

## 2026-09-09 — Settlement claims

- Claims use whole Minecraft chunks.
- A settlement may own many chunks; a player may own multiple settlements.
- Every owned Settlement forms one four-directionally contiguous chunk component; diagonal contact alone is insufficient.
- A new chunk must share an edge with the existing component.
- Unclaiming a chunk must fail if it would split the remaining component.
- Ordinary unclaim rejects removal of the last chunk for every role, including Owner. The Owner must explicitly abandon; Managers cannot implicitly abandon through unclaiming.
- Two Settlements may occupy edge-adjacent chunks without merging, including Settlements with the same owner.
- Adjacent Settlements retain separate permissions, teams, activity, history, and lifecycles.
- Settlement chunk claiming is initially free, with no currency or material payment.
- Configurable `max_chunks_per_settlement` defaults to 64 chunks and is enabled by default; server policy may change or disable it.
- The cap applies separately to each Settlement, not to the owner's total claimed chunks.
- Claims cannot cross physical world instances.
- Settlement chunks are exclusive within a world instance.
- Main World claims protect blocks and interactions.
- Settlements support an owner, members, roles, ownership transfer, and settlement-local permissions.
- Protection includes indirect mechanics such as explosions, fluids, fire, pistons, and hoppers.
- Decay and Repair act as explicit system actors and revalidate before world mutation.

Reason: contiguity gives each Settlement one coherent place and keeps activity, history, release, and visualization local. Adjacent Settlements remain distinct because spatial contact does not imply shared ownership or identity.

The user selected free claiming and a default-enabled size cap. Reason: bound each Settlement's expansion without introducing claim pricing or an economy dependency.

The user selected 64 chunks as the default cap. This permits a 128 × 128-block area if arranged as an 8 × 8-chunk square; square shape is not required. The limit constrains individual Settlement size rather than total player ownership.

Reason for the last-chunk restriction: an owned Settlement must remain non-empty, and operational claim permissions must not bypass the Owner-only abandon boundary.

## 2026-09-09 — Abandonment confirmation

The Owner must separately confirm abandonment after a warning identifying the Settlement and clearly stating that claim protection will be removed. Showing or dismissing the warning does not change ownership or protection. Revalidate current ownership and the target before applying the confirmed operation.

The confirmation lifetime defaults to **60 seconds** and is configurable. Any ownership or claimed-chunk change invalidates the confirmation and requires a fresh warning, including changes later reversed. Bind the warning to ownership and claim revisions and validate them, together with expiry, atomically when abandoning.

Reason: removing protection is consequential and must not happen through an accidental initial command.

## 2026-09-09 — Architecture discussion scope

Prioritize gameplay boundaries, aggregate ownership, and major lifecycle decisions. Defer fine-grained command UX, exhaustive event catalogues, and minor operational settings to implementation unless they affect a core invariant. Ask related architectural questions in batches rather than requesting confirmation for every implementation detail. Existing accepted decisions remain valid.

## 2026-09-09 — Fixed Settlement roles

- Each owned Settlement has exactly one Owner.
- A Settlement may have zero or more Managers and zero or more Members.
- Every player who is not the Owner, a Manager, or a Member is a Visitor.
- Owner identity is stored only as `Settlement.owner_uuid`.
- `SettlementMember` stores only `MANAGER` and `MEMBER` rows.
- Visitor is derived and is not persisted as membership.
- Settlements cannot define custom roles.
- A player's effective role is evaluated separately for each Settlement.

Reason: a fixed hierarchy keeps authorization, commands, persistence, and vanilla-client UI predictable while still supporting group ownership workflows.

## 2026-09-09 — Former Owner after transfer

The user selected Member as the former Owner's role after successful ownership transfer. The former Owner keeps collaborative access but loses administrative authority. Ownership and membership updates commit atomically; the new Owner is represented only by `Settlement.owner_uuid`.

## 2026-09-09 — Ownership transfer acceptance

The nominated recipient must explicitly accept ownership transfer. A pending offer changes no ownership or permissions. At acceptance, revalidate the initiating Owner and offer validity before committing the transfer.

Reason: ownership must not be assigned unexpectedly or transferred through an offer whose initiating Owner no longer has authority.

## 2026-09-09 — Voluntary Settlement departure

Members and Managers may leave without approval and become Visitors when membership removal commits. Claims and other players' roles remain unchanged. Manager self-withdrawal does not grant authority over other Managers. Owners must complete ownership transfer or use the abandon operation instead of ordinary leave.

Reason: membership is voluntary, while the exactly-one-Owner invariant must remain intact.

## 2026-09-09 — Settlement invitation acceptance

Players must explicitly accept an invitation before joining as Members. Pending invitations grant no membership or additional access. Only the invited player may accept; acceptance revalidates invitation validity and the inviter's authority without overwriting an existing role.
Reason: joining a Settlement requires consent, and sending an invitation alone must not grant protected access.

## 2026-09-09 — Automatic membership invitation expiry

Membership invitations expire automatically after a configurable duration, defaulting to **24 hours**. Store `expires_at` when issuing an invitation and reject acceptance at or after that timestamp, independently of background cleanup.

Reason: pending invitations must not retain validity indefinitely, and delayed cleanup must not allow expired invitations to grant membership.

## 2026-09-09 — Transfer expiry and offer cancellation

Ownership-transfer offers expire automatically after a configurable duration, defaulting to **30 minutes**. Their persisted expiry is checked at acceptance, independently of cleanup.

The Owner or any Manager may cancel any pending membership invitation in their Settlement. Only the current Owner may cancel ownership-transfer offers. Recipients may decline their own invitations or transfer offers. Acceptance, cancellation, and decline compete for one atomic pending-state transition; no terminal offer can later grant access.

Reason: a short ownership handover window limits stale offers; role-based cancellation allows operational deputies to manage invitations without ownership powers. Recipient decline preserves consent.


## 2026-09-09 — Ownership transfer recipient eligibility

The recipient must already be a Manager or Member of the same Settlement at both offer creation and acceptance. Visitors must join first. Revalidate membership at acceptance and reject a transfer to a recipient who is no longer eligible.

Reason: ownership transfer should go to an existing collaborator, not bypass the Settlement's membership process.

## 2026-09-09 — Fixed role capability matrix

- Owner has every Settlement capability, including Manager promotion/demotion, ownership transfer, and abandon/release.
- Managers are operational deputies. They can claim/unclaim, manage ordinary Members, change ordinary settings, build, interact, use protected resources, and start repair.
- Managers cannot promote/demote Managers, transfer ownership, or abandon/release the Settlement.
- Members are standard collaborators. They can build, break, interact, use containers, interact with protected entities or machines, and start repair.
- Members cannot change claims, membership, roles, or settings.
- Visitors may enter, observe, and use safe public interactions such as doors, trapdoors, fence gates, buttons, levers, and pressure plates.
- Visitors cannot build, break, use protected containers, alter persistent inventories, interact with protected entities, or directly operate protected machine interfaces.
- Visitor protection is action-specific. ESMP does not switch their real game mode or cancel every right-click.
- Normal redstone consequences from an allowed public control remain allowed.

Reason: Managers need enough authority to operate a Settlement without receiving irreversible ownership powers. Members need ordinary collaborative gameplay. Visitors should feel adventure-like rather than blocked from every interaction.

## 2026-09-09 — Removal, promotion, and demotion

- Authorized removal needs no recipient approval and makes the removed player a Visitor on commit. Owner may remove Members or Managers; Managers may remove ordinary Members only. Membership removal cannot remove the Owner.
- Owner may offer Manager promotion to an existing Member. Only that Member may accept, and pending promotion grants no new powers. Acceptance revalidates current ownership, offer validity, and the recipient's Member role.
- Manager promotion offers expire automatically after a configurable duration, defaulting to **24 hours**. Persist expiry at creation and enforce it at acceptance independently of cleanup.
- Only the current Owner may cancel a promotion offer; the nominated Member may decline. Acceptance, cancellation, and decline share one atomic pending-state transition; terminal offers cannot grant Manager permissions.
- Owner may demote a Manager to Member without approval; the change applies on commit and preserves membership.

Reason: granting administrative responsibility requires consent, while authorized revocation must not depend on the affected player's cooperation.

## 2026-09-09 — Claim visualization

- Use vanilla-client temporary particles for nearby chunk boundaries.
- Use ActionBar or chat components for settlement, owner, role, and status.
- Visualization is temporary, per requesting player, and must not mutate blocks or scan a whole settlement.

Reason: preserve the vanilla-client-first UI direction.

## 2026-09-09 — World identity

- A stable `WorldDefinition` identifies a configured role such as `MAIN` or `RESOURCE_NETHER`.
- A `WorldInstance` identifies one physical Bukkit world incarnation.
- Coordinate-sensitive records use `world_instance_id`.
- A Resource reset creates new world instances.

Reason: configured names may be reused after reset; stale records must not target a later cycle.

## 2026-09-09 — Cross-realm transfer

- ESMP permits free transfer of items and normal player state between Main and Resource realms.
- Inventory, hotbar, equipment, offhand, Ender Chest, and experience remain shared.
- ESMP does not implement per-realm inventories or contraband filtering.

Reason: the Resource Realm exists to supply the other realms, and Paper normally preserves the same player's data across worlds. Recording this explicitly prevents later ESMP or third-party configuration from changing the intended economy.

## 2026-09-09 — Settlement lifecycle

- Settlement ownership and physical condition are separate state dimensions.
- `Founded` and `Reclaimed` are events, not permanent states.
- Reclaiming land restores protection under a new ownership epoch but does not automatically restore ruined buildings.
- An owned ruined settlement remains protected until a valid release completes.

## 2026-09-09 — Automatic Building discovery and revisions

- An algorithm discovers Buildings automatically within player-claimed Settlements. No player registration or candidate confirmation is required.
- Each tracked Building belongs to one Settlement and its footprint stays inside that Settlement's claims. A cross-Settlement physical structure cannot be one tracked Building.
- Authorized player building edits automatically update affected restoration-baseline data. ESMP decay and repair do not redefine the baseline or fold unrelated damage into it.
- Authorized demolition changes the intended target; repair is not a rollback of player edits.
- Discovery is localized and budgeted, including existing construction in newly claimed land. Stable Building identity must survive ordinary rediscovery.

Reason: the user selected automatic discovery and revisions to avoid manual setup, while choosing one Settlement per Building to keep permission and activity authority unambiguous. Detection algorithms remain open; inherited-baseline policy is settled in the reclaim decision below.

## 2026-09-09 — Activity, recovery, and release boundaries

- Team participation is the primary activity source. Meaningful Visitor interactions contribute very little; passersby contribute nothing. Visitor activity does not expand Visitor permissions.
- Sufficient activity recovery stops further decay. It does not heal damage or advance Era; repair remains necessary. Pending decay must revalidate activity eligibility before mutation.
- Owners may voluntarily abandon intact or active Settlements through the confirmation flow. Automatic release requires both inactivity and ruin conditions.
- Release preserves history and does not itself damage or restore buildings. Scoring weights, recovery thresholds, and ruin aggregation remain deferred.

Reason: public use should count modestly without treating traffic as upkeep. Returning participation prevents further decline without bypassing repair. Voluntary release expresses owner intent; automatic release needs evidence of abandonment.

## 2026-09-09 — Reclaim ownership and inherited baselines

- Released land may be reclaimed chunk by chunk into a new or existing Settlement, subject to normal contiguity, exclusivity, world-instance, and size-cap rules.
- The claiming Settlement's current team and permissions apply. Former teams, permissions, and owning Settlement identity are not automatically revived.
- Building and site history survive. The active restoration baseline is newly established from the inherited structure's current state, not its former intact target.
- Reclaiming does not restore blocks or advance Era. Old ownership/baseline jobs cannot mutate reclaimed structures without revalidation.
- Partial reclaim retains historical linkage but must respect one Settlement per active tracked Building; exact split/merge bookkeeping is deferred.

Reason: the user chose ordinary claiming and current-team authority, with historical continuity but a fresh repair target based on the ruins the new occupants inherit.

## 2026-09-09 — Loaded-chunk mutation and recovery

- Decay and Repair wait for naturally loaded chunks; they do not load or retain chunks solely to progress. Pending jobs survive restarts.
- Preserve concurrent player edits and replan affected outdated work from current authorized state. Unaffected valid work continues without a manual whole-job restart.
- Persist operation identity, expected state, context, and progress; revalidate on the world execution context before applying bounded changes.
- SQL commits and Minecraft world saves are independent. Recovery reconciles interrupted or ambiguous work rather than assuming exactly-once mutation or blindly replaying.
- Material accounting and durable completion effects must be idempotent; retries cannot duplicate charges, history, or Era advancement.
- Record durable domain events with the owning state in a transactional outbox, deliver after commit at least once, and deduplicate consumer effects.

Reason: the user selected natural loading to avoid background chunk-loading cost and localized replanning to preserve edits without stopping all work. Durable job and event boundaries protect these semantics across failures; detailed storage and checkpoint mechanics remain implementation work.

## 2026-09-09 — Single modular plugin and SQLite

- Deploy one Paper plugin JAR with one bootstrap and coordinated lifecycle. Core, Civilization, and Resource remain internal modules with explicit domain boundaries.
- Use embedded SQLite for initial application state, durable jobs, and transactional outbox records. Domains own repositories and schemas; Core owns database infrastructure.
- Coordinate short writes for SQLite's single-writer model; keep database I/O off the world execution context and Paper world access out of database workers.
- Versioned migrations precede dependent gameplay enablement. Exact driver, schema, migration library, and build tooling remain implementation choices.

Reason: the user selected simpler single-server installation and operations without discarding modular architecture. Separate plugin artifacts and a separate database service are unnecessary initially; transparent backend portability is not promised.

## 2026-09-09 — Shared Resource cycle with advance preparation

- Resource Overworld, Nether, and End reset together as one cycle, each with a fresh physical instance.
- Generate and pre-generate all replacements while the old cycle remains open. Replacement worlds are not publicly accessible before activation.
- Only after all replacements are ready: close entry, evacuate, unload old worlds, switch the shared cycle and routing, and reopen. Never admit players into mixed cycles.
- Persist cutover progress and reconcile world readiness after restart. Preparation failure leaves the old cycle serving players.
- Retired-world references cannot route players into deleted instances or reinterpret old coordinates. Archive/delete occurs after cutover under a separate retention policy.

Reason: shared cycles simplify routing and generation identity; advance preparation reduces the closed-access window at the cost of temporary storage and generation load. Exact schedules, evacuation destinations, and retention remain deferred.

## 2026-09-09 — Consolidated implementation gates

Preserve accepted gameplay while separating prerequisites by feature. Runtime/build selection precedes the first runnable plugin; SQLite and protection details precede durable claims; Building identity, restoration safety, recovery/accounting, and eligibility configuration precede automated mutation. Resource features have their own operational verification gates.

Ownership/history facts must be persisted from the first owning-state changes. Later History milestones concern presentation and rankings, not reconstructing events that were never saved.

Reason: the user requested faster architecture work with minor details deferred. Gate each feature safely instead of treating all unresolved algorithms and tuning as a prerequisite for beginning the project. No runtime versions or libraries are selected by this consolidation.

## 2026-09-09 — Accepted runtime and development baseline

The user approved Minecraft Java Edition/Paper 26.2, Java plugin source, JDK 25, and Gradle Kotlin DSL. Kotlin is used for build scripts, not plugin source. The project remains one modular plugin JAR with SQLite.

Reason: Paper's supported 26.2 release requires Java 25 minimum, and its official project setup uses Java with Gradle Kotlin DSL. Sources: [Paper version metadata](https://fill.papermc.io/v3/projects/paper/versions/26.2), [Paper project setup](https://docs.papermc.io/paper/dev/project-setup/).

Exact Gradle wrapper, Paper server/API build, JDK distribution/patch, SQLite driver, and migration-library pins remain implementation work. No scaffold or server validation is implied by this decision.

## 2026-09-09 — Git and canonical architecture

The user requested Git scaffolding before code, regular milestone commits, and the canonical filename `ARCHITECTURE.md`. Keep local tooling, server data, logs, and databases untracked. Commit coherent verified work with the configured identity; do not push without a request.

## 2026-09-09 — Runnable plugin foundation and package identity

- User-selected package prefix and Gradle group: `xdu.undarubra.esmp`.
- Four internal Gradle modules produce one Paper JAR: a Paper composition root, infrastructure-only core, and two modules owning their world definitions. No empty gameplay implementations are advertised.
- Pin Gradle 9.7.1, Paper server 26.2 build 121 / API `26.2.build.121-stable`, SQLite JDBC 3.53.4.0, and JUnit Jupiter 5.12.2. Java 25 remains the source/runtime target; verification used local Temurin 25.0.4.1+1.
- SQLite version 1 persists world definitions and physical identities, using transactional JDBC migrations and `PRAGMA user_version`. A bounded serial worker performs database I/O; no migration framework or generic event bus is introduced before a real consumer requires it.
- Runtime bindings only use already-loaded, explicitly configured names with compatible environments. The plugin creates no worlds and never maps default Nether/End worlds into renewable Resource roles implicitly.

Reason: deliver a working, restart-safe architecture rather than placeholder gameplay. Subsequent domains can extend verified persistence and lifecycle boundaries without making Core own gameplay semantics.

## 2026-09-09 — Factory Realm removed from current scope

The team removed the dedicated Factory Realm and industrial gameplay to reduce implementation workload and avoid RPG-survival balance and performance pressure from survival redstone machinery. Former Plot, Factory role, generator, and machine-operation decisions are superseded; Git history retains them, not the active architecture or backlog.

Remove the module, active world definition, configuration mapping, status entry, and development milestone. Back up and migrate old configs once. Preserve physical worlds and historical registry identities while excluding unsupported definitions from active snapshots and new registration. Core handles definition retirement generically rather than importing Factory business semantics.

No blanket redstone restriction in the remaining worlds is introduced by this scope cut. Such gameplay policy would require its own decision.

## 2026-09-09 — Playable Settlement implementation

- Civilization owns schema version 1, Settlement transactions, contiguous claim topology, fixed roles, expiring offers, history, and outbox. Core provides database-only work on its bounded SQLite worker.
- The Paper runtime reserves affected territory while changes commit, publishes the committed index on the server thread, then reports success and closes unauthorized inventory views. Fast cached admission checks reject obvious unauthorized mutations before territory is reserved; SQL remains authoritative.
- Implemented controls include create/claim/unclaim, inspect/list/view, invite/promote/transfer and offer resolution, kick/leave/demote, rename, confirmed abandon, historical inspection, and explicit admin protection bypass.
- Protection uses action-specific Paper events, not game-mode switching or blanket right-click cancellation. Same-Settlement mechanisms remain usable; cross-claim block/inventory effects are constrained. Ongoing brushing revalidates permission without loading remote chunks.
- Private particle borders are limited to nearby loaded chunks, 10 seconds, and 256 particles per viewer per update. Basic PvP, loose-item pickup, and natural entity movement are not new Settlement policies.
- Failure to initialize or maintain reliable persistence/protection stops the server. Hot reload is unsupported. Building/Activity/decay/repair and Resource reset gates remain separate.

Reason: deliver the complete manual Settlement gameplay loop for real-game testing while preserving committed permission boundaries and avoiding placeholder automated systems.

## 2026-09-09 — Local offline multiplayer and transition feedback

The user requested offline mode for local multiplayer testing. The development launcher and existing `.run/dev/server.properties` use `online-mode=false` and `enforce-secure-profile=false`, retaining the loopback bind. This is not a production authentication policy; online/offline UUID changes require explicit player-data/ownership migration rather than name-based reassignment.

Automatic movement feedback compares the previous and next Settlement IDs. Same-Settlement chunk crossings and wilderness-to-wilderness travel are silent; entry into a different identity or exit is announced once. Remove the duplicate world-change notification path; apply the same rule to teleport/respawn. Explicit command feedback is unchanged.

## 2026-09-09 — Settlement testing and deferred chat ergonomics

The user reports that Settlement testing in game is complete. Chat output could be more ergonomic, but this is explicitly not a current priority. Preserve the verification checklist for future regressions; do not spend the next feature iteration on chat polish.

## 2026-09-09 — Gameplay extension approved for architecture integration

The user explicitly approved integrating the consolidated gameplay proposal and revising the roadmap. `ARCHITECTURE.md` §16A is now canonical; the former proposal is a navigation record, not a competing specification. This is design acceptance, not runtime implementation or approval of unresolved numeric/recovery defaults.

- Economic stages are Settlement/Village/Town/City on the existing Settlement; cultural capability is parallel and does not increase production capacity or change Building Era. Vanilla crafting remains unrestricted.
- Main World Facilities use functional automatic checks, reachable shared housing and reasonable workforce density, with administrator certification for higher tiers. Relevant changes suspend affected capability; eligible basic production continues and stocks remain. Decorative edits do not automatically require staff review.
- One Settlement production budget bounds all Facility allocations. Hybrid production consumes inputs where required, including mined inputs for metal. Offline accrual uses bounded ledger stock at physical depots, never chunk loading or fake ticking villagers. Activity deficiency pauses production after a configurable grace period; paused time cannot become catch-up output and production grants no Activity.
- Wallets and treasuries are ledger accounts. Budgeted server purchases/rewards mint; trades transfer; service fees burn. Owner controls withdrawals/budgets, Managers make audited budget-limited operational payments, Members cannot spend treasury funds. No wages or membership taxes.
- Server Trade uses global bounded Cheap/Normal/Expensive bands, finite replenished stock, and separate purchase/sale eligibility lists for basic commodities. Player markets initially use physical stocked stalls. Rare/specialized supply primarily uses exploration/events/history and player markets; no farm ban or provenance tracker is selected.
- Timed ledger freight reserves bulk cargo and throughput over reviewed physical routes. Mode/distance inform quotes; nested cargo counts. Targeted rechecks stop new departures after disruption, not existing shipments. Unavailable destinations hold goods for the original recipient, with bounded storage/recovery still gated.
- Village or certified-cultural-landmark status qualifies for basic public teleport. Base-plus-cargo charges provide soft freight incentives while preserving shared inventory/Ender Chests and accepting storage bypasses.
- Natural biome/terrain profiles affect efficiency, separately from seasons. One Minecraft day equals one calendar day, 365/366 days per year starting at 0 A.D. Initial effects are bounded native weather/particles and reversible loaded-area snow/vegetation; full seasonal palettes remain deferred. The later shared-game-clock decision below settles the time basis for financial and Activity accounting; source/jump reconciliation remains gated.

Reason: make productive settlements, visible infrastructure, and historical/cultural places useful without unlimited automation, compulsory seasonal chores, or restoring the removed Factory Realm. Bounded ledger accounting enables unloaded production without weakening world-thread and recovery invariants.

Logical ownership separates Civilization qualification/Activity from Economy ledger/stock, Transport lifecycle, and Calendar/Seasons modifiers. These remain one plugin lifecycle; no new Gradle modules or tables are created by this decision. Core stays gameplay-agnostic.

Roadmap: Activity/time foundations → Building/Facility qualification → recoverable ledger/stock and bounded production → markets → transport. Decay/repair branches after Building/Activity and world recovery; seasons require calendar/effect safety; Resource cutover remains independently gated. Persist facts from each feature's first commit, not when later historical UI is built. Resolve concrete thresholds, eligibility, item/payment recovery, clock semantics, and workload budgets before enabling their feature.

## 2026-09-09 — Shared game clock for all gameplay timing

The user rejected real-life-clock-based Activity and selected the game's reckoning clock (ticks/days) for all plugin gameplay timing. `ARCHITECTURE.md` §16A.6 supersedes the separate elapsed-time recommendation.

- `advance_time=false` stops the clock and time-driven mechanisms. No server-downtime accrual or wall-clock catch-up.
- Tick-rate changes affect real-world pacing, not the Activity/decay result for the same elapsed game days.
- Activity, decay/repair progression, production/grace periods, freight, cooldowns, offer expiry, market intervals and Resource schedules consume the same clock. Offline accounting means absent players/unloaded chunks while game time advances, not a stopped server.
- Implement coherent clock persistence and migrate existing gameplay timers before Activity; do not mistake this design update for an implemented runtime cutover.
- The subsequent implementation decision below settles MAIN source, forward sleep/command jumps, rewind re-anchoring and normal restart. Calendar boundaries and coordinated world/database restoration remain separate gates.

Reason: gameplay belongs to the world's chronology. A frozen world must not age according to elapsed real-life time, and changing server pacing must not change balance measured in game days. Full seasonal presentation remains deferred and is not needed to implement the shared clock.

## 2026-09-09 — Shared clock and Activity implemented

The user selected **count all forward jumps**: sleep and forward time commands age the world; backwards commands re-anchor without reversing completed work. MAIN's loaded world clock is authoritative, isolated from Resource resets. `GameClock` persists monotonic reckoning ticks and source identity; normal boot resumes the checkpoint and re-anchors without downtime catch-up. Source UUID replacement fails safely.

Game-timed writes checkpoint in their transaction; otherwise clock persistence is every 200 game ticks and at shutdown. Abrupt failure can lose uncommitted clock-only progress/queued observations. This is not a coordinated world/database save protocol. Real timestamps are retained only for diagnostic/legacy history and one-time migration of absolute legacy offer deadlines.

Config version 3 replaces offer seconds with game ticks; Civilization schema 2 converts remaining deadlines once and records founding/history game ticks without deleting identities or history. Borders last 200 game ticks; protection-message throttling uses 20 game ticks.

Activity uses lazy exponential decay: maximum 100, initial 30, seven-game-day half-life, inactive below 20, recovery at 30; production eligibility grace is 72000 ticks. Authorized BUILD credit is 1, team INTERACTION .25, Visitor INTERACTION .02; cooldown 200 ticks per actor/category, player daily cap 5, Settlement cap 20, Visitor cap one twentieth of player cap. These are explicit initial tuning values, not a claim of perfect anti-bot detection.

No login, movement, pressure-plate passage, idle, automation or frozen-clock credit. Persisted role/chunk/revision validation rejects stale observations. Founding/migration anchors prevent first-visit refreshes; transfer preserves score, release blocks contributions, reclaim gets a new identity. Closed pause intervals persist for future production exclusion. Status reads do not write decayed scores.

Policy is configurable before initialization; changing persisted policy requires explicit migration rather than silently changing historical score meaning. That migration interface is deferred. Paper collects bounded/coalesced observations and status appears in `/settlement activity [name]` and `info`. No Building/decay/production systems are enabled by Activity.

Reason: provide visible, restart-safe eligibility without per-Settlement timers, wall-clock progression, or prematurely implementing consumers. Verification used Gradle build, seven added behavioral regressions (including legacy expiry migration), and an isolated live Paper fixture for game-time freeze/jumps/rewind, changed tick rate, real block credit, inactivity/grace, exact frozen restart and actual bed sleep.
