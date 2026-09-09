# Gameplay Extension — Discussion Only

Source: user's “ESMP — Gameplay Systems Architecture Extension, Draft v0.1”. This is a summary for discussion continuity, not an accepted implementation specification. Do not add these systems to `ARCHITECTURE.md` or its roadmap until the user explicitly approves integration. The proposed Activity implementation is paused for this discussion.

## Proposed direction

The economy serves the civilization loop: Explore → Settle → Build → Produce → Trade → Develop → Prosper → Decline → Ruin → Reclaim. Settlement development, useful buildings, infrastructure, and history should outperform compressed villager captivity and large redstone automation. Keep rules understandable, physical facilities visible in the world, and economy/transport/seasons/decay modular.

## Agreed within this proposal — not yet integrated into architecture

- Production: hybrid, with bounded offline accrual from saved valid Facility state and no chunk loading. Basic renewables use capacity/workers/time; processed goods consume inputs. Inputs, output storage, and the shared Settlement production budget cap accrual. Insufficient Activity eventually pauses production after a configurable grace period; stored goods and unused inputs remain. Production never grants Activity.
- Currency: ledger-based, with distinct player wallets and Settlement treasuries. Budgeted server purchases/rewards create money; player trades transfer it. Owner controls treasury withdrawals and operating budgets. Managers may make audited operational payments within approved budgets; Members cannot spend treasury funds. Budget allocation/replenishment remains open.
- Teleport/freight: soft incentives preserve shared Ender Chests and accept manual storage bypasses. Teleport/market service fees burn currency. Freight pays the route operator plus a separate disclosed system fee that is burned. No recurring wages or membership taxes are introduced. Exact cargo weights and fees remain deferred.
- Progression: four economic stages on the existing Settlement aggregate—Settlement, Village, Town, City. Cultural standing is a parallel capability track for landmarks/public significance; it does not independently raise production capacity or redefine Building Era.
- Certification: basic production requires usable workspace, valid workstations, reachable housing, and reasonable worker density. Housing may be shared elsewhere in the same Settlement. Higher production tiers require administrator certification. Dimensions, type-specific environmental checks, and certificate review/revocation workflow remain open.

These selections refine the discussion draft only. No code, accepted architecture, or implementation roadmap has been changed.

## Proposed systems

### Settlement progression

Four economic stages are selected: I Settlement (traditional survival start), II Village (community), III Town (specialization), IV City (a significant civilization node). These are development labels on the existing Settlement aggregate, not new ownership entities. The draft's housing, public-space, industry, trade, transport, Activity, and historical requirements remain candidates for concrete progression criteria. Exact upgrade requirements and unlocks are not settled.

### Facilities and certification

Facility + Settlement + Villager produces goods or services. Examples: Farm/food, Lumber Yard/timber, Quarry or Stone Works/stone, Smithy/metal goods, Bakery/processed food, Port/maritime capacity, Railway Station/land transport.

Basic Facility production starts after automatic checks for usable work area, valid workstations, reachable housing, and reasonable worker density. Housing may be in another building within the same Settlement; each Facility need not duplicate its own dormitory. Higher production tiers require administrator certification. Review concerns function, exploits, and consistent standards—not aesthetic scoring. Numerical minima, type-specific terrain/environment requirements, certificate scope, review reuse, and revocation workflow remain open.

Each economic stage supplies a shared Settlement production-capacity budget allocated among Facilities. Combined allocations must not exceed that budget; adding Facilities or villagers does not increase the budget by itself. Facility tiers remain bounded by economic capability and certification. Cultural standing alone bypasses neither ceiling. Exact budgets, capacity units, allocation authority, and resource/recipe classifications remain open.

Production may accrue while chunks are unloaded from saved valid state, without loading chunks or pretending villagers keep ticking. Inputs and output storage bound accrual. When insufficient Activity persists beyond a configurable grace period, new production pauses until meaningful activity recovers, independently of visible ruin. Stored goods and unused inputs are preserved. Paused time must not become catch-up output after recovery; the exact grace period, eligibility thresholds, and in-progress-job/accounting mechanics remain to be designed.

Facility inputs and outputs use bounded ledger stock accessed through a real depot at the Facility. Depositing removes physical items into stock; withdrawing returns physical items. Offline accounting operates on that stock, not a duplicate snapshot of items still present in a chest. Deposit/withdrawal recovery and stock-access permissions remain design work; the physical interface must preserve the vanilla-client experience.

### Goods and specialization

- Basic resources: timber, stone, food, clay, glass, basic metal; stable affordable supply should support construction.
- Processed goods: building materials, masonry/bricks, processed food, tools/components; intended to create industry chains and trade.
- Rare goods: Elytra, Dragon Egg, Wind Burst III, special equipment/materials, event rewards, historical relics; exploration/events/history rather than unlimited ordinary Facility production.

Regions modify efficiency rather than hard-lock goods. Candidate influences include climate, terrain, crops, trees, livestock, and special city/NPC conditions. Mountains favor stone/metal, plains food/livestock, northern areas forestry, coasts fishing/shipping. Example multipliers 1.5/0.8 are illustrative, not selected tuning.

### Trade

