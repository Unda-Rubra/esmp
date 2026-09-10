# ESMP Agent Instructions

These instructions apply to the entire repository.

## Mission and current state

ESMP is a Paper-first, vanilla-client Minecraft SMP architecture for a permanent civilization world and renewable Resource worlds. A dedicated industrial realm is outside current scope; do not restore it from historical decisions without explicit approval.

The plugin implements the legacy Settlement role/claim workflows, shared GameClock and Activity on Paper 26.2. The target architecture now requires paid claims, Governor/Citizen/Public plus bans, Parcels, tools and naming administration; these changes are not yet implemented. Building discovery, decay/repair, Resource resets and economy remain disabled. Automatic Settlement release is outside the MVP; dynamic cycles are required immediately after a stable MVP, not an optional someday feature.

Deployment is one modular Paper plugin JAR with a coordinated bootstrap/lifecycle. Initial application persistence is embedded SQLite with versioned migrations and module-owned repositories. Coordinate short writes and keep database I/O off the world execution context. Do not introduce separate plugin artifacts or promise transparent database portability.

Use Java source under `xdu.undarubra.esmp`, JDK 25, and Gradle Kotlin DSL. Pins: Gradle Wrapper 9.7.1, Paper 26.2 build 121 / API `26.2.build.121-stable`, SQLite JDBC 3.53.4.0. Core has no Paper dependency. Domain modules contribute world definitions; `esmp-plugin` is the single composition root.

## Read order

Before non-trivial work, read:

1. `README.md` for the human entry point.
2. `ARCHITECTURE.md`, especially Current Decisions, Deferred and Open Design, Recommended Development Order, and Immediate Architecture Work.
3. `.agent/DECISIONS.md` for rationale.
4. `.agent/OPEN_QUESTIONS.md` for unresolved designs.
5. `.agent/HANDOFF.md` for the current state.

## Canonical vocabulary

- **Civilization** is the overall persistent Main World gameplay environment/domain. It is not a player-owned aggregate.
- **Settlement** is the Main World aggregate for ownership, members, roles, permissions, activity, and claimed chunks.
- **Claim** is the protected chunk relationship and the operation that changes it.
- **Parcel** is a 3D permission subdivision within one Settlement, using world-aligned 8×8×8 cells; not a sovereign claim or the removed Factory Plot.
- Target governance vocabulary is Owner / Governor / Citizen / Public; Holder is an independent Parcel relationship and BANNED is a negative Settlement-local override. Existing Manager/Member/Visitor code is legacy awaiting migration, not an alias to retain.
- Use `world_definition_id` for a stable configured world role.
- Use `world_instance_id` for anything tied to blocks, chunks, entities, or coordinates.

Do not reintroduce `Territory` as an alias for `Settlement`. Cleanly migrate every affected document, API, schema, event, and caller when vocabulary changes.

## Architectural invariants

