# Current Handoff

## State

- Frozen roadmap **Phases 0–6 are implemented/verified**. Phase 7 station material/time Repair plus History/Era is next. No Repair job/material accounting, Era advancement, automatic Claim release or deferred simulation systems were added.
- Active registered Buildings now support bounded WEATHER, vertical DROP and connected CAVE_IN. Default entry/recovery support thresholds are 20/30, grace 72000 game ticks, operation interval 24000 and deferred check interval 1200. New/migrated Buildings begin a fresh assessment rather than receiving retroactive instant damage.
- SQL intent precedes physical mutations. The existing coordinator reserves the Settlement, drains admitted Activity, checks current eligibility/authority, and keeps protection reserved through world application and outcome publication. Direct and persisted intent profiles, world/Building/ownership/Claim/security/support/source facts and both-side Owner BUILD permissions are checked.
- Damage is separate from intended baseline. INTACT means no current damage; DAMAGED includes weathering/debris; RUINED defaults to at least 50% of mutable occupied baseline cells becoming air. Recovery stops future work without reducing damage. Real manual restoration clears obsolete damage and advances baseline revision even if intended data is unchanged; it does not grant Era.
- Config is 13, backup `config-before-decay.yml`. New structured tables: `decay_state`, `decay_operations`, `decay_damage`. Existing bridge markers, dependency pins, released baselines and frozen architecture bundle are unchanged.
- There are 72 pending player checks: 48 historical +2 Phase 0 +3 each Phases 1–4 +5 each Phases 5–6. No native-client/user acceptance was automatically checked off.

## Completed

- Added DecayService/Policy/State/Operation contracts, deterministic DecayPlan and typed JooqDecayRepository. Due selection is indexed and bounded; candidate geometry comes from the bounded baseline, not a global world scan. Candidate lists cap at 32 and physical batches at eight cells.
- WEATHER uses explicit full-cube substitutions. DROP moves one block down one cell into air within the Building; CAVE_IN moves up to four same-height face-connected blocks with the same primitive, all in one chunk. No item or falling-block entity is spawned. Block entities, stations, unsafe attachment/fluid/gravity neighbors and occupied destinations are excluded or deferred; physics cascades are not started.
- Accepted local credits update nearby Building eligibility revisions and assess recovery at the exact credit tick. Hysteresis and threshold-crossing math preserve short recovery pulses and shared-clock freeze/downtime semantics. Aggregate Activity behavior remains intact.
- Durable operations carry stable IDs/global sequences, baseline/world/authority/activity/policy context and exact expected/target cells. Prepared work waits for natural loads. Per-chunk `esmp:decay/<buildingUUID>` revision/sequence markers reconcile independently from SQL outcomes. Before/target mixtures converge; already-applied facts are not duplicated. New operations cannot overtake recovery for the same Building/chunk.
- Player baseline changes supersede old intents and obsolete damage. Historical operations never physically restore a superseded baseline; read-only reconciliation can clear damage that is absent in the actual saved world. Conflicting cells are preserved, not overwritten. Legal restoration of original intended data now also advances revision and clears damage.
- Added `/settlement decay <buildingUUID>` status, lifecycle/config wiring and Activity barrier integration. All world calls remain on the Paper thread and all SQL in repositories. No force-load/ticket pathway was added.

## Changed files

- `schema/db/changelog/phase6-decay.xml`, master changelog, root `build.gradle.kts` — new structured Decay state/intent/damage schema and generated table ownership.
- Civilization `decay/DecayService.java`, `DecayPlan.java`, `persistence/JooqDecayRepository.java` — eligibility, planning, durable operations, reconciliation and condition facts.
- `JooqActivityRepository.java`, `ObservationServices.java`, `SettlementServices.java` — contribution-time Decay observation and composition; all factory callers migrated without a compatibility overload.
- `JooqBuildingRepository.java` — baseline rebase/invalidation and manual-restoration damage resolution.
- Plugin `decay/DecayRuntime.java`, `BuildingRuntime.java`, `SettlementRuntime.java`, `ActivityListener.java`, `EsmpPlugin.java`, `SettlementCommands.java`, config.yml — bounded world pipeline, metadata access, serialized reservation/barrier, config 13 and status command.
- New `DecayServiceTest.java`; activity/certification/Parcel factory fixtures, legacy Settlement/Parcel fixtures and `SchemaMigrationTest.java` — behavior, corrupted-intent rejection, old/new schema preservation and fresh grace.
- README/AGENTS and .agent decision/gates/checklist/handoff — current behavior, operator tuning, precise evidence and Phase 7 boundary.

## Verification

