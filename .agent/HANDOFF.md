# Current Handoff

## State

- Current UI fixes: `f675a15` (short player text and live Parcel overlays), followed by `e823189` (precise cardinal targeting at voxel boundaries).
- Tools remain `/esmp tools parcel_wand` and `/esmp tools management_book`. Existing item identities, governance/consent and numeric-only pricing are preserved. Core schema 1, Civilization schema 3 and config 4 remain current.
- Parcel targets use dense blue surfaces; previews have blue wire borders, existing Parcels green exposed edges, and invalid/excluded selections red borders. Aim and clicks share the same resolver, including air.
- Player tooltips and confirmations use short purpose/action text. Existing tool descriptions refresh on join, hotbar change and use. Detailed audit facts remain stored separately.
- The user development server was left intact. Verification used the isolated Parcel fixture. The temporary acceptance checklist now contains 30 pending user checks, with all previous outcomes preserved.

## Completed

- Replaced corner-only outlines with sampled wireframes and a 386-point target surface. Cached nearby geometry and draft validation use committed publication/selection state. Each overlay frame is capped at 1536 particles per viewer.
- Sample aim every two server ticks and refresh stable overlays every four ticks. A first-corner selection follows aim until the second corner is chosen; invalid selections remain visible, confirmation requires validity, and completed selections clear.
- Reachable blocks select their containing voxel. Air/out-of-reach rays select beyond occupied camera/body cells. Negative coordinates, vertical directions, exact grid boundaries and head/body straddling are handled; tracing clips before unavailable chunks.
- Corrected two concrete input/geometry edge cases: right-click swing packets also causing left-air input, and tiny trigonometric components selecting a sideways voxel during cardinal aiming on exact boundaries. The latter was reproduced with a failing regression before correction.
- Shortened tool lore, book introduction, claim/Parcel confirmations and Activity/Settlement summaries. Kept useful failure reasons, permissions and durable history unchanged.

## Changed files

- `esmp-plugin/.../settlement/ManagementTools.java` — shared targeting, live drafts, air input, metadata refresh, dense/cached overlays and concise text.
- New `ParcelAim.java`, `ParcelDraft.java`, `ParcelOverlayGeometry.java` — pure ray/validation/particle geometry helpers.
- `SettlementRuntime.java` — committed publication version for renderer cache invalidation.
- `SettlementCommands.java`, `EsmpPlugin.java`, and Civilization `SettlementService.java` / `ParcelService.java` — concise player output.
- New `ParcelOverlayTest.java`, plugin test dependencies and lockfile — six deterministic geometry/ray/preview regressions using existing JUnit versions.
- `ARCHITECTURE.md`, `README.md`, `AGENTS.md`, `.agent/DECISIONS.md`, `.agent/OPEN_QUESTIONS.md`, `.agent/TEST_CHECKLIST.md`, `.agent/HANDOFF.md` — current controls, colors, text convention and verification.

## Verification

- Final `./gradlew build --no-daemon --console=plain` with the existing JDK 25 toolchain passed. Initial test dependency resolution used `--write-locks`.
- Six `ParcelOverlayTest` cases cover air/camera/body targeting, unloaded-chunk clipping, cardinal precision, surface density/edge budgets, concave Parcel edges, and invalid/clipped draft geometry. The cardinal-boundary regression failed on the pre-fix code and passed after correction.
- Live Paper fixture `esmp-parcel-smoke` on loopback 25584/25585 passed actual block target, out-of-reach fallback, left/right air clicks, first-corner live preview, frozen-calendar responsiveness, exact-grid vertical aim, negative coordinates, body/camera boundary straddling, visible red invalid selection with rejected confirmation, and successful blue preview confirmation.
- Decoded real particle packets contained 386 unique blue target-surface positions, intermediate green edge positions and red invalid-border positions. Final smoke output: `.run/parcel-smoke/overlay-smoke-cardinal.log`; packet sample: `.run/parcel-smoke/overlay-particles.json`.
- The fixture's old Minecraft-data dust schema expected float RGB, while the received payload used packed RGB plus scale. A process-local decoder correction enabled zero-error particle inspection; installed client libraries were not edited. Native desktop appearance/comfort remains in the user checklist.
- A broad `/data get` lore query was truncated; checking the specific first lore element confirmed the refreshed text. An Eval string-rewriting issue was reported and corrected with anchored edits. These tooling errors did not change production semantics.
- Touched Java files were formatted with temporary google-java-format 1.36.1, four-space AOSP mode. Documentation link/fence checks passed before final checklist/handoff verification.
- Final report/checklist checks passed: six overlay regressions, 30 pending player checks, valid local links/anchors and balanced fences. Fixture shutdown was confirmed with exit 0; temporary smoke client, formatter and fixture translator JARs were removed. Ignored logs and packet samples remain.

## Next action

Ask the user to try the updated wand visuals and text in the target client, following checklist section 8. Continue Building geometry/evaluation after feedback; keep real currency integration deferred to the user's choice.

## Risks or blockers

- Particle position/color and interaction behavior are verified through the live protocol fixture; native-client visual acceptance remains pending.
- Rendering is bounded and cached; larger selections adapt border sampling and rotate through nearby existing edges. Tune from real client feedback rather than restoring global scans or per-frame full-Parcel analysis.
- Keep input deduplication shared across air-interact and swing events, and preserve cardinal-component cleanup in both ray clipping and the native block trace.
