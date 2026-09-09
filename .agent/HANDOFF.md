# Current Handoff

## State

- Java 25 / Paper 26.2 Settlements remain implemented in four Gradle modules, one JAR, with Core and Civilization SQLite schemas at version 1. This work changes documentation only.
- The user approved integrating the gameplay extension into `ARCHITECTURE.md` and revising the roadmap. §16A is canonical; the discussion pause is lifted. No extension runtime is implemented or enabled by this approval.
- Factory Realm remains removed. Shared player state/Ender Chests, Building Era restoration semantics, and current-state reclaim baselines remain unchanged. Full seasonal palettes and chat ergonomics remain deferred.
- Settlement in-game testing is user-confirmed. Prior build/live ActionBar evidence belongs to the preceding implementation milestone; it was not rerun here.

## Completed

- Integrated progression/culture, functional Facility certification, shared capacity, bounded offline production and Activity pause, physical depot stock, goods/regional specialization, ledger currency/budgets, finite Server Trade and physical stalls, freight/teleport, and the Minecraft-day calendar/native seasonal effects.
- Assigned logical Civilization/Economy/Transport/Calendar ownership without creating Gradle modules, schemas, or new Core gameplay responsibilities.
- Extended persistence, recovery, scheduling, data ownership, vocabulary, current decisions, and feature-enablement gates. Retained unresolved numerical, qualification, accounting, and clock choices explicitly.
- Replaced the old linear roadmap with dependency-aware slices. Activity is next; Building qualification precedes production, stock/payment recovery precedes markets/transport, and decay/repair, seasonal presentation, and Resource operations have independent gates.
- Retired the discussion specification into a canonical navigation pointer; prior discussion remains in Git history, not a competing live architecture.

## Changed files

- `ARCHITECTURE.md` — accepted §16A gameplay contract, cross-cutting boundaries, decisions, gates, roadmap, and next action.
- `README.md` — Simplified Chinese accepted-but-unimplemented summary and next development sequence.
- `.agent/DECISIONS.md` — dated integration approval, rationale, invariant preservation, and dependency decisions.
- `.agent/OPEN_QUESTIONS.md` — unresolved feature gates only, distinguishing selected boundaries from remaining details.
- `.agent/GAMEPLAY_EXTENSION_PROPOSAL.md` — superseded proposal replaced by links to canonical architecture/gates.
- `.agent/HANDOFF.md` — this integration state and exact verification evidence.

## Verification

- Executed an inline Python document check: 42 accepted-contract assertions passed against `ARCHITECTURE.md` across stages/culture, certification, production, markets/money, transport, seasons, and architectural boundaries.
- The same check passed balanced fenced-block and local Markdown file/heading-anchor resolution for `ARCHITECTURE.md`, `README.md`, `.agent/DECISIONS.md`, `.agent/OPEN_QUESTIONS.md`, and `.agent/GAMEPLAY_EXTENSION_PROPOSAL.md`.
- Checked the active question/proposal files no longer require the already-granted integration approval. README additions remain Simplified Chinese. No build, test suite, server restart, or gameplay scenario was run for this documentation-only change.

## Next action

Resolve the bounded Settlement Activity contribution/elapsed-time contract, including meaningful participation/caps and production-pause eligibility, then implement that slice. Keep mutable calendar time separate from Activity/financial elapsed-time accounting unless explicitly reconciled; no need to reopen the accepted extension directions.

## Risks or blockers

- Numeric thresholds, precise qualification/reactivation, workforce sharing, physical stock/payment recovery, recipient/ownership changes, and calendar reconciliation remain feature gates—not approved defaults.
- Full seasonal palette techniques and compatibility remain deferred; native block effects also need safe loaded-area mutation/recovery before enablement.
- Local development defaults remain offline/unsigned-profile and loopback-only. Do not expose this impersonation-permitting configuration publicly; online/offline UUID migration requires explicit mapping and backup. No server state was changed here.
