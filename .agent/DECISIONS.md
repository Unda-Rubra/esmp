# ESMP Architecture Decisions

This is a dated rationale log, not a competing specification. The canonical authority is now `ESMP MVP ARCHITECTURE 2026-09-12/ARCHITECTURE.md` plus its requirements freeze and strict roadmap. All earlier entries describe pre-freeze decisions/implementation history where superseded, including Owner-wallet expansion and plans for automatic Building discovery. The deleted root architecture is not restored.

## 2026-09-16 — MVP+1 ends active membership and governance at termination

The user selected option 1: end effective Owner/Governor/Citizen relationships, retain history, and invalidate old invitations/unexecuted governance proposals. Canonical details are in §30.8. A landless dormant-team model was not selected.

Rationale: historical identity should not preserve actionable governance after the owned Settlement ends. Stale invitations, transfers, reviews or Parcel requests cannot revive it; committed facts remain historical rather than being rolled back. Invalidation belongs to actual termination, not the beginning of a release notice.

Treasury and Repair refund entitlements survive independently under §§30.6–30.7, without restoring an active role to collect. Other Settlements' memberships are unaffected. Holder safeguards and unresolved material/world obligations cannot be discarded as though they were merely stale proposals.

Documentation only. Runtime/schema/test-checklist behavior is unchanged; Building/Decay lifecycle, concrete terminal storage/API mapping, notice/recovery and timing remain open.

## 2026-09-15 — MVP+1 reconciles Repair before cancelling remaining work

The user selected option 1 for unfinished Repair at Settlement termination. Canonical §30.7 requires safe inventory/world/SQL reconciliation first, then cancellation of unexecuted work. Completed physical repairs and consumed-material accounting remain; cancellation does not itself complete a qualifying Repair cycle or award Era.

Rationale: an otherwise settled unfinished job should not require its payer to return and voluntarily finish/cancel the whole restoration before land can end. This is not permission to discard pending intents: offline payer proof or naturally unloaded world work may still block safe reconciliation.

Unused reconciled materials stay claimable manually by their original payer, not by the last Owner merely because of that role. Durable post-termination entitlement and idempotent/capacity-safe item transfers are required; material escrow is not Treasury currency. After safe cancellation, uncollected refunds alone need not keep land owned.

Only terminal Repair settlement is approved. Notice-stage admission/recovery, active membership/roles, pending proposals, Building/Decay lifecycle and exact command/API details remain open. No runtime/schema/test-checklist behavior changed.

## 2026-09-15 — MVP+1 retains historical Treasury for manual collection

The user selected option 2: leave the balance in the historical Treasury after land ownership ends, with a post-termination collection path for the last Owner. Canonical details are in §30.6. Automatic Wallet payout was not selected. A nonzero balance alone need not block the terminal land transition.

Rationale: preserve funds, account identity and audit history while separating land lifecycle from the player's decision to collect. The final Owner's entitlement must survive removal of active ownership, cannot depend on future Claim ownership, and must not pass to later reclaimers. Collection is a supply-conserving ledger TRANSFER, with existing atomic/idempotent accounting; no issuance, sink or forfeiture policy was approved.

Only Treasury currency disposition is settled. Active membership/roles, proposals, Repair material refunds and unresolved physical/inventory work still need rules. No runtime/schema/test-checklist behavior changed; exact collection commands are not yet implemented.

## 2026-09-15 — MVP+1 may terminate all Settlement land ownership

The user first answered option 1, then immediately corrected it to option 2. The final authoritative choice permits releasing the final Claim or a valid whole remaining group and ending all active Settlement land ownership when every release condition is met. Canonical details are in §30.5; a permanent one-Claim floor was not adopted.

Rationale: permit a complete decline/reclaim cycle instead of leaving every abandoned Settlement with protected residual land. History remains. Holder consent and whole Building/Parcel safeguards still block ineligible termination; a terminal lifecycle is not an alternate way around those rights or permission for ordinary unclaim to remove its last Claim.

Treasury disposition, active membership/roles, pending proposals and unfinished Decay/Repair/material reconciliation still require decisions. No payout, sink, confiscation or deletion of obligations was selected. Documentation only: frozen MVP runtime/schema and existing test acceptance remain unchanged.

## 2026-09-15 — MVP+1 releases whole unassigned Parcels with eligible land

The user selected option 1: an unassigned Parcel may end automatically with an otherwise eligible safe land-release group, provided the entire Parcel is included and it still has no Holders at commit. Canonical details are in §30.4. History remains; no partial Parcel cut or early protection removal at notice is allowed.

Rationale: a permission subdivision without Holders should not itself become a permanent land-release veto. This does not weaken assigned Holder rights: a new assignment invalidates the unassigned assumption and restores §30.1 consent requirements. Whole-building, topology, notice and container safeguards remain in force.

Only this lifecycle boundary is approved. Pending proposals/commit reconciliation, remaining property and Repair accounting, timing, final-Claim and capacity decisions remain open. No runtime/schema or player acceptance changes; ordinary MVP unclaim remains unchanged.

## 2026-09-15 — MVP+1 leaves container contents on released land

The user selected option 1: advance notice and relocation grace, then keep land-local containers and contents in place as land protection changes. Canonical details are in §30.3. Nonempty contents alone do not prevent release; access changes only when release commits and then follows unclaimed/new Settlement permissions. Items left behind may be taken by others: that consequence was explicitly disclosed and accepted.

Rationale: avoid making one nonempty chest a permanent release veto, without introducing automatic item relocation, deletion, dropped loot or a storage service. Player inventories and personal Ender Chest contents are not in scope. Assigned Holder consent and whole registered ruin requirements remain binding.

Only land-local container disposition is settled. Notice delivery/duration, cross-boundary storage, other property/entities, unassigned Parcels, Treasury/final-Claim handling and Repair accounting are not implicitly approved by this choice. No runtime, schema or acceptance-checklist behavior changed.

## 2026-09-15 — MVP+1 admits whole registered ruins as candidates

The user selected option 2: a whole RUINED registered Building may enter the automatic-release process if its claimed footprint can safely exit together without violating Holder rights. Canonical details are in architecture §30.2. Buildings are not split across release steps; a group touching several registered Buildings must satisfy the whole-RUINED rule for all of them. Boundary/topology constraints and §30.1 consent remain binding.

Rationale: permanent exemption of every registered Building would strand registered ruins indefinitely, preventing the intended decline/reclaim cycle. Whole-group candidacy permits that cycle without tearing a Building's ownership boundary apart. Existing physical structures and historical records are preserved, not automatically demolished or erased.

This choice does not authorize public access to container contents, evacuation/storage, unassigned-Parcel removal, timing, final-Claim loss or an implementation. Registration retirement/ownership transition and pending Repair/material reconciliation still need design. The MVP runtime, schema and test acceptance remain unchanged.

## 2026-09-14 — MVP+1 preserves assigned Holder consent

The user selected option 1: retain Holder consent rather than allow timeout-based automatic revocation. Canonical details are in architecture §30.1. Inactivity, absence, notice and grace expiry do not waive consent; parent Claim/Settlement release cannot remove assigned Parcel protection indirectly. Offline disputes remain explicit audited administrator cases, not scheduled overrides.

Accepted tradeoff: protected Parcels can indefinitely constrain boundary retreat; topology safeguards still apply. This preserves the existing rights contract rather than silently converting ownership-related consent into an expiring privilege.

Only this rights boundary was approved. The preceding suggested Local Activity-only scope, omission of Civic Capacity, timing and other MVP+1 release rules were not collectively accepted by that selection. Frozen MVP behavior, including the absence of passive land tax, remains unchanged. Buildings, unassigned Parcels, property disposition and final-Claim handling still need decisions. Documentation only; no runtime/schema/test acceptance changes and no automatic release implementation.

## 2026-09-14 — Phase 8 core server release gates

Kept the frozen architecture and existing balance/schema pins. Hardening addresses defects rather than adding gameplay: Repair preparation now enforces its documented Owner/Governor/admin gate; payer verification reads remain on the Paper thread; Activity eligibility updates spatially filter Buildings and avoid decoding geometry whose damage did not change.

Due scheduling must be fair to unavailable candidates. Each repository presents one due candidate and advances a SQL-lane UUID cursor past only that candidate, matching the runtime's one-attempt-per-poll contract. Advancing past an entire eight-row page could still starve later rows when the first remains due; the consumer-shaped ten-job regression catches that case. Blocked Decay recovery chunks rotate without losing their durable sequence. Physical mutation bounds and saved-world reconciliation remain unchanged.

The real config-5 development-copy migration still requires the verified legacy bridge, so it is retained; deleting it would remove a supported upgrade path. Forward changelogs and the canonical bundle remain untouched. Help/docs now expose the actual optional wallet/Governor history arguments, history catalog and explicit bypass.

Evidence: clean/no-cache check/build passes 141 tests with zero failures/errors/skips. Citizen preparation and first-attempt fairness regressions failed before their fixes. Fresh Paper proved payment/consent/review, Building ruin, partial funding, restart and material Repair/Era; real local Activity recovery stopped new decay without healing. Typed API/SQLite and actual Paper capture timings, limitations and operational steps are recorded in RELEASE.md.

Cold restore reproduced 132 world/player files byte-for-byte and all 414 SQL rows of a funded checkpoint after later edits, material completion and Resource replacement. Without the probe, an offline payer left the distant target unloaded; joining/resuming restored all 20 operation targets, consumed ten materials once and awarded Era once. Resource maintenance replaced all three physical directories together, produced three new identities, retained old identities and left other gameplay tables unchanged.

`mvp-v1.0` is the core server acceptance milestone, not a claim that native visuals/user sign-off or arbitrary deployment load have been validated. The build artifact version remains 0.1.0-SNAPSHOT. MVP+1 release rules must be frozen separately; no automatic release or other deferred system was implemented.

