# ESMP Open Questions

Implementation gates for the current `ARCHITECTURE.md`, especially §§7, 9–16A and 26–28. The accepted economy addendum's final razor review supersedes earlier economic-stage/production/freight/season dependencies. Selected rules are not reopened because implementation is pending.

## Implemented baseline and accepted target

The current JAR implements Settlement/Parcel governance, tools, shared GameClock and Activity. It still uses numeric-only area quotes, a direct administrator naming toggle and two-party ownership transfer. Core schema 1, Civilization schema 3 and config 4 are unchanged by the architecture merge.

The target currency is now selected: one scarce integer ledger currency, non-negative Player Wallet/Settlement Treasury, typed idempotent movements and budgeted issuance. LandValueProfile-based paid Claims, personal subsidies, UNVERIFIED/VERIFIED certification and administrator-approved acquisition are accepted but not implemented. Preserve all 30 pending implementation checks in [TEST_CHECKLIST.md](TEST_CHECKLIST.md); add new checks only when those features ship.

## Immediate execution: economic land foundations

1. **Currency and transaction boundary.** Define stable currency/account identity, display-only names/symbol/format, integer amount and checked arithmetic rules, transaction IDs/reasons, account authorization and atomic movement/audit semantics. Separate TRANSFER, ISSUANCE and SINK; reject overdraw and duplicate commits. Minimal Wallet/Treasury access should not become an appropriation-law or credit system.
2. **First issuance and usable feedback.** Define game-time monetary periods, budget tiers based on genuine activity, carry-over/missed-period/forward-clock-jump handling, reservation/reversal rules and one-time starting-grant eligibility for existing versus new players. Budget enforcement must precede the first grant. Implement starting funds around 100, balance/transaction feedback and basic peer/treasury transfers; any simple per-pay/new-account controls must not become permanent wealth caps.
3. **LandValueProfile.** Capture natural value at first Main chunk generation using the correct execution context, with recoverable persistence. Store only world instance/chunk/valuation version/base value and optional class. Define a credible legacy-chunk backfill policy, missing-profile handling and explicit revaluation rules; do not silently price edited terrain as pristine. Operator diagnostics may justify extra provenance, not an unconditional feature dump.
4. **Actual Claim charging.** Configure portfolio and Settlement-size tiers, a light Activity/development factor, founding-only own-nearby surcharge and diminishing early discounts. First-ever founding costs 25. Define distance metric, integer rounding/minimums, payer versus assessed-player identity when Governors act, quote revision/expiry and atomic debit+Claim+history+subsidy usage. Reconstruct personal subsidy history without reset/transfer loopholes or retroactive billing of current numeric-only Claims.
5. **Certification and public naming.** Implement Activity/meaningful-development eligibility, application authority, administrator review/provenance and UNVERIFIED/VERIFIED status. Define legacy boolean/name migration, revalidation at approval and any later revocation rule. Denial preserves Claims/governance. Public named listing and official names require approval; a website/map implementation is not a prerequisite for storing/enforcing certification state.
6. **Ownership acquisition.** Migrate current pending transfers to recipient acceptance, a simple current land-based fee quote and administrator approval before atomic ownership/fee commit. Define fee coefficient/rounding, recipient funding, quote invalidation, history and treasury authorization. No predecessor subsidy or original-purchase-price anchoring; no Building/labor valuation or private sale-price enforcement. Full autonomous recipient portfolio/proximity revaluation is deferred.

The next implementation task is the integer transaction/account/budget foundation, not another currency-selection questionnaire. Coefficients and presentation names are configuration decisions; preserve hard economic invariants while keeping the first slice bounded.

## Core MVP: Building identity / baseline / geometry

Keep automatic bounded discovery and renovation-safe geometry. Obvious expansion/contraction preserves identity; major reinterpretation can retire the old Building and discover new ones, retaining lightweight predecessor links where obvious. General graph matching or perfect split/merge identity continuity is not a gate for shipping the ruin/repair loop. Old historical records must remain traceable.

Resolve the minimum occupied/exterior/interior/opening/support representation. A ruined tower enclosed by a new hall must not be restored through the new interior merely because target cells are air. Retire/conflict affected baseline fragments without deleting history. Block entities need explicit safe policy before ordinary-block mutation applies.

### Detection/evaluation research retained as tooling leads

No detector/model or Building benchmark has been implemented. A bounded read-only snapshot/annotation/replay comparison remains a possible development aid, not a perfect-identity research prerequisite. Evaluate benign-edit identity stability separately from complex changes where retire/rediscover is permitted; preserve Parcel/Settlement boundaries and mark unknown chunks rather than treating them as air.

