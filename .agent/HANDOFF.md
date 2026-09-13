# Current Handoff

## State

- Frozen roadmap **Phases 0–3 are implemented/verified**. Phase 4 Local Activity is next. No Local Activity, Building, appraisal, private-contract engine or automatic release was added. The frozen architecture bundle and released baseline changeSets remain unchanged.
- Acquisition: current Owner proposes a Citizen/Governor; recipient explicitly accepts the disclosed integer fee; administrator approves before any money or ownership changes. Default fee is ceil(10% × immutable base values of current Claims). Missing profiles and changed participants/revisions/Claims/profiles/policy reject rather than reprice silently.
- Config is 10. `acquisition.offer-ticks` defaults to 36000 shared game ticks; `fee-basis-points` defaults to 1000. Upgrade backs up `config-before-acquisition.yml` and migrates the old transfer duration. Existing Core/Civilization/Economy bridge version markers remain unchanged.
- The forward `phase3-acquisition` migration creates durable acquisition records and cancels only pending legacy TRANSFER offers. Accepted transfer history and unrelated invitations remain. The immediate-transfer enum, application branch and timing field are removed.
- There are 59 pending player checks (48 historical +2 Phase 0 +3 Phase 1 +3 Phase 2 +3 Phase 3). Agent execution does not mark them user-approved. Native Management Book page layout remains unverified visually.

## Completed

- Added Acquisition/Policy/Service and typed JooqAcquisitionRepository. Recipient acceptance binds an exact fee without charging; administrator approval revalidates and atomically commits recipient Wallet SINK, ownership/membership changes, decision, History/outbox and GameClock checkpoint. Proposal UUID is the stable ledger receipt UUID.
- Former Owner becomes Citizen; incoming Owner membership is removed. Treasury identity/balance/history, certification/name and personal subsidy usage remain. Existing ownership revision invalidates old Governor allowances. Only pending governance/Parcel proposals are cancelled during transfer; accepted invitation/promotion history is preserved.
- Approval and consent replay are mutation-free, including after restart. Insufficient funds or a post-debit transaction failure leave ownership/money unchanged. Denial and cancellation are free; cancellation can withdraw recipient consent before approval. Expired or stale proposals cannot approve.
- Added `/settlement transfer`, `acquisition`, `acquisitions`, `accept-transfer`, `review-transfer`, `cancel-transfer` routing/help and book links. Every mutation uses the existing runtime reservation/commit/publication coordinator.
- Migrated existing governance/ledger/paid-Claim tests to the new workflow; added seven real-ledger acquisition regressions and previous-phase migration coverage. Legacy downgrade fixtures remove all forward-only tables before exercising the verified bridge.

## Changed files

- `schema/db/changelog/phase3-acquisition.xml`, `db.changelog-master.xml` — structured forward table/index/cancellation migration; released baselines untouched.
- `buildSrc/src/main/java/GenerateJooq.java` — UUID mappings for acquisition and captured Owner identities.
- Civilization `acquisition/*`, `persistence/JooqAcquisitionRepository.java`, `api/SettlementServices.java` — domain contracts, deterministic quote/consent/decision and atomic repository flow.
- Civilization `persistence/JooqSettlementRepository.java`, `settlement/SettlementOffer.java`, `settlement/SettlementPolicy.java` — remove immediate-transfer API and branch.
- Plugin `EsmpPlugin.java`, `settlement/SettlementRuntime.java`, `SettlementCommands.java`, `ManagementTools.java`, `resources/config.yml` — ledger callback, configuration migration, coordinated commands/help/book links.
- `SettlementServiceTest.java`, `ParcelServiceTest.java`, `LedgerServiceTest.java`, `PaidClaimTest.java`, new `AcquisitionTest.java`, `SchemaMigrationTest.java` — changed governance contract, atomic payment/replay/staleness, previous/legacy schema migration preservation.
- `README.md`, `AGENTS.md`, `.agent/DECISIONS.md`, `OPEN_QUESTIONS.md`, `TEST_CHECKLIST.md`, this handoff — implemented Phase 3, exact operator commands/config and Phase 4 next boundary.

## Verification

- Final `JAVA_HOME=.tooling/jdk-25.0.4.1+1/Contents/Home ./gradlew clean check build --no-build-cache --no-daemon --console=plain` passed: **102 tests, zero failures/errors/skips**, 31 tasks executed/two up-to-date. Dependency verification and ArchUnit remain enabled. No dependency pins changed. Google Java Format 1.28.0 formatted the 19 changed/new Java sources before this build.
- Native tests cover empty DB/reopen, verified legacy bridge, deliberate constraint mismatch rejection and exact previous Phase 2 schema upgrade. Only pending TRANSFER offers cancel; accepted historical TRANSFER and pending INVITE survive. Existing long balances and certification migration checks still pass.
- Acquisition tests cover exact consent/admin requirement, no acceptance debit, insufficient funds, injected failure after real ledger debit/rollback, restart/receipt replay, treasury/certification preservation, Claim/profile/policy changes, missing valuation, ban/expiry/denial and wide-integer fee rounding. Existing paid-Claim and allowance tests verify ownership-change effects without resetting subsidies.
- Actual final-JAR Paper ran only in `.run/mvp-phase3-upgrade` (copy of the prior Phase 2 fixture) and `.run/mvp-phase3-fresh` (empty application/world data). Both reached ESMP READY; fresh status reported protection enabled. The original development server was not started or upgraded.
- On upgraded Paper, fee 10 was disclosed. Pre-consent approval, fee 9 and non-admin review rejected. Acceptance retained the old Owner; balance 5 caused approval rejection with no partial mutation. Funding recipient to 10 allowed exactly one SINK, leaving recipient Wallet 0 and Treasury 17. Former Owner became Citizen and lost Owner-only command authority; VERIFIED/name persisted.
- Stop/start retained all **325 non-clock rows** exactly. Rejoined recipient remained Owner with Wallet 0/Treasury 17; repeated administrator approval and recipient acceptance did not debit or transfer again. Exactly one OWNERSHIP_TRANSFERRED event and one 10-unit acquisition SINK persisted.
- Actual protocol clients attempted the same protected stone break: outsider retained stone, new Owner changed it to air, both before and after restart. Management-book grant worked; no claim of native page-layout/visual acceptance.
- Evidence: ignored `.run/phase3-live.log` plus both isolated servers' logs/databases. Temporary driver removed after use. All three supervised verification processes exited normally; no test server/client is left running.
- Intermediate failures were fixed before the final pass: initially wrong system JAVA_HOME, one mutable-lambda test variable, forward acquisition table left in legacy downgrade fixtures, and a fixture using a nonexistent expires_tick column instead of stored expires_at ticks. No production validation was weakened.

## Next action

Begin frozen Phase 4 only: implement lazy Claim-chunk Local Activity buckets and bounded neighbor support alongside aggregate Activity; derive ActivityRegion without stored/player-managed identities. Do not add Buildings or automatic land release.

## Risks or blockers

- Native book rendering remains pending user acceptance; protocol command/block-state evidence is not visual sign-off.
- Pinned Liquibase's existing Phase 2 SQLite ALTER exceptions remain necessary and isolated. Phase 3 uses structured changes and does not expand those exceptions.
- Historical accepted legacy TRANSFER rows deliberately remain as audit data, not active enum/API aliases. Old pending consent cannot be reused for paid acquisition.
- Missing historical LandValueProfiles continue to block automatic acquisition; this phase adds no backfill or appraisal policy. Recipient must remain a current Citizen/Governor, preserving the preexisting eligibility restriction.
