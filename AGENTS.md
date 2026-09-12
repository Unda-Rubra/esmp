# ESMP Agent Instructions

These instructions apply to the entire repository.

## Mission and current state

ESMP is a Paper-first, vanilla-client Minecraft SMP architecture for a permanent civilization world and renewable Resource worlds. A dedicated industrial realm is outside current scope; do not restore it from historical decisions without explicit approval.

The canonical authority is `ESMP MVP ARCHITECTURE 2026-09-12/`: its architecture, requirements freeze and ordered roadmap supersede prior plans. Phases 0 and 1 are implemented/verified; Phase 2 certification is next. Founding uses the founder Wallet; EVERY expansion now uses Settlement Treasury and current-Owner assessment. Do not skip to acquisition/Local Activity/Buildings or treat the whole MVP as accepted.

Deployment remains one modular Paper JAR. Frozen persistence uses SQLiteDataSource/SQLiteConfig, a bounded database executor, a Core UnitOfWork backed by jOOQ transactions, repository ports and domain DTOs. jOOQ/generated types belong inside persistence adapters, never cross-domain/public gameplay APIs. Structured Liquibase changelogs become canonical schema; the current custom migration machinery becomes compatibility-only behind a verified legacy bridge.

Frozen pins: Java 25 / Temurin 25.0.4.1+1, Gradle 9.7.1, Paper API `26.2.build.121-stable`, SQLite JDBC 3.53.4.0, jOOQ 3.21.8, Liquibase Community 5.0.4, JUnit 6.1.2. Add ArchUnit dependency rules, exact dependency locks and Gradle verification metadata. Existing dependencies/architecture are not automatically compliant.

## Read order

Before non-trivial work, read:

1. `README.md` for the human entry point.
2. `ESMP MVP ARCHITECTURE 2026-09-12/ARCHITECTURE.md` — canonical frozen architecture.
3. `ESMP MVP ARCHITECTURE 2026-09-12/MVP_REQUIREMENTS_FREEZE.md` — frozen requirements and exclusions.
4. `ESMP MVP ARCHITECTURE 2026-09-12/IMPLEMENTATION_ROADMAP.md` — strict Phase 0–8 order and completion gates; inspect the accompanying diagram sources where relevant.
5. `.agent/HANDOFF.md`, `.agent/DECISIONS.md` and `.agent/OPEN_QUESTIONS.md` — implementation snapshot, historical rationale and current-phase issues, subordinate to the bundle.

## Canonical vocabulary

- **Civilization** is the overall persistent Main World gameplay environment/domain. It is not a player-owned aggregate.
- **Settlement** is the Main World aggregate for ownership, members, roles, permissions, activity, and claimed chunks.
- **Claim** is the protected chunk relationship and the operation that changes it.
- **Parcel** is a 3D permission subdivision within one Settlement, using world-aligned 8×8×8 cells; not a sovereign claim or the removed Factory Plot.
- Governance vocabulary is Owner / Governor / Citizen / Public; Holder is an independent Parcel relationship and BANNED is a negative Settlement-local override. Legacy role strings are allowed only in migration/historical evidence, not active role aliases.
- Use `world_definition_id` for a stable configured world role.
- Use `world_instance_id` for anything tied to blocks, chunks, entities, or coordinates.

Do not reintroduce `Territory` as an alias for `Settlement`. Cleanly migrate every affected document, API, schema, event, and caller when vocabulary changes.

## Architectural invariants

