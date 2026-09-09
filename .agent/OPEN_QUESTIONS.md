# ESMP Open Questions

This is a gate-oriented index of `ARCHITECTURE.md` §§26–28, not a second architecture. Settled policies must not be reopened merely because their implementation is unfinished.

## Playable Settlement slice completed

The Java 25/Paper 26.2 plugin implements world identity, Settlement persistence, contiguous claiming, fixed-role and offer workflows, confirmed abandonment, immutable cache publication, historical/outbox records, protection adapters, and private borders.

The user has tested Settlements in game. Preserve the native checklist for regressions and deployment-specific integration checks, rather than treating first in-game verification as the next blocker. Chat ergonomics are deferred by explicit user preference.

Current boundaries: normal PvP, ground-item pickup and natural entity movement remain unchanged. Shared game time, migrated offer/border/throttle timers, Activity scoring/caps/hysteresis, pause intervals and status are implemented. Building/decay/repair/reset/production remain disabled.

## Next: Building discovery and stable identity

- Define automatic discovery, split/merge identity and claim-change reconciliation before implementing Building tracking.
- Future production must consume Activity's persisted pause intervals and define input/output/in-progress-job accounting; no catch-up from paused time.
- Activity balance testing and explicit migration of an installed Activity policy; initial formula, caps and thresholds are no longer unresolved.
- Leap/season boundaries and calendar presentation remain open. MAIN source, all-forward-jump counting, rewind re-anchoring, frozen time and normal restart are implemented; paired world/database restore remains separately gated.

### Building detection research — 2026-09-09, not an algorithm decision

The user requested research before implementing discovery and a useful way to evaluate competing algorithms. No detector, large-model dependency, or evaluation-tool implementation is approved by this survey.

Primary sources inspected:

- [CraftAssist paper](https://arxiv.org/abs/1907.08584), §§5.4–5.5 and 6: connected components of unnatural/player-placed blocks plus a 3D CNN for semantic house parts. Releases 2586 house-building traces and annotations for 2050 houses; these are not crowded-settlement whole-building instance labels. [Implementation](https://github.com/facebookresearch/craftassist/blob/master/python/craftassist/heuristic_perception.py) confirms local connected-component heuristics. Its README targets Minecraft 1.12-era infrastructure, not a drop-in Paper 26.2 module.
- [Yang et al., 2022, voxel-based room segmentation and space partition](https://www.mdpi.com/2220-9964/11/10/530): occupancy/free/unknown voxels, Euclidean distance transform, inner-sphere graph seeds, wavefront growth, plane partitioning and graph-cut labeling. The authors explicitly report wide-opening undersegmentation and remaining manual correction/efficiency limitations. Room partitions are not whole-building identities.
- [scikit-image watershed example](https://scikit-image.org/docs/stable/auto_examples/segmentation/plot_watershed.html): marker-based distance-transform watershed separates touching regions; useful reproducible baseline, not a demonstrated Minecraft building detector.
- [Mask3D](https://github.com/JonasSchult/Mask3D): learned 3D instance segmentation with published checkpoints on ScanNet/ScanNet200/S3DIS/STPLS3D. Its reference stack uses CUDA/MinkowskiEngine; no inspected evidence establishes Minecraft/Paper accuracy or cheap CPU deployment. Minecraft-specific data/features and retraining/evaluation would need investigation.
- [napari labels](https://napari.org/stable/howtos/layers/labels.html) supports integer-label overlays, slicing and manual annotation; [scikit-image segmentation metrics](https://scikit-image.org/docs/stable/auto_examples/segmentation/plot_metrics.html) documents adapted Rand and separate split/merge variation-of-information errors.

Research recommendation [INFERENCE], not accepted design: first build a read-only snapshot/label/replay benchmark, comparing material-filtered components, free-space/geometry partitioning, and a learned candidate on the same scenes. Use exact block states and claim bounds rather than screenshots alone. Distinguish construction evidence, room/region segmentation, whole-building grouping, and persistent identity matching; no one inspected method solves all four.

Proposed evaluation tool: bounded Paper snapshots exported with world instance, coordinates, block palette/state, claim mask and unknown/unloaded mask; offline napari reference/prediction overlays and slice inspection; reproducible algorithm/config runs; edit-sequence replay. Annotate solid block ownership separately from optional room-air volumes. Measure per-building overlap/precision/recall, false merges/splits, terrain leakage, identity changes under benign edits, and capture/analysis resource costs. Unknown chunks must not be treated as air.

Candidate fixture families: isolated houses, road-connected houses, shared walls, multi-storey/underground structures, open barns/ruins, natural caves/trees/cliffs, renovations/bridges, and cross-Settlement boundaries. Human reference labeling is an offline development activity, not a requirement for player building registration. Resolve ambiguous shared-wall/building-group semantics before treating any model output as authoritative. Hold out real builds and edit sequences; rotated/material-swapped copies must not leak across training and evaluation.

No algorithms/models were run on ESMP scenes and no accuracy or latency claims are established. Some search providers blocked automated requests; the MDPI full method/discussion was recovered through the browser. Retain the initial survey as leads, not an exhaustive literature review.

## Before enabling Facilities and cultural capabilities

- Concrete four-stage upgrade criteria, cultural qualification and higher-stage unlocks; Village-or-certified-landmark basic teleport access is settled.
- Facility/Building linkage, workforce checks and Repair sharing, certificate scope/records/rechecks/reactivation, and ownership/claim-change handling.
- Shared production-capacity units, allocation authority and stage amounts; a Settlement-wide ceiling is settled.
- Recipes, eligible mined inputs, stale-state validity, inputs/output reservations and bounded offline accounting. Functional shared housing and affected-capability suspension are settled.

## Before enabling Economy and Transport

- Stable ledger operations/audit, issuance-budget allocation/replenishment and Owner-approved operating-budget controls.
- Physical depot/stall stock permissions, deposit/withdrawal and purchase/collection recovery, crash-safe item/payment settlement, and ownership changes.
- Exact NPC buy/sell catalogues including vanilla farm outputs, prices/spreads/quote behavior, quantities and replenishment. Global bounded bands, finite stock and separate lists are settled.
- Natural regional profiles/modifier values and exact seasonal effects; keep underlying regional identity independent from seasonal cosmetics.
- Route corridors/land permissions/targeted invalidation, bulk weights and throughput windows, mode/distance rates, reservations and payment recovery.
- Bounded destination storage, held-cargo recovery and original-recipient access after endpoint/ownership changes; no new departures after disruption and preserved existing shipment schedules are settled.
- Teleport cargo classification/portable-container treatment, fee presentation, safe destinations and payment recovery. Shared-storage bypasses are accepted.

## Before enabling native seasonal mutations

- Calendar reconciliation above and reversible loaded-area effects that preserve player builds, expected state, ownership, and Building baselines.
- Bounded scheduling and world/database reconciliation; offline production is not permission to simulate physical mutations in unloaded chunks.

## Before enabling automated Building mutation

- Automatic discovery algorithm, stable identity across split/merge and claim changes, and historical linkage during partial reclaim.
- Block-entity/inventory baseline representation and restoration safety; inherited structures use current-state baselines and authorized edits automatically revise targets.
- Durable journal/checkpoint implementation, world-save/database reconciliation, idempotent material accounting, and transactional outbox storage/retry scheduling.
- Activity/ruin aggregation and eligibility configuration; meaningful Visitor credit is very small, passersby earn none, and recovered activity stops further decay without healing.
- Repair/workforce integration and qualifying restoration-cycle rules for Era advancement.

## Before enabling Resource operations

- Resource reset interval, generation settings, pre-generation radius, evacuation destination, offline/respawn/portal handling, and retention policy. Shared cycles and advance preparation are settled.
- Coordinated world/database backup and restore verification, including recovery during Resource cutover.

## Deferred tuning and optional features

- Administrator exemptions, cooldowns, and remaining expansion policy.
- Activity weights/caps, decay curves/probabilities/material rules, vegetation, repair speed/material values, and workload budgets; configure safe eligibility before enabling affected features.
- Particle styles, command/Dialog presentation, localization, historical UI, rankings, and Era names.
- Alliance relationships.
- Full seasonal grass/foliage palette techniques and compatibility; explicitly deferred beyond the initial native-effects scope.

These details do not block the initial plugin or claim slice. Resolve them when their feature is implemented; do not enable an incomplete or unsafe feature to avoid its gate.
