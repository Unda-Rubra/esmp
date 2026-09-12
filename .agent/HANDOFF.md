# Current Handoff

## State

- Frozen roadmap **Phase 0 is implemented and verified**. Phase 1 is next and has not started. The supplied `ESMP MVP ARCHITECTURE 2026-09-12/` bundle remains canonical and unchanged; root ARCHITECTURE.md remains deleted.
- Liquibase Community 5.0.4, JUnit 6.1.2, ArchUnit 1.5.0 and SHA-256 Gradle dependency verification are integrated alongside Java 25, Gradle 9.7.1, jOOQ 3.21.8, SQLite JDBC 3.53.4.0 and Paper 26.2 build 121.
- Application service façades depend on repository ports. Cross-module callbacks carry opaque UnitOfWork.Transaction, not DSLContext. Persistence adapters alone use generated tables and guarded Core persistence SPI. jOOQ owns transactions on the existing bounded serial writer; SQLiteConfig/DataSource configures the driver.
- Canonical schema is `schema/db/changelog/db.changelog-master.xml` plus core/economy/civilization XML includes. Current gameplay schema tuple remains Core 1/Civilization 3/Economy 2/land-value 1/Claim-pricing 1, config 8. Liquibase adds only migration metadata. Old SQL resources and legacy adapters remain compatibility-only.
- Gameplay is deliberately unchanged, including Owner-wallet expansion and Governor-treasury expansion. Phase 1 must migrate every expansion to Treasury; no later certification/acquisition/Local Activity/Building/Decay/Repair code was added.
- The original development DB/world was not modified or started. A copied development fixture and a fresh fixture were used, then stopped. Historical 48 pending checks remain plus two Phase 0 player checks; agent verification is not user sign-off.

## Completed

- Added structured Liquibase baseline with explicit comments for SQLite-only CHECK/composite/partial-index constraints that standard change types cannot preserve. SchemaMigrationTest compares native shapes, rejects an altered legacy CHECK before baselining, verifies native negative-balance rejection and exact Long.MAX_VALUE preservation.
- Added startup legacy handoff: old adapters first reach known versions, then DatabaseSchema verifies columns/SQLite affinities/PK/FK/index/CHECK shape against a Liquibase-created reference before changeLogSync. Unknown/mismatched schemas fail closed; current Liquibase schemas validate on reopen. No blind baseline or destructive rebaseline.
- Build-time generation now applies Liquibase to a disposable real SQLite database, then official jOOQ generation. SQLite metadata parser accepts vendor-function syntax without dropping parsed constraints/keys; previous typeof parser diagnostics are absent in the final clean build. Long/UUID/enum storage semantics stay unchanged.
- Added Core UnitOfWork, guarded transaction scope and ClockRepository. Closed/cross-thread context access rejects; nested owning work rejects rather than deadlocking. Completion chaining outside an active transaction remains supported. Removed SqlWork and public DSLContext gameplay callbacks.
- Split Civilization services into façades/ports, pure rules/projections and persistence adapters; moved ClaimPricing under persistence. Split Economy likewise. Public API factories compose adapters without exposing persistence types. Core world-definition lookup avoids Civilization imports of Core generated tables.
- Added ArchUnit gates for Core/gameplay/Paper directions, cross-domain persistence/generated imports, public gameplay jOOQ leakage and JDBC statement isolation. Canonical schemas are packaged once, avoiding duplicate Liquibase classpath resources.
- Fixed Paper-only Liquibase SPI discovery: migration runs under the plugin classloader/Scope, restored afterward. Initial attempts failed closed; corrected fresh and copied-legacy startup and reopen succeeded.

## Changed files

- Root/module build files, buildSrc/GenerateJooq.java, new buildSrc/EsmpSQLiteDatabase.java, lockfiles and gradle/verification-metadata.xml — frozen pins, Liquibase schema generation, checked artifact inputs and vendor-function-aware metadata parsing.
- `schema/db/changelog/{db.changelog-master,core,economy,civilization}.xml` — canonical baseline; legacy SQL files retained only for compatibility.
- Core storage UnitOfWork/DatabaseAccess/DatabaseSchema/WorldStore; time GameClock/ClockRepository; persistence JooqClockRepository and legacy LegacyWorldMigration; removed SqlWork — scope, driver, migration and transaction ownership.
- Civilization public services/repository ports/api factories/GovernanceQueries, pure SettlementRules/ParcelRules/ActivityProjection, `.persistence` and `.persistence.legacy` implementations — no generated/jOOQ types in public gameplay APIs.
- Economy LedgerService/LedgerRepository/api factory and `.persistence`/legacy implementation — same monetary invariants through opaque transaction scopes.
- Plugin bootstrap/runtime construction and tests — API factory wiring, finishMigration before gameplay admission, migrated fixtures and architecture/schema tests. No command syntax/price/payer behavior changed.
- AGENTS.md, README.md and .agent memory — Phase 0 completion, operational migration policy, preserved historical evidence and Phase 1 next action. Frozen source bundle not edited.

