# Gameplay Extension — Discussion Only

Source: user's “ESMP — Gameplay Systems Architecture Extension, Draft v0.1”. This is a summary for discussion continuity, not an accepted implementation specification. Do not add these systems to `ARCHITECTURE.md` or its roadmap until the user explicitly approves integration. The proposed Activity implementation is paused for this discussion.

## Proposed direction

The economy serves the civilization loop: Explore → Settle → Build → Produce → Trade → Develop → Prosper → Decline → Ruin → Reclaim. Settlement development, useful buildings, infrastructure, and history should outperform compressed villager captivity and large redstone automation. Keep rules understandable, physical facilities visible in the world, and economy/transport/seasons/decay modular.

## Agreed within this proposal — not yet integrated into architecture

- Production: hybrid, with bounded offline accrual from saved valid Facility state and no chunk loading. Basic renewables use capacity/workers/time; processed goods consume inputs. Inputs, output storage, and the shared Settlement production budget cap accrual. Insufficient Activity eventually pauses production after a configurable grace period; stored goods and unused inputs remain. Production never grants Activity.
- Currency: ledger-based, with distinct player wallets and Settlement treasuries. Budgeted server purchases/rewards create money; player trades transfer it. Owner controls treasury withdrawals and operating budgets. Managers may make audited operational payments within approved budgets; Members cannot spend treasury funds. Budget allocation/replenishment remains open.
- Teleport/freight: soft incentives preserve shared Ender Chests and accept manual storage bypasses. Teleport/market service fees burn currency. Freight pays the route operator plus a separate disclosed system fee that is burned. No recurring wages or membership taxes are introduced. Exact cargo weights and fees remain deferred.
- Progression: four economic stages on the existing Settlement aggregate—Settlement, Village, Town, City. Cultural standing is a parallel capability track for landmarks/public significance; it does not independently raise production capacity or redefine Building Era.
- Certification: basic production requires usable workspace, workstations, reachable shared housing, and reasonable density; higher tiers require administrator certification. Relevant changes trigger rechecks and suspend affected capabilities only. Basic production may continue when its checks still pass. Goods and unused inputs are preserved; decorative edits do not automatically require staff review.

These selections refine the discussion draft only. No code, accepted architecture, or implementation roadmap has been changed.

## Proposed systems

### Settlement progression

Four economic stages are selected: I Settlement (traditional survival start), II Village (community), III Town (specialization), IV City (a significant civilization node). These are development labels on the existing Settlement aggregate, not new ownership entities. The draft's housing, public-space, industry, trade, transport, Activity, and historical requirements remain candidates for concrete progression criteria. Exact upgrade requirements and unlocks are not settled.

### Facilities and certification

Facility + Settlement + Villager produces goods or services. Examples: Farm/food, Lumber Yard/timber, Quarry or Stone Works/stone, Smithy/metal goods, Bakery/processed food, Port/maritime capacity, Railway Station/land transport.

Basic Facility production starts after automatic checks for usable work area, valid workstations, reachable housing, and reasonable worker density. Housing may be elsewhere in the same Settlement. Higher production tiers require administrator certification. Relevant changes trigger automatic revalidation: invalid higher-tier certification pauses that capability, while basic production may continue if basic requirements still pass. Failure of basic requirements also blocks basic production. Material scope changes or exploits require staff review, not every decorative edit. Preserve goods and unused inputs. Numerical minima, type-specific requirements, and exact certificate scope/review records remain design work.

Each economic stage supplies a shared Settlement production-capacity budget allocated among Facilities. Combined allocations must not exceed that budget; adding Facilities or villagers does not increase the budget by itself. Facility tiers remain bounded by economic capability and certification. Cultural standing alone bypasses neither ceiling. Exact budgets, capacity units, allocation authority, and resource/recipe classifications remain open.

Production may accrue while chunks are unloaded from saved valid state, without loading chunks or pretending villagers keep ticking. Inputs and output storage bound accrual. When insufficient Activity persists beyond a configurable grace period, new production pauses until meaningful activity recovers, independently of visible ruin. Stored goods and unused inputs are preserved. Paused time must not become catch-up output after recovery; the exact grace period, eligibility thresholds, and in-progress-job/accounting mechanics remain to be designed.

Facility inputs and outputs use bounded ledger stock accessed through a real depot at the Facility. Depositing removes physical items into stock; withdrawing returns physical items. Offline accounting operates on that stock, not a duplicate snapshot of items still present in a chest. Deposit/withdrawal recovery and stock-access permissions remain design work; the physical interface must preserve the vanilla-client experience.

### Goods and specialization

- Basic resources: timber, stone, food, clay, glass, basic metal; stable affordable supply should support construction.
- Processed goods: building materials, masonry/bricks, processed food, tools/components; intended to create industry chains and trade.
- Rare goods: Elytra, Dragon Egg, Wind Burst III, special equipment/materials, event rewards, historical relics; exploration/events/history rather than unlimited ordinary Facility production.

