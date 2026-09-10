# Current Handoff

## State

- Current command cutover: `7aaccd6`, on the Settlement/Parcel implementation `d14bb63`.
- Tool requests are `/esmp tools parcel_wand` and `/esmp tools management_book`. The user corrected the requested spelling to plural `tools`; no singular or old material-name/Settlement issuance aliases are retained.
- Root command registration permits ordinary players to reach tools; issuance checks `esmp.settlement` and readiness. `/esmp status` remains administrator-only. Console cannot receive tools.
- Existing PDC tool tags deliberately remain unchanged, preserving issued items and duplicate detection. No database/config-version migration or gameplay policy change: Core schema 1, Civilization schema 3, config 4; prices remain numeric-only with no real currency/items consumed.
- User development server/data were untouched. The isolated Parcel fixture was used for validation. The temporary acceptance checklist now has 27 pending user checks; no automated result was marked as user acceptance.

## Completed

- Moved issuance into the native `/esmp` handler before the administrator-only status branch, with semantic tool names and player/permission/readiness checks.
- Removed the command-level admin gate from the shared root descriptor while keeping status authorization explicit.
- Updated root/tool-name tab completion, removed legacy Settlement completion/dispatch, and updated help and the management-book Parcel Wand link.
- Updated canonical architecture, Chinese README, decision rationale and the running acceptance checklist. Tool behavior, cooldowns and item persistence remain unchanged.

## Changed files

- `esmp-plugin/.../EsmpPlugin.java` — root tools dispatch and permission-aware completion.
- `esmp-plugin/.../settlement/SettlementCommands.java` — removed old issuance branch/help entry.
- `esmp-plugin/.../settlement/ManagementTools.java` — semantic name mapping over existing item identities and updated book link.
- `esmp-plugin/src/main/resources/plugin.yml` — shared root availability and usage text.
- `ARCHITECTURE.md`, `README.md`, `.agent/DECISIONS.md` — accepted interface and permission boundaries.
- `.agent/TEST_CHECKLIST.md`, `.agent/HANDOFF.md` — commit reference, updated commands and pending acceptance.

## Verification

- `./gradlew build --no-daemon --console=plain` with the existing JDK 25 toolchain passed.
- Live Paper fixture `esmp-parcel-smoke` on loopback 25584/25585: non-OP root completion exposed tools, tool completion returned management_book/parcel_wand, and both semantic requests issued the correct items.
- Verified non-admin status denial, admin status success after temporary OP, console tool rejection, invalid/singular/legacy route non-issuance and duplicate prevention.
- An already-issued ParcelOwner wand was recognized for duplicate prevention; its existing book opened successfully and the outgoing book contents contained `/esmp tools parcel_wand`.
- The first smoke observer failed on an empty protocol packet while serializing it. The test observer was corrected to handle empty packets/BigInt; no production change was made to hide the observer error. Successful retry output is retained in ignored `.run/parcel-smoke/native-tools-retry.log`.
- No native desktop page/particle visual verification is claimed; this change alters routing/text, not rendering. Previous Parcel/gameplay evidence remains in prior handoffs and the checklist.
- Fixture shutdown confirmed exit 0. Current command references, local documentation links/anchors and fenced blocks passed checks; 27 pending player checks remain. Temporary smoke client and fixture translator JARs were removed; ignored logs/data remain.

## Next action

Use a full restart to load the new command registration when the user is ready to test; then continue the bounded Building geometry/evaluation slice. Keep real currency integration deferred to the user's selection.

## Risks or blockers

- Do not restore the old root-wide admin permission: that would hide tool requests from ordinary players.
- Semantic CLI names are not persisted item-tag names; renaming stored tags would require an explicit item-data migration and is not part of this command cutover.
- Existing native visual acceptance and installed Activity policy-migration gates remain unchanged. No further blocker was found for these command routes.
