# Current Handoff

## State

- Frozen roadmap **Phases 0 and 1 are implemented/verified**. Phase 2 certification is next; no certification/acquisition/Local Activity/Building implementation occurred here. The frozen source bundle remains unchanged.
- All Settlement expansion now pays from Treasury, assessed against current Owner, including Owner-initiated expansion. Founding remains founder Wallet and first-ever founding remains 25. Governor allowance enforcement is unchanged and shares the owning transaction.
- Personal purchase count is explicitly modeled by PlayerLandProgress as its monotonic revision: every supported mutation increments that persisted counter; failed/replayed purchases and transfer do not mutate it. No redundant column or schema change was added.
- Quote context now has a treasury-expansion format version. Existing unpurchased contexts reject and require a new quote; paid receipts bypass revalidation and remain replayable. Historical payments and progression records are not rewritten.
- Schema/config versions and released Liquibase baseline remain unchanged: Core 1, Civilization 3, Economy 2, land-value 1, Claim-pricing 1, config 8. No legacy migration was extended.
- Only isolated `.run/mvp-phase0-fresh` ran, including intentional SIGKILL recovery testing, then stopped cleanly. The original development server/world/database was not changed. Player checklist retains 48 historical checks plus two Phase 0 and three Phase 1 checks, all pending user sign-off.

## Completed

- Removed the Owner-wallet branch from expansion payer selection. Treasury insufficiency cannot fall back to personal funds; a deposit can fund a still-valid quote without changing its payer/assessment.
- Added PlayerLandProgress domain invariant/revision semantics and included that revision in validated quote context. Existing exact integer/fixed-point pricing, portfolio/size/Activity factors, first founding override, missing-profile rejection, topology and history remain.
- Versioned pending quote consent without destructively deleting paid records or altering released changelogs. Real prior-JAR quote compatibility was exercised, not only fabricated test data.
- Updated command help and Chinese onboarding to explain treasury funding and deposits. No command alias or alternate free/direct Claim mutation path was added.
- Added four real-ledger regressions: Owner wallet cannot cover empty treasury; Governor quote stale after Owner portfolio changes; claim then unclaim cannot revive same-area consent; prior-format unpaid quote rejects but paid receipt replays after restart. Existing expiry/ownership/insufficient-funds/rollback/no-reset/rounding tests remain passing.

## Changed files

- `esmp-civilization/.../persistence/ClaimPricing.java` — treasury-only expansion, progression value/revision and versioned quote context.
- `esmp-civilization/.../settlement/PlayerLandProgress.java` — immutable personal progression contract; revision equals its persisted monotonically consumed purchase count.
- `esmp-economy/src/test/java/xdu/undarubra/esmp/economy/PaidClaimTest.java` — treasury-funded existing fixtures and four new boundary regressions.
- `esmp-plugin/.../settlement/SettlementCommands.java` — funding/payer help text.
- AGENTS.md, README.md and .agent decision/open-question/acceptance/navigation/handoff notes — completed Phase 1, preserved historical evidence and Phase 2 next action.

## Verification

- `JAVA_HOME=.tooling/jdk-25.0.4.1+1/Contents/Home ./gradlew clean check build --no-build-cache --no-daemon --console=plain` passed using ordinary committed repositories (no temporary TLS transport): 30 tasks executed, three up-to-date. XML totals **87 tests, zero failures/errors/skips**. Dependency verification and ArchUnit remained enabled.
- Google Java Format 1.28.0 ran on the four changed Java files before the clean build. No schema/codegen configuration or frozen dependency pin changed.
- Previous-version fixture: launched its actual Phase 0 JAR, generated an Owner expansion quote for 80 with WALLET payer, froze GameClock and stopped. Installed Phase 1 JAR; that genuine old quote rejected with `Claim price context changed`.
- Real Paper treasury scenario: Owner wallet 1005, treasury 55, price 80. Purchase rejected with insufficient funds and preserved both balances. Depositing 25 made treasury 80 and wallet 980; confirmation charged treasury to zero, not the wallet. Paid replay left balances unchanged. New command help explicitly identified treasury funding.
- Captured committed state, sent SIGKILL to the verified isolated server process, then restarted. All **369 non-clock rows** survived unchanged, including receipts/progress/Claims/Liquibase metadata. Live rejoin showed wallet 980, treasury zero, four claimed chunks and idempotent paid replay.
- Protection after recovery: a Public player's raw block-break action on the newly claimed chunk left the grass block intact; the same action by the Owner produced air. Console conditional world checks and chat markers confirmed both outcomes. The block was restored afterward. The first runner assertion incorrectly looked for an asynchronous `say` marker in the RCON return; it was corrected to observe the actual chat marker, with no production-code change.
- Evidence retained in ignored `.run/phase1-previous-quote.log`, `phase1-live.log`, `phase1-crash-recovery.log`, `phase1-crash-recovery-complete.log`, `phase1-protection-positive-control.log` and `phase1-quotes.json`. Temporary smoke runner/formatter are removed after verification. Native visual acceptance remains with the user; older protocol particle decoding limitations are not UI sign-off.

## Next action

Begin frozen Phase 2: durable lifetime meaningful Activity credit, fixed eligibility gate (age ≥3 game days, non-INACTIVE, lifetime credit ≥20), governance application, administrator approval/denial and explicit old-name/flag migration. Do not add Phase 3 acquisition, Local Activity or Building work at the same time.

## Risks or blockers

- Before the first new post-Phase-0 schema migration, fix the legacy bridge to verify/synchronize only the known Phase 0 baseline and then apply later changeSets normally. It currently references the full master because neither Phase 0 nor Phase 1 required newer schema. Do not rewrite released baseline files or mark future Phase 2 changes already applied. Cover this necessary migration-correctness adjustment with bridge→new-latest tests.
- Progress revision equivalence is valid while every supported progression mutation consumes a purchase. Any future administrative progression edit would need an explicit revision/migration policy; no such operation exists now.
- Phase 1 acceptance is not full MVP or user acceptance. Old pre-freeze wallet-funded expansion checks remain historical only; current user instructions explain treasury deposits.
- The isolated fixture is intentionally time-frozen and contains test grants; it is not the user development server/configuration.
