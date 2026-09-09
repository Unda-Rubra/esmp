# ESMP Architecture Decisions

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

- ESMP permits free transfer of items and normal player state between Main, Resource, and Factory realms.
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

- Deploy one Paper plugin JAR with one bootstrap and coordinated lifecycle. Core, Civilization, Resource, and Factory remain internal modules with explicit domain boundaries.
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

## 2026-09-09 — Factory Plot roles and land allocation

- Each Plot has one Owner, Managers, Members, and derived Visitors. Membership belongs to the Plot, not a reusable crew aggregate, and is independent of Settlements and other Plots.
- Store the Owner on `Plot`, Manager/Member records in `PlotMember`, and chunk membership in `PlotChunk`. Industrial capability policy remains Factory-owned.
- Players choose locations and claim contiguous edge-connected chunks; no predefined parcel grid is required. Plots remain non-empty and connected while owned, with exclusive chunks and separate adjacent identities.
- Factory limits, costs, permissions, and release rules do not inherit Settlement defaults; the Settlement 64-chunk cap is not a Factory decision.

Reason: the user chose familiar fixed roles and flexible contiguous land allocation while retaining independent industrial-domain state. Generator detail and policy tuning are deferred.

## 2026-09-09 — Factory role capabilities

- Members may build, break, operate machinery, use controls/containers, and interact with protected entities.
- Managers additionally manage claims, ordinary Members, and settings. Only Owner manages Managers, transfers ownership, or releases the Plot.
- Visitors may move through and observe, but cannot operate controls, machinery, containers, or protected entities. Unlike Settlement Visitors, they receive no public-control allowance.
- Treat movement-triggered control activation separately from movement; implement action-specific protection, not game-mode switching or blanket right-click cancellation.

Reason: industrial controls can affect an entire production line. Allowing observation does not authorize control activation or justify stopping otherwise authorized machinery.

## 2026-09-09 — Factory compatibility and ordinary ticking

- Prefer vanilla-like behavior over aggressive redstone optimization; behavior-changing settings require explicit compatibility evaluation. Paper is not a guarantee of exact vanilla parity.
- Use ordinary Paper chunk ticking, proximity, and world rules. No initial ESMP machine simulation, power system, or per-machine operating budget.
- Unloaded chunks do not operate, and ESMP does not simulate catch-up production. Existing no-custom-chunk-loader/fake-player constraints remain.

Reason: the user selected predictable machine behavior and vanilla-style operation instead of another industrial simulation layer. Exact Paper settings and future offline-capacity features remain deferred.

## 2026-09-09 — Consolidated implementation gates

Preserve accepted gameplay while separating prerequisites by feature. Runtime/build selection precedes the first runnable plugin; SQLite and protection details precede durable claims; Building identity, restoration safety, recovery/accounting, and eligibility configuration precede automated mutation. Resource and Factory features have their own operational verification gates.

Ownership/history facts must be persisted from the first owning-state changes. Later History milestones concern presentation and rankings, not reconstructing events that were never saved.

Reason: the user requested faster architecture work with minor details deferred. Gate each feature safely instead of treating all unresolved algorithms and tuning as a prerequisite for beginning the project. No runtime versions or libraries are selected by this consolidation.

## 2026-09-09 — Accepted runtime and development baseline

The user approved Minecraft Java Edition/Paper 26.2, Java plugin source, JDK 25, and Gradle Kotlin DSL. Kotlin is used for build scripts, not plugin source. The project remains one modular plugin JAR with SQLite.

Reason: Paper's supported 26.2 release requires Java 25 minimum, and its official project setup uses Java with Gradle Kotlin DSL. Sources: [Paper version metadata](https://fill.papermc.io/v3/projects/paper/versions/26.2), [Paper project setup](https://docs.papermc.io/paper/dev/project-setup/).

Exact Gradle wrapper, Paper server/API build, JDK distribution/patch, SQLite driver, and migration-library pins remain implementation work. No scaffold or server validation is implied by this decision.

## 2026-09-09 — Git and canonical architecture

The user requested Git scaffolding before code, regular milestone commits, and the canonical filename `ARCHITECTURE.md`. Keep local tooling, server data, logs, and databases untracked. Commit coherent verified work with the configured identity; do not push without a request.