Ordinary vanilla crafting remains available at every Settlement stage. Progression gates Facility tiers and automated throughput, not players' access to normal recipes.

Market category and production method are separate: a basic commodity can still require processing (for example, glass consumes sand/fuel). Metal-producing Facilities require supplied ores or other eligible mined inputs; capacity and time alone do not create new metal. This is a recipe-input rule, not a selected item-provenance tracker or ban on vanilla metal farms.

Server Trade initially covers selected basic construction/survival commodities, including simple processed essentials where appropriate. Rare goods and most specialized products belong primarily to player markets. NPC buying and selling have separate eligibility lists: selling an essential to players does not imply purchasing it from them. The exact lists, including treatment of vanilla farm outputs, remain open.

Regional production advantages use stable natural biome and terrain profiles, with seasonal modifiers applied separately. They change efficiency, not whether a good is available. Seasonal cosmetic changes must not redefine the underlying region or create a specialization exploit. Mountains/forests/plains/coasts remain illustrative profiles; exact mappings and multipliers are deferred.

### Trade

Server Trade is a bounded basic-commodity backstop, not an unlimited quest-money shop. It uses global Cheap/Normal/Expensive price bands, adjusted at coarse configurable intervals from stock and demand, with fixed price bounds. Stock is finite; purchases consume it, and eligible player sales plus controlled system replenishment restore it. Temporary shortages are possible, so this is not a promise of unlimited immediate availability.

Initial Player Trade uses physical market stalls with stocked goods and posted prices: buyers visit to purchase and collect, rather than receiving global instant delivery. Orders, procurement, and bulk fulfillment can be considered later; they are not implicitly included in the initial stall choice.

Ledger currency has player wallets and Settlement treasuries. New currency comes from budgeted server purchases and exploration/event rewards. Owner controls treasury withdrawals and operating budgets; Managers may make audited operational payments within approved budgets, and Members cannot spend treasury money. This is bounded delegated trust, not a guarantee against self-dealing. Exact price curves/buy-sell spreads, quote and bulk-transaction handling, stock/replenishment amounts, issuance-budget allocation, and detailed spending controls remain open.

### Transport and teleport

Trade Routes connect physical depots using timed ledger shipments, not travelling cargo entities. Dispatch reserves cargo and route throughput. Goods consume simple stack-equivalent bulk units, counting nested cargo contents rather than treating a filled portable container as one empty item. Transport mode and distance inform the quoted fee and travel time. Exact bulk weights, throughput windows, rates, and payment-settlement mechanics remain deferred.

Routes require real roads, rails, or port connections. Qualification combines automatic endpoint/basic checks with initial administrator review of the physical connection. Relevant infrastructure edits or invalid endpoints trigger targeted revalidation rather than periodic whole-route scans. The exact registered corridor, land permissions, and invalidation coverage remain design work.

Route damage or invalidity blocks new departures but does not destroy or automatically pause already-dispatched cargo; those shipments retain their planned arrival. If the destination is unavailable, hold the cargo for the original recipient. Delivery is tied to explicit recipient identity, not whoever later controls the destination coordinates. Endpoint ownership changes, recovery access, and storage reservations must be designed so undeliverable queues do not become an unlimited-storage bypass.

Separate ordinary movement, public Settlement Teleport, and bulk freight. Economic stage II (Village) qualifies for basic teleport infrastructure; an independently certified cultural landmark may also qualify without raising production capacity. Higher economic stages can support stronger transport capabilities, with exact unlocks still to be defined. Teleport charges base plus carried-cargo cost under the selected soft-incentive policy; exact categories, fee bands, and prices remain open.

Freight payment has two explicit parts: operator revenue (a transfer) and a disclosed system fee (currency destruction). Teleport and market service fees are also sinks; ordinary player trade payments are transfers, not minting or burning. No recurring wages or membership taxes are added.

### Culture and history

Economic development and cultural standing are parallel capabilities. Economic stages govern production capacity; architecture/history/landmark achievements grant independent public significance. Nonproductive castles, ruins, and monuments can develop culturally without becoming industrial centers. Culture does not independently raise production capacity. Building Era retains its existing restoration-cycle meaning; cultural ranks, criteria, and specific public unlocks remain to be designed.

### Seasons

Seasons are rhythm, not chores. The user selected a Minecraft-day calendar and clarified: **one Minecraft day equals one calendar day; a year has 365 days, or 366 in a leap year; the starting year is 0 A.D., followed by 1 A.D., 2 A.D., etc.** This supersedes the tentative fixed five-real-day year. Sleeping and world-time changes can affect pacing; exact leap-year rules, season boundaries, canonical world-day source, and backward-time handling remain to be specified.