- [CraftAssist](https://arxiv.org/abs/1907.08584) and its [heuristics](https://github.com/facebookresearch/craftassist/blob/master/python/craftassist/heuristic_perception.py): connected construction components and house-part labels, not a ready-made crowded-settlement instance detector.
- [Voxel room segmentation](https://www.mdpi.com/2220-9964/11/10/530) and [watershed](https://scikit-image.org/docs/stable/auto_examples/segmentation/plot_watershed.html): geometric baselines with room/building and wide-opening limitations.
- [Mask3D](https://github.com/JonasSchult/Mask3D): learned 3D instance approach requiring Minecraft-specific evaluation, not an established cheap production solution.
- [napari labels](https://napari.org/stable/howtos/layers/labels.html) and [segmentation metrics](https://scikit-image.org/docs/stable/auto_examples/segmentation/plot_metrics.html): optional offline reference/prediction inspection and split/merge metrics. Human development annotations do not require player registration.

## Core MVP: durable Decay / Repair / History

- Resolve journal/checkpoint/material recovery, expected-state plus geometry conflict preservation, ownership/Parcel permission revalidation and naturally loaded budgeted batches.
- Initial Repair is repair point/station + physical materials + shared game time. Whole-Building player scope can use local batches. Live villagers/artisans, housing/pathfinding/death/profession simulation are not prerequisites.
- Initial collapse vocabulary is DROP and CAVE_IN; tune a small material/weathering/integrity set. TOPPLE/full physics is later. System decay has no ordinary collectible block drops.
- Era is a lightweight counter/state for qualifying completed restoration cycles and History facts; dedicated abilities/rankings/UI are later.
- MVP ownership stays persistent despite low Activity/ruin. Ordinary wild structures/infrastructure retain bounded/coarse treatment; do not expand them into global structural simulation.

## Required immediately after core MVP: Dynamic Settlement Cycle

Design Civic Capacity/maintenance demand or an equivalent, selective preservation, mature-city/new-Settlement incentives, gradual peripheral decline/release and history-preserving reclaim. Resolve formulas, UI, Holder/ownership guarantees, release timing, contiguity and protected historic cores before enabling automatic ownership loss.

This phase precedes Server Trade/Facility recognition, Resource automation, production, freight, teleport and seasons. None of those systems or a disaster/Epoch framework is a prerequisite.

## Post-core: Server Trade and lightweight Facility recognition

- Define narrow commodity whitelists/unit conversions/whole lots, separate purchase/sale eligibility, finite stock, optional small essential restocking and resistance to crafting/value loops. Avoid generic material decomposition.
- Quote/admit stock, global issuance budget, category buy quota, optional player quota and stock-demand limits before taking items. Resolve recoverable physical item exchange and deterministic buy/sell direction/reasons.
- Use coarse-period discrete stock bands with fixed multipliers, spread and min/max integer prices. Resolve period snapshots, rounding that preserves spread and quantity reservation; avoid per-trade price oscillation. Continuous pressure formula/hysteresis require later evidence.
- Observe wallet/treasury supply, issuance/sinks, median/top balances and Server Trade volume/stock. Manual tuning first; basket indexes and automatic monetary-policy responses remain deferred.
- Facility is identity, Settlement plus Building/region, type and enabled/certified state. Shared production budgets, offline accrual, worker density, reachable housing, depots and detailed production chains are not initial requirements.

## Resource operations and optional systems

Safe operator-triggered Resource preparation/activation or a verified maintenance procedure can precede full automation. Define evacuation, saved-location/portal handling, fresh instance publication, paired backup/restore and retained-world policy before use; do not expose mixed cycles. Automate when actual reset frequency warrants it.

Keep GameClock. Calendar presentation is optional; gameplay seasons and reversible seasonal block effects are deferred. Retain History/landmark/site flags rather than a Cultural Standing score. Freight and public Settlement teleport are deferred in full; physical travel remains the default and any later teleport must use direct qualification, not removed levels.

Economic Receiving Capacity is a deferred anti-abuse candidate. If ever justified, distinguish peer-origin inflow from legitimate self-earned/issued money, reject over-limit inflows atomically and cover all player/treasury routes. Do not implement Settlement-size ceilings or advanced treasury law as current MVP requirements.

World evolution/Anthropogenic Landforms, regeneration and disasters/Epoch systems retain separate later safety design. Landform history is not an excuse to mutate immutable natural LandValueProfiles.

## Current implementation acceptance and refinements

The completed Parcel text/visual fixes remain pending user acceptance in the temporary checklist. The existing installed Activity value-policy migration limit also remains. Neither is evidence that the new economy/certification/transfer requirements already run. Existing direct naming and transfer tests remain version-specific until their respective implementations are replaced.
