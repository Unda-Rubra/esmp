# Current Handoff

## State

- Frozen roadmap Phases 0–8 core server gates are implemented/verified. Release marker: `mvp-v1.0`. Native-client/user sign-off is separate: 80 pending checklist items (previous 77 plus three Phase 8 checks).
- MVP+1 remains design-only. §§30.1–30.8 settle rights, regions, property, terminal collection and governance. The user selected continued system Decay of released registered ruins as an unowned mode in §30.9, not automatic retirement solely to stop Decay. Frozen MVP runtime is unchanged; unowned operating rules and the remaining release/coordination design are unresolved.
- One modular JAR, config 14 and existing bridge/schema/dependency pins remain. No Phase 8 migration was added. Artifact version intentionally remains `0.1.0-SNAPSHOT`; the MVP tag is a frozen core acceptance milestone.
- Final JAR SHA-256: `1e87d89fab3c75ea098a34510debce0216cf917859b77991bd715d6f7941ef2f`.

## Completed

- Latest decision: validly released registered whole ruins continue as unowned ruins subject to Decay, preserving identity/history continuity. This applies even when the former Settlement retains other land; it does not add automatic discovery or natural-terrain erosion.
- Unowned authority cannot reuse former Owner permissions, Settlement Activity or stale ownership-bound intents. Existing edit/protection/reconciliation and bounded/natural-load safeguards remain; no old Repair continuation, free repair, restored rights or Era award is implied. Eligibility/rate, edit/baseline handling, takeover and eventual end-of-decay rules remain open.

- Repair preparation now requires Owner/Governor or the explicit administrator path, retaining station/damage permissions. An ordinary Citizen cannot occupy the whole-Building Repair slot.
- Decay and Repair due queries present one candidate per SQL-lane cursor step, matching the runtime's one-attempt-per-poll behavior. Cursor advancement over an eight-row page was insufficient: a continually due first row could hide its siblings. The consumer-shaped ten-Building/job regression now defends actual fairness. Blocked Decay recovery chunks rotate with their durable sequence retained.
- Local Activity contribution SQL filters nearby Building bounds before materializing rows. Eligibility updates no longer decode baseline geometry; actual damage assessment still computes conditions where required. Exact credit-time recovery semantics remain.
- Repair payer verification is read on the Paper thread after the Activity barrier, not from a database completion callback. Existing world/inventory/SQL reconciliation remains intact.
- Corrected Settlement/economy/Treasury help and stale README statements. Added Simplified Chinese `RELEASE.md` with cold upgrade, paired restore, Resource maintenance, measured costs and limitations.
- Retained the verified legacy bridge: the real development copy is config 5 and needs that supported upgrade path. It is not an alternate current runtime.

## Changed files

- Latest decision adds architecture §30.9 and updates related scope sentences in §§30.5/30.7/30.8, README, `.agent/DECISIONS.md`, `.agent/OPEN_QUESTIONS.md` and this handoff. The following code paths describe the prior Phase 8 milestone and were not edited in this decision.

- `esmp-civilization/.../persistence/JooqDecayRepository.java`: eligibility-only credit updates, nearby query and single-candidate fair scheduling.
- `esmp-civilization/.../persistence/JooqRepairRepository.java`: preparation governance and single-candidate fair scheduling.
- `esmp-civilization/.../repair/RepairServiceTest.java`: Citizen/Governor preparation and one-attempt scheduling regressions.
- `esmp-plugin/.../decay/DecayRuntime.java`: skip Repair reservations and rotate blocked recovery chunks.
- `esmp-plugin/.../repair/RepairRuntime.java`: main-thread payer verification.
- `esmp-plugin/.../settlement/SettlementCommands.java`, `.../economy/EconomyCommands.java`: missing help entries/optional target forms.
- `README.md`, `RELEASE.md`, `AGENTS.md`, `.agent/DECISIONS.md`, `.agent/OPEN_QUESTIONS.md`, `.agent/TEST_CHECKLIST.md`, this handoff: current milestone, operator procedure, evidence and next-design boundary.

## Verification

