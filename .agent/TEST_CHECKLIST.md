# Temporary Player Acceptance Checklist

Purpose: accumulate completed implementation milestones and what the user should test at the end of this development batch. This is not an implementation backlog or a second architecture.

Maintenance: append each completed gameplay change with its commit, player-visible checks and known limits. Keep automated/agent verification separate from user acceptance. Check boxes only after the user reports the corresponding result; carry unresolved checks through handoffs. Retain this temporary document until the user confirms the batch is tested and requests removal or archival.

## 2026-09-12 frozen-MVP supersession

This checklist preserves **pre-freeze evidence and 48 pending historical player checks**, followed by frozen-phase evidence. The [requirements freeze](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/MVP_REQUIREMENTS_FREEZE.md) and [roadmap](../ESMP%20MVP%20ARCHITECTURE%202026-09-12/IMPLEMENTATION_ROADMAP.md) control acceptance. Phases 0–7 are implemented/verified; Phase 8 is next. Old direct naming-toggle and immediate-transfer checks are historical, superseded by the Phase 2/3 sections. Agent results are not native-client/user acceptance.

In particular, old Owner-wallet expansion expectations are not the frozen target (ALL expansion uses Treasury in Phase 1), and prior public-DSLContext/SQL migration architecture is not Phase 0 complete. Preserve these historical results without checking off or rewriting new unimplemented behavior as delivered. Append each frozen phase's executed evidence only when that phase actually ships.

## Version coverage

Sections 1–5 preserve the `f8a9f50` clock/Activity baseline checks and their pending user outcomes. Its Manager/Member/Visitor, player-naming and config-version-3 expectations are historical. **The current Settlement/Parcel implementation is `d14bb63`; use section 6 for the new roles, numeric-only prices, tools, consent, naming and config/schema migration.** No automated result below is a user sign-off.

Tool command routing is updated by `7aaccd6`: current examples use `/esmp tools parcel_wand` and `/esmp tools management_book`. The underlying `d14bb63` tools/Parcel behavior and stored item identities remain unchanged; old issuance commands are not retained as aliases.

Current text and Parcel visuals are updated by `f675a15` and `e823189`. Use the blue preview/target, green existing-border and red invalid-selection expectations below; they supersede the earlier corner-only palette.

Inactivity-driven automatic release remains outside MVP. Existing pending checks retain their version-specific scope. Current Claims are paid quote-confirmation operations; section 14 supersedes old numeric-only/no-payment expectations.

Ledger, first-join grants, natural values, paid Claims/subsidies, certification applications and administrator-approved acquisition are implemented. No check is automatically marked accepted.

## Completed implementation awaiting user acceptance

| Milestone | Commit | Agent verification | User acceptance |
|---|---|---|---|
| Shared persisted game clock | `f8a9f50` | Build/regressions; live forward jumps, rewind, freeze, changed tick rate, exact frozen restart and bed sleep | Pending |
| Existing gameplay timers migrated to ticks | `f8a9f50` | Build; existing offer tests and legacy deadline conversion regression; plugin boot | Pending |
| Settlement Activity scoring and status | `f8a9f50` | Build/regressions; live real block credit, inactivity/grace, persisted status | Pending |
| Numeric claim pricing and stable numbering | `d14bb63` | Build/domain tests; live 100/125 prices with explicit not-charged output | Pending |
| Governor/Citizen/Public and local bans | `d14bb63` | Role migration tests; live promotion, ban-driven inventory revocation and restart | Pending |
| Parcel geometry, policies and Holder consent | `d14bb63` | Six new domain regressions; live Public Holder access, mutual deletion and two-sided hopper checks | Pending |
| Management book/stick and naming administration | `d14bb63` | Real book-open packet, named-stick selection/preview/commit, admin naming and restart; native visual appearance still pending | Pending |
| Semantic `/esmp tools` routing | `7aaccd6` | Live non-OP issuance/completion, status permission gate, invalid/legacy route rejection, existing-item compatibility and updated book link | Pending |
| Short action text and refreshed tool descriptions | `f675a15` | Build and live refreshed-lore/confirmation checks | Pending |
| Live Parcel targets, surfaces and wire borders | `e823189` | Six geometry/ray regressions; live particle colors/positions, block/air clicks, invalid previews and boundary cases | Pending |
| Integer ledger, monetary budgets and treasury commands | Working tree, not committed | Build; 10 ledger regressions; isolated Paper commands and persisted replay | Pending |
| Spaced names, quoted commands and readable time | Working tree, not committed | 46-test build; live quoted names, natural Name Tag/wand, pause/advance display and restart | Pending |
| Automatic first-join starting funds | Working tree, not committed | 49-test build; live first join, reconnect, exhausted budget, frozen time, restart and offline deferred payment | Pending |
| Immutable natural land values and inspection | Working tree, not committed | 53-test build; live generation/edit stability, non-Main exclusion, chunk-journal recovery and future-only policy version | Pending |
| Generated-type jOOQ persistence cutover | Working tree, not committed | Clean generation/build: 67 tests; exact old/fresh data preservation and live Paper query/mutation paths | Pending |
| Paid Claims, durable quotes and personal subsidies | Working tree, not committed | 74-test build; real Paper debit, insufficient funds/retry, receipt replay, unclaim progression and policy-stale restart | Pending |