Server Trade is a bounded basic-market backstop and economic anchor, not an unlimited quest-money shop. Global supply/demand may produce Cheap/Normal/Expensive bands with price limits. Initial Player Trade uses physical market stalls with stocked goods and posted prices: buyers visit to purchase and collect, rather than receiving global instant delivery. Orders, procurement, and bulk fulfillment can be considered later; they are not implicitly included in the initial stall choice.

Ledger currency has player wallets and Settlement treasuries. New currency comes from budgeted server purchases and exploration/event rewards. Owner controls direct treasury withdrawals and operating budgets; Managers may make auditable operational payments within those budgets, and Members may not spend treasury money. This is bounded delegated trust, not a guarantee against self-dealing or overpriced purchases. Market stock, price behavior, issuance-budget allocation/replenishment, and detailed budget controls remain open.

### Transport and teleport

Trade Routes abstract origin, destination, mode, capacity, and cost, backed by real roads/rails/ports. Do not simulate every box travelling physically.

Separate ordinary personal movement, progression-unlocked Settlement Teleport, and long-term bulk freight. Proposed teleport fee = base cost + cargo cost, intended to favor people teleporting and goods using routes. Exact cargo classification, containment handling, and prices remain open.

Freight payment has two explicit parts: operator revenue (a transfer) and a disclosed system fee (currency destruction). Teleport and market service fees are also sinks; ordinary player trade payments are transfers, not minting or burning. No recurring wages or membership taxes are added.

### Culture and history

Economic development and cultural standing are parallel capabilities. Economic stages govern production capacity; architecture/history/landmark achievements grant independent public significance. Nonproductive castles, ruins, and monuments can develop culturally without becoming industrial centers. Culture does not independently raise production capacity. Building Era retains its existing restoration-cycle meaning; cultural ranks, criteria, and specific public unlocks remain to be designed.

### Seasons

Seasons are rhythm, not chores. Tentative timing: one Minecraft day approximately one season day; a seasonal year approximately five real days. Priorities: environment strongest, production/agriculture next, economic demand next, hard survival restrictions minimal.

Spring favors growth/building, summer production, autumn harvest/storage/trade, winter reduced agriculture plus crafts/trade. Seasons modify production/demand rather than owning another economy. Decay eligibility stays independent; seasonal ruin appearance communicates passing time. Avoid compulsory daily tasks, punitive survival, and intricate stock management.

## Proposed initial scope versus exclusions

The draft proposes progression, certification, basic facility production/commodity categories, Server Trade, basic Player Trade, regional modifiers, lightweight freight, settlement teleport, seasonal appearance/basic production modifiers, and a basic cultural/landmark framework. This is proposed overall scope, not one approved implementation milestone.

Exclude detailed population simulation, wages/happiness/taxes, full physical logistics, unlimited automation, large redstone farms as the economic core, complex finance, faction warfare, forced seasonal chores, and complex NPC-city AI.

## Existing decisions that must be reconciled

1. Factory Realm was removed. Facilities in the Main World are a new economic proposal, not permission to restore that realm/module or its machine systems.
2. Ender Chests and player state are shared. A carried-inventory teleport surcharge can be bypassed by global storage; nested portable containers also need consideration. Soft incentives and strict freight separation are different promises.
3. Era currently means qualifying Building restoration cycles, not elapsed seasons or Settlement economic level. Reclaim preserves history but resets the active baseline to current ruins; it does not restore an old intact building or increment Era.
4. Facility economics now propose bounded offline accounting without chunk loading or fake villagers. This remains distinct from physical world mutations, and production must not sustain Activity. Validity and input/output accounting need an explicit safety boundary.
5. Seasonal client visuals must respect the vanilla-client constraint. A normal 20-minute Minecraft day implies 360 seasonal days per five-real-day year (90 per season); sleeping/time commands require an explicit clock policy if the year is meant to stay fixed.

The proposal resolves the unloaded-decay loophole with an Activity-based production pause after a grace period, rather than waiting for physical damage. How this operating state interacts with earned economic stages, certificate validity, and in-progress jobs still requires design; automatic loss of progression or culture is not selected by the pause decision.

## Discussion questions from the draft

### Progression and certification

- Concrete upgrade requirements and experience within the selected four economic stages.
- Numerical and Facility-specific eligibility rules within the selected workspace/workstation/shared-housing/reachability/density baseline.
- Certification scope, review/reuse, and revocation workflow; automatic basic eligibility and reviewed higher production are selected.

### Production

- Concrete input classifications and recipes within the selected hybrid production model.
- Basic/processed commodity classifications.
- Shared production-budget units, allocation authority, and stage-specific amounts; the Settlement-wide ceiling is selected.

### Economy

- Regional specialization's relationship to climate and seasons.
- Bounded Server Trade dynamic-price complexity.
- Stall stocking, purchase/collection, and permission rules within the selected physical-market model.
- Allocation/replenishment of issuance budgets and detailed operating-budget controls; money sources/sinks and Owner-budgeted Manager spending are selected.

### Transport

- Route capacity and cost abstraction.
- Carried-cargo classifications and understandable fee presentation within the selected soft-incentive transport model; shared storage bypasses are accepted.
- Progression-based transport unlocks.

### Culture, seasons, and performance

- Cultural qualification and unlocks within the selected parallel capability model; no automatic production-capacity increase.
- First seasonal visual/production effects.
- Low-overhead scheduling, data ownership, and persistence boundaries.

No gameplay extension choices have yet been integrated into the accepted architecture.
