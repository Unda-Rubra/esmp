# ESMP Open Questions

Gate-oriented implementation index of `ARCHITECTURE.md` §§7–11 and 25–28. The user-authored 2026-09-10 MVP revision supersedes the earlier Building-detector-first plan. Accepted boundaries are not open questions merely because code is unfinished.

## Implemented baseline versus target

The existing Java 25/Paper 26.2 plugin implements legacy Settlement roles/free claims/player naming, contiguous claims, durable offers/history, protection adapters, GameClock and Activity. Core world schema is 1, Civilization schema 2, config version 3. Governor/Citizen/Public, bans, Parcels, paid claims, official-name administration and management items are target architecture, not implemented behavior.

Preserve user-confirmed old Settlement coverage and pending `f8a9f50` checks in [TEST_CHECKLIST.md](TEST_CHECKLIST.md). No new target feature should appear there as completed until its implementation ships. Legacy authorization/Activity checks must be explicitly superseded at migration, not silently renamed now.

## Immediate plan: MVP Settlement/Parcel revision

Follow §§27–28 without expanding the full economy to satisfy the first dependency:

1. **Paid claim transaction and pricing hook.** Define the minimal real ledger/charging contract, payer (wallet/treasury), initial funding, configured marginal area curve, quote/commit consistency, insufficient-funds behavior, retries and atomic debit+claim+history. Define any refund/no-refund behavior before changing unclaim. Existing claims are not retroactively billed without an explicit migration decision. Full markets, Facility production and transport are not prerequisites.
2. **Governance migration and bans.** Map legacy Manager/Member/Visitor to Governor/Citizen/Public in schema, APIs, commands, cached views, offer workflows and Activity admission. Preserve one Owner and transfer-to-Citizen atomically. Define migration of pending offers, administrator identification/override scope, ban/governance consistency and cache invalidation. Bans deny governed interactions, not movement/information; administrators bypass bans.
3. **Parcel persistence and geometry.** Implement world-aligned 8×8×8 cells inside one Settlement, no overlap, six-face connectivity, unique local names and zero/multiple Holders. Define bounded geometry/edit budgets, coordinate handling at negative/world-height limits, immutable publication and atomic conflict rejection. A Parcel is not a sovereign claim or Building identity.
4. **Authorization and complete commands.** Implement administrator → ban → spatial lookup → Parcel/default policy → relation-set resolution for BUILD/INTERACT/CONTAINER/AUTOMATION. Define server default policy, policy-edit authority and source/destination checks for automation. Public can be a Holder; Citizen+Holder is not collapsed to one highest role. Specify durable governance+all-Holder consent, membership/Holder changes during approval, admin dispute handling, and interaction of assigned Parcels with Settlement unclaim/abandonment so governance cannot bypass consent indirectly.
5. **Management frontends and naming administration.** Management Book/Parcel Wand must call the command/application APIs, not introduce parallel rules. Define issuance/PDC handling, session selections and previews, named off-hand Name Tag matching, cuboid addition of only unparcelled cells, surface removal and atomicity. Assigned geometry still requires consent. Define stable numeric identity alongside/migrated from existing UUIDs, generated labels, qualification/approval, legacy-name treatment, naming charges if enabled, name uniqueness and historical preservation. Do not invent final command grammar from the illustrative §16 examples.

Activity integration is part of the authorization cutover, not a cosmetic enum rename: Public/Holder credit eligibility/weights, bans, policy revisions, persistent actor limits and installed-policy migration need a coherent contract. Keep game-time/freeze/no-downtime semantics and anti-farming bounds. The current installed-policy-change rejection is not a completed migration mechanism.

## Next MVP slice: Building identity, geometry and evaluation

After Settlement/Parcel revision, define discovery and stable identity across splits/merges/claim changes. The geometry contract now explicitly needs occupied volume, exterior surface, interior volume, openings and structural components/support relations. Recognition and persistent identity remain distinct from Parcel usage permissions.

The evaluation corpus must cover the new renovation invariant: a ruined tower enclosed by a new hall must not have obsolete exterior walls repaired through the new interior merely because target cells are air. Label active/damaged/conflicted/retired/historical-only fragments or an equivalent representation; preserve historical evidence while preventing obsolete restoration. Unknown/unloaded data is not air. Block entities require separate policy before any ordinary-block mutation treatment.

### Research leads retained — not a detector selection

The 2026-09-09 survey remains evidence for this later slice, not the immediate next implementation. No model, dataset, benchmark tool or detector was deployed, and no ESMP accuracy/latency was measured.

