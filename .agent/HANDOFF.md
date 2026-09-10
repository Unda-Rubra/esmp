# Current Handoff

## State

- The user's revised `ARCHITECTURE.md` is canonical. This work read the complete revision and synchronized plans/notes only; the user's architecture bytes were preserved and its pre-existing unstaged edit is not included in this notes commit.
- Runtime baseline remains `f8a9f50`: four-module Java 25/Paper 26.2 plugin, Core world schema 1, Civilization schema 2, config 3. Existing Manager/Member/Visitor workflows, free claims, player naming, game-clock timers and Activity are implemented. Paid claims, Governor/Citizen/Public+BANNED, Parcels, naming administration and tools are not implemented.
- Immediate priority is now the MVP Settlement/Parcel revision, before Building discovery/geometry. The earlier detector-comparison research is retained for that later slice, not deleted or claimed as implemented.
- MVP Settlements remain protected/owned unless explicitly abandoned or changed by an authorized lifecycle action. Automatic inactivity/ruin/peripheral release is excluded from MVP; dynamic Settlement cycles are the required next major phase after stable MVP, not optional. Disaster/Epoch systems are separate open/later work.
- Existing player acceptance remains unchanged: prior Settlement testing was user-confirmed and `.agent/TEST_CHECKLIST.md` still has 14 pending baseline checks. No new target feature is marked complete. No code, build configuration, database or server was changed.

## Completed

- Replaced the Building-first immediate plan with §28 order: paid claim transaction/pricing → role migration and local bans → Parcel schema/geometry/authorization and complete commands → Management Book/Parcel Wand plus naming administration → Building geometry → durable Decay/Repair.
- Recorded target invariants: rising area-based claim costs; numeric identity/generated labels and administered official names; relation-set authorization; 8×8×8 six-connected non-overlapping Parcels; zero/multiple Holders independent of membership; governance plus all-Holder consent for assigned boundary/revocation changes; policy-driven Public access and administrator ban exemption.
- Added implementation gates for a minimal real charging ledger, pending-offer/role/Activity migration, consent/revision handling, assigned Parcels versus parent unclaim/abandonment, policy editing and legacy ID/name preservation. No unpaid/fake-balance shortcut or full-economy expansion is approved.
- Updated Building planning for occupied/exterior/interior geometry, openings/support, renovation-conflicted/retired baseline fragments and the ruined-tower/new-hall case. Kept special block-entity safety, no system decay drops, simple collapse candidates and coarse wild/infrastructure handling explicit.
- Recorded required post-MVP dynamic cycles and deferred Anthropogenic Landforms/world regeneration/disasters without inventing final capacity or release formulas.
- Updated root agent instructions and Chinese README so future work distinguishes the implemented legacy contract from accepted target scope. Added a supersession notice and dated rationale to the decision log rather than rewriting historical evidence. Marked acceptance checks as baseline-specific without changing their outcomes.

## Changed files

- `AGENTS.md` — corrected implemented state and target vocabulary/invariants, immediate scope and handoff/checklist rules.
- `README.md` — Simplified Chinese target/current distinction, new MVP summary and implementation order; current commands/legacy tests remain accurate for the existing JAR.
- `.agent/DECISIONS.md` — dated 2026-09-10 supersession and rationale grounded in the user's revision.
- `.agent/OPEN_QUESTIONS.md` — reordered implementation gates, retained detector research for the later geometry phase, required post-MVP cycle questions and canonical ambiguities.
- `.agent/TEST_CHECKLIST.md` — baseline-version notice; all 14 pending player checks and prior acceptance facts preserved.
- `.agent/GAMEPLAY_EXTENSION_PROPOSAL.md` — updated canonical section link after §26 rename and current priority pointer.
- `.agent/HANDOFF.md` — this current state, plan and evidence. `ARCHITECTURE.md` itself was not edited.

## Verification

- Read the entire 1724-line architecture revision, including the full long Activity migration paragraph, alongside README, agent instructions, decision log, open questions, handoff and acceptance checklist.
- Inline document checks passed: 15 target-plan contract assertions, paid-claim-before-Building order, balanced fences and local Markdown file/heading anchors across the six changed documents preceding this handoff.
- SHA-256 check confirmed the user architecture was unchanged by synchronization. Acceptance check count remains exactly 14; no automated result was promoted to user acceptance.
- No build, tests, model benchmark, server scenario or migration was run for this documentation-only task. Prior runtime proof belongs to `f8a9f50` and remains in history/checklist, not evidence of the new MVP implementation.

## Next action

Define the bounded paid-claim charging contract from §§7.6 and 28: minimal real ledger, payer/funding, rising price curve and atomic debit+claim+history with retry/failure handling. Then implement that slice before governance/Parcel migration. Keep the existing runtime and user data intact until a complete tested cutover is ready; append acceptance checks only when corresponding behavior ships.

## Risks or blockers

- Paid claims require real currency/accounting, but the full economy is not implemented. The architecture does not settle payer/funding, exact rates or unclaim refund behavior; do not assume legacy free claims or invent an unlimited balance fallback.
- Numeric ID/legacy name migration, assigned-Parcel consent versus parent claim release, ban/admin identity details, policy-edit authority and Holder/Public Activity weights need explicit implementation contracts. Existing installed Activity policies currently reject changes without migration.
- Broad legacy lifecycle diagrams and the generic Release glossary still mention automatic release, while specific §§23 and 25–28 exclude it from MVP. Follow the specific MVP boundary; the illustrative `create <name>` command does not override administered naming. The user architecture was deliberately not rewritten to settle these details.
- Building baseline geometry, block-entity handling and world/database/material recovery remain prerequisites for safe physical decay/repair; no algorithm is selected or measured by retained research.
- Local development uses loopback-only offline authentication; do not expose it publicly. No running server or runtime data was touched.