- The Main World is permanent.
- Resource world resets create new physical world instances.
- Resource dimensions retain a shared cycle and fresh physical identities. Initial resets may use operator-triggered commands or a verified maintenance procedure; preserve evacuation/no-mixed-cycle/restore safety without requiring full automation before core gameplay ships.
- Player inventory, equipment, Ender Chest, experience, and normal state are shared across realms.
- Settlement claims protect direct and indirect world changes.
- Each owned Settlement is one four-directionally contiguous chunk component; diagonal contact alone does not connect chunks.
- Adding a chunk must preserve contiguity, and unclaiming must not split the Settlement.
- Ordinary unclaim must not remove the last Settlement chunk. Ending ownership requires explicit confirmed Owner abandonment or an authorized administrative lifecycle operation.
- Adjacent Settlements remain separate identities and never merge automatically, including when they share an owner.
- One server-wide integer ledger currency, non-negative wallets/treasuries, stable transaction IDs and distinct TRANSFER/ISSUANCE/SINK semantics. Budget all issuance; peer transfers conserve supply. First-join funds and explicit administrator/public grants suffice for MVP; no repeatable automatic faucet is required.
- LandValueProfile is created at first Main chunk generation and stays immutable/versioned: world instance, chunk coordinates, valuation version, base value and optional class. Player edits cannot change price basis; legacy backfill/revaluation needs explicit policy.
- Frozen payer contract is implemented: founder Wallet pays founding; Settlement Treasury pays EVERY expansion, assessed against current Owner, including Owner-initiated expansion. Governor spending requires authorization/allowance. First founding costs 25; discounts never reset/transfer. PlayerLandProgress uses the monotonically incremented purchase count as its revision; old unpurchased quote contexts reject, paid receipts remain replayable.
- Initial target ownership acquisition requires recipient acceptance, a simple land-based fee paid by the incoming Owner, and administrator approval before atomic transfer. Preserve treasury identity/history; never appraise Buildings/labor or reuse predecessor subsidies/historical cheap prices.
- The target has one Owner, zero or more Governors and Citizens, and derived Public users. Store Owner only in `Settlement.owner_uuid`, Governors/Citizens in `SettlementMember`, and never persist Public memberships. No duplicate legacy role names after cutover.
- Ownership transfer preserves consent/atomicity; the former Owner becomes Citizen. Existing ownership-only governance restrictions remain subject to the canonical Settlement/Parcel rules, not a blanket authorization bypass.
- Owner/Governor may ban non-administrators locally. Banned players retain movement/informational access but cannot perform Settlement/Parcel-governed interactions; server administrators bypass Settlement bans.
- Authorization order is administrator override, Settlement ban, world/Settlement lookup, Parcel lookup, applicable policy, then actor relations. Relations are a set: any allowed applicable relation grants the action unless an earlier negative override applies.
- Parcel policy overrides ordinary Settlement defaults but cannot grant governance powers. Expose only BUILD / INTERACT / CONTAINER / AUTOMATION; no per-block ACLs.
- Unparcelled space uses a server-defined default policy. Public/Holder access is policy-driven, not a game-mode switch, blanket cancellation, or fixed adventure-like Visitor rule.
- Parcel cells must lie inside one Settlement, not overlap, and form a six-face-connected set. Names are unique within a Settlement. Zero/multiple Holders are valid; Public players may hold Parcels without becoming Citizens.
- Unassigned Parcels are governable by Owner/Governors. Assigned Parcel revocation, Holder removal or boundary changes require governance and all Holders to agree; neither side may reshape unilaterally. Administrators resolve disputes. Rent/contracts are not modeled.
- Source and destination of protected automation must both authorize the operation; same Settlement does not imply same Parcel policy.
- Unattributed internal Parcel automation uses HOLDER permission when assigned, OWNER when unassigned. Across distinct protected regions, both sides must explicitly grant PUBLIC AUTOMATION. Do not restore a same-Settlement blanket bypass.
- Assigned geometry, Holder, policy and naming edits use durable governance+all-current/new-Holder approval; stale/expired proposals cannot apply. Parent unclaim/abandon requires affected Parcels removed through their consent workflow first. Administrator overrides are audited.
- Commands must cover all operations. Management Book and Parcel Wand are replaceable convenience frontends with no item-owned authority. Wand + named off-hand Name Tag selects aligned 8×8×8 cells; preview then atomically validate/commit. Surface-cell deletion must preserve connectivity.
- Certification is UNVERIFIED/VERIFIED only. Default application gate: age ≥3 game days, aggregate Activity not INACTIVE, lifetime credited meaningful Activity ≥20. Eligibility permits governance application, not auto-verification; admin approves/denies. No automatic revocation, aesthetic score or Town/City ladder. Migrate existing names/flags explicitly in Phase 2.
- Settlement writes go through the Paper runtime coordinator, which reserves affected territory until committed SQL is published to the main-thread index. Do not add mutation callers that bypass this publication gate.
- Fatal persistence failures and hot unloading stop the server rather than dropping protection. Use full restarts; no reload command or plugin-manager hot reload.
- Phase 4 adds lazy chunk-level Local Activity Buckets alongside aggregate Activity, with bounded neighbor influence. ActivityRegion is derived four-connected supported Claim components, never persisted/named/player-managed. Building decay uses footprint-weighted local support, not only Settlement-wide score.
- Activity revalidates exact position/action, governance, bans, Parcel policy and security/ownership/claim revisions. Public actors (including non-Citizen Holders) use small credit and caps; holding alone never grants team credit. Equivalent legacy policy vocabulary migrates without resetting state; arbitrary installed value changes still need explicit migration.
- Sufficient activity recovery stops further decay without repairing damage or advancing Era; queued mutations must revalidate activity eligibility.
- MVP Settlements remain owned/protected regardless of inactivity or ruin unless explicitly abandoned or changed by an authorized lifecycle operation. Automatic peripheral release/Civic Capacity belongs to the required post-MVP dynamic-cycle phase; its rules remain unresolved.
- Reclaim uses normal chunk claiming and the claiming Settlement's team, never restored former permissions. History survives, but the active restoration baseline starts from the inherited structure's current state; reclaim does not repair or advance Era.
- Never scan every block in every settlement on a global timer.
- Never access or mutate Paper world state from an arbitrary asynchronous thread.
- World mutations must revalidate world instance, ownership, permission, and expected state immediately before application.
- Decay and Repair wait for naturally loaded chunks; do not load or retain chunks solely to progress jobs. Pending work survives restart.
- Preserve concurrent player edits and replan affected outdated work; unaffected valid work may continue.
- Durable events use a transactional outbox with at-least-once delivery and idempotent consumer effects. Reconcile world-save and DB progress independently rather than assuming exactly-once mutation.
- Phase 5 Building creation is explicit Owner/Governor registration of a bounded 3D region inside one Settlement with all touched chunks naturally loaded. No automatic city-wide discovery. Baseline revision 1 includes occupied/exterior/interior/opening/simple support geometry; authorized edits revise affected design, demolition is not damage, and obvious edits preserve identity.
- MVP Decay affects ONLY registered Buildings: whitelisted WEATHER, bounded vertical DROP and small connected CAVE_IN patches. No collectible drops, automatic block-entity mutation, wild/natural terrain decay, TOPPLE, raids, snow/water simulation or rigid-body physics.
- Whole-Building Repair uses one recognized Station/point, original materials and GameClock-driven durable batches. Station is protected from ordinary decay; no automatic baseline block-entity restoration, villagers/artisans or partial-wing UI. Player renovation wins over obsolete restoration work.
- Building condition is INTACT/DAMAGED/RUINED; Era starts at 0 and increments exactly once when a previously RUINED Building becomes fully INTACT through Repair. No bonuses, rankings or loot.
- Phase 8 is hardening only: complete restart/conflict/material/permission acceptance and paired world+DB restoration before tagging MVP v1.0. Dynamic Settlement Cycle is mandatory MVP+1 but cannot begin before the MVP acceptance/tag. No deferred-system tables/modules/events/placeholders.
- Server Trade, Facility recognition/production, automated Resource resets, freight, public teleport, seasons, culture, disasters, world evolution and automatic Building discovery are outside frozen MVP. Manual safe Resource maintenance remains required.
- All gameplay timing uses shared game time. Freeze with `advance_time=false`; count forward sleep/command jumps, re-anchor rewinds, and never accrue downtime.