- The Main World is permanent.
- Resource world resets create new physical world instances.
- Resource Overworld, Nether, and End share one reset cycle. Prepare all replacements before closing the old cycle, and reopen only after evacuation and a coordinated switch; never expose mixed cycles.
- Player inventory, equipment, Ender Chest, experience, and normal state are shared across realms.
- Settlement claims protect direct and indirect world changes.
- Each owned Settlement is one four-directionally contiguous chunk component; diagonal contact alone does not connect chunks.
- Adding a chunk must preserve contiguity, and unclaiming must not split the Settlement.
- Ordinary unclaim must not remove the last Settlement chunk. Ending ownership requires explicit confirmed Owner abandonment or an authorized administrative lifecycle operation.
- Adjacent Settlements remain separate identities and never merge automatically, including when they share an owner.
- Target claims consume currency on creation/expansion through a marginal price curve increasing with current claimed area. Existing free claims are legacy behavior awaiting migration. The independent configurable chunk cap remains available.
- The target has one Owner, zero or more Governors and Citizens, and derived Public users. Store Owner only in `Settlement.owner_uuid`, Governors/Citizens in `SettlementMember`, and never persist Public memberships. No duplicate legacy role names after cutover.
- Ownership transfer preserves consent/atomicity; the former Owner becomes Citizen. Existing ownership-only governance restrictions remain subject to the canonical Settlement/Parcel rules, not a blanket authorization bypass.
- Owner/Governor may ban non-administrators locally. Banned players retain movement/informational access but cannot perform Settlement/Parcel-governed interactions; server administrators bypass Settlement bans.
- Authorization order is administrator override, Settlement ban, world/Settlement lookup, Parcel lookup, applicable policy, then actor relations. Relations are a set: any allowed applicable relation grants the action unless an earlier negative override applies.
- Parcel policy overrides ordinary Settlement defaults but cannot grant governance powers. Expose only BUILD / INTERACT / CONTAINER / AUTOMATION; no per-block ACLs.
- Unparcelled space uses a server-defined default policy. Public/Holder access is policy-driven, not a game-mode switch, blanket cancellation, or fixed adventure-like Visitor rule.
- Parcel cells must lie inside one Settlement, not overlap, and form a six-face-connected set. Names are unique within a Settlement. Zero/multiple Holders are valid; Public players may hold Parcels without becoming Citizens.
- Unassigned Parcels are governable by Owner/Governors. Assigned Parcel revocation, Holder removal or boundary changes require governance and all Holders to agree; neither side may reshape unilaterally. Administrators resolve disputes. Rent/contracts are not modeled.
- Source and destination of protected automation must both authorize the operation; same Settlement does not imply same Parcel policy.
- Commands must cover all operations. Management Book and Parcel Wand are replaceable convenience frontends with no item-owned authority. Wand + named off-hand Name Tag selects aligned 8×8×8 cells; preview then atomically validate/commit. Surface-cell deletion must preserve connectivity.
- Initial Settlements use stable numeric identity/generated labels; official naming and every rename are administrator-mediated after qualification. Name/ID migration from existing data is an implementation gate, not permission to delete historical names.
- Settlement writes go through the Paper runtime coordinator, which reserves affected territory until committed SQL is published to the main-thread index. Do not add mutation callers that bypass this publication gate.
- Fatal persistence failures and hot unloading stop the server rather than dropping protection. Use full restarts; no reload command or plugin-manager hot reload.
- Activity is settlement-local and does not require scheduled writes to every inactive settlement.
- Migrate Activity authorization to Governor/Citizen/Holder/Public and bans without increasing farming potential. Existing Visitor weights are legacy; Public/Holder weights and their persisted-policy migration still need definition.
- Sufficient activity recovery stops further decay without repairing damage or advancing Era; queued mutations must revalidate activity eligibility.
- MVP Settlements remain owned/protected regardless of inactivity or ruin unless explicitly abandoned or changed by an authorized lifecycle operation. Automatic peripheral release/Civic Capacity belongs to the required post-MVP dynamic-cycle phase; its rules remain unresolved.
- Reclaim uses normal chunk claiming and the claiming Settlement's team, never restored former permissions. History survives, but the active restoration baseline starts from the inherited structure's current state; reclaim does not repair or advance Era.
- Never scan every block in every settlement on a global timer.
- Never access or mutate Paper world state from an arbitrary asynchronous thread.
- World mutations must revalidate world instance, ownership, permission, and expected state immediately before application.
- Decay and Repair wait for naturally loaded chunks; do not load or retain chunks solely to progress jobs. Pending work survives restart.
- Preserve concurrent player edits and replan affected outdated work; unaffected valid work may continue.
- Durable events use a transactional outbox with at-least-once delivery and idempotent consumer effects. Reconcile world-save and DB progress independently rather than assuming exactly-once mutation.
- Building geometry must represent occupied/interior/exterior regions, openings and structural/support relationships. Renovation may conflict/retire old restoration fragments; expected-air checks alone cannot justify restoring obsolete walls through a new interior.
- Block entities require a dedicated safe policy; system decay creates no ordinary collectible drops. Start structural decay with bounded material profiles/failure modes, not full physics; wild structures/infrastructure use coarse localized/lightweight models.
- All gameplay timing uses shared game time. Freeze with `advance_time=false`; count forward sleep/command jumps, re-anchor rewinds, and never accrue downtime.

## How to change architecture

1. Distinguish an accepted decision from an option still under discussion.
2. Update `ARCHITECTURE.md` for every accepted architecture change.
3. Add or amend the dated rationale in `.agent/DECISIONS.md`.
4. Remove the corresponding item from `.agent/OPEN_QUESTIONS.md`.
5. Update `README.md` only when the human onboarding path or settled summary changes.

The architecture is canonical. `.agent` files summarize it; they must not create a second architecture. Older decision-log entries are historical where the current architecture explicitly supersedes them. Preserve the distinction between implemented legacy behavior and the accepted target.

## How to change code

For code changes:

1. Reuse existing module, package, persistence, and test conventions.
2. Update every caller in one clean cutover; do not leave aliases or deprecated paths unless explicitly requested.
3. Keep gameplay semantics out of `esmp-core`.
4. Put synchronous invariant enforcement behind application APIs; publish domain events only after owning state commits.
5. Treat DB-to-world mutation as recoverable, idempotent work because SQL and Minecraft world state cannot share one transaction.
6. Run the narrow behavioral test or actual server scenario that exercises the change.

## Git workflow

Commit completed, coherent milestones regularly using the configured Git identity. Inspect staged changes before committing, keep runtime data/local tooling out of Git, and never commit secrets or unrelated user work. Record actual verification in the handoff. Do not push or configure a remote unless the user requests it.

Use `./gradlew build` for compilation, packaging, and tests, and `./gradlew :esmp-core:test` for storage regressions. With `JAVA_HOME` pointing to JDK 25, `ESMP_ACCEPT_EULA=true ./scripts/run-dev.sh` starts the isolated loopback Paper server after EULA acceptance; use `esmp status` and `stop` in its console. Never infer gameplay coverage from the foundation smoke.

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

1. Accepted decisions are present in `ARCHITECTURE.md`.
2. `.agent/DECISIONS.md` contains the rationale.
3. Resolved items are removed from `.agent/OPEN_QUESTIONS.md`.
4. Human-facing instructions remain in Simplified Chinese in `README.md`.
5. The handoff never reports unexecuted verification as successful.
