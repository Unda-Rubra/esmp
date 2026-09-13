# Current Handoff

## State

- Frozen roadmap Phases 0–7 are implemented/verified. Phase 8 full acceptance/performance/paired backup and release gates are next; no MVP tag or user acceptance is implied.
- Whole-Building station Repair consumes real plain materials, progresses with shared GameClock in bounded naturally loaded batches, preserves current design/conflicts, and records History/Era. No automatic Claim release, rewards, artisans, historical replay or post-MVP systems were added.
- Config 14, backup `config-before-repair.yml`. Structured `phase7-repair` adds `building_eras`, `repair_jobs`, `repair_cells`, `repair_materials`, `repair_transfers`, `repair_operations`, `repair_era_awards`. Existing bridge markers/dependency pins and frozen architecture bundle are unchanged.
- There are 77 pending player checks: previous 72 plus five Phase 7 checks. Automated/protocol-client evidence is not native-client acceptance.

## Completed

- Added RepairService/RepairMaterials and typed JooqRepairRepository, plus Paper RepairRuntime/RepairInventory and lifecycle/coordinator wiring. Public factories/callers were migrated together; no persistence types leak into domain APIs.
- Commands: `/settlement repair inspect|prepare|supply|status|start|cancel|refund|jobs`; `/settlement history-catalog` complements existing history reads and structured MvpHistory API.
- One station, payer-only supply/refund, six-block supply radius, current governance and Parcel BUILD/INTERACT authorization. Only ordinary matching items are accepted; partial funding waits. Unused escrow can be refunded within inventory capacity. Cancellation never undoes completed physical work.
- Slot before/after intents plus saved player sequence receipt reconcile inventory separately from SQL. Per-chunk Building revision/sequence markers reconcile world progress, including SQL-completed jobs whose world save rolled back. Unresolved operations block unsafe refunds/metadata changes. No forced chunk loading.
- Current active-baseline damage alone is repairable. Conflicts retire only affected cells; renovations and retired baselines win. Default batch interval is 200 shared game ticks; freezes/downtime do not generate progress.
- Era begins at zero without invented prior repairs. Durable qualifying ruin cycles survive permission pauses and award once after Repair reaches INTACT. Ordinary damage repair and manual completion do not award; a later genuine ruin cycle can award again.

## Changed files

- `schema/db/changelog/phase7-repair.xml`, master changelog, `build.gradle.kts`, `buildSrc/.../GenerateJooq.java`: structured schema, generated ownership and schema validation.
- Civilization `repair/*`, `persistence/JooqRepairRepository.java`, Building/Decay repositories, `api/SettlementServices.java`, `settlement/SettlementService.java`, `MvpHistory.java`: material/jobs/recovery/Era contracts and integration.
- Plugin `repair/*`, `SettlementRuntime.java`, `SettlementCommands.java`, `EsmpPlugin.java`, config.yml: inventory/world execution, commands, lifecycle and config migration.
- `RepairServiceTest.java`, Settlement/Parcel factory fixtures, `SchemaMigrationTest.java`: behavioral and migration coverage.
- README, AGENTS and .agent decision/gates/checklist/handoff: implemented behavior, operator instructions and evidence. Canonical frozen design remains unchanged.

## Verification

- `JAVA_HOME=.tooling/jdk-25.0.4.1+1/Contents/Home ./gradlew clean check build --no-build-cache --no-daemon --console=plain` passed. XML reports total 139 tests, zero failures/errors/skips. Dependency verification and architecture checks stayed enabled. No production code changed after that build.
- Twelve Repair regressions cover partial funds/duplicate confirmation, ruin completion/restart, ordinary/manual no-award, cancellation/refund capacity, pending-world barriers, per-cell conflicts, renovation, plain-item/capacity codec, later cycles, player receipt reconciliation, native Phase 6 migration, permission-pause cycle retention and terminal-state rejection.
- Ignored `.run/phase7-smoke.cjs` exercised isolated Paper copies of a registered RUINED roof with real plain/named inventory. Partial supply waited; full supply consumed ten plain items, retained two named items, repaired to INTACT and advanced Era to one.
- Controlled exit-137 fault injection wrapped real repository calls, never production switches. Supply cuts: durable intent only; saved inventory before SQL confirm; confirmed SQL with restored old player data. Every recovery used exactly one material movement.
- World cuts: durable intent; saved world before SQL outcome; SQL outcome before world save; completed job/Era before world save; deliberately saved partial batch. All recovered without duplicate consumption/Era. Terminal-job recovery checked every applied target (including debris air) with server block predicates before freezing time; SQL COMPLETED was not accepted as physical proof.
- Per-cell injected gold conflict: nine materials consumed, one unused item refunded, conflict preserved and Era stayed zero. Valid other work completed.
- Refund cuts: saved credited inventory before SQL confirm and SQL-confirmed refund with old player data restored. Both recovered to ten plain plus two named items, escrow zero and refunded ten, with player receipt sequence two.
- Evidence remains in ignored `.run/phase7-mvp-phase7-*.log`, per-case `repair.json`/`roof.json`, databases, player/world saves and server logs. Fixture timing is intentionally shortened; do not copy fixture tuning to deployment. Exit 137 is intentional crash instrumentation.
- After removing the probe, the repaired-world server restarted to READY; `node .run/phase7-smoke.cjs plain` read COMPLETED, zero pending cells/escrow, ten consumed materials, Era 1 and the History catalog. All deployed probe JARs and temporary probe source/classes/smoke script were removed; evidence logs and isolated saved data remain.
- All supervised Phase 7 verification servers and clients are stopped; final no-probe shutdown exited zero.

## Next action

Begin Phase 8 by enumerating and running its frozen full acceptance matrix on disposable copies, including real development-copy migration, performance and paired world/player-data/database restore. Do not add features or tag MVP before those gates pass.

## Risks or blockers

- Inventory, world saves and SQL remain independent stores. Preserve intent/receipt/marker/outcome reconciliation; never promise general exactly-once storage or arbitrarily restore only one component.
- Plain full-cube material profiles and block-entity exclusion are deliberate. Native-client interaction/visual acceptance remains pending.
- Historical phase checklist sections describe their then-current state; the Phase 7 section supersedes prior no-Repair/no-Era expectations.
- User development data was not upgraded; isolated protocol clients and operator-only probes do not constitute the complete Phase 8 release gate.