## How to change architecture

1. The frozen bundle, not the deleted root file or historical notes, is canonical. Preserve its freeze: new ideas go Post-MVP.
2. Execute phases in roadmap order; never mark a phase complete with failing acceptance criteria. Phase 0 changes infrastructure, not prices/payer rules or other gameplay.
3. Changes to frozen invariants require an explicit architecture decision naming affected acceptance tests; only permitted correctness/security/data-loss/migration/performance/acceptance fixes can adjust implementation details without reopening scope.
4. Record accepted changes in the appropriate bundle contract and dated `.agent/DECISIONS.md`; remove resolved issues from `.agent/OPEN_QUESTIONS.md`. Do not rewrite supplied frozen files merely to make legacy code appear compliant.
5. Keep Chinese README navigation and current-state caveats aligned. `.agent` files summarize status and execution, never create a second architecture.

## How to change code

For code changes:

1. Preserve tested invariants while moving code into domain/application/persistence/api layers. Reuse compatible algorithms, not obsolete architecture leakage; follow current-phase scope and ArchUnit rules.
2. Update every caller in one clean cutover; do not leave aliases or deprecated paths unless explicitly requested.
3. Keep gameplay semantics out of `esmp-core`.
4. Put synchronous invariant enforcement behind application APIs; publish domain events only after owning state commits.
5. Treat DB-to-world mutation as recoverable, idempotent work because SQL and Minecraft world state cannot share one transaction.
6. Run the narrow behavioral test or actual server scenario that exercises the change.
7. Keep player-facing tooltips and confirmations short and natural: describe the action or purpose, not implementation status or omitted features. Preserve actionable rejection reasons and audit data. Parcel overlays use blue previews/target surfaces, green existing borders and red invalid selections; clicks and rendering must share targeting logic.