Earlier Settlement claiming/protection/team functionality was already user-confirmed. Its existing [README checklist](../README.md#两名玩家的验证清单) remains available for optional regressions; do not reopen it as untested work.

## Test preparation

Use a disposable copy of both world and database, not the permanent world. Time jumps can expire offers and age every Settlement; future consumers may also react. Use the native Minecraft Java 26.2 client and the newly built JAR after a full stop/start, never hot reload. Administrative time commands require OP/console access. Record the tested commit and any changed configuration when reporting results.

The existing development server was not upgraded automatically. Follow [README startup instructions](../README.md#现在进入游戏验证). Preserve a pre-upgrade backup for migration checks; never manually downgrade database schema numbers.

## 1. Shared game clock

- [ ] Inspect `/esmp status`. With `advance_time` enabled, use `/time add 24000`: reckoning advances by one game day plus any normally elapsed ticks. Sleep through a night: the skipped time also counts. Use `/time set 0` to move the source backwards: reckoning must not decrease or reverse completed progress.
- [ ] Set `/gamerule minecraft:advance_time false`, record the reported game tick, wait and use `/time add 24000`: the tick and time-driven Activity state remain unchanged. Restart while still frozen: the saved tick remains unchanged. Restore the rule to `true` afterward.
- [ ] Change `/tick rate 40`, then restore `/tick rate 20`. World-time progression is faster in real life; Activity still follows elapsed game days rather than elapsed real seconds. Restore any experimental tick freeze/rate settings before other checks.

## 2. Migrated gameplay timers

- [ ] Issue an invitation or abandonment warning. Its output identifies a game-tick deadline. While game time is frozen it does not expire; advance past the deadline and acceptance/confirmation is rejected without granting membership or removing protection.
- [ ] On a disposable pre-upgrade copy with a pending offer, upgrade and inspect `/settlement offers`: the offer identity and remaining lifetime are preserved by the one-time conversion, not reset to a full lifetime. Already-expired offers stay unusable; existing claims, roles and history remain. Mark not applicable if no pre-upgrade fixture is available.
- [ ] Run `/settlement view`: borders last 200 advancing game ticks and stop expiring while time is frozen. Confirm native-client particle rendering and protection feedback. The older automated protocol client reported particle decoding errors, so native visuals have not been signed off.

## 3. Activity contributions and limits

- [ ] Create a fresh Settlement and inspect `/settlement activity` or `/settlement info`: initial score is approximately 30, decreasing only with elapsed game time. Successfully place/break a block and wait for collection: BUILD receives up to 1 point. Permitted inventory opening/public-control use gives smaller INTERACTION credit.
- [ ] Repeatedly interact/build: the 200-tick per-player/category cooldown, player daily cap 5 and Settlement daily cap 20 bound credit. A Visitor cannot earn BUILD credit through denied changes; deliberate permitted public-control use earns only 0.02 with a much smaller daily cap. Mere entry, movement, pressure-plate passage and idling earn nothing.
- [ ] Freeze game time, perform otherwise eligible actions and query Activity: no new credit. Restore time advancement. Actions in one Settlement must not sustain an adjacent Settlement, even with the same Owner.

## 4. Inactivity, recovery and persistence

- [ ] On the disposable test world, advance ten game days (`/time add 240000`) without meaningful activity. Default Activity should become INACTIVE and the three-game-day production-grace interval should have elapsed. No actual production, decay, repair or automatic release should occur: these systems are not enabled.
- [ ] Resume eligible participation over enough game days to respect contribution caps. Reaching 30 points restores ACTIVE eligibility; merely logging in does not. Scores between 20 and 30 retain the appropriate prior state rather than switching repeatedly.
- [ ] Restart and inspect status: recent credit, inactivity origin and contribution limits persist without downtime accrual. Transferring ownership does not reset Activity. A newly founded/reclaimed Settlement is a new identity; first inspecting an old Settlement must not make it freshly active.

## 5. Configuration and limitations

- [ ] Confirm config version 3 and `*-ticks` offer settings. An upgraded configuration has `config-before-game-clock.yml`; legacy historical timestamps remain available while new history reports game ticks.
- [ ] Understand the current policy gate: Activity defaults may be configured before first initialization. Changing an already-persisted Activity policy requires explicit migration; startup currently rejects it rather than silently reinterpreting old scores. Do not test this on the main development data without a backup.

Abrupt failure can lose uncheckpointed clock-only progress and uncommitted/coalesced observations. Normal shutdown flushes them. Paired world/database restoration remains a separate future operational gate, not a guarantee from these checks.

## Later completed milestones

Append completed work here as implementation proceeds. Do not add planned Building/Facility/decay features until they actually exist.

## 6. Settlement/Parcel acceptance — `d14bb63`

Use disposable copied data and a native 26.2 client. Keep ordinary governance test accounts non-OP: server administrators can override Parcel consent, which would invalidate a test of normal Holder rights. Replace example `1` with the returned Settlement number.

### Numeric prices and naming

- [ ] Use a fresh player on naturally valued land: `/settlement create` quotes 25 without charging; confirm with `purchase <UUID>`. Quote and confirm adjacent expansion; default first expansion is 50 on base-100 active land. History and balance must reflect the real payment. This supersedes the historical 100/125 numeric-only check.
- [ ] New Settlements start as `S#number`. An ordinary Owner cannot rename one. An administrator uses `/settlement certify 1 true` and `/settlement rename 1 Harbor`; number/UUID stay stable and history retains the naming event. Existing pre-migration names remain usable.

### Roles and bans

- [ ] Invite/accept produces Citizen; Owner promotion plus recipient acceptance produces Governor. Governor cannot promote other Governors or perform ownership-only operations. Public users remain nonmembers even when holding a Parcel.
- [ ] A Public Holder opens an allowed Parcel container. Owner/Governor bans them: the inventory closes, subsequent governed interactions are denied, but walking and informational commands still work. Unban restores ordinary policy; identified administrators cannot be banned.

### Tools and geometry

- [ ] `/esmp tools management_book` opens useful current command links. `/esmp tools parcel_wand` supplies the marked stick; a named off-hand Name Tag plus left/right clicks selects a named Parcel. Passing either item to another player does not grant governance. Duplicate issuance and crafting/anvil/grindstone conversion are blocked as documented.
- [ ] Preview and commit an 8×8×8-aligned Parcel, including negative coordinates. Face-connected extensions succeed; diagonal-only/disconnected/outside-Settlement selections fail atomically. Existing other-Parcels' cells are skipped only when the resulting shape stays valid. Surface removal must not split the remainder.
- [ ] Compare the dense blue target surface, blue preview borders, green existing-Parcel borders and red invalid/excluded selections in the native client. `parcel commit` requires a valid current preview; expiry, state change, logout and restart require a fresh selection. Position commands work without owning tools.

### Holder consent and parent protection

- [ ] Assign two Holders using a proposal; both must approve along with governance. Then propose a boundary, policy, Holder-list or naming change: no unilateral edit succeeds. Approvals survive restart; stale/expired proposals cannot apply, stale still-pending proposals may be rejected, and resolved proposals cannot replay.
- [ ] Parent unclaim/abandonment cannot remove a Parcel indirectly. Remove affected Parcels through their required consent first. Unassigned Parcel governance and explicit administrator dispute handling remain usable and auditable.

### Policy separation, automation and migration

- [ ] Give a Public player HOLDER access in one Parcel: permitted building/containers work there, not in the unassigned surrounding Settlement. A Citizen+Holder can use either allowed relation. Denying CONTAINER while allowing BUILD must block containers even for Owner unless explicit administrator bypass is enabled.
- [ ] A hopper crossing two different Parcels is blocked by default. Grant PUBLIC AUTOMATION only on one side: still blocked. Grant it on both protected sides: items move. Confirm the corresponding desired piston/fluid boundaries on the native server too; do not assume every adapter from the hopper result alone.
- [ ] Upgrade a disposable old database/config and restart: roles become Governor/Citizen, UUIDs/offers/history/Activity stay intact, stable numbers and grandfathered names appear, and config 4/Civilization schema 3 are present. Parcel cells, policies, Holders and proposals persist across another restart; session previews do not.

Visual verification now includes decoded live particle positions/colors. The fixture uses a process-local packed-RGB dust decoder correction; desktop appearance and comfort remain player acceptance checks.

## 7. Semantic tool commands — `7aaccd6`

- [ ] As a non-OP player, tab-complete and request `/esmp tools parcel_wand` and `/esmp tools management_book`. `/esmp status` must remain denied without administrator permission. Singular `tool`, old material-name arguments and the former Settlement issuance route must not issue items. Existing tools must still work and prevent duplicate issuance; the book's Parcel Wand link must use the new command.

## 8. Clear text and live Parcel visuals — `f675a15` / `e823189`

- [ ] Existing and newly issued tools show short purpose/instruction text. Check claim/Parcel confirmations and Activity information for concise, readable wording. Old tool descriptions refresh when joining, changing the held item or using it.
- [ ] Aim at a reachable block: the blue surface encloses its voxel. Aim into air or beyond block reach: the target is the first voxel in your facing direction outside your occupied camera/body cells. Check up/down, negative coordinates and standing exactly on voxel boundaries. Left/right air clicks and no-coordinate position commands select the highlighted cell.
- [ ] Set a first corner and turn: the blue preview follows promptly. Valid selections stay blue; invalid/outside/disconnected selections remain visibly red and reject confirmation. Existing Parcel outlines stay green. Right-click completion plus its swing animation must preserve the first corner. Check responsiveness with frozen calendar time, confirmation cleanup, and readability of larger selections.

## 9. Integer ledger and treasury — current working tree

Agent proof: Gradle build and 10 ledger regressions passed. Isolated Paper 26.2 accepted budgeted grants, wallet transfers/replay, overdraw rejection, treasury deposit/withdrawal, Public debit denial and non-admin issuance denial. Restart preserved balances (75/25), zero treasury and the original four receipts. No user dev world was upgraded. The older protocol client emitted particle decoding errors during the first run; native book layout, clipboard interaction and sound remain unverified.

- [ ] With two players, record both balances, grant Alice 100 as administrator, then transfer Bob 25. Confirm net changes of +75/+25 and recent history. Retry the same transfer UUID: no second debit; an overdraw and non-admin grant must reject. Account for any first-join funds before taking the initial balances.
- [ ] Deposit into a Settlement treasury. Public/Citizen cannot debit; Owner may withdraw. Set a Governor allowance, spend within it, exceed it, then demote/re-promote: unused old authority must not revive. A funded treasury must prevent abandonment.
- [ ] Stop/start and inspect balances, history, allowances and issuance budget. With disposable data, freeze/skip periods and confirm no unused-budget carry-over; display-name changes preserve balances.
- [ ] Open the Management Book and use wallet/treasury links. Check native layout, receipt hover/copy and payment feedback. Claim quotes require purchase confirmation; starting funds are covered in section 11.

## 10. Spaced names and visible time — current working tree

Agent verification: quoted Settlement/Parcel commands preserved repeated interior spaces after edge trimming; actual named off-hand tag plus wand interaction created `Tag  Store` without quotes. Quoted treasury lookup and malformed-quote rejection passed. Status reported paused tick 11440 across a frozen forward edit, MAIN 12:00 at source tick 6000, and advancing tick 35453 after resumed +24000. Native visual layout remains user acceptance.

- [ ] As administrator, rename a certified Settlement with `/settlement rename 1 "  New  Haven  "`. Its stored/displayed title is `New  Haven` with two internal spaces and no outer spaces. Quoted references and management-book links continue to work.
- [ ] Name an off-hand Name Tag `  Tool Store  ` without quote characters. Use the Parcel Wand and confirm: the resulting Parcel is `Tool Store`. Verify quoted Parcel info/rename commands and existing consent/duplicate-name rules.
- [ ] Use `/esmp status` as administrator: reckoning day/progress/total tick and advancing/paused state appear near the top, followed by the separate MAIN clock. With a disposable world, freeze time and confirm reckoning remains fixed while direct sky-time changes can affect HH:mm.

## 11. First-join starting funds — current working tree

Agent verification: 49 tests passed, including 13 ledger tests. Actual Paper first join credited NewFundsA 100; reconnect/restart retained 100 without a second welcome grant. Existing LedgerAlice retained 75 before explicit administrative funding. At 2000/2000 issuance, NewFundsB stayed pending with zero balance; a frozen forward sky-time edit did not refresh the budget. After restart and one advancing monetary period, NewFundsB received 100 while offline and returned with one STARTING_FUNDS receipt. NewFundsC's welcome showed both +100 and final balance 100.

- [ ] Join with a genuinely new player UUID: receive the configured 100 once, with currency, final balance and `[Balance]` link. Reconnect and restart: no second grant. Existing test players must not receive retroactive grants.
- [ ] On a disposable test world, exhaust the monetary budget and join with another new UUID. The grant stays pending at zero balance; frozen time does not refresh it. Restart, advance a monetary period with time enabled, and confirm the pending player receives exactly one grant even while offline.
- [ ] With a pending grant, change `economy.starting-funds` and restart: its original amount remains, while new players use the new amount. Current config is 8; Economy schema remains 2 and its prior configuration backup remains.

## 12. Immutable natural land values — current working tree

Agent verification: all 53 tests passed. On actual Paper, old spawn chunk (-1,-1) remained unknown; new chunk (625,625) captured 100/FLAT/v1 and retained it after a surface-height edit. Nether inspection rejected. After clean stop, one test SQL profile was deliberately removed while its saved chunk journal remained; restart restored exactly the original SQL tuple, not the edited surface. A new policy v2/base 200 applied to new chunk (1250,1250), while the original stayed 100/v1 and old spawn stayed unknown. This was isolated test data, not the user's development world.

- [ ] Use `/settlement landvalue` and the management-book link on old spawn terrain and genuinely new Main chunks. Old uncaptured terrain shows unknown; new terrain shows base/class/version. Other dimensions reject inspection.
- [ ] Build a tall column or excavate inside a captured chunk and restart: its original value/version remains unchanged. Inspection itself never charges; Claim purchase charges separately after confirmation.
- [ ] On disposable data, change coefficients under the same valuation version: startup must reject. A new version affects only new chunks. Current config is 8; retain `config-before-land-values.yml`.

## 13. jOOQ persistence migration — current working tree

Agent proof: clean build regenerated all schema bindings from canonical migrations and passed 67 tests. Existing-data Paper startup preserved 30 non-clock tables/319 rows exactly; existing balances, spaced names, Parcels and both land-value versions loaded. Fresh Paper exercised first grants, replay/overdraw, treasury allowances, roles, Holder consent, bans, real Activity credit and new land capture. Its stop/start preserved all 328 non-clock rows and balances 107/93; no repeat starting grant occurred. One temporary smoke assertion initially expected the wrong approval wording; continuation verified the persisted Holder and completed behavior.

- [ ] On a disposable copy of existing world/database data, install the new JAR with a full stop/start. Check the same Settlement IDs/names/claims, Parcel Holders/policies, balances/history and land-value versions; perform a small transfer and verify it after restart. Schema/config versions and gameplay rules must remain unchanged.

## 14. Paid Claims and personal subsidies — current working tree

Agent evidence: 74 tests passed, including seven real-ledger paid-Claim tests (rollback after debit, insufficient funds, Governor allowance/owner assessment, expiry, missing values, ownership/Activity staleness, no-reset progression, exact rounding). Actual Paper confirmed 25 founding, 50/65 expansions, insufficient-funds rejection then successful same-quote retry after funding, and balance 10 afterward. Unclaim did not refund or reset progress; the next quote was 80. After restart, paid replay did not debit and a changed pricing policy invalidated the pending quote. Missing-profile rejection was proven by domain test; the initially chosen live spawn locations already had valid profiles and were not used as missing-profile evidence.

- [ ] On a new UUID with 100 starting units and captured land, verify quoting leaves 100, first founding confirmation leaves 75 and first base-100 active expansion leaves 25. Retry the paid quote; no second debit or Claim mutation.
- [ ] Request a purchase beyond available funds; rejection preserves balance/land/subsidy. Fund the payer and retry before expiry. Move away from the quoted chunk, expire the quote or alter its context: confirmation must reject.
- [ ] As Governor, quote expansion: payer is the treasury, assessment is Owner's portfolio/subsidy. Without sufficient approved allowance it rejects; successful payment consumes allowance and Owner usage only. Demotion, ban or ownership changes invalidate authorization.
- [ ] Unclaim/abandon/transfer and restart: no refund or personal discount reset. Paid receipts remain idempotent; a new pricing configuration invalidates pending quotes. Check config 8 and `config-before-paid-claims.yml`; existing pre-cutover Claims are not retroactively billed.

## Frozen roadmap Phase 0 — persistence/build stabilization

Implemented: Liquibase 5.0.4 XML baseline and verified legacy handoff, jOOQ schema from temporary migrated SQLite, opaque scoped UnitOfWork, repository/adapter separation, SQLiteConfig/DataSource, JUnit 6.1.2, ArchUnit 1.5.0 and SHA-256 dependency verification.

Agent proof: 83 tests pass, including native empty/legacy/mismatched-constraint migration, exact long balance and transaction lifetime/thread tests. Copied real development data preserved 25 existing non-clock tables exactly, with only the Economy version marker advancing 1→2 through its legacy migration. Original dev DB hash stayed unchanged. Fresh Paper exercised existing paid quotes, Owner-wallet expansion, Governor treasury/allowance, Holders/consent, bans, real Activity and land capture; restart preserved all 352 non-clock rows, wallets 5/100, treasury 55, Holder identity and zero remaining allowance. Liquibase SPI plugin-loader startup failure was reproduced and fixed before these passes.

- [ ] On a disposable existing world/database copy, full stop/start into Phase 0. Confirm bridge/schema validation, retained IDs/balances/Claims/Parcels/history and no unprotected startup. The Owner-wallet expansion behavior must remain unchanged in this phase.
- [ ] From a fresh installation, exercise first grant, quoted purchase and replay, treasury allowance and Parcel consent, then restart. Confirm no duplicate money and retained protection/Holder state. These checks do not approve Phase 1 treasury-only expansion or any later feature.

## Frozen roadmap Phase 1 — treasury-funded expansion

Implemented: founding remains founder-wallet; ALL expansions use Treasury and current Owner assessment. PlayerLandProgress exposes the existing monotonically consumed purchase counter as progression revision. New quote-context format rejects old unpurchased quotes without rewriting paid receipts or adding redundant schema columns. Phase 0 Liquibase baseline/config versions remain unchanged.

Agent proof: clean no-cache build/check passed 87 tests. Four new cases cover Owner treasury insufficiency despite personal funds, Governor quote staleness from Owner portfolio changes, claim/unclaim same-area staleness and prior-format quote versus paid-receipt restart compatibility. A genuine Phase 0 wallet quote was created with the prior JAR and rejected by the new JAR. Owner wallet 1005 could not cover treasury 55 against price 80; deposit 25 permitted the treasury payment, leaving wallet 980/treasury 0. Replay did not debit. SIGKILL after commit retained all 369 non-clock rows; restart loaded four Claims. Public break was denied, the identical Owner break succeeded, and the test block was restored.

- [ ] As Owner, confirm expansion uses Treasury even when your personal wallet has enough money. Fund the treasury explicitly, purchase, and verify only the treasury is charged at confirmation. Founding must still use the founder's wallet.
- [ ] Verify Governor allowance/Owner assessment and old pending quote invalidation; paid receipts remain replayable. Changing portfolio/topology/progression must reject stale consent without spending money or allowance.
- [ ] On a disposable fixture, restart after a committed purchase. Verify Claims/protection, balances, receipt replay and personal progression. No subsidy resets after unclaim, abandonment or transfer.

## Frozen roadmap Phase 2 — certification applications and naming

Implemented: lifetime actual credited Activity, default age ≥3 game days/non-INACTIVE/lifetime ≥20 gate, governance applications, administrator decisions, immutable decision provenance and verified-name read/export. Old certify toggle/API/column removed. Legacy approved flags or official names map to VERIFIED, without fabricating lifetime credit. Baseline-only synchronization precedes real forward migrations and snapshot loading.

Agent proof: 94-test clean no-cache build, including seven certification state-machine tests and empty/legacy naming/default/retired-column checks. Real Paper rejected the old toggle and an early application; at age >3 days and lifetime 19 it still rejected, then an additional credited action reached 20 and permitted application. Non-admin review rejected; denial retained UNVERIFIED/Claim/balance; subsequent approval assigned the name. Repeated approval after a later rename preserved the rename and produced only one approval fact. Restart preserved all 282 non-clock rows. A development-copy migration retained Oakfall/Test as VERIFIED and unnamed S#3 as UNVERIFIED, with no lifetime rows; original dev DB bytes stayed unchanged.

- [ ] Observe UNVERIFIED, age/current Activity/lifetime requirements through `/settlement certification`. Below any gate, application rejects. After eligibility, Owner/Governor can apply with an optional quoted name; eligibility never auto-verifies.
- [ ] Deny an application as administrator: Claims, roles, funds and protection remain. Reapply and approve; verify name uniqueness, formal naming/export and decision notes. Replay approval after an admin rename; it must not restore the older proposed name.
- [ ] Upgrade a disposable old-data copy and restart: old approved/named records migrate, the writable certify toggle is absent, new defaults are UNVERIFIED, and lifetime credit is not invented. Confirm config 9 and `config-before-certification.yml`; inspect pending/decided application provenance after restart.

## Frozen roadmap Phase 3 — administrator-approved ownership acquisition

Implemented: durable proposal/exact-fee recipient acceptance/admin approval or denial/cancellation; default ceil(10% × immutable current Claim base total), missing-profile and stale-context rejection, atomic recipient Wallet SINK/ownership/membership/History/outbox, unchanged treasury/certification/subsidies, replay and config 10 migration. Pending legacy TRANSFER offers cancel; accepted history and ordinary invitations survive. Immediate transfer is removed.

Agent proof: clean no-cache check/build passed 102 tests (zero failures/errors/skips). Seven acquisition tests cover consent/admin authority, insufficient funds, injected post-debit rollback, restart/replay, stale Claims/profiles/policy, missing valuation, ban/expiry/denial and wide-integer rounding. Previous-phase migration preserves accepted transfer history/invitations while cancelling pending transfers; existing legacy bridge and empty-schema checks pass.

Actual Paper upgraded a disposable Phase 2 world/database and started a separate empty-data instance. Fee 10 was disclosed; pre-consent approval, wrong fee and non-admin review rejected. Accepted proposal retained old Owner. Recipient balance 5 rejected approval without partial mutation; funding to 10 permitted one SINK. Treasury remained 17; former Owner became Citizen and lost Owner-only command authority. Restart preserved all 325 non-clock rows; replay left recipient Wallet 0, Treasury 17, VERIFIED name and exactly one transfer fact. Protocol clients showed outsider denial and Owner break responses; Phase 4 clarified that the post-restart Owner response was client prediction, not server-side block-state proof. Management-book grant worked; native page layout is not visually signed off.

- [ ] As two non-admin players, propose transfer to a Citizen/Governor and verify the exact fee; wrong amount rejects, correct acceptance alone neither pays nor transfers. Have an administrator approve, then check the recipient debit, former Owner Citizen, unchanged Treasury and new Owner authority.
- [ ] On disposable data, test insufficient funds, stale/expired quotes, denial and cancellation. Retry a successful approval with the same reviewer/note; no second charge or transfer. Confirm preserved certification and no reset of personal Claim discounts.
- [ ] Upgrade/restart a disposable copy: old pending transfers are cancelled, accepted history/invitations preserved, config 10 and backup present. Verify restored protection and book acquisition links in the native client.

## Frozen roadmap Phase 4 — Local Activity Buckets

Implemented: actual admitted contributions atomically credit the exact Claim bucket alongside aggregate/lifetime/caps; zero initial local score and no historical backfill. Lazy shared-clock decay, bounded one-hop cardinal same-Settlement kernel, area-weighted footprint API and read-only derived supported components. Admin inspection is `/settlement local-activity [settlement] [page]`. Config 11 adds weight/threshold and backup; unclaim/abandon delete buckets in their owning transaction, so reclaimed Claims cannot revive old local support. No automatic release/buffs/Building behavior.

Agent proof: clean no-cache check/build passed **108 tests, zero failures/errors/skips**. Five new local tests cover negative coordinates and isolation, bounded/disabled kernel, weighted read-only/transactional footprint projection and invalid geometry/time, derived region split/recovery, unclaim/reclaim/abandon lifecycle and unknown-version atomic rollback. Existing freeze/restart and unauthorized/stale/public-credit tests now assert local state too. A new native previous-Phase-3 migration test preserves aggregate scores and creates no fabricated local buckets; existing empty/legacy checks still pass.

Actual final-JAR Paper: separate empty-data startup reached READY. Upgrading copied Phase 3 data retained all prior non-clock/non-changelog rows and started with no local buckets. Non-admin inspection rejected; login, teleports, purchases and system setblocks did not fabricate local credit. Real server-confirmed breaks produced score 1 in each end of a three-Claim line; a rapid repeated break did not add credit. Default kernel showed middle raw 0/support approximately 0.5. While time was frozen, a forward jump and real middle break left local rows unchanged. Re-enabled time plus seven game days halved projected scores without rewriting rows or releasing Claims.

Restart retained all **360 non-clock rows**. Only the disposable read-time threshold was changed from 20 to 0.3; endpoints formed two derived components with unsupported middle. A real credited middle action merged them into one, with all three Claims retained. The runner explicitly checked server `execute if block ... air` broadcasts and persistent credits, not client-predicted air. Initial runner failures came from a held Management Book (which intentionally cancels breaks) and expecting synchronous RCON text for an asynchronous broadcast; fixed in the runner without changing gameplay or inventing scores.

- [ ] As non-admin, local inspection rejects; as administrator, inspect raw/support/derived components and page a larger Settlement. Verify only cardinal currently claimed same-Settlement neighbors contribute, with no persistent Region IDs or management commands.
- [ ] Perform legal and rejected/cooldown-capped actions in different Claims. Confirm exact local credit, unchanged aggregate rules, small Public credit and no idle/automation credit. Freeze time and restart; no decay or downtime accrual occurs while frozen.
- [ ] On disposable data, tune read-time weight/threshold, observe regions split/merge under decay/recovery, and verify Claims never release automatically. Unclaim/reclaim must not revive old local scores. Check config 11 and `config-before-local-activity.yml`.

## Frozen roadmap Phase 5 — explicit Building registration and active baseline

Implemented: Owner/Governor inclusive selection, one-Settlement/BUILD authorization, naturally loaded capture, default 4096/hard 32768 volume cap, non-overlapping active Buildings, stable identity and compressed revision snapshots. Geometry derives occupied/exterior/interior/opening/vertical-support data without world rescans. Authorized edits publish through the existing coordinator with durable chunk journals and idempotent receipts; resize retains identity, retirement retains history and guards Claim release. Block entities have no NBT payload or ordinary automatic-mutation eligibility; station metadata marks a crafting table and clears on legal removal. Config 12 backs up `config-before-buildings.yml`. No Decay/Repair/Era work is implemented.

Agent proof: final clean/no-cache build/check passed **116 tests, zero failures/errors/skips**. Seven Building regressions cover geometry/palette/entity exclusion, governance/world/Claim/volume/overlap rejection, legal demolition and later-edit replay across restart, Holder policy versus Owner, stale/partial edits with no receipt, station history/exclusion/clearing and resize/retirement/release. Native previous-Phase-4 migration retains local Activity and creates no Buildings; existing legacy/empty checks pass. Synthetic 4096/32768-cell solid/shell/checker codec+geometry benchmarks verified round trips and the hard ceiling; measured p95 below 4ms/23ms respectively, not a Paper world-scan guarantee.

Actual Paper upgraded a disposable Phase 4 copy without changing prior gameplay rows; an empty-data server reached READY. A 343-cell Building reported 217 occupied/exterior cells, 125 interior air cells, one opening and 142 vertical support links. Non-governance, oversize, outside-Claim and overlap attempts rejected. Both registration and resize rejected an unloaded target, and the server's loaded flag remained false. Server-confirmed demolition changed the current cell to air while revision 1 remained stone; real gold-block placement updated the same Building. An outsider's break was denied without revising the baseline.

Station marking made the crafting-table cell ineligible for automatic mutation; legal destruction cleared the current marker while revision 4 retained it. Resize kept the UUID and grew volume to 392; unclaim required retirement. A temporary operator-only QA probe captured the real pre-publication chunk journal (after-state absent), then restored it after later committed edits to simulate stale world/SQL progress. Restart acknowledged it without rolling back gold or adding revisions, retaining all **378 non-clock rows**. Retirement kept old snapshots, and re-registration used a new UUID/current gold design. Replaying the same retired-Building journal with bounded probes retained all **383 non-clock rows** and the replacement Building.

- [ ] As Owner/Governor, select corners, preview and register a Building. Reject outside-Settlement, excessive volume, overlapping active volume and unloaded selections; registration must not cause chunk loads. Check Parcel BUILD policies still apply.
- [ ] Inspect geometry and individual cells, including negative Y, air openings and block entities. Revisit a historical revision after legal construction/demolition; old snapshots remain, current intended design changes and the UUID stays stable.
- [ ] As a Citizen or Public Holder with BUILD permission, renovate; rejected or banned edits must not alter the baseline. Verify ordinary protection and rapid-edit backpressure without losing legitimate observations.
- [ ] Mark a captured crafting table as station, inspect its exclusion, then remove it legally. Current marker clears, old station history remains, and no Repair or material-consumption workflow starts.
- [ ] On disposable data, resize, restart, inspect, retire and re-register. Retired history survives; current world design—not old walls—is captured. Confirm config 12/backup and that affected Buildings must retire before unclaim/abandon.

## Frozen roadmap Phase 6 — durable registered-only Decay

Implemented: footprint support/grace/hysteresis, indexed due selection, explicit WEATHER profiles, one-block DROP and bounded connected CAVE_IN, SQL intent before world mutation, fresh authority/activity/source validation, natural-load-only execution, independent world-save markers and durable outcomes. Damage stays separate from the baseline; conditions are INTACT/DAMAGED/RUINED. Recovery stops future work without repairing; legal manual restoration clears damage and advances revision even when intended data is unchanged. Config 13; no station Repair or Era.

Agent proof: clean no-cache check/build passed **127 tests, zero failures/errors/skips**. Ten Decay regressions cover grace/freeze/downtime/recovery timing, whitelist and codec, deterministic connected/vertical planning, damage/ruin, recovery without healing, independent saved-world/SQL reconciliation, player baseline conflicts, destination Parcel and station/entity exclusions, partial-conflict outcomes, persisted-intent corruption and manual restoration. Native Phase 5 upgrade retains baseline bytes and starts new grace without fabricating damage.

Real Paper used isolated copies and a removable operator-only repository-delegating fault probe. Four controlled hard exits (137) exercised durable intent before mutation, saved full world mutation before SQL outcome, committed SQL outcome with world autosave disabled, and a deliberately saved partial CAVE_IN world state before outcome. Recovery retained stable operation IDs, completed expected before/target mixtures without duplicate facts, and left an unloaded target unloaded until natural player arrival. Server block predicates checked targets, retained chest/station/torch and untouched external grass; item/falling-block entity counts stayed zero.

The final artifact exercised all three modes, reached RUINED with 20 damaged cells/10 lost source cells, and stopped after real local Activity recovered to approximately 0.991 without reducing damage. A gold-block conflict injected after durable intent was preserved with no false damage. Real manual restoration returned INTACT and advanced baseline revisions; the chest still held seven diamonds. After removing the QA plugin, restart/reconciliation preserved all manually restored roof blocks without reapplying old Decay. Fresh single-plugin startup also reached READY.

- [ ] On disposable registered structures, inspect `/settlement decay <UUID>` through low support, grace, and recovery. Freeze time and restart; no downtime progress or new physical decay while frozen.
- [ ] Observe whitelist WEATHER and bounded vertical DROP/CAVE_IN. Verify no item/falling-block entities, no changed block entities/station/unsafe attachments, and no changes outside registered bounds.
- [ ] Recover through meaningful local actions: new decay stops but existing damage remains. Manually rebuild damaged cells and remove debris; current condition/design update without an Era or station-repair reward.
- [ ] On disposable copies, interrupt intent/apply/outcome and restore independently saved world/DB progress. Stable operations must not duplicate effects, unloaded chunks stay unloaded, and conflicting/newer player design must win.
- [ ] Upgrade/restart existing Buildings and inspect condition/history/config 13 plus `config-before-decay.yml`. Old baselines remain; no retroactive instant damage, automatic Claim release or material Repair appears.

## Frozen roadmap Phase 7 — material Repair and History/Era

Implemented: whole-Building station inspection/preparation, real plain-item supply and durable escrow, game-time bounded natural-load Repair, current-baseline/permission checks, per-cell conflict preservation, cancellation/refunds, independent player/world/SQL reconciliation and structured History/Era. Config 14 backs up `config-before-repair.yml`; migration initializes Era zero without inventing prior repairs.

Agent proof: clean no-cache check/build passed **139 tests, zero failures/errors/skips**. Twelve Repair regressions cover partial funds/duplicate receipts, restart completion, ordinary versus ruin repair, manual completion, cancellation/refund capacity, pending-operation barriers, conflicts, renovation, plain-item codec/capacity, later ruin cycles, player receipt reconciliation, native Phase 6 upgrade and permission-pause cycle retention.

Isolated real Paper cases used a removable operator-only fault probe around real repositories. Real inventory retained two named blocks while consuming ten plain blocks; partial supply waited for the balance, then the roof returned to INTACT and Era 1. Intent-only, saved debit before SQL confirmation and SQL-confirmed/player-data rollback recovered exactly once. World-intent, saved-world-before-outcome, SQL-ahead-world, completed-job/Era-before-world-save and partial-world cuts all resumed. Terminal-job recovery checked every applied target, including removed debris, before freezing time; SQL completion alone was not treated as physical proof. Injected per-cell gold conflict preserved the conflicting state, consumed nine materials, refunded one and awarded no Era.

Saved-refund-before-SQL and SQL-refund/player-data-rollback cuts both recovered to ten plain plus two named items with one refund receipt. After removing all probe JARs, a clean restart read COMPLETED, no pending cells/escrow, ten consumed materials, Era 1 and the History catalog. Temporary probe source/classes and smoke script were removed; saved evidence remains ignored. All verification servers/clients stopped normally after recovery.

- [ ] At a registered Building station, inspect/prepare/supply with plain and named materials. Partial supply must wait, named/modified items stay untouched, station distance and Parcel BUILD/INTERACT permissions apply.
- [ ] Freeze time, unload naturally, restart and resume a funded job. No downtime progress or forced loading; current valid damage repairs in bounded batches, not retired or renovated design.
- [ ] Change permission or one affected block during repair. Pause/revalidation must retain valid work and preserve the conflicting block; cancellation/refund returns only unused items, including with limited backpack space.
- [ ] On disposable paired backups, interrupt supply/refund and physical Repair before/after saves. Rejoin/restart must reconcile without duplicated inventory, repeated consumption or repeated Era.
- [ ] Restore a RUINED Building through Repair: INTACT and one Era increment with durable History. Ordinary damage repair/manual restoration grants no Era; a later genuine ruin cycle can increment again. Inspect config 14, backup and `/settlement history-catalog`.
