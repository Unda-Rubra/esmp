# Current Handoff

## State

- Frozen roadmap **Phases 0–4 are implemented/verified**. Phase 5 explicit bounded Building registration and active baseline is next. No Building/Decay/Repair, automatic release, buffs, named districts or deferred-system placeholders were added.
- Local Activity stores only actually admitted meaningful credit at the exact Claim chunk, in the same transaction as aggregate/lifetime/cap updates. New and migrated buckets start at zero; aggregate scores are not invented spatial history. Aggregate certification/pricing/status semantics remain unchanged.
- Default local support is own decayed raw score + 0.25 × four cardinal same-Settlement claimed neighbor raw scores, capped at aggregate maxScore. No diagonals, recursion or cross-Settlement influence. Default supported threshold is 20. Half-life remains the existing aggregate default of seven game days.
- Config 11 adds `local-activity.neighbor-weight` (0..0.25) and positive finite `support-threshold`, with `config-before-local-activity.yml`. These are read-time tuning, not rewrites of raw scores. Stored scoring version is 1; unknown versions reject. Existing aggregate policy identity is unchanged.
- `ActivityRegion` is an ephemeral four-connected supported-component view. `/settlement local-activity [settlement] [page]` is administrator-only, 32 Claim rows per page; component numbers are snapshot-local, not identities.
- There are 62 pending player checks (48 historical +2 Phase 0 +3 each Phases 1–4). None were automatically user-approved. Prior native Management Book visual acceptance remains pending; this phase adds only command inspection.

## Completed

- Added `LocalActivityPolicy`, immutable `LocalActivityView`, pure bounded support/component projection and typed transaction-local `JooqLocalActivity` helper. Shared the existing exponential decay calculation without changing aggregate thresholds, recovery or cap semantics.
- Added read-only `ActivityService.localSupport(settlement, worldInstance, Map<ChunkPosition,Integer> footprint, gameTick)` and an existing-transaction overload. Weights represent 1..256 footprint block columns per chunk. Every footprint chunk must currently belong to that Settlement/world. Future projection is permitted; pre-anchor history is rejected. Queries neither load chunks nor update scores/checkpoints; footprint queries omit far-away Claims and do not derive unrelated Regions.
- Admitted observations update the local bucket only after existing authorization, bans, Parcel rules, category cooldowns, actor/Settlement caps and aggregate headroom determine the actual amount. No new listener or walking/login/automation credit path was added.
- Structured `phase4-local-activity` migration creates the local table with composite primary lookup key and Settlement/world foreign keys. Unclaim and abandonment delete affected local buckets in their owning transaction, preventing support revival on reclaim. Acquisition keeps buckets with the Settlement. No global scan/timer or Claim release was introduced.
- Cleanly migrated the observation factory and all runtime/test callers to include LocalActivityPolicy, without an old-signature shim. Added strict plugin config parsing, backup/version migration, admin command/help/completion and paginated output.

## Changed files

- `schema/db/changelog/phase4-local-activity.xml`, `db.changelog-master.xml` — new forward local table, no historical backfill or released-baseline edits.
- Civilization `activity/LocalActivityPolicy.java`, `LocalActivityView.java`, `LocalActivityProjection.java`, `ActivityProjection.java`, `ActivityService.java`, `ActivityRepository.java`, `api/ObservationServices.java` — policy/projection/view and stable footprint read contracts.
- Civilization `persistence/JooqLocalActivity.java`, `JooqActivityRepository.java`, `JooqSettlementRepository.java` — generated-type atomic credit/read and Claim lifecycle cleanup.
- Plugin `EsmpPlugin.java`, `settlement/SettlementRuntime.java`, `SettlementCommands.java`, `resources/config.yml` — config 11 and read-only admin inspection through the existing query boundary.
- `ActivityServiceTest.java`, `CertificationServiceTest.java`, `ParcelServiceTest.java`, `SettlementServiceTest.java`, plugin `SchemaMigrationTest.java` — local behavior and previous-phase migration tests, changed factory callers and truthful legacy downgrade fixtures.
- README/AGENTS and `.agent/DECISIONS.md`, `OPEN_QUESTIONS.md`, `TEST_CHECKLIST.md`, this file — current semantics, pending player acceptance, Phase 5 boundary and corrected historical evidence scope.

