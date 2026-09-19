# Current Handoff

## State

Binary WILD/CLAIMED Building transitions are implemented after core `mvp-v1.0`. WILD is wholly unclaimed; CLAIMED wholly inside one Settlement. No fragments/mixed ownership. Config 14 and frozen dependency pins remain. Automatic notice/grace land release is not implemented. Native-client acceptance remains separate.

## Completed

- Nullable current Settlement plus immutable historical association, revisioned ownership, historical baseline association and current/publication query separation.
- Whole RUINED explicit unclaim/confirmed abandon releases after pending-work reconciliation. Remaining Repair cancels with original-payer refundable escrow retained. Existing Parcel, Treasury-clear and ordinary last-Claim guards remain.
- Paid Claim requires bounded naturally loaded exact Building captures; payment/Claim/association/current-state baseline commit atomically. Stable replay does not capture/pay again. UUID/history remain, no free physical repair or Era. Multi-chunk wild footprints reject single-chunk takeover.
- Wild legal edits use new ownership stamps; independent world/chunk Activity with existing cooldown/caps/lazy decay/four-neighbor support suppresses future Decay. Wild Decay uses system principal/current unclaimed boundaries, existing material hazards and journals. Former roles never authorize it.
- Two forward migrations; existing Buildings become CLAIMED without history loss. SQLite-native ALTER avoids tested Liquibase rebuild loss of defaults/FK metadata. No released changelog rewritten.

## Changed files

Civilization Building/Settlement APIs, Building/Claim/Repair/Decay/Activity persistence, local support and history catalog; Paper Building/Settlement/Activity/Decay adapters; generated-table configuration; two cycle changelogs and master; affected purchase/observation callers and migration fixtures. README, architecture §30 implementation status, AGENTS, decisions/checklist/notes updated.

## Verification

- `JAVA_HOME=.tooling/jdk-25.0.4.1+1/Contents/Home ./gradlew clean check build --no-build-cache --no-daemon --console=plain` passed: 146 tests, zero failures/errors/skips. Includes empty/legacy/native Phase 7 migration, transition/refund/replay/ownership-stamp barriers, wild Activity/restart/Decay and multi-chunk boundary rejection.
- Real isolated Paper `.run/cycle-buildings`, migrated from prior Phase 8 data: explicit unclaim changed Roof CLAIMED→WILD while another Claim/assigned Parcel remained. Public maintenance placed a block, advanced baseline, credited 1.0 support and suppressed further Decay. After game-time advancement and safe-neighbor setup, SYSTEM_ACTOR WEATHER changed stone bricks to cracked stone bricks. Restart retained wild association and physical state.
- Real founding purchase 25 captured the current cracked block, associated the same Building UUID with new S#2, retained historical S#1, reset damage against inherited state without Era change and cleared wild buckets. Receipt replay kept balances identical. Former Owner retirement rejected; outsider break rejected; new Owner break revised current baseline to air while revision 9 retained cracked brick.
- Live two-chunk Wide registration succeeded; unclaim rejected with `Release would cross a tracked Building boundary` and Claims unchanged. First fixture attempt lacked five Treasury units (price 105), so was not counted; funded retry passed. Initial wild WEATHER waited on hazardous dirt neighbor; changing fixture neighbor to stone passed without changing production safety.
- Logs/snapshots retained ignored under `.run/cycle-buildings` and `.run/cycle-wild-snapshot`. No user development data was changed. Test server stopped normally; client supervisor termination was intentional. Temporary driver is removed at cleanup. No native visual verification claimed.

## Next action

Implement the bounded notice/grace peripheral release loop on these whole-transition APIs, retaining topology/Holder/financial safeguards. Do not restart the design questionnaire or add mixed ownership.

## Risks or blockers

Automatic Parcel removal, final Treasury collection and terminal governance changes are not shipped. Ordinary last-Claim/Treasury/Parcel checks still apply. Multi-chunk wild Buildings cannot be adopted by current single-chunk purchase; deliberate safe rejection, not fragment processing. Wild governance/Repair requires complete claiming. Original material refunds remain separate from new Building ownership. Use paired world/player/database backups before upgrading.