- Final `JAVA_HOME=.tooling/jdk-25.0.4.1+1/Contents/Home ./gradlew clean check build --no-build-cache --no-daemon --console=plain` passed: **127 tests, zero failures/errors/skips**, 30 tasks executed/three up-to-date. Nineteen changed/new Java sources were formatted during implementation. Dependency verification and ArchUnit stayed enabled; no pins changed.
- Ten Decay regressions cover grace/freeze/downtime/recovery timing, explicit WEATHER/codec, connected CAVE_IN and vertical DROP, damage/ruin, recovery without healing, independent saved-world/SQL reconciliation, player baseline conflicts, destination Parcel and station/entity exclusions, partial conflicts, persisted material-profile corruption and manual restoration. A native Phase 5 upgrade retains baseline bytes and starts new grace with no invented damage; earlier migration suites still pass.
- Real Phase 5 data copied to `.run/mvp-phase6-base` upgraded without changing prior gameplay rows or inventing operations/damage. A new frozen fixture used one registered floating roof, a station, a chest containing seven diamonds, a protected adjacent wall torch and external natural grass. All runtime tuning was confined to disposable copies.
- Operator-only **test instrumentation**, not production switches, delegated real repository calls and introduced controlled exit-137 boundaries. Separate prepared/world/sql/partial fixtures proved:
  - durable PREPARED intent before world application; players were saved away from the target, and restart kept the target unloaded until natural arrival;
  - a fully saved world target/marker while SQL remained PREPARED;
  - SQL APPLIED with world autosave disabled, so restart initially had the original block and no marker;
  - a deliberately saved partial CAVE_IN target mixture with SQL PREPARED, modeling interrupted batch persistence.
- All four recovered using the original operation ID. Server block predicates verified expected targets; DECAY_APPLIED facts were not duplicated. Chest/station/torch and external grass stayed intact; item and falling-block entity counts remained zero. The partial case reconstructed all four moves from mixed before/target states.
- The **final artifact** then ran the modes/conflict/manual scenarios. WEATHER/DROP/CAVE_IN reached RUINED with 20 damaged cells and 10 lost source cells. A real local action recovered support to approximately 0.991 and stopped later mutations without reducing damage. An injected gold-block conflict after persisted intent was preserved, with no false damage. Manual placement/removal restored the original design, cleared damage to INTACT and advanced baseline revisions; chest inventory still contained seven diamonds.
- The fault probe was removed from every fixture and its sources deleted. A final no-probe restart reconciled superseded historical operations without changing the manually restored roof or baseline revision. The fresh single-ESMP-JAR deployment reached READY/protection=true. No player-facing material Repair or Era behavior was claimed.
- Evidence: ignored `.run/phase6-mvp-phase6-*.log`, `roof.json` files and the isolated `mvp-phase6-*` server databases/worlds/logs. The four exit-137 failures are intentional crash tests; recovered servers later stopped normally. Temporary probe JAR/classes/source and smoke script were removed. All verification servers/clients are stopped; the user's development server was not started/upgraded.
- Initial redundant floor-mod casts failed `-Werror` and were removed; validation was not suppressed. Additional profile validation, exact credit-time recovery and same-intended-data manual restoration were covered before final acceptance.

## Next action

Begin frozen Phase 7 only: derive current damage material requirements, accept real materials at the Building station, persist reservations/jobs and game-time progress, restore only current active-baseline damage through guarded natural-load batches, recover inventory/world/SQL accounting and advance Era once for a qualifying RUINED→Repair→INTACT cycle.

## Risks or blockers

- World saves, SQL and future item inventories are not atomic. Preserve the intent/marker/outcome distinction and expected-state checks; do not replace them with an exactly-once assumption or replay superseded baselines.
- Profiles are intentionally conservative full cubes. Unsafe neighboring attachments/fluids/gravity blocks or entities can defer/exclude candidates; some structures will weather or remain partially damaged rather than undergo general physics collapse. Expansion requires explicit safe profiles/evidence.
- Decay uses current Owner BUILD permission for source/destination, without administrator or same-Settlement bypass. A restrictive Holder policy may make a locally inactive Building ineligible for physical changes.
- Condition is derived from current damage and structural loss. Manual rebuilding is allowed vanilla gameplay but must not be mistaken for future station Repair or award Era. Phase 7 needs durable qualifying-cycle identity/accounting.
- Isolated cases use short timing/low thresholds and protocol compatibility plugins; shipped defaults remain 72000/24000/1200 and 20/30. Do not copy fixture configuration or fault instrumentation to user deployment.
- Existing Building observation recovery still fails closed on genuinely stale world/authority/bounds. Keep its journal for explicit reconciliation rather than deleting evidence or bypassing permissions.