## 2026-09-13 — Frozen roadmap Phase 7 material Repair and History/Era

Implemented the frozen whole-Building station workflow without changing the architecture bundle. One corresponding plain original block item funds each restored material cell; debris removal consumes no item. Named/modified items are excluded. Partial funding waits; cancellation refunds unused escrow without undoing already restored blocks. Default batch interval is 200 shared game ticks. World work remains bounded, naturally loaded, permission-checked and coordinated with Building/Decay publication.

Inventories, world saves and SQL have separate durable progress. Slot before/after intents and a saved player sequence receipt reconcile supply/refund before further inventory use. Repair operations use stable identities and per-chunk revision/sequence markers; terminal SQL jobs still reconcile rolled-back world saves. Conflicts retire only affected cells; renovations and retired baselines win. Refunds cannot race unresolved physical work.

Era starts at zero without invented history. Durable ruin-cycle identity survives a permission pause and awards once only after qualifying Repair reaches INTACT. Ordinary damage repair and manual completion do not award Era; subsequent genuine ruin cycles may award again. Structured History exposes the frozen catalog through existing history reads and the new catalog command.

Config 14 backs up `config-before-repair.yml`; structured `phase7-repair.xml` owns jobs/cells/materials/transfers/operations and Era state/awards. Generated jOOQ ownership, schema verification and public factory callers were updated together. Phase 8 release gates remain separate; no automatic Claim release, rewards, villagers or historical replay.

Evidence: clean/no-cache check/build passed 139 tests with zero failures/errors/skips. Twelve Repair regressions cover funding/retries, refund capacity, conflicts/renovation, permissions/pause, independent recovery, cycle awards and native Phase 6 migration. Isolated Paper exercised real plain/named inventory, partial supply and full ruin restoration; intent, saved-inventory, player rollback, saved-world, SQL-ahead world, terminal completion rollback and partial-world crash cuts recovered without repeated material spending or Era. Controlled exits are test instrumentation, not production features.

## 2026-09-13 — Frozen roadmap Phase 6 durable Decay implemented

Decay is indexed/due work over active registered Buildings, not a world/block timer scan. Footprint support enters decline below 20, recovers at 30, and receives a default 72000-game-tick grace; effect/check intervals are 24000/1200. New/migrated state starts a fresh assessment, not invented prior decline. Contribution-time revalidation records recovery at the exact admitted credit tick and advances a Building-local eligibility revision; short recovery pulses cannot disappear between polls.

The frozen modes use conservative full-cube profiles: explicit WEATHER substitutions, one-block vertical DROP, and same-height connected CAVE_IN of at most four blocks/eight source-target cells within one chunk. No falling entities, collectible drops, generic material conversion or unregistered-terrain changes. Current Owner BUILD permission is checked on both sides; block entities, stations, unsafe attachments/fluids/gravity neighbors and occupied destinations cannot be bypassed. This is a safe limited structural primitive, not general physics.

Complete SQL intent precedes world changes. The shared coordinator reserves the Settlement and drains admitted Activity before authorization; main-thread world/Building/ownership/security/source checks precede each batch. Stable operation identities, exact before/target handling and per-chunk Building-revision/sequence markers reconcile saved world state separately from SQL. Recovery of a chunk precedes new operations for its Buildings. Partial/conflicting work preserves newer blocks; superseded baselines are never physically restored. Material/profile validation also runs when reading persisted intents.

Damage is separate from intended design. WEATHER creates damage but not structural-loss ruin; RUINED defaults to at least 50% of mutable occupied baseline cells becoming air. Activity recovery does not heal. Legal manual restoration clears damage and increments baseline revision even if the intended data itself did not change, preventing old operations from targeting the rebuilt work. No station Repair/material accounting or Era advancement was introduced.

Config 13 and `config-before-decay.yml` ship with structured `decay_state`, `decay_operations` and `decay_damage` tables. Generated table ownership/factory wiring were extended; released baseline changes and the frozen architecture bundle remain untouched. The next scope is Phase 7 only.

Evidence: final clean/no-cache check/build passed 127 tests. Isolated Paper hard exits covered intent, saved-world-before-outcome, SQL-before-world-save and partial CAVE_IN recovery with stable IDs, no duplicate facts, no forced loads and zero item/falling-block entities. The final artifact executed WEATHER/DROP/CAVE_IN through RUINED, stopped on real recovery without healing, preserved an injected post-intent gold conflict, and cleared damage after real manual restoration. A no-probe restart did not reapply superseded damage; chest contents remained seven diamonds. Fault-injection tooling is test-only and removed from deployment.

## 2026-09-13 — Frozen roadmap Phase 5 Building registration and baseline implemented

Buildings are explicitly selected inclusive cuboids within one Settlement. Owner/Governor and BUILD rights across covered space are required; all touched chunks must already be loaded. Default registration volume is 4096, with an absolute configurable ceiling of 32768. Active selections cannot overlap, preventing duplicate future mutation ownership. Resize captures the newly selected current world region under the same identity; retirement retains snapshots/history and permits a new registration. Affected active Buildings must retire before unclaim/abandon rather than becoming silently detached from their Claims.

Storage is version-1 GZIP-compressed palettes in immutable baseline revisions, plus small Building metadata and operation receipts. Snapshots contain BlockData and air/solid/opening/entity traits, never block-entity NBT or inventories. Geometry is derived from the bounded snapshot: occupied cells, flood-classified exterior/interior air, explicit portals and narrow one-cell passages, and simple vertical support links. This intentionally does not claim full physics, perfect room reconstruction or automatic discovery. Current design changes on authorized edits; no damage/Era state or Decay/Repair mutation was added.

Bounded storage measurements supported complete revision snapshots rather than premature fragment/delta infrastructure: 4096-cell solid/shell/checker samples used 81–178 compressed bytes and encode+decode+geometry p95 2.620–3.877ms; 32768-cell samples used 194–985 bytes and p95 16.020–22.033ms. These are synthetic codec/geometry measurements, not a Paper world-scan latency guarantee or universal compression ratio.

Player-edit observations are journaled in chunk PDC and published through the existing Settlement coordinator. SQL revalidates world/ownership/Claim/security/BUILD facts; baseline, revision, receipt, History/outbox and GameClock checkpoint commit together. Stable operation identities and canonical fingerprints make replay harmless after later edits. Recovered pre-capture journals first check their receipt, avoiding resampling a later world state as an older action. Receipt probes and commits are serialized; queue backpressure rejects affected edits before overflow. World/SQL remain separate stores; stale recovery authorization/world/bounds fail closed with the journal retained, not silently overwritten or discarded.

Station metadata refers to an existing captured crafting table, is stored in revision history, and clears when legally removed. The automatic-mutation eligibility filter excludes station coordinates and block entities. This is the Phase 5 placement hook only, not Repair. Config 12 and `config-before-buildings.yml` ship; new tables/bindings are generated from the structured forward changelog. The frozen bundle and released baselines remain unchanged.

Evidence: clean no-cache check/build passed 116 tests. Actual Paper registered a 343-cell shell with 125 interior cells, rejected unauthorized/oversize/outside/overlap/unloaded requests, tracked real stone demolition and gold replacement without losing revision 1, denied an outsider break and preserved station history after removal. Resize retained identity at volume 392. A captured pre-publication chunk journal restored after later edits was safely acknowledged on restart with all 378 non-clock rows unchanged; after retirement/re-registration, another replay retained all 383 rows and the new design. The isolated QA probe only simulated stale journal persistence and was removed afterward; production uses the single ESMP JAR.

## 2026-09-13 — Frozen roadmap Phase 4 Local Activity implemented

Admitted meaningful credit now updates aggregate/lifetime/actor-cap state and the exact Claim bucket in one UnitOfWork. Existing authorization, bans, Parcel rules, cooldowns, PUBLIC limits and aggregate cap semantics are unchanged. Buckets begin at zero; no historical spatial distribution is invented from aggregate scores. The structured forward migration adds only `settlement_local_activity`, with a composite lookup key and Settlement/world foreign keys. Claim removal/abandonment delete affected buckets in their owning transaction; ownership acquisition retains Settlement-local data. This prevents unclaim/reclaim from reviving stale support without deleting History.

Permitted tuning: one-hop cardinal same-Settlement neighbor weight defaults to 0.25 and is bounded 0..0.25; support is capped at aggregate maxScore and the supported threshold defaults to 20. Raw scores inherit the existing seven-day default half-life and admitted credit/caps. Neighbor weight/threshold are read-time interpretation, not new stored scoring semantics. Stored version 1 is checked; unknown versions reject. Config 11 backs up prior configuration. Existing aggregate policy identity and released baseline files remain unchanged.

`ActivityService.localSupport` provides read-only, area-weighted footprint projection, both asynchronously and inside an existing opaque transaction. Weights are block columns per chunk (1..256); all footprint chunks must still belong to the given Settlement/world. Future projection is allowed, but pre-anchor history is rejected rather than inferred. Reads never persist scores or load chunks; footprint-only queries omit far-away Claims and do not derive Regions. Inspection returns ephemeral deterministic four-connected components through an administrator-only paginated command.

The frozen bundle is unchanged: this is Phase 4 execution/tuning, not districts, buffs, Building implementation or automatic release. Phase 5 explicit bounded registration is next.

Evidence: clean no-cache `check build` passed 108 tests, including local spatial/weighted/lifecycle/region/rollback regressions and previous-phase migration preservation. Actual Paper preserved prior rows on upgrade, admitted only real authorized credits, applied bounded neighbor support, froze local writes/decay during a time freeze and projected a seven-day half-life without rewriting stored rows. Restart retained all 360 non-clock rows. With only isolated read-time threshold tuning, two supported components merged after a real middle-Claim action; Claims stayed owned. Final empty-data startup reached READY. Server-side block predicates, not client prediction, confirmed successful actions.

