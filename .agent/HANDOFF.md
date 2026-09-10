# Current Handoff

## State

- Implementation commit: `d14bb63` — Settlement/Parcel governance, numeric claim pricing, role migration, bans, naming administration and book/stick tools. Four-module Java 25/Paper 26.2 plugin; Core world schema 1, Civilization schema 3, config 4.
- The user explicitly deferred real currency. Current price is base + step×claimed area (defaults 100 + 25×area); create/claim records `numeric_price` and `charged=false`. No wallet, fake balance, real currency/item deduction or refund exists. Integrate currency only after the user's selection.
- Governor/Citizen/Public replaces active legacy roles; Holder is independent. Parcel changes use committed immutable snapshots and the existing protection publication gate. Shared GameClock semantics remain unchanged.
- The user's revised architecture was preserved and included with the feature's implementation/currency clarifications. Building geometry/evaluation is next; decay/repair/production/Resource reset remain disabled. Automatic Settlement release stays outside MVP; dynamic cycles remain mandatory after stable MVP.
- User development server/data were not overwritten or restarted. Live tests used isolated `.run/parcel-smoke` on loopback 25584/25585. `.agent/TEST_CHECKLIST.md` preserves previous user outcomes and adds current-commit acceptance checks; automated results are not user acceptance.

## Completed

- Numeric price API/configuration, stable global public numbers with retained internal UUIDs, S#number labels, grandfathered legacy names, and administrator certification/official-name approval.
- Schema/config migrations preserving members, pending offers, history and Activity. Governor/Citizen/Public, local bans, administrator ban exemption, and immediate closure of newly unauthorized protected inventory views.
- 8×8×8 Parcel cells, six-face connectivity, no overlap, world-height/Settlement containment, bounded geometry and immutable policy/Holder views. Limits: 4096 cells per Parcel/selection, 128 Parcels per Settlement, 32 Holders, 64 pending proposals.
- Durable governance plus all current/new Holder approvals for assigned geometry/Holder/policy/name changes; 24000-tick expiry, revision checks, rejection, restart persistence and audited administrator override. Stale proposals cannot apply; still-pending stale proposals may be rejected. Affected Parcels must be removed before parent unclaim/abandonment.
- BUILD/INTERACT/CONTAINER/AUTOMATION relation-set authorization. New Parcels grant team/Holder relations and deny Public by default. Unattributed cross-boundary automation requires explicit Public automation on both protected sides; internal automation uses Holder grant when assigned, Owner otherwise.
- Complete `/settlement parcel` command surface and server-issued PDC Management Book/Parcel Wand. Book opens current command links; stick uses named off-hand Name Tag and left/right voxel selection. Add/remove preview then commit, bounded colored corner particles, transient selection and issuance/utility safeguards.
- Activity observations now include actual block/action/security context and revalidate Parcel policy and bans. Non-Citizen Holder/Public credit remains .02 with a .25 daily cap; equivalent legacy policy names migrate without resetting scores/caps.

## Changed files

- `esmp-civilization/.../settlement/Role.java`, `SettlementService.java`, `SettlementPolicy.java`, `SettlementView.java` — governance/price/naming migration and existing workflow cutover.
- New `ParcelAction.java`, `ParcelRelation.java`, `ParcelCell.java`, `ParcelView.java`, `LandSnapshot.java`, `ParcelService.java` — geometry, policy, snapshots and durable proposals.
- `esmp-civilization/.../activity/ActivityContribution.java`, `ActivityPolicy.java`, `ActivityService.java` — Parcel-aware eligibility and policy-name migration.
- `esmp-plugin/.../EsmpPlugin.java`, `settlement/SettlementRuntime.java`, `SettlementCommands.java`, `ClaimProtection.java`, `ClaimVisualization.java`, `ActivityListener.java`, new `ManagementTools.java`, and `resources/config.yml` — publication, action classification, commands, tools and config 4.
- `SettlementServiceTest.java`, `ActivityServiceTest.java`, new `ParcelServiceTest.java` — migrated existing tests and six new behavioral regressions.
- `ARCHITECTURE.md`, `AGENTS.md`, `README.md`, `.agent/DECISIONS.md`, `.agent/OPEN_QUESTIONS.md`, `.agent/GAMEPLAY_EXTENSION_PROPOSAL.md` — accepted numeric-only exception and implemented contracts; current README remains Simplified Chinese.
- `.agent/TEST_CHECKLIST.md`, `.agent/HANDOFF.md` — current implementation reference and pending player acceptance.

## Verification

- Final `./gradlew build --no-daemon --console=plain` with the existing JDK 25 passed after the final consent rejection adjustment. Parsed reports: ParcelServiceTest 6, SettlementServiceTest 8, ActivityServiceTest 4; zero failures/errors. Existing Core tests also ran/remained up-to-date through the build.
- New regressions cover geometry/negative coordinates/parent release, all-Holder consent and restart, relation union/bans/Activity, stale/expired/rejected proposals, configurable numeric pricing/admin naming, and legacy role/name/offer preservation.
- Actual isolated Paper run exercised numeric 100/125 claim prices; a book-open packet from real item use; stick left/right selection using a named off-hand Name Tag; preview/commit; Public Holder block placement and chest opening; ban-driven inventory closure; denied outside-Parcel building; mutual deletion; Governor promotion; administrator naming and persistent Parcel creation.
- Restart run preserved official name Harbor, Governor ParcelHolder and Parcel geometry. Real hopper test read destination NBT: empty by default, empty with a one-sided Public automation grant, then cobblestone appeared with both sides granted. Owner BUILD permission did not bypass a CONTAINER deny. All fixture server runs ended with exit 0.
- The first automation assertion used `/execute ... run say` and received an empty RCON response despite a server-side FLOW message. The test was corrected to inspect actual container Items; production authorization was not changed to hide the test issue.
- Older 1.21.11 protocol clients through ViaVersion/ViaBackwards emitted particle decoding warnings. Their logs are retained under the ignored fixture; native 26.2 page/particle visual appearance remains a player acceptance task. No native desktop rendering claim is made.
- Touched Java files were formatted with temporary google-java-format 1.36.1 in four-space AOSP mode; the Maven-published checksum was verified. No formatter dependency was added to the project. Documentation local links/anchors and fenced blocks passed checks.
- Acceptance document checks passed: 26 pending user checks, comprising 14 preserved baseline checks plus 12 new current-cutover checks, with valid local links and the actual `d14bb63` reference. Temporary smoke clients, formatter JAR and isolated translation JARs were removed after confirmed fixture exit; ignored fixture logs/data remain.

## Next action

Have the user test the completed Settlement/Parcel checklist when ready; continue with the bounded Building snapshot/evaluation and identity/interior-exterior design slice. Keep real currency integration deferred until a provider/system is selected. Do not enable physical decay/repair before geometry, block-entity and world/material recovery gates.

## Risks or blockers

- Numeric prices are not payments. A later real currency integration must define payer, quote/debit atomicity or recovery, retries and refund semantics; do not retroactively charge current claims.
- Installed Activity value changes still require explicit migration; only equivalent legacy naming is migrated automatically. Safety limits/default-policy tuning can evolve through tested migrations.
- Administrator Parcel operations override consent and are audited; use non-OP test accounts to verify ordinary governance rights. Administrator detection uses online esmp.admin permission or offline OP state.
- Native client visual acceptance remains open; older-protocol particle incompatibility is not proof that the target client renders incorrectly or correctly.
- Abrupt failures can lose uncheckpointed clock-only progress/uncommitted Activity observations. Paired world/database restore remains a separate gate; no economic or physical mutation atomicity is implied by current SQL transactions.