- Final `JAVA_HOME=.tooling/jdk-25.0.4.1+1/Contents/Home ./gradlew clean check build --no-build-cache --no-daemon --console=plain` passed: **141 tests, zero failures/errors/skips**, 30 tasks executed and three up-to-date. Dependency verification/ArchUnit stayed enabled. No Java/schema source changed after that build; subsequent edits only recorded documentation/evidence.
- Both new regressions were observed failing before their fixes. Fairness test consumes only the first candidate per poll, rather than incorrectly treating all returned rows as processed. Existing legacy/empty migration, payment, certification/acquisition, Activity, Building, Decay/Repair and History/Era suites pass.
- Real stopped `.run/dev` data was copied into `.run/phase8-dev-upgrade`, migrated from config 5 to 14 and restarted with protection. Original gameplay rows remain unchanged. Existing identities/Claims/roles/Parcels/ledger/history remain; intended changes are certification columns/grandfathering, economy metadata version and running GameClock. A missing-Claim-table copy stopped before READY instead of exposing Main unprotected.
- `.run/phase8-fresh` began with an empty ESMP database and new world. Protocol clients exercised founding 25/replay, Treasury expansion 50, certification denial/approval/replay, recipient consent then administrator acquisition fee 20/replay, registered roof decay to RUINED, Citizen Repair rejection, partial 3+7 supply, frozen/restart waiting and full Repair to INTACT/Era 1. All 20 applied targets were checked through real server block predicates, including debris air; materials ended escrow zero/consumed ten with one Era award.
- Real Parcel assignment waited for Holder approval. An outsider could not break the Repair Station. Legal station removal revised current design to air while the previous revision retained its crafting table; no Era was added by manual work. Real local actions recovered support to approximately 48.05; advancing another 1000 ticks added no Decay operations and left 32 damaged cells.
- Public API measurements: 10,000 Claim/index-role lookups median 0.948 ms/p95 1.866 ms per group; geometry+codec 4096 cells median 3.223/p95 7.270 ms, 32768 median 15.506/p95 17.003 ms. Activity 256-observation bursts with 100 nearby Buildings: SQL median 43.065/p95 56.562 ms (caps/cooldowns apply). SQLite checkpoint round trip median 0.042/p95 0.091 ms. Saturation behind one held worker admitted 255, rejected 45, drained in 8.578 ms and recovered.
- Actual Paper max-volume capture/permission checks: 20 samples, median 9.002 ms, maximum/p95 17.035 ms. Real authorization-to-finish wall intervals: 17 Decay samples median 46.965/max 56.582 ms; three Repair samples median 51.023/max 52.717 ms. This includes SQL/server scheduling, NOT main-thread CPU time. Result maps were checked at no more than eight cells. Timing was measured during hardening before the final due-page reduction; affected scan/physical paths were unchanged by that reduction.
- Three Resource directories were cold-archived together and recreated by disposable operator loading instrumentation. Three fresh identities were added, old identities retained, and every other gameplay table unchanged. All three realms rejected Claims; inventory remained shared. These are not new ESMP creation/reset commands.
- Paired restore archived the later timeline and restored a funded checkpoint: **132 world/player files byte-for-byte and all 414 SQL rows**. Probe removed. Before payer login, advancing time created no Repair operation and `execute unless loaded ...` confirmed the distant target remained unloaded. Natural arrival then restored every target with ten materials consumed, no duplicated plain inventory and one Era award.
- The final single-candidate artifact restarted and rechecked all 20 targets plus COMPLETED/escrow zero/consumed ten/Era one. After removing compatibility JARs, a standalone deployment with only `esmp.jar` reached READY and protection enabled. Deployed SHA matches the build.
- Evidence remains ignored in `.run/phase8-post-scenario`, `.run/phase8-fresh`, `.run/phase8-dev-upgrade`, `.run/phase8-fail-closed`, paired snapshots/manifests, `.run/phase8-static-audit.txt`, and `.run/phase8-perf/output.txt`. Test probe JARs/source/classes, temporary driver and benchmark source/class were removed. Static audit is explicitly pre-fix evidence, not post-fix proof.
- Harness corrections are not product successes: wrong SQL column/removed command assumptions, RCON broadcast versus reply semantics, PTY input truncation, falling players during floor-breaking setup, and retained Python SQLite handles were corrected before acceptance. Cold file replacement now uses closed/independent readers. Controlled negative tests remain in logs; only the successful cases above count.
- All Phase 8 verification servers and clients are stopped. Final standalone Paper shutdown exited zero; driver termination 143 was intentional supervisor shutdown. No deployed Phase 8 probe JAR remains. Local README/RELEASE/handoff/gate document links were checked with no missing targets.
- Latest work is documentation-only continued unowned ruin Decay under §30.9 with synchronized README/decision/gate/handoff text. No runtime/schema/test-checklist behavior changed; Phase 8 build/Paper evidence above was not rerun for this decision.
- Documentation scope check passed: reversing §30.9 and three scope references reproduces the prior architecture exactly. Frozen MVP/prior rights remain unchanged; the formerly registered scope, separate unowned authority, edit safeguards and unresolved operating rules are explicit.

## Next action

Next freeze unowned ruin eligibility/rate and whether local player Activity can suppress it; then define player-edit/baseline protection and takeover/reclaim. Continue notice/grace/recovery, cross-boundary property, timing/capacity and concrete coordination/storage/API design. Continued unowned Decay is selected, but no automatic-release or unowned-mode implementation is authorized yet.

## Risks or blockers

- Native visual/user acceptance remains pending; protocol-client and server-predicate evidence does not certify native book/particle layout. Current 80 user checks remain unchecked.
- Measurements are bounded local experiments, not arbitrary production capacity. Profile representative dense Parcel layouts, maximum damaged Repair buildings and multi-chunk observation backlog for actual deployment load; preserve queue backpressure.
- Inventory, world saves and SQL are independent stores. Use the cold paired procedure; never delete journals, refund unresolved physical work or restore just one component.
- Resource creation/loading is operator-provided. The final standalone server intentionally does not load Resource worlds itself. Keep entry closed until the chosen loader and evacuation/rollback process are verified.
- User development data was not started/upgraded. No remote push or dependency update was performed.
