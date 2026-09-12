# Current Handoff

## State

- Frozen roadmap **Phases 0–2 are implemented/verified**. Phase 3 administrator-approved ownership acquisition is next. No acquisition fee, Local Activity, Building, website, aesthetic ranking or automatic revocation was added here. The frozen architecture bundle remains unchanged.
- Certification is exactly UNVERIFIED/VERIFIED. Default application gate: age ≥72000 game ticks, current aggregate Activity not INACTIVE, lifetime actually credited meaningful Activity ≥20. Governance applies; administrator approves/denies. Denial preserves Claims/roles/balance/protection.
- Config is 9, with `certification.minimum-age-ticks` and `certification.minimum-lifetime-credit`. `config-before-certification.yml` preserves the prior config. Legacy schema markers remain their bridge versions; Liquibase `phase2-certification` owns the forward schema change.
- Old certify(boolean) API, `/settlement certify` routing and writable `settlement_details.certified` column are removed. Existing approved flags or official names migrate to VERIFIED; unnamed/unapproved records remain UNVERIFIED. Lifetime credit starts at zero, not synthesized from decayed scores/history estimates.
- Application/decision provenance, optional normalized proposed name, authority/name revision checks, decision replay and clean verified-name read/export are implemented through opaque UnitOfWork and generated repository types. Existing paid-Claim payer/subsidy rules are unchanged.
- Only `.run/mvp-phase2-upgrade` (copied actual development data) and `.run/mvp-phase2-fresh` ran; both were stopped. Original development DB bytes remained unchanged. There are 56 pending player checks (48 historical +2 Phase 0 +3 Phase 1 +3 Phase 2), none automatically user-approved.

## Completed

- Added CertificationStatus, Policy, Application, View, VerifiedSettlement and CertificationService repository façade, plus JooqCertificationRepository and factory wiring.
- Added `settlement_activity_lifetime`, credited in the same transaction and only by the amount surviving existing permission/cooldown/category/daily/score caps. Read does not decay this total or infer downtime/initial credit. No spatial Activity buckets were introduced.
- Added status/name revision to Settlement details and durable `settlement_certification_applications` with applicant/time/proposed name, captured ownership/name revisions, state and administrator/time/note. Approval rechecks applicant governance/bans, ownership/name revisions, eligibility and name uniqueness before atomically setting VERIFIED, naming and appending history. Exact decision replay leaves a later rename intact.
- Added `/settlement certification`, `apply`, `applications`, `review` and `verified`, with visible IDs/eligibility and book links. Admin rename now requires VERIFIED and advances naming revision. No fee is charged in this phase.
- Corrected the forward-migration prerequisite: `db.changelog-baseline.xml` includes only the unchanged Phase 0 changes; baseline verification/sync use it, while the full master applies Phase 2 afterward. New-schema land/permission snapshots load only after `finishMigration`, before gameplay admission. This is the scoped migration correctness fix identified in the prior handoff.
- Kept unrelated bootstrap/wand/overlay bodies intact; applied only certification wiring, new status projection and migration-before-snapshot ordering. Restored unneeded rewrite churn before final validation.

## Changed files

- `schema/db/changelog/db.changelog-master.xml`, new `db.changelog-baseline.xml` and `phase2-certification.xml` — separated fixed bridge and forward changes, explicit naming migration, lifetime/app tables. Released core/economy/civilization baseline files are unchanged.
- Core `DatabaseSchema.java` — baseline-specific reference/sync and full forward update/validation.
- `buildSrc/GenerateJooq.java` — typed UUID mappings for application/applicant/decision actor columns.
- Civilization `certification/*`, `persistence/JooqCertificationRepository.java`, SettlementServices factory; JooqActivityRepository, JooqSettlementRepository, JooqParcelRepository, SettlementService, LandSnapshot — new workflow/lifetime credit, retired toggle, enum snapshot and verified-only rename.
- Plugin EsmpPlugin/SettlementRuntime/SettlementCommands/ManagementTools/config.yml — policy/config backup, safe startup ordering, commands, read/export and book links.
- CertificationServiceTest (seven tests); SchemaMigrationTest and existing naming/legacy/overlay fixtures — frozen gate, denial, replay/rename, stale governance, uniqueness and native migration correctness.
- README/AGENTS and .agent notes — current commands/phase, explicit counter/migration semantics, acceptance evidence and Phase 3 next action.