## Verification

- Final `JAVA_HOME=.tooling/jdk-25.0.4.1+1/Contents/Home ./gradlew clean check build --no-build-cache --no-daemon --console=plain` passed: **108 tests, zero failures/errors/skips**; 30 tasks executed/three up-to-date. Dependency verification/ArchUnit remained enabled. Google Java Format 1.28.0 formatted 18 changed/new Java sources before this build; no Java changed afterward.
- Five new local regressions cover exact negative chunk coordinates, cardinal/diagonal/cross-Settlement isolation, disabled/bounded kernel, weighted read-only and transactional footprint reads, invalid geometry/time, derived Region split/recovery, unclaim/reclaim/abandonment cleanup and unsupported-version rollback. Existing freeze/restart and unauthorized/stale/Public-credit tests now also assert local state. Aggregate/certification/acquisition/ledger/Parcel suites remain green.
- Native previous Phase 3 schema upgrade preserves aggregate score/anchor/credit and creates no local history. Existing empty/reopen, legacy bridge and earlier phase migration tests pass. Downgrade fixtures remove the new forward table before exercising their historical schemas.
- Actual final-JAR Paper ran only on `.run/mvp-phase4-upgrade` (disposable Phase 3 copy) and `.run/mvp-phase4-fresh` (empty world/application data). Both reached READY. On real upgrade, every prior non-clock/non-changelog row stayed identical and local buckets were empty.
- Live command scenario: non-admin local inspection rejected; login/teleport/purchase/system-setblock setup created no local credit. Three adjacent Claims remained. Real server-confirmed breaks credited exactly 1 in each endpoint; an immediate repeated break was cooldown-rejected for credit. Default neighbor support showed middle raw 0/support approximately 0.5.
- With `advance_time=false`, a forward 168000-tick world-time jump plus a real middle-Claim break left local rows exactly unchanged. Re-enabling time and advancing seven game days halved projected endpoint scores to approximately 0.5, with unchanged stored rows and no Claim release.
- Stop/start preserved **all 360 non-clock rows**. Only the disposable read-time threshold changed from 20 to 0.3, without modifying scoring data. The restored endpoints formed two supported components; a real credited middle action merged them into one while retaining all three Claims. Successful breaks were checked with server `execute if block ... air` broadcasts and durable credited state, not client prediction.
- Initial smoke-driver failures were fixture/runner issues: the copied player held a Management Book, which intentionally cancels mining, and RCON returned empty synchronous output for a broadcast delivered asynchronously. The corrected runner selected an empty hotbar slot and checked the server broadcast. Failed-run evidence remains in `.run/mvp-phase4-runner-debug`; the disposable copy was reset from Phase 3 before the complete successful run. No gameplay bypass or fabricated score was used.
- Evidence retained in ignored `.run/phase4-live.log` and isolated server logs/databases. Temporary smoke script removed; both servers stopped normally and finite clients exited. The user development server was neither started nor upgraded.
- Historical clarification: Phase 3's post-restart Owner block control observed client-predicted air after granting a Management Book, not server-authoritative block state. Decision/checklist wording now reflects that limitation; the Phase 4 restart scenario uses explicit server predicates.

## Next action

Begin frozen Phase 5 only: bounded Owner/Governor Building registration inside one Settlement with naturally loaded chunks, revisioned occupied/exterior/interior/opening/support baseline and legal-edit updates. Include only the frozen station-metadata hook, not Decay/Repair mutations or automatic discovery.

## Risks or blockers

- Local buckets begin at migration/admitted actions, not founding score or reconstructed history; old areas initially have zero local support. This is explicit policy.
- Neighbor weight/threshold tuning changes derived support/Regions on read; raw scoring/half-life changes still require explicit migration. Footprint consumers must revalidate current membership/world and handle pre-anchor rejection instead of inventing historical eligibility.
- Existing Phase 2 SQLite ALTER exceptions remain isolated; Phase 4 adds no raw SQL migration exception.
- Native book rendering remains pending user acceptance. The isolated fixture retains a 0.3 test threshold; bundled/operator defaults remain 20.