## 2026-09-13 — Frozen roadmap Phase 3 ownership acquisition implemented

Owner proposal discloses the land-only fee before recipient acceptance; acceptance binds that exact amount but never spends or changes ownership. Administrator approval revalidates parties/bans, ownership/Claim/security revisions, every immutable profile and pricing policy, then atomically records recipient Wallet SINK, ownership/membership changes, acquisition decision, History/outbox and GameClock checkpoint. Existing runtime reservation/publication remains authoritative.

Allowed tuning: default coefficient 1000 basis points (10%), rounded upward once using a wide integer base sum. Default expiry is 36000 shared game ticks. Missing profiles reject; changed context requires a fresh proposal, not silent repricing. Proposal ID is the payment receipt ID. Exact approval replay precedes historical-context validation, so restart/later ownership changes cannot double-charge or replay ownership. Denial/cancellation charge nothing; recipient consent remains revocable before approval.

The structured forward migration adds `settlement_acquisitions` and cancels only pending legacy TRANSFER offers. Accepted historical offers and unrelated invitations remain. Immediate-transfer enum/branch/timing API are removed. Config 10 backs up prior config, migrates the old offer duration and introduces the coefficient. Treasury rows/history, certification and personal subsidy usage remain untouched; former Owner becomes Citizen, incoming Owner's membership is removed and old Governor allowances become invalid through ownership revision.

The frozen architecture bundle and released baseline changeSets are unchanged. This is Phase 3 execution and permitted coefficient tuning, not a new acquisition/appraisal/contract system. No Local Activity or Building implementation was added.

Evidence: `./gradlew clean check build --no-build-cache --no-daemon --console=plain` under the repository JDK passed 102 tests with no failures/errors/skips. Actual upgraded Paper rejected premature approval/wrong fee/non-admin review/insufficient funds, then charged one 10-unit SINK and retained Treasury 17/VERIFIED name. The former Owner became Citizen and lost Owner-only authority. Restart preserved all 325 non-clock rows; replay kept Wallet 0 and one transfer fact. Protocol clients showed outsider denial and Owner break responses; the post-restart Owner response was client prediction, not an authoritative server block assertion (clarified during Phase 4). Separate empty-data final-JAR startup reached READY. Native book layout remains player acceptance.

## 2026-09-12 — Frozen roadmap Phase 2 certification implemented

Added lifetime actually credited Activity, the frozen 3-game-day/non-INACTIVE/20-credit default gate, governance applications and authoritative administrator approve/deny. All application/name/state/provenance/history changes commit in the same UnitOfWork and publish through the existing coordinator. Denial preserves land/ownership/balance; approval replay does not duplicate facts or overwrite a later rename. No automatic verification/revocation or new fee.

The old certify boolean API, command and column are removed. Existing explicit naming flags or official names migrate deterministically to VERIFIED, while unnamed/unapproved Settlements remain UNVERIFIED. Lifetime counters begin at zero and only record post-migration awarded contributions; current decayed scores are not retrospective proof.

Corrected the Phase 0 bridge for forward changes: a separate immutable baseline manifest references the same released changeSets; only that baseline is verified/synchronized before applying the full master. Projection loading now follows forward migrations, before gameplay admission. Released baseline files and the frozen architecture bundle were not edited.

Pinned Liquibase SQLite add/drop-column table rebuilds were observed to retain retired columns, lose defaults/nullability/unique metadata, and fail on legacy unnamed foreign keys. Three native SQLite ALTER statements are isolated/documented migration exceptions; structured updates/table/index creation remain the default. Empty/legacy/constraint/default/old-column-removal tests prove the exception rather than weakening data checks.

Evidence: clean no-cache check/build passed 94 tests. A real legacy development copy migrated names correctly with zero invented lifetime rows; original DB bytes stayed unchanged. Actual Paper rejected the old toggle and early/19-point application, accepted after 20 credited points, denied without loss, approved a later application and preserved a subsequent rename across replay/restart. All 282 non-clock rows survived restart. Phase 3 is next; no acquisition/Local Activity/Building code was added.

## 2026-09-12 — Frozen roadmap Phase 1 completed

Every expansion now selects Settlement Treasury regardless of whether the actor is Owner or Governor; founding remains founder Wallet. Current Owner remains the assessed portfolio/subsidy identity. Governor allowance enforcement uses the existing Economy API within the same UnitOfWork; no wallet fallback, new fee or price coefficient change was introduced.

`PlayerLandProgress` makes the existing `claims_used` counter's revision role explicit. Every supported mutation consumes a purchase and increments this counter; first-founding state changes in that same operation. Therefore it is an equivalent monotonic revision, not an additional column that could diverge. Failed/replayed purchases and ownership transfers do not change it. No released Liquibase baseline was edited.

New quote contexts carry a treasury-expansion format version. All prior unpurchased quotes reject safely and can be replaced by a new quote; completed receipts remain idempotently replayable. This avoids destructive quote/data migration and preserves historical wallet-funded payments as history.

Validation: ordinary clean/no-cache check/build passed 87 tests. New cases defend Owner treasury funding, owner-portfolio changes behind Governor quotes, claim/unclaim same-area staleness and old quote/paid-receipt restart compatibility. Actual Paper generated a legacy wallet quote before upgrading, rejected it afterward, rejected treasury insufficiency despite a rich Owner wallet, accepted a funded treasury payment and replayed without duplication. SIGKILL after committed purchase preserved all 369 non-clock rows; restart reconstructed four Claims, blocked a Public break and allowed the same Owner action.

Scope: Phase 1 only. No certification, acquisition, Local Activity or Building code. Phase 2 is next; its first new forward migration must preserve the known Phase 0 bridge baseline rather than synchronize future changeSets as already applied.

## 2026-09-12 — Frozen roadmap Phase 0 implemented

Pinned Liquibase Community 5.0.4, JUnit 6.1.2 and ArchUnit 1.5.0 alongside the frozen runtime/codegen pins. Canonical XML changelogs construct temporary SQLite before official jOOQ generation; dependency locks and SHA-256 verification metadata accompany the cutover. No prices, payer rules or roles changed.

Core UnitOfWork provides an opaque transaction; application façades depend on Repository ports, and only persistence adapters use generated identifiers. Scoped adapter access rejects stale/cross-thread transactions. jOOQ owns atomic transaction/rollback; SQLiteConfig/DataSource owns driver configuration. Existing callback composition no longer exposes DSLContext.

The legacy bridge first runs the existing isolated migration logic, then compares actual SQLite columns/affinities/PK/FK/index/CHECK shape to the canonical Liquibase reference before synchronizing baseline records. Liquibase owns subsequent migrations. REAL/FLOAT spellings compare by actual SQLite affinity, not false incompatibility. Missing/altered constraints reject baseline. Genuine old-database tests remove Liquibase tracking before testing legacy upgrade; tampered current databases are not treated as legacy.

Verified Liquibase 5.0.4 createTable does not emit configured CHECK constraints, while SQLite cannot add them afterward. Existing CHECK/composite/partial-index constraints are documented SQLite-specific baseline exceptions, tested for shape equivalence and invalid-data rejection; ordinary application SQL remains forbidden. jOOQ introspection is configured to retain unknown vendor-function calls (e.g. typeof) rather than failing to parse constraint metadata.

Actual Paper startup revealed service discovery using the server thread loader instead of the plugin loader. Liquibase now runs in an explicit plugin-classloader scope, restored after migration. Both initial failures stopped the server; corrected fresh and legacy-copy startup succeeded.

Validation: clean no-cache build/check with 83 tests; real development database copy preserved 25 of 26 existing non-clock tables exactly (only the retained Economy schema marker advanced 1→2), original DB bytes unchanged. Fresh real Paper exercised wallets/paid quotes/replay, existing Owner-wallet expansion, Governor treasury allowances, consent, bans, real Activity credit and land capture. Restart preserved all 352 non-clock rows and balances/Holder/allowance state. Phase 1 remains unimplemented.

## 2026-09-12 — Frozen MVP bundle replaces the former architecture

The user replaced the root architecture with the frozen MVP bundle and requested a rewrite assessment. All eight text/graph source documents were read in full and the three rendered diagrams inspected. The bundle is preserved unchanged; README/agent navigation now points to it.

Execution resets to roadmap Phase 0, not the prior “certification next” handoff. Existing jOOQ/ledger/paid-Claim/protection work is reusable implementation evidence, not frozen-phase acceptance. Phase 0 must preserve the actual current behavior/schema, including recently added quote/subsidy data; “do not enable payments” does not mean roll already-implemented payments backward. Phase 1 then makes EVERY expansion treasury-funded as explicitly frozen.

Key supersessions: structured Liquibase 5.0.4, JUnit 6.1.2, ArchUnit/verification metadata, opaque UnitOfWork and repository/API boundaries replace the prior public-DSLContext/custom-SQL migration convention. Core domain logic/protection algorithms may be reused, but crossing persistence APIs must be rewritten, not hidden behind aliases.

MVP is explicit bounded Building registration, not global automatic discovery. Local Activity is a required Phase 4 precursor to Building decay; derived ActivityRegion has no persisted/player-managed identity. Only registered Buildings receive WEATHER/DROP/CAVE_IN. Whole-Building station/material/time Repair and exact RUINED→INTACT cycle Era close the MVP. Dynamic Settlement Cycle remains mandatory only after Phase 8 acceptance and MVP tag.

Phase 2 certification requires lifetime meaningful-credit accumulation (currently absent), in addition to age/current Activity; add that dependency in Phase 2 without prematurely implementing Phase 4 spatial buckets. Frozen thresholds are not reopened. Phase-specific mechanics such as repair inventory journaling require correctness design/tests in their own phase, not a new gameplay system.

No implementation, migration, build or server run occurred in this assessment. No phase is marked DONE under the new roadmap. Historical 74-test evidence and 48 pending user checks remain explicitly pre-freeze.

