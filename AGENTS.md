# ESMP Agent Instructions

These instructions apply to the entire repository.

## Mission and current state

ESMP is a Paper-first, vanilla-client Minecraft SMP architecture for a permanent civilization world and renewable Resource worlds. A dedicated industrial realm is outside current scope; do not restore it from historical decisions without explicit approval.

The plugin implements playable Settlements on Paper 26.2: persistent claims/roles/offers, confirmed abandonment, history/outbox, action-specific protection, and private borders. Building discovery, Activity, decay/repair, automatic release, and Resource resets remain disabled.

Deployment is one modular Paper plugin JAR with a coordinated bootstrap/lifecycle. Initial application persistence is embedded SQLite with versioned migrations and module-owned repositories. Coordinate short writes and keep database I/O off the world execution context. Do not introduce separate plugin artifacts or promise transparent database portability.

Use Java source under `xdu.undarubra.esmp`, JDK 25, and Gradle Kotlin DSL. Pins: Gradle Wrapper 9.7.1, Paper 26.2 build 121 / API `26.2.build.121-stable`, SQLite JDBC 3.53.4.0. Core has no Paper dependency. Domain modules contribute world definitions; `esmp-plugin` is the single composition root.

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
- Settlement writes go through the Paper runtime coordinator, which reserves affected territory until committed SQL is published to the main-thread index. Do not add mutation callers that bypass this publication gate.
- Fatal persistence failures and hot unloading stop the server rather than dropping protection. Use full restarts; no reload command or plugin-manager hot reload.
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