## Verification

- Final clean no-cache command: `JAVA_HOME=.tooling/jdk-25.0.4.1+1/Contents/Home ./gradlew clean check build --no-build-cache --no-daemon --console=plain --init-script .tooling/maven-transport.init.gradle` passed, 32 tasks executed/1 up-to-date, **83 tests, zero failures/errors/skips**. Dependency verification remained enabled. The init script was a temporary local transport, not committed build configuration.
- Maven Central Java TLS HEAD/GET calls intermittently failed; curl HTTPS to the same artifacts worked. A temporary loopback proxy fetched Maven Central artifacts via verified HTTPS to bootstrap dependencies. Standard repositories and pinned bytes were unchanged. Plain clean/no-cache invocation reached the final test compile but failed retrieving slf4j-api-2.0.18 over TLS; do not misreport that invocation as a passing run. Temporary tooling is removed after use; caches/verification metadata remain.
- Google Java Format 1.28.0 ran on changed Java sources. Final schema generation had no Unknown function/ParserException output. Source audit found no TODO/FIXME/stubs for this phase.
- Native schema tests: empty→Liquibase, genuine old fixtures→legacy migrations, matching bridge→Liquibase baseline, malformed constraint rejection before baseline, exact 64-bit balance, and stale/cross-thread/nested UnitOfWork behavior. Existing paid/governance/Activity tests remain passing under JUnit 6.
- Real `.run/dev` database/world copied to `.run/mvp-phase0-upgrade`. Original DB SHA-256 unchanged. Of 26 existing non-clock tables, 25 remained byte-for-value identical; economy_metadata changed only schema version 1→2 through the retained legacy migration. Liquibase recorded the three baseline changeSets, loaded existing Settlements/Claims and reached ESMP READY.
- Fresh `.run/mvp-phase0-fresh` real Paper: first grants 100/100; founding 25; duplicate receipt no charge; Owner expansion 50 from wallet (intentionally preserved); Governor expansion 65 from treasury rejected without allowance, then succeeded after allowance. Parcel Holder consent, bans/denied treasury action, actual block-break Activity +1 and immutable land capture all passed.
- Fresh stop/start with final formatted JAR preserved all **352 non-clock rows**, including Liquibase metadata, quotes/ledger/progress/Holders/Activity. Live rejoin showed wallets 5/100, treasury 55, retained Holder/revision and allowance 0, without another first grant.
- Initial fresh/copy Paper attempts failed because Liquibase SPI used the server loader (`No executor jdbc`/unknown validate command), not due to schema mismatch. Explicit plugin-loader Scope fixed the cause; failures stopped protection fail-closed. Subsequent runs passed.
- Evidence retained only under ignored .run: phase0-live.log, phase0-restart.log and both server directories. The older protocol client's known particle decoding errors do not constitute native visual acceptance; relevant server interactions/messages/state were exercised.

## Next action

Begin frozen roadmap Phase 1 only: make ALL expansion Treasury-funded and Owner-assessed, migrate/invalidate obsolete pending quote payer context safely, preserve paid receipts/progress, and re-run the frozen atomicity/staleness/permission/restart matrix. Do not skip to certification or Building work.

## Risks or blockers

- Phase 0 is not full MVP acceptance. Existing Owner-wallet expansion is still a known Phase 1 difference, intentionally unchanged here.
- SQLite baseline SQL exceptions are justified/tested for missing structured constraint support; do not extend them to ordinary gameplay queries or new legacy migrations.
- Maven Java TLS failures are environmental; final validation used temporary local transport with unchanged HTTPS artifacts and checksum enforcement. Keep production repository configuration standard and never disable dependency verification to bypass corruption.
- Liquibase 5.0.4's published artifact is under FSL-1.1-ALv2; it is the explicitly frozen Community dependency. No license substitution or dependency-version deviation was made.
- Native client UI acceptance remains pending. Do not copy isolated fixture data/config into the real development server.