## 2026-09-11 — Paid Claim quotes and persistent personal subsidies

Implemented paid Claims over generated jOOQ bindings and the existing Paper publication gate. `create`, `claim` and `price` request durable quotes; explicit `purchase` commits the ledger SINK, Claim, progress and history atomically. Replays return the paid receipt without another charge; insufficient funds, expiry, missing natural profile and changed context reject.

Selected payer contract: founder/Owner wallet; Governor treasury within existing approved allowance. Assessment and subsidy use belong to the land Owner, preventing Governor rotation from resetting discounts. Unclaim/abandon never refund or reset progress; ownership transfer does not transfer subsidy history. First founding remains fixed 25, requiring a natural profile even though its price is fixed.

Defaults use basis-point portfolio/size/proximity tiers, Manhattan distance to same-owner chunks, active/inactive expansion 1/1.05 and personal factors .50/.65/.80/.90 after first founding. Exact arithmetic rounds upward once. Config 8 removes numeric-price settings; Claim-pricing schema 1 stores quote receipts and personal usage. The explicit development migration counts old FOUNDED/CHUNK_CLAIMED events by recorded actor, without retroactive billing or pretending old Governor actions identify historical owner epochs.

Reason: make currency-backed land purchasing usable without partial payment, stale quote consent or subsidy reset loopholes, while leaving certification/acquisition flows for their own cutovers. Seven real-ledger regressions plus live Paper confirmation/overdraw/replay/unclaim/restart-policy checks passed.

## 2026-09-11 — Completed generated-type jOOQ migration

The user approved the migration. Core world/clock, Settlement/Parcel/Activity/land and Economy application queries now use jOOQ 3.21.8 generated bindings. The SQL worker and physical transaction owner remain unchanged; cross-domain callbacks carry DSLContext and preserve atomic commits.

Canonical module-owned migration resources feed both runtime and disposable build-time SQLite schemas. Generated INTEGER fields are Long, REAL fields Double, UUIDs use an explicit TEXT converter and enums retain explicit mappings. Generated output is ignored/build-owned. No independent query writer, Hibernate/Spring session, generic string DAO or second schema definition was introduced.

Use SQLite-native execution for migration scripts. The initial parser experiment rejected valid SQLite `typeof` checks and `INSERT OR IGNORE`; delegating complete scripts to Xerial preserves native semantics and avoids writing a custom parser. Application DSL remains generated/type-bound; SQLite virtual rowid references preserve insertion-order contracts.

Reason: meet the user's no-raw-application-SQL goal without changing gameplay or resetting stored state. A clean build regenerated sources and passed 67 tests; existing and fresh Paper data paths and exact non-clock row preservation passed. jOOQ runtime/resources/bindings are packaged; codegen/JAXB implementation remain build-only. Paid Claims are still deferred to the next gameplay slice.

## 2026-09-11 — Prefer generated-type jOOQ over handwritten application SQL

The user interrupted paid-Claim development to request Java ORM/framework best practice and less raw SQL. Select jOOQ for ESMP's SQLite-first, explicit-transaction architecture. Its typed DSL and generated schema bindings address stringly typed query maintenance without introducing an entity session, lazy world-thread reads or separate transaction ownership.