## Git workflow

Commit completed, coherent milestones regularly using the configured Git identity. Inspect staged changes before committing, keep runtime data/local tooling out of Git, and never commit secrets or unrelated user work. Record actual verification in the handoff. Do not push or configure a remote unless the user requests it.

Phase completion requires the roadmap gates: clean build/check, empty and legacy-fixture migrations, unit/persistence/architecture tests, relevant real Paper scenarios, current help and no forbidden surfaces. Use the roadmap's phase report format. Existing smoke results are historical evidence, not frozen-MVP acceptance. With JAVA_HOME pointing to JDK 25, `ESMP_ACCEPT_EULA=true ./scripts/run-dev.sh` starts the isolated loopback server after EULA acceptance; use `stop`, never hot reload.

## Handover protocol

Before ending non-trivial work, update `.agent/HANDOFF.md` with:

Keep the handoff a current snapshot, not an append-only transcript. Retain durable rationale in `.agent/DECISIONS.md` and distinguish actual server/client evidence from unit tests or unverified UI behavior.
- Maintain `.agent/TEST_CHECKLIST.md` with completed implementation and pending user acceptance. Do not add unimplemented target features as completed or replace legacy checks with unimplemented semantics; append/update them when the corresponding migration actually ships.

```text
# Current Handoff

## State
What is true now.

## Completed
Concrete behavior or decisions completed in this work.

## Changed files
Paths and the reason each changed.

## Verification
Exact commands or document checks run, with outcomes.

## Next action
One bounded action for the next agent.

## Risks or blockers
Only unresolved facts that affect the next action.
```

Also complete these checks:

1. Accepted decisions agree with the frozen architecture/requirements/roadmap bundle; the root `ARCHITECTURE.md` is not recreated.
2. `.agent/DECISIONS.md` contains the rationale.
3. Resolved items are removed from `.agent/OPEN_QUESTIONS.md`.
4. Human-facing instructions remain in Simplified Chinese in `README.md`.
5. The handoff never reports unexecuted verification as successful.
