# Temporary Player Acceptance Checklist

Purpose: accumulate completed implementation milestones and what the user should test at the end of this development batch. This is not an implementation backlog or a second architecture.

Maintenance: append each completed gameplay change with its commit, player-visible checks and known limits. Keep automated/agent verification separate from user acceptance. Check boxes only after the user reports the corresponding result; carry unresolved checks through handoffs. Retain this temporary document until the user confirms the batch is tested and requests removal or archival.

## Completed implementation awaiting user acceptance

| Milestone | Commit | Agent verification | User acceptance |
|---|---|---|---|
| Shared persisted game clock | `f8a9f50` | Build/regressions; live forward jumps, rewind, freeze, changed tick rate, exact frozen restart and bed sleep | Pending |
| Existing gameplay timers migrated to ticks | `f8a9f50` | Build; existing offer tests and legacy deadline conversion regression; plugin boot | Pending |
| Settlement Activity scoring and status | `f8a9f50` | Build/regressions; live real block credit, inactivity/grace, persisted status | Pending |

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