Primary-source comparison: [jOOQ supports existing JDBC transactions](https://www.jooq.org/doc/latest/manual/sql-execution/transaction-management/) and [schema code generation](https://www.jooq.org/doc/latest/manual/code-generation/); [Hibernate's SQLite dialect is community-maintained](https://docs.hibernate.org/orm/7.1/dialect/#community-dialects); [Jdbi explicitly retains handwritten SQL](https://jdbi.org/#_introduction_to_jdbi_3). jOOQ runtime/codegen use Apache-2.0 in the [open-source distribution](https://www.jooq.org/legal/licensing); the tested artifact was 3.21.8.

Keep one SQL worker/owning transaction, generated domain-owned table bindings, bound parameters and immutable results. Isolate necessary migration/pragma SQL; avoid raw-SQL wrappers masquerading as an ORM. Canonical migrations feed code generation, with explicit Long mappings for money/ticks. No Spring/Hibernate dependency or automatic schema mutation is selected.

An isolated Java 25/SQLite 3.53.4.0 run with the actual WorldStore passed transaction and integer-safety checks. The runtime migration has not shipped. Removed only the interrupted paid-Claim draft changes; the last working 53-test baseline is restored. Next action is the jOOQ persistence cutover before paid Claims.

## 2026-09-11 — Immutable natural land capture

Implemented canonical §16A.3 with a minimal Civilization-owned profile keyed by physical world instance and chunk. Capture only a newly populated MAIN chunk while the runtime is active; old chunks without provenance remain unknown, including pre-activation spawn terrain. Other dimensions are excluded and current player-modified terrain is never silently backfilled.

Version 1 uses 16 fixed surface samples: base 100 minus height-relief×2 capped at 50, plus 25 for mixed water/land samples. All-water chunks receive no shore bonus. This deliberately begins with two explainable natural factors, not a biome/real-estate simulation. Coefficients are configurable but locked to a version; new versions affect future captures only.

Chunk PDC holds the original instance-bound capture as recovery provenance before asynchronous SQL publication. SQL remains the owning application record; later paid operations must require that record. Replays are idempotent, conflicting immutable values reject, and missing journal plus missing SQL remains unknown. World saves and SQL cannot be one transaction, so no stronger crash/backup guarantee is claimed.

Reason: make natural values durable before paid Claims without global scans, chunk forcing, reinterpretation of edited terrain or speculative production-server backfill. `/settlement landvalue` and a book link expose known/unknown state; Claims still use their old numeric-only quotes. Land-value schema 1/config 7 are introduced; other schema versions remain unchanged.

## 2026-09-11 — First-time-player starting funds

The user clarified that this is not a production server and the fund is only for newly joined players. Do not reopen a legacy-player distribution questionnaire. Paper's join-time `!hasPlayedBefore()` observation enrolls the UUID; returning players only resume an existing entitlement.

Default grant is configurable 100. Persist its original amount and deterministic transaction identity; admission, wallet credit, issuance budget consumption and completion receipt commit together. Insufficient budget defers to a later monetary period without partial payment or extra issuance. Bounded indexed retries include offline players; configuration changes do not alter pending amounts, and unaffordable entries advance their retry period rather than blocking the queue.

Use the existing stable console/system actor and STARTING_FUNDS reason. On payment, show amount, currency and committed final balance with a balance link. Existing players are not backfilled. Economy schema 2 adds the entitlement table; config 6 adds the amount and preserves the config-5 backup.

Reason: deliver the planned first-join behavior without inventing production migration policy, passive/AFK minting or a second monetary path. Ledger, quoted names, governance and time semantics remain intact. Next slice is generation-time LandValueProfile capture, not more onboarding policy.

## 2026-09-11 — Natural spaced names and readable status time

User explicitly requested interior spaces, trimming only leading/trailing whitespace, quoted command arguments and natural unquoted Name Tags. Shared `LandName` normalization now serves Settlement persistence, Parcel proposals and previews; interior spaces are preserved, not collapsed. Existing length, case-insensitive uniqueness, certification and consent rules remain. Generated links/approval commands use stable identities.

Live evidence showed Bukkit's legacy argument splitting collapsed repeated spaces inside quotes even though the raw player command retained them. `QuotedCommandListener` therefore parses raw quoted ESMP commands before that split, then invokes the registered command with normal permission checks. Other plugins' commands and cancelled events are not intercepted.

The user could not see useful time in `/esmp status`; its implementation buried a raw `Game tick` line at the bottom. Time now appears immediately after readiness with zero-based reckoning day/progress/total ticks and advancing/paused state, plus separate MAIN solar HH:mm. This is readable existing-clock output, not a new calendar or a change to time accounting.

## 2026-09-11 — Implemented bounded integer ledger

Implemented canonical §16A.2 in `esmp-economy` with the shared serial SQL transaction owner: checked non-negative integer accounts, typed movements, immutable audit receipts, stable-ID retries and budget consumption committed together. Exact aggregate supply uses wider arithmetic rather than overflowing SQLite/Java sums.

Selected initial policy: seven reckoning days, operator-selected coarse issuance tiers 2000/5000/10000/20000, per-period limit snapshots, no carry-over or skipped-period entitlement. Administrative grants are budgeted immediately. A deterministic per-currency/player starting-grant ID prevents repeated issuance; automatic new/legacy-player eligibility remains deliberately unconnected.

Treasury spending uses current Civilization authority on the same SQL connection, not a cached role. Owner withdrawals and Owner-set Governor remaining allowances are separate; ownership revision/promotion identity invalidate stale delegation. Positive treasury balances prevent abandonment. Administrators can inspect/issue but cannot debit another player's wallet.

Reason: make the first ledger usable without silently charging old Claims, enabling alt/AFK minting, inventing a credit system or deciding legacy onboarding eligibility. Wallet/treasury commands and book links ship now; paid land, new certification and acquisition remain explicit later cutovers. Core/Civilization schemas stay 1/3; Economy schema 1 and config 5 are new.

## 2026-09-11 — Accepted economy addendum and razor review merged

Source: the user-supplied `ARCHITECTURE_ADDENDUM.md`. Its final §§21–23 supersede earlier provisional variants. Accepted rules now live in `ARCHITECTURE.md`; the source file is not a second maintained architecture.

- Currency is selected: one scarce integer ledger unit, Wallet/Treasury accounts, non-negative balances, stable transaction IDs and TRANSFER/ISSUANCE/SINK distinctions. Every issuance channel is budgeted, including approximately 100 starting funds. Peer transfers conserve supply; no jobs minting, passive tax, negative-balance debt or hard wealth cap.
- LandValueProfile is minimal, immutable/versioned and captured at first Main chunk generation. Claim prices combine natural base value, personal portfolio, Settlement size, light Activity and personal early subsidies; same-owner proximity applies only to separate founding. First-ever founding costs 25; subsidy history never resets or transfers.
- UNVERIFIED/VERIFIED certification replaces the removed four-level economic ladder. Activity/development eligibility permits application, administrators approve, then official names/public named listings become eligible. Denial retains ordinary Claims/governance.
- Incoming ownership acquisition requires recipient acceptance, simple land-based fee and administrator approval. No historic-price/subsidy anchoring or Building/labor appraisal; private bargains remain social and treasury remains Settlement property. Complex acquisition automation and receiving-capacity tiers are deferred.
- Core reduction: obvious Building identity or retire/rediscover with lightweight links; station/material/time Repair; DROP/CAVE_IN; lightweight Era/history. Villagers, TOPPLE, cultural ranks, production capacity/offline/workforce/depot simulation, freight, teleport and gameplay seasons are deferred.
- Server Trade follows the core Dynamic Settlement Cycle: finite normalized categories/lots, quotas/budget admission, periodic discrete bands/spread and observational monitoring. Continuous formulas, hysteresis and automatic policy require later evidence. Resource operations may start with safe operator procedures before automation.

Execution: integer transaction/account/budget foundation → starting funds and balance feedback → generation-time valuation/legacy backfill → paid Claims and personal subsidies → certification/admin acquisition → Building → durable Decay/Repair/History → required Dynamic Settlement Cycle → Server Trade/lightweight recognition → reset automation → optional advanced systems.

Reason: support scarce meaningful exchange and land expansion without placing a broad simulation economy in front of ESMP's defining persistent settlement/ruin/reclaim loop. Global budget enforcement is placed with first issuance, not postponed until the later Server Trade phase.

All 18 source merge requirements are reflected in the canonical document:

| Source checklist item | Canonical destination |
|---|---|
| 1. Replace area-only Claim pricing | §§7.6, 16A.3–16A.4 |
| 2. Two-state certification and naming | §§7.3, 7.6 |
| 3. Remove economic stages | §§16A.10, 25 |
| 4. Defer Cultural Standing | §§16A.10, 26.5 |
| 5. Lightweight Facility first | §§16A.10, 26.4 |
| 6. Optional villagers for Repair | §§11–12 |
| 7. Best-effort Building lineage | §§9.2, 26.3 |
| 8. DROP/CAVE_IN before TOPPLE | §§9.2, 25 |
| 9. Defer freight/public teleport | §§16A.10, 27 |
| 10. Retain clock, defer gameplay seasons | §16A.6 |
| 11. Operator Resource lifecycle first | §15 |
| 12. Defer peer receiving capacity | §16A.5 |
| 13. Admin-approved acquisition and simple fee | §§7.4, 16A.5 |
| 14. Periodic discrete Server Trade bands | §16A.8 |
| 15. Observational economic monitoring | §16A.9 |
| 16. Minimal immutable valuation fields | §16A.3 |
| 17. Outbox without speculative classes | §19 |
| 18. Dynamic cycle immediately after core | §§26.1, 27–28 |

Runtime distinction: this is architecture/planning acceptance only. Existing numeric quotes, direct naming toggle and two-party transfer remain implemented behavior until their migrations ship. Preserve prior acceptance results; do not mark any new ledger/grant/fee/certification behavior implemented by this merge.

## 2026-09-11 — Concise player text and responsive Parcel overlays

The user rejected developer-style disclaimers in player text and requested clear blue/green/red Parcel visuals with a facing-direction air target.

- Replaced verbose tool lore, claim/Parcel confirmations and Activity summaries with short action/purpose text. Existing tool metadata refreshes on join, hotbar change and use; stored tool identity and audit data are preserved.
- Reachable block hits select the containing 8×8×8 cell. Air/out-of-reach targeting follows the camera ray beyond camera/body occupied cells, including negative coordinates, vertical directions and voxel-boundary straddling. Trace reach stops before unavailable chunks. Rendering and click handling use the same resolver.
- Blue dense target surface and preview wireframes, green existing Parcel edges, red invalid/excluded selections. Invalid geometry remains visible but uncommittable; live preview follows aim after the first corner and confirmation clears the selection.
- Poll at two server ticks; redraw changed targets/drafts immediately and otherwise refresh every four ticks. Bound each overlay frame to 1536 particles; use cached publication/selection-dependent validation and nearby exposed edges. This is UI refresh, independent of paused calendar progression.
- Actual air-click testing exposed a right-click swing also generating left-air input. Deduplicate that input so completing a selection preserves its first corner.

Reason: make the wand's target and selection outcome obvious without filling the player's current view with an air-target cube or mixing developer status into gameplay feedback. Geometry/ray regression tests and a live particle-packet fixture verify the implementation; native desktop appearance remains a user acceptance check.

## 2026-09-10 — Semantic tool requests under the ESMP root command

The user corrected the desired spelling to plural `/esmp tools`. Use `parcel_wand` and `management_book`, not material names. Remove the prior Settlement issuance route; do not add singular or material-name aliases. Update root/subcommand completion, help and management-book links together.

Root registration no longer globally requires `esmp.admin`: player tool issuance checks the existing default-enabled `esmp.settlement` permission and readiness, while status retains its administrator check. Console tool requests are rejected. Persisted item tag values remain unchanged because this is a command-interface cutover, not an item-data migration; old tools still work and count for duplicate prevention.

Reason: make server-provided tools discoverable under a semantic, native ESMP command without accidentally restricting ordinary players or broadening administrator diagnostics access.

## 2026-09-10 — Settlement/Parcel implemented with numeric-only claim pricing

The user explicitly postponed real currency: use numbers now and do not consume currency/items until a currency system is selected. This supersedes the earlier immediate real-ledger requirement. Claim price is configurable base + step×current area (initially 100 + 25×area); committed history records the value and `charged=false`. No mock wallet, funding, deduction or refund is introduced.

- Migrated active roles/schema to Owner/Governor/Citizen/Public while preserving UUIDs, offers, history, scores and caps. Civilization schema is 3, config 4. Added stable public numbers and S#number labels; existing names are grandfathered, new names require administrator naming certification and approval.
- Implemented local bans, relation-set Parcel policy, world-aligned 8×8×8 cells, six-face connectivity, exclusion/height checks and immutable committed spatial publication. Current bounds: 4096 cells, 128 Parcels/Settlement, 32 Holders and 64 pending proposals/Settlement.
- Holder is independent from membership. Assigned geometry, Holder, policy and name changes require governance plus all current/new Holders; proposals survive restart, expire at 24000 game ticks and validate Parcel/ownership/chunk/security revisions. Administrator overrides are auditable. Parent claim release cannot bypass Parcel consent.
- New Parcel defaults grant Owner/Governor/Citizen/Holder all four domains and Public none. Unparcelled Public access remains safe controls. Unattributed internal automation uses Holder grant when assigned or Owner grant otherwise; crossing distinct protected boundaries requires Public automation permission on both sides. Player/container access is separately authorized; bans revoke open protected inventory access.
- Issued PDC book/stick tools carry no authority. Book pages link current command services. Stick+named off-hand Name Tag or equivalent position commands produces bounded 1200-tick previews, then commits through the same service. Tool issuance is limited to one of each in inventory plus a short game-time cooldown; crafting/anvil/grindstone conversion is blocked.
- Activity now captures block coordinates/action/security revision and rechecks bans and Parcel relations. Non-Citizen Holder/Public credit remains small (.02, daily cap .25) rather than becoming team credit; existing balances/caps remain intact and equivalent `visitorCredit` policy nomenclature migrates to `publicCredit`.

Reason: implement usable spatial governance and safe consent now without pretending a real economy exists. Preserve the user's currency decision for a later integration. Six new domain regressions and isolated live Paper workflows covered geometry, consent, stale/expired approvals, migrations, real tool interactions, Public Holder access, bans, role/naming persistence and two-sided hopper authorization. Native client visual layout remains a manual acceptance check because the older protocol test client cannot reliably decode current particles.

## 2026-09-10 — User-authored MVP revision supersedes the previous immediate plan

Source: the user's revised `ARCHITECTURE.md` §§7–11 and 23–28. This synchronization changes plans and notes only; the existing JAR still uses its implemented legacy roles/free claims/naming. Do not advertise an unimplemented migration.

- **Next priority:** paid claim transactions and pricing, governance migration/bans, Parcel storage/authorization/commands, and convenience tools/naming administration precede Building identity/geometry. Build only the necessary real currency/accounting dependency for paid claims, not the full market/Facility economy. Payer, initial funding and exact price/recovery policy remain implementation gates.
- **Claims and naming:** creation/expansion consume currency with rising marginal area cost; full-height chunks and contiguity remain. Stable numeric identity/generated labels precede optional official names. Certification and administrator approval control naming; every later rename is also administered and recorded. Existing UUID/name/history migration requires an explicit policy.
- **Governance and bans:** Owner/Governor/Citizen/derived Public replaces legacy Manager/Member/Visitor in the target. Owner stays solely in `owner_uuid`, Governors/Citizens in membership; former Owner becomes Citizen on transfer. BANNED is a local negative override; Owner/Governor can ban non-administrators from governed interactions, not movement/information. Server administrators bypass bans.
- **Authorization:** administrator override → ban → world/Settlement → Parcel → applicable policy → relation set. A player can be both Citizen and Holder; any allowed applicable relation can authorize unless an earlier negative override denies. Parcel policy replaces ordinary default policy, never grants governance. Only BUILD/INTERACT/CONTAINER/AUTOMATION are exposed; both sides of cross-boundary automation must authorize.
- **Parcels:** world-aligned 8×8×8 cells within one Settlement, no overlap, six-face connectivity, unique local names. Zero/multiple Holders are valid and Public can hold. Assigned revocation, Holder removal and reshaping require governance plus all current Holders; administrators resolve disputes. No rent/contracts/debt subsystem.
- **Management:** command-complete application APIs are authoritative. Disposable/replaceable Management Book and Parcel Wand are frontends, never authority tokens. Wand plus named off-hand Name Tag proposes unparcelled cuboid cells, previews before atomic commit, and must preserve connectivity. Surface-cell removal also preserves connectivity; assigned geometry requires consent.
- **Building and Repair:** geometry includes occupied/exterior/interior volumes, openings and support relationships. Local renovation may conflict/retire restoration fragments without erasing history; air at an old wall position is not sufficient permission to restore through a new interior. MVP Repair is whole-Building externally and bounded internally. Block entities require special safety policy; system decay yields no ordinary collectible drops. Coarse profiles and DROP/TOPPLE/CAVE_IN are initial candidates, not full physics.
- **Wild/natural evolution:** ordinary wild construction uses coarse, localized due work; important structures alone justify rich persistent tracking. Roads/rails may use lightweight segments. Coarse Anthropogenic Landforms are an accepted later direction, with 8×8×8 preferred initially; adaptive refinement, regeneration and disaster/Epoch systems remain deferred/open.
- **MVP boundary:** no inactivity/ruin-driven automatic Settlement release. Owned protection persists until explicit authorized lifecycle action. Dynamic cycles are the required next major phase immediately after stable MVP, not optional: Civic Capacity/maintenance demand, old/new-city incentives and gradual peripheral release are candidates whose formulas, Holder guarantees and reclamation behavior remain unresolved.

Reason stated by the revised architecture: prove permissions, Parcels, Building geometry, Activity and recovery first; preserve history and player renovations, keep MVP mechanics bounded, and defer dynamic ownership loss without abandoning the long-term civilization cycle. Existing detector research remains useful for the later geometry/evaluation slice, not the immediate implementation priority.

## 2026-09-09 — Civilization and Settlement vocabulary

- **Civilization** means the persistent Main World gameplay environment/domain.
- Civilization is not a player-owned aggregate or planned database table.
- **Settlement** is the Main World aggregate for ownership, team membership, permissions, activity, and claimed chunks.
- A solo owner is valid; a separate one-member Civilization entity is not needed.

Reason: the project models player claims and historical settlements, not nation-like Civilization membership.

## 2026-09-09 — Settlement claims

- Claims use whole Minecraft chunks.
- A settlement may own many chunks; a player may own multiple settlements.
- Every owned Settlement forms one four-directionally contiguous chunk component; diagonal contact alone is insufficient.
- A new chunk must share an edge with the existing component.
- Unclaiming a chunk must fail if it would split the remaining component.
- Ordinary unclaim rejects removal of the last chunk for every role, including Owner. The Owner must explicitly abandon; Managers cannot implicitly abandon through unclaiming.
- Two Settlements may occupy edge-adjacent chunks without merging, including Settlements with the same owner.
- Adjacent Settlements retain separate permissions, teams, activity, history, and lifecycles.
- Settlement chunk claiming is initially free, with no currency or material payment.
- Configurable `max_chunks_per_settlement` defaults to 64 chunks and is enabled by default; server policy may change or disable it.
- The cap applies separately to each Settlement, not to the owner's total claimed chunks.
- Claims cannot cross physical world instances.
- Settlement chunks are exclusive within a world instance.
- Main World claims protect blocks and interactions.
- Settlements support an owner, members, roles, ownership transfer, and settlement-local permissions.
- Protection includes indirect mechanics such as explosions, fluids, fire, pistons, and hoppers.
- Decay and Repair act as explicit system actors and revalidate before world mutation.

Reason: contiguity gives each Settlement one coherent place and keeps activity, history, release, and visualization local. Adjacent Settlements remain distinct because spatial contact does not imply shared ownership or identity.

The user selected free claiming and a default-enabled size cap. Reason: bound each Settlement's expansion without introducing claim pricing or an economy dependency.

The user selected 64 chunks as the default cap. This permits a 128 × 128-block area if arranged as an 8 × 8-chunk square; square shape is not required. The limit constrains individual Settlement size rather than total player ownership.

Reason for the last-chunk restriction: an owned Settlement must remain non-empty, and operational claim permissions must not bypass the Owner-only abandon boundary.

## 2026-09-09 — Abandonment confirmation

The Owner must separately confirm abandonment after a warning identifying the Settlement and clearly stating that claim protection will be removed. Showing or dismissing the warning does not change ownership or protection. Revalidate current ownership and the target before applying the confirmed operation.

The confirmation lifetime defaults to **60 seconds** and is configurable. Any ownership or claimed-chunk change invalidates the confirmation and requires a fresh warning, including changes later reversed. Bind the warning to ownership and claim revisions and validate them, together with expiry, atomically when abandoning.

Reason: removing protection is consequential and must not happen through an accidental initial command.

## 2026-09-09 — Architecture discussion scope

Prioritize gameplay boundaries, aggregate ownership, and major lifecycle decisions. Defer fine-grained command UX, exhaustive event catalogues, and minor operational settings to implementation unless they affect a core invariant. Ask related architectural questions in batches rather than requesting confirmation for every implementation detail. Existing accepted decisions remain valid.

## 2026-09-09 — Fixed Settlement roles

- Each owned Settlement has exactly one Owner.
- A Settlement may have zero or more Managers and zero or more Members.
- Every player who is not the Owner, a Manager, or a Member is a Visitor.
- Owner identity is stored only as `Settlement.owner_uuid`.
- `SettlementMember` stores only `MANAGER` and `MEMBER` rows.
- Visitor is derived and is not persisted as membership.
- Settlements cannot define custom roles.
- A player's effective role is evaluated separately for each Settlement.

Reason: a fixed hierarchy keeps authorization, commands, persistence, and vanilla-client UI predictable while still supporting group ownership workflows.

## 2026-09-09 — Former Owner after transfer

The user selected Member as the former Owner's role after successful ownership transfer. The former Owner keeps collaborative access but loses administrative authority. Ownership and membership updates commit atomically; the new Owner is represented only by `Settlement.owner_uuid`.

## 2026-09-09 — Ownership transfer acceptance

The nominated recipient must explicitly accept ownership transfer. A pending offer changes no ownership or permissions. At acceptance, revalidate the initiating Owner and offer validity before committing the transfer.

Reason: ownership must not be assigned unexpectedly or transferred through an offer whose initiating Owner no longer has authority.

## 2026-09-09 — Voluntary Settlement departure

Members and Managers may leave without approval and become Visitors when membership removal commits. Claims and other players' roles remain unchanged. Manager self-withdrawal does not grant authority over other Managers. Owners must complete ownership transfer or use the abandon operation instead of ordinary leave.

Reason: membership is voluntary, while the exactly-one-Owner invariant must remain intact.

## 2026-09-09 — Settlement invitation acceptance

Players must explicitly accept an invitation before joining as Members. Pending invitations grant no membership or additional access. Only the invited player may accept; acceptance revalidates invitation validity and the inviter's authority without overwriting an existing role.
Reason: joining a Settlement requires consent, and sending an invitation alone must not grant protected access.

## 2026-09-09 — Automatic membership invitation expiry

Membership invitations expire automatically after a configurable duration, defaulting to **24 hours**. Store `expires_at` when issuing an invitation and reject acceptance at or after that timestamp, independently of background cleanup.

Reason: pending invitations must not retain validity indefinitely, and delayed cleanup must not allow expired invitations to grant membership.

## 2026-09-09 — Transfer expiry and offer cancellation

Ownership-transfer offers expire automatically after a configurable duration, defaulting to **30 minutes**. Their persisted expiry is checked at acceptance, independently of cleanup.

The Owner or any Manager may cancel any pending membership invitation in their Settlement. Only the current Owner may cancel ownership-transfer offers. Recipients may decline their own invitations or transfer offers. Acceptance, cancellation, and decline compete for one atomic pending-state transition; no terminal offer can later grant access.

Reason: a short ownership handover window limits stale offers; role-based cancellation allows operational deputies to manage invitations without ownership powers. Recipient decline preserves consent.


## 2026-09-09 — Ownership transfer recipient eligibility

The recipient must already be a Manager or Member of the same Settlement at both offer creation and acceptance. Visitors must join first. Revalidate membership at acceptance and reject a transfer to a recipient who is no longer eligible.

Reason: ownership transfer should go to an existing collaborator, not bypass the Settlement's membership process.

## 2026-09-09 — Fixed role capability matrix

- Owner has every Settlement capability, including Manager promotion/demotion, ownership transfer, and abandon/release.
- Managers are operational deputies. They can claim/unclaim, manage ordinary Members, change ordinary settings, build, interact, use protected resources, and start repair.
- Managers cannot promote/demote Managers, transfer ownership, or abandon/release the Settlement.
- Members are standard collaborators. They can build, break, interact, use containers, interact with protected entities or machines, and start repair.
- Members cannot change claims, membership, roles, or settings.
- Visitors may enter, observe, and use safe public interactions such as doors, trapdoors, fence gates, buttons, levers, and pressure plates.
- Visitors cannot build, break, use protected containers, alter persistent inventories, interact with protected entities, or directly operate protected machine interfaces.
- Visitor protection is action-specific. ESMP does not switch their real game mode or cancel every right-click.
- Normal redstone consequences from an allowed public control remain allowed.

Reason: Managers need enough authority to operate a Settlement without receiving irreversible ownership powers. Members need ordinary collaborative gameplay. Visitors should feel adventure-like rather than blocked from every interaction.

## 2026-09-09 — Removal, promotion, and demotion

- Authorized removal needs no recipient approval and makes the removed player a Visitor on commit. Owner may remove Members or Managers; Managers may remove ordinary Members only. Membership removal cannot remove the Owner.
- Owner may offer Manager promotion to an existing Member. Only that Member may accept, and pending promotion grants no new powers. Acceptance revalidates current ownership, offer validity, and the recipient's Member role.
- Manager promotion offers expire automatically after a configurable duration, defaulting to **24 hours**. Persist expiry at creation and enforce it at acceptance independently of cleanup.
- Only the current Owner may cancel a promotion offer; the nominated Member may decline. Acceptance, cancellation, and decline share one atomic pending-state transition; terminal offers cannot grant Manager permissions.
- Owner may demote a Manager to Member without approval; the change applies on commit and preserves membership.

Reason: granting administrative responsibility requires consent, while authorized revocation must not depend on the affected player's cooperation.

## 2026-09-09 — Claim visualization

- Use vanilla-client temporary particles for nearby chunk boundaries.
- Use ActionBar or chat components for settlement, owner, role, and status.
- Visualization is temporary, per requesting player, and must not mutate blocks or scan a whole settlement.

Reason: preserve the vanilla-client-first UI direction.

## 2026-09-09 — World identity

- A stable `WorldDefinition` identifies a configured role such as `MAIN` or `RESOURCE_NETHER`.
- A `WorldInstance` identifies one physical Bukkit world incarnation.
- Coordinate-sensitive records use `world_instance_id`.
- A Resource reset creates new world instances.

Reason: configured names may be reused after reset; stale records must not target a later cycle.

## 2026-09-09 — Cross-realm transfer

- ESMP permits free transfer of items and normal player state between Main and Resource realms.
- Inventory, hotbar, equipment, offhand, Ender Chest, and experience remain shared.
- ESMP does not implement per-realm inventories or contraband filtering.

Reason: the Resource Realm exists to supply the other realms, and Paper normally preserves the same player's data across worlds. Recording this explicitly prevents later ESMP or third-party configuration from changing the intended economy.

## 2026-09-09 — Settlement lifecycle

- Settlement ownership and physical condition are separate state dimensions.
- `Founded` and `Reclaimed` are events, not permanent states.
- Reclaiming land restores protection under a new ownership epoch but does not automatically restore ruined buildings.
- An owned ruined settlement remains protected until a valid release completes.

## 2026-09-09 — Automatic Building discovery and revisions

- An algorithm discovers Buildings automatically within player-claimed Settlements. No player registration or candidate confirmation is required.
- Each tracked Building belongs to one Settlement and its footprint stays inside that Settlement's claims. A cross-Settlement physical structure cannot be one tracked Building.
- Authorized player building edits automatically update affected restoration-baseline data. ESMP decay and repair do not redefine the baseline or fold unrelated damage into it.
- Authorized demolition changes the intended target; repair is not a rollback of player edits.
- Discovery is localized and budgeted, including existing construction in newly claimed land. Stable Building identity must survive ordinary rediscovery.

Reason: the user selected automatic discovery and revisions to avoid manual setup, while choosing one Settlement per Building to keep permission and activity authority unambiguous. Detection algorithms remain open; inherited-baseline policy is settled in the reclaim decision below.

## 2026-09-09 — Activity, recovery, and release boundaries

- Team participation is the primary activity source. Meaningful Visitor interactions contribute very little; passersby contribute nothing. Visitor activity does not expand Visitor permissions.
- Sufficient activity recovery stops further decay. It does not heal damage or advance Era; repair remains necessary. Pending decay must revalidate activity eligibility before mutation.
- Owners may voluntarily abandon intact or active Settlements through the confirmation flow. Automatic release requires both inactivity and ruin conditions.
- Release preserves history and does not itself damage or restore buildings. Scoring weights, recovery thresholds, and ruin aggregation remain deferred.

Reason: public use should count modestly without treating traffic as upkeep. Returning participation prevents further decline without bypassing repair. Voluntary release expresses owner intent; automatic release needs evidence of abandonment.

## 2026-09-09 — Reclaim ownership and inherited baselines

- Released land may be reclaimed chunk by chunk into a new or existing Settlement, subject to normal contiguity, exclusivity, world-instance, and size-cap rules.
- The claiming Settlement's current team and permissions apply. Former teams, permissions, and owning Settlement identity are not automatically revived.
- Building and site history survive. The active restoration baseline is newly established from the inherited structure's current state, not its former intact target.
- Reclaiming does not restore blocks or advance Era. Old ownership/baseline jobs cannot mutate reclaimed structures without revalidation.
- Partial reclaim retains historical linkage but must respect one Settlement per active tracked Building; exact split/merge bookkeeping is deferred.

Reason: the user chose ordinary claiming and current-team authority, with historical continuity but a fresh repair target based on the ruins the new occupants inherit.

## 2026-09-09 — Loaded-chunk mutation and recovery

- Decay and Repair wait for naturally loaded chunks; they do not load or retain chunks solely to progress. Pending jobs survive restarts.
- Preserve concurrent player edits and replan affected outdated work from current authorized state. Unaffected valid work continues without a manual whole-job restart.
- Persist operation identity, expected state, context, and progress; revalidate on the world execution context before applying bounded changes.
- SQL commits and Minecraft world saves are independent. Recovery reconciles interrupted or ambiguous work rather than assuming exactly-once mutation or blindly replaying.
- Material accounting and durable completion effects must be idempotent; retries cannot duplicate charges, history, or Era advancement.
- Record durable domain events with the owning state in a transactional outbox, deliver after commit at least once, and deduplicate consumer effects.

Reason: the user selected natural loading to avoid background chunk-loading cost and localized replanning to preserve edits without stopping all work. Durable job and event boundaries protect these semantics across failures; detailed storage and checkpoint mechanics remain implementation work.

## 2026-09-09 — Single modular plugin and SQLite

- Deploy one Paper plugin JAR with one bootstrap and coordinated lifecycle. Core, Civilization, and Resource remain internal modules with explicit domain boundaries.
- Use embedded SQLite for initial application state, durable jobs, and transactional outbox records. Domains own repositories and schemas; Core owns database infrastructure.
- Coordinate short writes for SQLite's single-writer model; keep database I/O off the world execution context and Paper world access out of database workers.
- Versioned migrations precede dependent gameplay enablement. Exact driver, schema, migration library, and build tooling remain implementation choices.

Reason: the user selected simpler single-server installation and operations without discarding modular architecture. Separate plugin artifacts and a separate database service are unnecessary initially; transparent backend portability is not promised.

## 2026-09-09 — Shared Resource cycle with advance preparation

- Resource Overworld, Nether, and End reset together as one cycle, each with a fresh physical instance.
- Generate and pre-generate all replacements while the old cycle remains open. Replacement worlds are not publicly accessible before activation.
- Only after all replacements are ready: close entry, evacuate, unload old worlds, switch the shared cycle and routing, and reopen. Never admit players into mixed cycles.
- Persist cutover progress and reconcile world readiness after restart. Preparation failure leaves the old cycle serving players.
- Retired-world references cannot route players into deleted instances or reinterpret old coordinates. Archive/delete occurs after cutover under a separate retention policy.

Reason: shared cycles simplify routing and generation identity; advance preparation reduces the closed-access window at the cost of temporary storage and generation load. Exact schedules, evacuation destinations, and retention remain deferred.

## 2026-09-09 — Consolidated implementation gates

Preserve accepted gameplay while separating prerequisites by feature. Runtime/build selection precedes the first runnable plugin; SQLite and protection details precede durable claims; Building identity, restoration safety, recovery/accounting, and eligibility configuration precede automated mutation. Resource features have their own operational verification gates.

Ownership/history facts must be persisted from the first owning-state changes. Later History milestones concern presentation and rankings, not reconstructing events that were never saved.

Reason: the user requested faster architecture work with minor details deferred. Gate each feature safely instead of treating all unresolved algorithms and tuning as a prerequisite for beginning the project. No runtime versions or libraries are selected by this consolidation.

## 2026-09-09 — Accepted runtime and development baseline

The user approved Minecraft Java Edition/Paper 26.2, Java plugin source, JDK 25, and Gradle Kotlin DSL. Kotlin is used for build scripts, not plugin source. The project remains one modular plugin JAR with SQLite.

Reason: Paper's supported 26.2 release requires Java 25 minimum, and its official project setup uses Java with Gradle Kotlin DSL. Sources: [Paper version metadata](https://fill.papermc.io/v3/projects/paper/versions/26.2), [Paper project setup](https://docs.papermc.io/paper/dev/project-setup/).

Exact Gradle wrapper, Paper server/API build, JDK distribution/patch, SQLite driver, and migration-library pins remain implementation work. No scaffold or server validation is implied by this decision.

## 2026-09-09 — Git and canonical architecture

The user requested Git scaffolding before code, regular milestone commits, and the canonical filename `ARCHITECTURE.md`. Keep local tooling, server data, logs, and databases untracked. Commit coherent verified work with the configured identity; do not push without a request.

## 2026-09-09 — Runnable plugin foundation and package identity

- User-selected package prefix and Gradle group: `xdu.undarubra.esmp`.
- Four internal Gradle modules produce one Paper JAR: a Paper composition root, infrastructure-only core, and two modules owning their world definitions. No empty gameplay implementations are advertised.
- Pin Gradle 9.7.1, Paper server 26.2 build 121 / API `26.2.build.121-stable`, SQLite JDBC 3.53.4.0, and JUnit Jupiter 5.12.2. Java 25 remains the source/runtime target; verification used local Temurin 25.0.4.1+1.
- SQLite version 1 persists world definitions and physical identities, using transactional JDBC migrations and `PRAGMA user_version`. A bounded serial worker performs database I/O; no migration framework or generic event bus is introduced before a real consumer requires it.
- Runtime bindings only use already-loaded, explicitly configured names with compatible environments. The plugin creates no worlds and never maps default Nether/End worlds into renewable Resource roles implicitly.

Reason: deliver a working, restart-safe architecture rather than placeholder gameplay. Subsequent domains can extend verified persistence and lifecycle boundaries without making Core own gameplay semantics.

## 2026-09-09 — Factory Realm removed from current scope

The team removed the dedicated Factory Realm and industrial gameplay to reduce implementation workload and avoid RPG-survival balance and performance pressure from survival redstone machinery. Former Plot, Factory role, generator, and machine-operation decisions are superseded; Git history retains them, not the active architecture or backlog.

Remove the module, active world definition, configuration mapping, status entry, and development milestone. Back up and migrate old configs once. Preserve physical worlds and historical registry identities while excluding unsupported definitions from active snapshots and new registration. Core handles definition retirement generically rather than importing Factory business semantics.

No blanket redstone restriction in the remaining worlds is introduced by this scope cut. Such gameplay policy would require its own decision.

## 2026-09-09 — Playable Settlement implementation

- Civilization owns schema version 1, Settlement transactions, contiguous claim topology, fixed roles, expiring offers, history, and outbox. Core provides database-only work on its bounded SQLite worker.
- The Paper runtime reserves affected territory while changes commit, publishes the committed index on the server thread, then reports success and closes unauthorized inventory views. Fast cached admission checks reject obvious unauthorized mutations before territory is reserved; SQL remains authoritative.
- Implemented controls include create/claim/unclaim, inspect/list/view, invite/promote/transfer and offer resolution, kick/leave/demote, rename, confirmed abandon, historical inspection, and explicit admin protection bypass.
- Protection uses action-specific Paper events, not game-mode switching or blanket right-click cancellation. Same-Settlement mechanisms remain usable; cross-claim block/inventory effects are constrained. Ongoing brushing revalidates permission without loading remote chunks.
- Private particle borders are limited to nearby loaded chunks, 10 seconds, and 256 particles per viewer per update. Basic PvP, loose-item pickup, and natural entity movement are not new Settlement policies.
- Failure to initialize or maintain reliable persistence/protection stops the server. Hot reload is unsupported. Building/Activity/decay/repair and Resource reset gates remain separate.

Reason: deliver the complete manual Settlement gameplay loop for real-game testing while preserving committed permission boundaries and avoiding placeholder automated systems.

## 2026-09-09 — Local offline multiplayer and transition feedback

The user requested offline mode for local multiplayer testing. The development launcher and existing `.run/dev/server.properties` use `online-mode=false` and `enforce-secure-profile=false`, retaining the loopback bind. This is not a production authentication policy; online/offline UUID changes require explicit player-data/ownership migration rather than name-based reassignment.

Automatic movement feedback compares the previous and next Settlement IDs. Same-Settlement chunk crossings and wilderness-to-wilderness travel are silent; entry into a different identity or exit is announced once. Remove the duplicate world-change notification path; apply the same rule to teleport/respawn. Explicit command feedback is unchanged.

## 2026-09-09 — Settlement testing and deferred chat ergonomics

The user reports that Settlement testing in game is complete. Chat output could be more ergonomic, but this is explicitly not a current priority. Preserve the verification checklist for future regressions; do not spend the next feature iteration on chat polish.

## 2026-09-09 — Gameplay extension approved for architecture integration

The user explicitly approved integrating the consolidated gameplay proposal and revising the roadmap. `ARCHITECTURE.md` §16A is now canonical; the former proposal is a navigation record, not a competing specification. This is design acceptance, not runtime implementation or approval of unresolved numeric/recovery defaults.

- Economic stages are Settlement/Village/Town/City on the existing Settlement; cultural capability is parallel and does not increase production capacity or change Building Era. Vanilla crafting remains unrestricted.
- Main World Facilities use functional automatic checks, reachable shared housing and reasonable workforce density, with administrator certification for higher tiers. Relevant changes suspend affected capability; eligible basic production continues and stocks remain. Decorative edits do not automatically require staff review.
- One Settlement production budget bounds all Facility allocations. Hybrid production consumes inputs where required, including mined inputs for metal. Offline accrual uses bounded ledger stock at physical depots, never chunk loading or fake ticking villagers. Activity deficiency pauses production after a configurable grace period; paused time cannot become catch-up output and production grants no Activity.
- Wallets and treasuries are ledger accounts. Budgeted server purchases/rewards mint; trades transfer; service fees burn. Owner controls withdrawals/budgets, Managers make audited budget-limited operational payments, Members cannot spend treasury funds. No wages or membership taxes.
- Server Trade uses global bounded Cheap/Normal/Expensive bands, finite replenished stock, and separate purchase/sale eligibility lists for basic commodities. Player markets initially use physical stocked stalls. Rare/specialized supply primarily uses exploration/events/history and player markets; no farm ban or provenance tracker is selected.
- Timed ledger freight reserves bulk cargo and throughput over reviewed physical routes. Mode/distance inform quotes; nested cargo counts. Targeted rechecks stop new departures after disruption, not existing shipments. Unavailable destinations hold goods for the original recipient, with bounded storage/recovery still gated.
- Village or certified-cultural-landmark status qualifies for basic public teleport. Base-plus-cargo charges provide soft freight incentives while preserving shared inventory/Ender Chests and accepting storage bypasses.
- Natural biome/terrain profiles affect efficiency, separately from seasons. One Minecraft day equals one calendar day, 365/366 days per year starting at 0 A.D. Initial effects are bounded native weather/particles and reversible loaded-area snow/vegetation; full seasonal palettes remain deferred. The later shared-game-clock decision below settles the time basis for financial and Activity accounting; source/jump reconciliation remains gated.

Reason: make productive settlements, visible infrastructure, and historical/cultural places useful without unlimited automation, compulsory seasonal chores, or restoring the removed Factory Realm. Bounded ledger accounting enables unloaded production without weakening world-thread and recovery invariants.

Logical ownership separates Civilization qualification/Activity from Economy ledger/stock, Transport lifecycle, and Calendar/Seasons modifiers. These remain one plugin lifecycle; no new Gradle modules or tables are created by this decision. Core stays gameplay-agnostic.

Roadmap: Activity/time foundations → Building/Facility qualification → recoverable ledger/stock and bounded production → markets → transport. Decay/repair branches after Building/Activity and world recovery; seasons require calendar/effect safety; Resource cutover remains independently gated. Persist facts from each feature's first commit, not when later historical UI is built. Resolve concrete thresholds, eligibility, item/payment recovery, clock semantics, and workload budgets before enabling their feature.

## 2026-09-09 — Shared game clock for all gameplay timing

The user rejected real-life-clock-based Activity and selected the game's reckoning clock (ticks/days) for all plugin gameplay timing. `ARCHITECTURE.md` §16A.6 supersedes the separate elapsed-time recommendation.

- `advance_time=false` stops the clock and time-driven mechanisms. No server-downtime accrual or wall-clock catch-up.
- Tick-rate changes affect real-world pacing, not the Activity/decay result for the same elapsed game days.
- Activity, decay/repair progression, production/grace periods, freight, cooldowns, offer expiry, market intervals and Resource schedules consume the same clock. Offline accounting means absent players/unloaded chunks while game time advances, not a stopped server.
- Implement coherent clock persistence and migrate existing gameplay timers before Activity; do not mistake this design update for an implemented runtime cutover.
- The subsequent implementation decision below settles MAIN source, forward sleep/command jumps, rewind re-anchoring and normal restart. Calendar boundaries and coordinated world/database restoration remain separate gates.

Reason: gameplay belongs to the world's chronology. A frozen world must not age according to elapsed real-life time, and changing server pacing must not change balance measured in game days. Full seasonal presentation remains deferred and is not needed to implement the shared clock.

## 2026-09-09 — Shared clock and Activity implemented

The user selected **count all forward jumps**: sleep and forward time commands age the world; backwards commands re-anchor without reversing completed work. MAIN's loaded world clock is authoritative, isolated from Resource resets. `GameClock` persists monotonic reckoning ticks and source identity; normal boot resumes the checkpoint and re-anchors without downtime catch-up. Source UUID replacement fails safely.

Game-timed writes checkpoint in their transaction; otherwise clock persistence is every 200 game ticks and at shutdown. Abrupt failure can lose uncommitted clock-only progress/queued observations. This is not a coordinated world/database save protocol. Real timestamps are retained only for diagnostic/legacy history and one-time migration of absolute legacy offer deadlines.

Config version 3 replaces offer seconds with game ticks; Civilization schema 2 converts remaining deadlines once and records founding/history game ticks without deleting identities or history. Borders last 200 game ticks; protection-message throttling uses 20 game ticks.

Activity uses lazy exponential decay: maximum 100, initial 30, seven-game-day half-life, inactive below 20, recovery at 30; production eligibility grace is 72000 ticks. Authorized BUILD credit is 1, team INTERACTION .25, Visitor INTERACTION .02; cooldown 200 ticks per actor/category, player daily cap 5, Settlement cap 20, Visitor cap one twentieth of player cap. These are explicit initial tuning values, not a claim of perfect anti-bot detection.

No login, movement, pressure-plate passage, idle, automation or frozen-clock credit. Persisted role/chunk/revision validation rejects stale observations. Founding/migration anchors prevent first-visit refreshes; transfer preserves score, release blocks contributions, reclaim gets a new identity. Closed pause intervals persist for future production exclusion. Status reads do not write decayed scores.

Policy is configurable before initialization; changing persisted policy requires explicit migration rather than silently changing historical score meaning. That migration interface is deferred. Paper collects bounded/coalesced observations and status appears in `/settlement activity [name]` and `info`. No Building/decay/production systems are enabled by Activity.

Reason: provide visible, restart-safe eligibility without per-Settlement timers, wall-clock progression, or prematurely implementing consumers. Verification used Gradle build, seven added behavioral regressions (including legacy expiry migration), and an isolated live Paper fixture for game-time freeze/jumps/rewind, changed tick rate, real block credit, inactivity/grace, exact frozen restart and actual bed sleep.