Initial presentation uses bounded native effects: weather, particles, and limited reversible snow/vegetation in eligible loaded areas, preserving builds and restoration baselines. Full seasonal grass/foliage palette changes are explicitly a future feature, with resource-pack/client-biome techniques and compatibility requirements deferred rather than assumed.

Spring favors growth/building, summer production, autumn harvest/storage/trade, winter reduced agriculture plus crafts/trade. Seasons modify production/demand rather than owning another economy. Decay eligibility stays independent; seasonal ruin appearance communicates passing time. Avoid compulsory daily tasks, punitive survival, and intricate stock management.

## Proposed initial scope versus exclusions

The draft proposes progression, certification, basic facility production/commodity categories, Server Trade, basic Player Trade, regional modifiers, lightweight freight, settlement teleport, seasonal appearance/basic production modifiers, and a basic cultural/landmark framework. This is proposed overall scope, not one approved implementation milestone.

Full seasonal palette support is a deferred future feature, not part of the initial native-effects scope.

Exclude detailed population simulation, wages/happiness/taxes, full physical logistics, unlimited automation, large redstone farms as the economic core, complex finance, faction warfare, forced seasonal chores, and complex NPC-city AI.

## Existing decisions that must be reconciled

1. Factory Realm was removed. Facilities in the Main World are a new economic proposal, not permission to restore that realm/module or its machine systems.
2. Ender Chests and player state are shared. A carried-inventory teleport surcharge can be bypassed by global storage; nested portable containers also need consideration. Soft incentives and strict freight separation are different promises.
3. Era currently means qualifying Building restoration cycles, not elapsed seasons or Settlement economic level. Reclaim preserves history but resets the active baseline to current ruins; it does not restore an old intact building or increment Era.
4. Facility economics now propose bounded offline accounting without chunk loading or fake villagers. This remains distinct from physical world mutations, and production must not sustain Activity. Validity and input/output accounting need an explicit safety boundary.
5. Seasonal visuals retain the vanilla-client constraint. The chosen 365/366-Minecraft-day calendar starts at 0 A.D.; at normal 20-minute days a non-leap year is five real days plus one hour forty minutes, not a fixed five days. Calendar-source, time-rewind, and Resource-reset behavior must be consistent with production/history accounting.

The proposal resolves the unloaded-decay loophole with an Activity-based production pause after a grace period, rather than waiting for physical damage. How this operating state interacts with earned economic stages, certificate validity, and in-progress jobs still requires design; automatic loss of progression or culture is not selected by the pause decision.

## Discussion questions from the draft

### Progression and certification

- Concrete upgrade requirements and experience within the selected four economic stages.
- Numerical and Facility-specific eligibility rules within the selected workspace/workstation/shared-housing/reachability/density baseline.
- Exact certificate scope, review records, and Facility-specific revalidation triggers; affected-capability suspension and continued eligible basic production are selected.

### Production

- Concrete recipes and eligible mined inputs; ordinary vanilla crafting remains unrestricted.
- Exact basic-commodity buy/sell catalogue and specialized/rare exclusions; market category does not imply input-free production.
- Shared production-budget units, allocation authority, and stage-specific amounts; the Settlement-wide ceiling is selected.

### Economy

- Natural biome/terrain profile mappings and modifier values, with seasonal modifiers kept separate from base regional identity.
- Exact global price-band/quote behavior, buy-sell spreads, stock quantities and replenishment; global bounded bands, finite stock, and separate NPC purchase/sale lists are selected.
- Stall stocking, purchase/collection, and permission rules within the selected physical-market model.
- Allocation/replenishment of issuance budgets and detailed operating-budget controls; money sources/sinks and Owner-budgeted Manager spending are selected.

### Transport

- Bulk weights, throughput windows, mode/distance fee and time rates, land permissions, and cargo/payment recovery within the selected stack-equivalent route-quote model.
- Carried-cargo classifications and understandable teleport fee presentation under soft incentives; shared storage bypasses remain accepted.
- Higher-stage transport capabilities and cultural destination qualification; Village-or-certified-landmark basic teleport access is selected.

### Culture, seasons, and performance

- Cultural qualification and unlocks within the selected parallel capability model; no automatic production-capacity increase.
- Leap-year rule, season boundaries, clock reconciliation, and concrete bounded native effects; full seasonal palette compatibility is a deferred future feature.
- Low-overhead scheduling, data ownership, and persistence boundaries.

No gameplay extension choices have yet been integrated into the accepted architecture.

## Integration readiness

The major gameplay directions are now recorded for a consolidated review. Remaining details are not implicitly approved defaults: material catalogues and numerical tuning, precise progression/certification criteria, clock reconciliation, stock/payment/ownership recovery, and safe bounded world effects must be resolved before their respective features are enabled.

Adding these directions to `ARCHITECTURE.md` and its implementation roadmap still requires explicit user approval. No extension system is implemented by this discussion.
