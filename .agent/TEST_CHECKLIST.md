# Temporary Player Acceptance Checklist

Purpose: accumulate completed implementation milestones and what the user should test at the end of this development batch. This is not an implementation backlog or a second architecture.

Maintenance: append each completed gameplay change with its commit, player-visible checks and known limits. Keep automated/agent verification separate from user acceptance. Check boxes only after the user reports the corresponding result; carry unresolved checks through handoffs. Retain this temporary document until the user confirms the batch is tested and requests removal or archival.

## Version coverage

Sections 1–5 preserve the `f8a9f50` clock/Activity baseline checks and their pending user outcomes. Its Manager/Member/Visitor, player-naming and config-version-3 expectations are historical. **The current Settlement/Parcel implementation is `d14bb63`; use section 6 for the new roles, numeric-only prices, tools, consent, naming and config/schema migration.** No automated result below is a user sign-off.

Tool command routing is updated by `7aaccd6`: current examples use `/esmp tools parcel_wand` and `/esmp tools management_book`. The underlying `d14bb63` tools/Parcel behavior and stored item identities remain unchanged; old issuance commands are not retained as aliases.

Inactivity-driven automatic release remains outside MVP. Existing pending checks are retained rather than marked passed or silently rewritten. Real currency integration is deliberately absent: current prices are numbers, not deductions or mock balances.

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

- [ ] Run `/settlement create`, then `/settlement price 1` and claim an adjacent chunk. Defaults show 100 for creation and 125 for the next chunk; no items, real currency or artificial wallet balance are deducted. History records the quoted number and `charged=false`.
- [ ] New Settlements start as `S#number`. An ordinary Owner cannot rename one. An administrator uses `/settlement certify 1 true` and `/settlement rename 1 Harbor`; number/UUID stay stable and history retains the naming event. Existing pre-migration names remain usable.

### Roles and bans

- [ ] Invite/accept produces Citizen; Owner promotion plus recipient acceptance produces Governor. Governor cannot promote other Governors or perform ownership-only operations. Public users remain nonmembers even when holding a Parcel.
- [ ] A Public Holder opens an allowed Parcel container. Owner/Governor bans them: the inventory closes, subsequent governed interactions are denied, but walking and informational commands still work. Unban restores ordinary policy; identified administrators cannot be banned.

### Tools and geometry

- [ ] `/esmp tools management_book` opens useful current command links. `/esmp tools parcel_wand` supplies the marked stick; a named off-hand Name Tag plus left/right clicks selects a named Parcel. Passing either item to another player does not grant governance. Duplicate issuance and crafting/anvil/grindstone conversion are blocked as documented.
- [ ] Preview and commit an 8×8×8-aligned Parcel, including negative coordinates. Face-connected extensions succeed; diagonal-only/disconnected/outside-Settlement selections fail atomically. Existing other-Parcels' cells are skipped only when the resulting shape stays valid. Surface removal must not split the remainder.
- [ ] Compare cyan current, green selected, blue existing and red conflict/removal corner particles in the native client. `parcel commit` requires a current preview; expiry, state change, logout and restart do not permit replaying an old selection. Position commands work without owning tools.

### Holder consent and parent protection

- [ ] Assign two Holders using a proposal; both must approve along with governance. Then propose a boundary, policy, Holder-list or naming change: no unilateral edit succeeds. Approvals survive restart; stale/expired proposals cannot apply, stale still-pending proposals may be rejected, and resolved proposals cannot replay.
- [ ] Parent unclaim/abandonment cannot remove a Parcel indirectly. Remove affected Parcels through their required consent first. Unassigned Parcel governance and explicit administrator dispute handling remain usable and auditable.

### Policy separation, automation and migration

- [ ] Give a Public player HOLDER access in one Parcel: permitted building/containers work there, not in the unassigned surrounding Settlement. A Citizen+Holder can use either allowed relation. Denying CONTAINER while allowing BUILD must block containers even for Owner unless explicit administrator bypass is enabled.
- [ ] A hopper crossing two different Parcels is blocked by default. Grant PUBLIC AUTOMATION only on one side: still blocked. Grant it on both protected sides: items move. Confirm the corresponding desired piston/fluid boundaries on the native server too; do not assume every adapter from the hopper result alone.
- [ ] Upgrade a disposable old database/config and restart: roles become Governor/Citizen, UUIDs/offers/history/Activity stay intact, stable numbers and grandfathered names appear, and config 4/Civilization schema 3 are present. Parcel cells, policies, Holders and proposals persist across another restart; session previews do not.

Native visual caveat: the automated older-protocol client opened the book and exercised real stick actions but produced particle decoding warnings. It did not verify how pages or colored previews look in the target desktop client.

## 7. Semantic tool commands — `7aaccd6`

- [ ] As a non-OP player, tab-complete and request `/esmp tools parcel_wand` and `/esmp tools management_book`. `/esmp status` must remain denied without administrator permission. Singular `tool`, old material-name arguments and the former Settlement issuance route must not issue items. Existing tools must still work and prevent duplicate issuance; the book's Parcel Wand link must use the new command.