## Verification

- Final `JAVA_HOME=.tooling/jdk-25.0.4.1+1/Contents/Home ./gradlew clean check build --no-build-cache --no-daemon --console=plain` passed: 30 tasks executed/three up-to-date, **94 tests, zero failures/errors/skips**. Dependency verification and ArchUnit remained enabled. No dependency pin changed.
- Google Java Format 1.28.0 ran on changed Java sources. Source audit found no old certify method/command/CERTIFIED generated field or TODO/FIXME stubs in active Civilization/plugin code.
- Native SQLite tests exposed pinned Liquibase addColumn/dropColumn rebuild defects: lost defaults/nullability/unique metadata, retained retired column, and failure on unnamed legacy foreign keys. The three affected ALTER operations are documented native SQLite exceptions; structured update/createTable/createIndex changes remain. Tests verify actual column removal, UNVERIFIED defaults/NOT NULL, old-name/flag mapping and baseline→forward execution without invented credit. No gameplay SQL was added.
- Real copied `.run/dev` data migrated through the actual legacy bridge into Phase 2 with the final JAR: Oakfall/Test records VERIFIED, unnamed S#3 UNVERIFIED; retired column absent and lifetime table empty. Original database SHA-256 stayed unchanged. Both legacy and fresh final artifacts reached ESMP READY.
- Real default-gate Paper scenario: fresh founder paid 25 and stayed UNVERIFIED; removed toggle did not change status; too-young application rejected. Live actions yielded lifetime 19 at age >3 days and application still rejected. An additional accepted action in the next game day reached 20 and permitted application. The initial runner incorrectly assumed 20 attempted actions meant 20 credited actions; no production bypass/fabricated credit was used to make the check pass.
- Non-admin review rejected. Admin denial retained one Claim, UNVERIFIED and wallet 75. A later application approved `Certified  Home`; verified export listed it. Admin renamed to `Renamed Home`; repeated original approval preserved the new name and exactly one CERTIFICATION_APPROVED history fact.
- Stop/start preserved all **282 non-clock rows**. Rejoin showed VERIFIED/Renamed Home/lifetime 20/wallet 75 and safe decision replay. The final narrow-bootstrap artifact also passed legacy startup and repeated this persisted readback.
- Evidence retained under ignored .run: phase2-certification-live.log, phase2-certification-live-complete.log, phase2-certification-restart.log, phase2-final-artifact-restart.log, phase2-applications.json and the two isolated servers. Native book layout remains user acceptance; old protocol-client particle limitations are not visual sign-off.

## Next action

Begin frozen Phase 3 only: Owner proposal → recipient acceptance → current immutable-land fee quote → administrator approval → atomic recipient Wallet SINK and ownership transition. Preserve treasury identity/history, remove the incoming Owner's membership, make former Owner Citizen, and explicitly handle old pending transfer offers. Acceptance alone must no longer transfer.

## Risks or blockers

- Native SQLite ALTER exceptions are required by the observed pinned Liquibase rebuild behavior and are documented/tested. Do not expand them into arbitrary application SQL or edit released baseline changeSets.
- Lifetime credit cannot be reconstructed honestly for pre-feature gameplay; existing VERIFIED legacy records are grandfathered, other old Settlements accrue actual credit from migration onward. This is explicit policy, not a hidden zero-score conversion.
- Configurable eligibility changes affect approval-time checks; admin denial can resolve stale applications. No automatic revocation or applicant-withdrawal feature was introduced.
- The old two-party ownership transfer remains until Phase 3. Do not advertise acquisition fee/admin approval as already implemented.
- The isolated fixtures contain test time jumps/actors and are not user development configuration. No original server was upgraded or started.
