# Current Handoff

## State

- Java 25 / Paper 26.2 Settlements remain implemented in four Gradle modules, one JAR, with Core and Civilization SQLite schemas at version 1. This work changes documentation only.
- The user approved the gameplay extension and subsequently selected one shared game-tick/day clock for all gameplay timing. `advance_time=false` freezes time-driven progression; server downtime adds no time. This supersedes the assistant's real-elapsed-time Activity recommendation. No runtime clock/Activity cutover was implemented in this documentation change.
- Factory Realm remains removed. Shared player state/Ender Chests, Building Era restoration semantics, and current-state reclaim baselines remain unchanged. Full seasonal palettes and chat ergonomics remain deferred.
- Settlement in-game testing is user-confirmed. Prior build/live ActionBar evidence belongs to the preceding implementation milestone; it was not rerun here.

## Completed

- Updated the canonical Activity and calendar contracts, current decisions, gates, roadmap and vocabulary to use shared game time rather than a separate real-time accounting clock.
- Clarified offline production: absent players/unloaded chunks while game time advances, never server-downtime or frozen-time accrual.
- Moved the shared clock and existing gameplay timer audit/migration before Activity. Source, sleep/command jumps, rewinds and restart/Resource-reset reconciliation remain unresolved; game time and the freeze/downtime policy are settled.
- Updated the Chinese README, dated rationale, open gates and proposal pointer. Full seasonal presentation remains deferred.

## Changed files

- `ARCHITECTURE.md` — shared gameplay clock, Activity/production implications, gates and clock-first roadmap.
- `README.md` — Chinese timing semantics and explicit unimplemented timer migration.
- `.agent/DECISIONS.md` — dated rejection of real-time gameplay accounting and rationale.
- `.agent/OPEN_QUESTIONS.md` — remaining source/jump/recovery/migration gates only.
- `.agent/GAMEPLAY_EXTENSION_PROPOSAL.md` — clock-first navigation pointer.
- `.agent/HANDOFF.md` — this integration state and exact verification evidence.

## Verification

- Inline Python check passed: eight shared-clock contract assertions against the architecture; obsolete separate-clock wording checks, balanced fenced blocks, and local Markdown links/heading anchors across all six changed documents.
- Documentation-only verification; no build, server restart, gameplay scenario or live-clock behavior is claimed. Prior runtime verification remains in Git history and is not evidence of implemented shared-clock support.

## Next action

Resolve canonical game-clock source and sleep/time-command jump semantics, then implement persistent shared game time and migrate existing gameplay timers before Activity. Activity scoring/caps and production-pause eligibility use game ticks/days; do not reintroduce a wall-clock fallback or downtime catch-up.

## Risks or blockers

- Canonical source, sleep/command jumps/rewinds, restart reconciliation and migration of existing timer state/configuration remain clock implementation gates. Numeric Activity rules, item/payment recovery and calendar boundaries also remain unresolved; no runtime migration has occurred.
- Full seasonal palette techniques and compatibility remain deferred; native block effects also need safe loaded-area mutation/recovery before enablement.
- Local development defaults remain offline/unsigned-profile and loopback-only. Do not expose this impersonation-permitting configuration publicly; online/offline UUID migration requires explicit mapping and backup. No server state was changed here.
