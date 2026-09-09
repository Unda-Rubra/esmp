# Current Handoff

## State

- Shared game clock, gameplay timer migration, and Settlement Activity are implemented in the existing four-module Java 25/Paper 26.2 plugin. Core world schema remains 1; Civilization schema is 2; config version is 3.
- The user selected all forward jumps: normal MAIN time, sleeping and forward time commands advance reckoning. Backward jumps re-anchor without reversing progress. `advance_time=false` freezes time-driven progression and Activity credit; shutdown/downtime adds no time.
- Clock identity/checkpoints, offers, Activity/caps and pause intervals persist. Building discovery, physical decay/repair, production, Resource resets and seasonal presentation remain disabled. Factory Realm remains removed; shared player state and Building Era semantics remain unchanged.
- Existing user development server/data were not stopped, hot-reloaded or overwritten. Built artifact is `esmp-plugin/build/libs/esmp-0.1.0-SNAPSHOT.jar`; deploy through the normal full restart path.

## Completed

- Added neutral `GameClock` and MAIN-thread Paper source adapter with positive-delta counting, freeze/rewind handling, boot re-anchoring, source UUID protection, periodic checkpoints and shutdown flush.
- Migrated offer policy/deadlines to ticks, preserving legacy remaining lifetime once; borders and protection-message throttling now use game time. New historical events expose game ticks, retaining legacy/diagnostic wall timestamps only.
- Added lazy exponential Activity, threshold hysteresis, exact inactivity-crossing/grace timing, persistent actor/category cooldowns and daily caps, current-role/world/chunk/revision revalidation, and closed production-pause intervals.
- Added bounded Paper observation coalescing for actual block place/break, inventory opening and intentional safe-control use. No idle/movement/pressure-plate/automation/frozen-clock credit.
- Added `/settlement activity [name]` and Activity output in `info`; `/esmp status` reports clock ticks and advancement.
- Updated canonical architecture, Chinese README, dated rationale, open gates and proposal pointer.

## Changed files

- `esmp-core/.../time/GameClock.java` and `GameClockTest.java` — persisted source-delta clock and freeze/rewind/restart/sampling-frequency regressions.
- `esmp-civilization/.../settlement/SettlementService.java`, `SettlementPolicy.java`, `SettlementOffer.java`, `SettlementEvent.java`, and `SettlementServiceTest.java` — schema/deadline migration, explicit tick fields and expiry migration regression; existing tests adapted.
- `esmp-civilization/.../activity/*` and `ActivityServiceTest.java` — policy, observations, status, durable accounting and lifecycle/revalidation tests.
- `esmp-plugin/.../GameTimeRuntime.java`, `EsmpPlugin.java`, `settlement/ActivityListener.java`, `SettlementRuntime.java`, `SettlementCommands.java`, `ClaimProtection.java`, `ClaimVisualization.java`, and `resources/config.yml` — Paper lifecycle/source/events, bounded collection, commands and config migration.
- `ARCHITECTURE.md`, `README.md`, `.agent/DECISIONS.md`, `.agent/OPEN_QUESTIONS.md`, `.agent/GAMEPLAY_EXTENSION_PROPOSAL.md`, `.agent/HANDOFF.md` — implemented scope, defaults, operational limitations, verification and next work.

## Verification

- `./gradlew build --no-daemon --console=plain` with the existing JDK 25 toolchain passed after final source changes. Seven added regression tests cover clock forward/rewind/freeze/restart/source protection and sample-frequency invariance; Activity lazy founding-time decay, exact grace boundary, restart/caps/freeze, role/revision validation, recovery intervals; legacy expiry migration once without identity/lifetime reset.
- Isolated managed Paper fixture `esmp-activity-smoke` on loopback port 25582 (RCON 25583), using pinned build 121, passed actual `/settlement create` and `activity` output, `advance_time=false` with a forward command jump, exactly-once 24000-tick forward jump, rewind without time subtraction, and progression at 40 TPS.
- A protocol client actually placed a block: status reported BUILD credit 1.00. Advancing eight game days produced INACTIVE and elapsed production-grace eligibility without mutating buildings or producing goods.
- Restarted with final built JAR. Frozen reckoning stayed exactly 219544 across shutdown/restart; Activity retained INACTIVE, its original inactivity tick 108322, and last BUILD tick 25380. Actual bed sleep advanced reckoning from 219547 to 230573, counting the night skip once.
- All fixture runs stopped normally with exit 0. The older 1.21.11 protocol client through ViaVersion/ViaBackwards reported particle decoding errors during the first run; gameplay actions and command assertions passed, but this is not native desktop visual verification. Existing user-confirmed Settlement coverage was not rerun wholesale.
- Background agents failed before execution because their runtime had no model configured. Reported the tool issue; all implementation was completed inline.
- Parsed final JUnit XML: GameClockTest 2 tests, ActivityServiceTest 4 tests, SettlementServiceTest 8 tests, all zero failures/errors. Local documentation links/anchors and fenced blocks passed checks. Temporary client scripts, extracted API files and isolated translator JARs were removed after the fixture stopped; ignored fixture data/logs remain as evidence.

## Next action

Define automatic Building discovery and stable identity across split/merge and claim changes, then implement that bounded slice. Do not re-enable decay/repair/production before their separate recovery/accounting gates.

## Risks or blockers

- Activity policy is configurable before first initialization. Altering an installed policy currently fails startup pending explicit policy migration, rather than silently reinterpreting old scores. Balance and policy-migration UX remain future work.
- Abrupt failure may lose uncheckpointed clock-only progress and uncommitted/coalesced Activity observations. Game-timed writes checkpoint in their own SQL transaction; paired world/database backup/restore remains required before physical mutation or economic exchange.
- Caps bound repetitive actions, not perfect automation/bot detection. Events coalesce by actor/Settlement/category and overflow is bounded; not every click is an independently audited contribution.
- Leap/season presentation, Facility/economic accounting, and physical mutation remain gated. Native visual testing should use the target 26.2 client, not assume old-protocol particle compatibility.
- Local offline/unsigned-profile settings remain loopback-only; public exposure permits impersonation. User development server was left untouched.
