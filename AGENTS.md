# ESMP Agent Instructions

These instructions apply to the entire repository.

## Mission and current state

ESMP is a Paper-first, vanilla-client Minecraft SMP architecture for a permanent civilization world, renewable Resource worlds, and a permanent Factory world.

The repository is currently architecture/documentation only. Do not claim that the plugin builds, starts, or passes tests until real project files and commands exist.

Deployment is one modular Paper plugin JAR with a coordinated bootstrap/lifecycle. Initial application persistence is embedded SQLite with versioned migrations and module-owned repositories. Coordinate short writes and keep database I/O off the world execution context. Do not introduce separate plugin artifacts or promise transparent database portability.

The accepted development baseline is Minecraft Java Edition/Paper 26.2, Java plugin source, JDK 25, and Gradle Kotlin DSL. Kotlin DSL is build configuration only. Pin a compatible wrapper, concrete Paper API/server build, and dependencies when creating project files; do not reopen the chosen language/platform as unresolved.

## Read order

Before non-trivial work, read:

1. `README.md` for the human entry point.
2. `ARCHITECTURE.md`, especially Current Decisions, Intentionally Unresolved, and Next Architecture Work.
3. `.agent/DECISIONS.md` for rationale.
4. `.agent/OPEN_QUESTIONS.md` for unresolved designs.
5. `.agent/HANDOFF.md` for the current state.

## Canonical vocabulary

- **Civilization** is the overall persistent Main World gameplay environment/domain. It is not a player-owned aggregate.
- **Settlement** is the Main World aggregate for ownership, members, roles, permissions, activity, and claimed chunks.
- **Claim** is the protected chunk relationship and the operation that changes it.
- **Plot** is the separate Factory World claim aggregate.
- Factory Plots use independent fixed Owner/Manager/Member/Visitor roles, per-Plot membership, and freely located four-directionally contiguous chunk claims. Do not introduce a shared crew owner or inherit Settlement membership, capability rules, or the 64-chunk cap.
- Factory Members may build and operate machinery; Managers additionally manage claims, ordinary Members, and settings; ownership powers are Owner-only. Factory Visitors may only move and observe, not activate controls (including by walking over pressure plates), use inventories, or interact with protected entities. Enforce actions individually.
- Factory machines use ordinary Paper ticking and player-proximity rules, without initial ESMP machine simulation, power, per-machine budgets, or offline catch-up production. Prefer vanilla-like compatibility; evaluate behavior-changing optimizations explicitly and never promise exact vanilla parity.
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
- Ordinary unclaim must not remove the last Settlement chunk, even for the Owner. Ending the claim requires the Owner's explicit abandon operation; Managers cannot bypass this through unclaiming.
- Adjacent Settlements remain separate identities and never merge automatically, including when they share an owner.
- Settlement chunk claiming is initially free. The configurable per-Settlement chunk cap is enabled by default at 64 chunks and may be changed or disabled by server policy.
- Settlement roles are fixed: exactly one Owner while owned, zero or more Managers, zero or more Members, and every other player as a derived Visitor.
- Store the Owner only in `Settlement.owner_uuid`, store Managers and Members in `SettlementMember`, and never persist Visitor memberships.
- Custom Settlement roles are not supported.
- Owner has every Settlement capability and exclusively controls Manager promotion/demotion, ownership transfer, and abandon/release.
- Managers may claim/unclaim, manage ordinary Members and settings, build, interact, use protected resources, and repair; they cannot manage Managers or exercise ownership-only operations.
- Members may build, break, interact, use containers, use protected entities or machines, and repair; they cannot manage claims, people, roles, or settings.
- Visitors may enter and use the fixed safe-public-interaction category but cannot build, use protected inventories, interact with protected entities, or directly use machine interfaces.
- Implement Visitor access with action classification, never an actual game-mode switch or blanket right-click cancellation.
- Authorization defaults to deny for non-members unless an explicit permission allows the action.
- Settlement and Factory domains may share spatial and authorization infrastructure, not business state.
- Activity is settlement-local and does not require scheduled writes to every inactive settlement.
- Meaningful Visitor interaction earns very small activity credit relative to the team; passersby earn none. Activity credit grants no extra permissions.
- Sufficient activity recovery stops further decay without repairing damage or advancing Era; queued mutations must revalidate activity eligibility.
- Confirmed Owner abandonment may release intact or active Settlements. Automatic release requires inactivity and ruin; release preserves history and does not mutate buildings.
- Reclaim uses normal chunk claiming and the claiming Settlement's team, never restored former permissions. History survives, but the active restoration baseline starts from the inherited structure's current state; reclaim does not repair or advance Era.
- Never scan every block in every settlement on a global timer.
- Never access or mutate Paper world state from an arbitrary asynchronous thread.
- World mutations must revalidate world instance, ownership, permission, and expected state immediately before application.
- Decay and Repair wait for naturally loaded chunks; do not load or retain chunks solely to progress jobs. Pending work survives restart.
- Preserve concurrent player edits and replan affected outdated work; unaffected valid work may continue.
- Durable events use a transactional outbox with at-least-once delivery and idempotent consumer effects. Reconcile world-save and DB progress independently rather than assuming exactly-once mutation.

## How to change architecture

1. Distinguish an accepted decision from an option still under discussion.
2. Update `ARCHITECTURE.md` for every accepted architecture change.
3. Add or amend the dated rationale in `.agent/DECISIONS.md`.
4. Remove the corresponding item from `.agent/OPEN_QUESTIONS.md`.
5. Update `README.md` only when the human onboarding path or settled summary changes.

The architecture draft is canonical. `.agent` files summarize it; they must not create a second architecture.

## How to change code

Once code exists:

1. Reuse existing module, package, persistence, and test conventions.
2. Update every caller in one clean cutover; do not leave aliases or deprecated paths unless explicitly requested.
3. Keep gameplay semantics out of `esmp-core`.
4. Put synchronous invariant enforcement behind application APIs; publish domain events only after owning state commits.
5. Treat DB-to-world mutation as recoverable, idempotent work because SQL and Minecraft world state cannot share one transaction.
6. Run the narrow behavioral test or actual server scenario that exercises the change.

## Git workflow

Commit completed, coherent milestones regularly using the configured Git identity. Inspect staged changes before committing, keep runtime data/local tooling out of Git, and never commit secrets or unrelated user work. Record actual verification in the handoff. Do not push or configure a remote unless the user requests it.

Do not invent build, test, or server commands. Read them from the checked-in build files and update this document or `README.md` when they become stable.

## Handover protocol

Before ending non-trivial work, update `.agent/HANDOFF.md` with:

Keep the handoff a current snapshot, not an append-only transcript. Replace superseded next actions and old verification logs; retain durable rationale in `.agent/DECISIONS.md`. Distinguish prerequisites for the first runnable plugin from later feature-enablement gates in architecture §§28–30. Do not reopen settled gameplay or require all deferred tuning before basic implementation.

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