- [CraftAssist paper](https://arxiv.org/abs/1907.08584), §§5.4–5.5 and 6: unnatural/player-placed connected components plus a 3D CNN for house parts; 2586 house traces and 2050 annotated houses, not crowded-settlement whole-building labels. [Heuristic implementation](https://github.com/facebookresearch/craftassist/blob/master/python/craftassist/heuristic_perception.py) is a Minecraft 1.12-era reference, not a Paper 26.2 module.
- [Yang et al., 2022](https://www.mdpi.com/2220-9964/11/10/530): voxel occupancy/free/unknown regions, distance transform, inner-sphere graph seeds, wavefront growth and graph cuts. Wide openings can undersegment; manual correction/efficiency limits remain. Rooms are not whole buildings.
- [Watershed baseline](https://scikit-image.org/docs/stable/auto_examples/segmentation/plot_watershed.html): reproducible distance-transform partitioning, not proven Minecraft detection.
- [Mask3D](https://github.com/JonasSchult/Mask3D): 3D instance segmentation on other datasets; reference CUDA/MinkowskiEngine stack, no established Minecraft accuracy or inexpensive CPU deployment.
- [napari labels](https://napari.org/stable/howtos/layers/labels.html) and [segmentation metrics](https://scikit-image.org/docs/stable/auto_examples/segmentation/plot_metrics.html): candidate annotation/slice overlays and adapted Rand/split-merge evaluation tooling.

Unaccepted research proposal [INFERENCE]: bounded read-only Paper snapshot export, offline reference/prediction overlays, reproducible candidate runs and edit-sequence replay. Compare filtered components, free-space/geometry partitioning and learned candidates. Record world instance, block states, claims/Parcels, unavailable masks and relevant revisions. Measure block assignment, terrain leakage, false merges/splits, interior/exterior and renovation errors, identity stability and capture/analysis cost. Offline annotation does not make player registration mandatory. Hold out real builds and avoid transformed-copy train/test leakage.

## MVP durable mutation, Decay and Repair gates

- Journal/checkpoint implementation, paired world/database reconciliation, expected-state and geometry-level conflict preservation, idempotent material accounting, and naturally loaded/budgeted work.
- Whole-Building Repair as the initial player-facing scope, internally localized batches, and clear baseline retirement under renovation. Block entities cannot be handled as ordinary blocks until safe policy exists.
- Small material/profile and weathering/integrity sets; DROP/TOPPLE/CAVE_IN are initial candidate failure modes, not a full-physics commitment. System decay must not generate ordinary collectible drops.
- Coarse localized due analysis for ordinary wild structures and lightweight infrastructure segments; define eligibility and promotion to richer persistent tracking. No global full-world scan.
- Repair/villager integration and qualifying Era thresholds. **No automatic inactivity/ruin-based Settlement release in the MVP**, even after physical decay exists.

## Economy / Facilities / Transport / Resource gates

- Preserve existing accounting/recovery gates beyond the minimal claim-charging dependency: physical depot/stall permissions, stock/item conversion, audited wallets/treasuries, issuance/operating budgets, transactional quotes/payments, ownership changes and restart recovery.
- Initial Facilities remain a bounded simple catalogue with automatic functional basics and reviewed higher certification. Define Building/Facility linkage, workforce/housing checks, capacity allocations, recipes/mined inputs, qualification reactivation and production pause-interval accounting.
- Define stage/culture criteria and later unlocks; Village-or-certified-landmark basic teleport qualification remains accepted. Culture does not independently increase production capacity or redefine Era.
- Server Trade has accepted finite stock, global bounded bands and separate buy/sell lists; catalogues, farm-output eligibility, spreads and replenishment values remain open. Physical stalls come before advanced order/delivery systems.
- Routes need corridor/land/Parcel authorization, targeted invalidation, bulk/throughput/rates, bounded held cargo, original-recipient access and payment recovery. Disruption blocks new departures, not existing shipment schedules. Teleport needs safe destinations and understandable cargo fees; shared-storage bypasses remain accepted.
- Shared Resource cutover needs evacuation, retired login/respawn/portal handling, generation/retention policy and verified paired backup/restore. MAIN is the clock source, not resettable Resource instances.
- Calendar leap/season boundaries and safe bounded native effects remain open; regional identity stays separate from cosmetics. Full seasonal palette techniques remain deferred.

## Required next major phase after stable MVP: dynamic Settlement cycles

This phase is mandatory, not optional or dependent on disasters. Civic Capacity/maintenance capacity, area/Building/Facility/infrastructure demand, selective preservation, mature/new-city incentives and gradual peripheral release are candidate mechanisms. Define formulas, UI, ownership guarantees, Holder consent/rights, release timing, contiguity, reclamation and protection of historic cores before enabling any automatic ownership loss.

MVP Settlements remain persistent unless explicitly abandoned or changed through an authorized lifecycle operation. Do not reinterpret existing Activity INACTIVE or future Building RUINED as permission to release claims.

## Further world evolution and open disaster design

- Coarse Anthropogenic Landforms are an accepted later direction for significant excavations, fills, lakes, hills or canals; incidental edits are not full persistent terrain records and Buildings remain Building data. Fixed 8×8×8 is preferred initially; significance/material rules, simulation, erosion and adaptive refinement remain open.
- Chunk retirement/regeneration needs explicit safeguards for Parcels, Buildings, entities, POI, infrastructure, history and player terrain changes.
- Disasters, sea-level shifts, Disaster Operators/Fields and Epoch transitions remain open/optional. They are not prerequisites for the required dynamic-cycle phase.
- Other deferred refinements: exemptions/cooldowns, presentation/localization/rankings, alliances, advanced Repair scopes and policy-migration UX. Resolve each at its own gate without inventing new MVP systems.

## Canonical ambiguities to resolve at the affected implementation gate

The user-authored architecture was not rewritten during this notes synchronization. The specific §§7, 23 and 25–28 govern the MVP: old broad civilization-cycle diagrams and the generic Release glossary should not be read as enabling automatic MVP release. The illustrative `/settlement create <name>` example is not a final naming contract. Numeric Settlement identity versus the existing UUID schema, assigned-Parcel interactions with parent claim release, and exact ban/administrator/policy-edit rules still require implementation decisions; do not silently fill them with legacy behavior.
