# Gameplay Extension — Discussion Only

Source: user's “ESMP — Gameplay Systems Architecture Extension, Draft v0.1”. This is a summary for discussion continuity, not an accepted implementation specification. Do not add these systems to `ARCHITECTURE.md` or its roadmap until the user explicitly approves integration. The proposed Activity implementation is paused for this discussion.

## Proposed direction

The economy serves the civilization loop: Explore → Settle → Build → Produce → Trade → Develop → Prosper → Decline → Ruin → Reclaim. Settlement development, useful buildings, infrastructure, and history should outperform compressed villager captivity and large redstone automation. Keep rules understandable, physical facilities visible in the world, and economy/transport/seasons/decay modular.

## Agreed within this proposal — not yet integrated into architecture

- Production: hybrid, with bounded offline accrual from saved valid Facility state and no chunk loading. Basic renewables use certified capacity/workers/time; processed goods consume inputs. Available inputs and output storage cap accrual. Production never grants Activity. Scarce/basic-metal exceptions remain to be classified.
- Currency: ledger-based, with distinct player wallets and Settlement treasury accounts. Budgeted Server Trade purchases and budgeted exploration/event rewards create currency; player trades only transfer existing balances. Treasury authority and budget allocation/replenishment remain open.
- Teleport/freight: soft incentives preserve shared Ender Chests and accept manual storage bypasses. Teleport/market service fees burn currency. Freight pays the route operator plus a separate disclosed system fee that is burned. No recurring wages or membership taxes are introduced. Exact cargo weights and fees remain deferred.

These selections refine the discussion draft only. No code, accepted architecture, or implementation roadmap has been changed.

## Proposed systems

### Settlement progression

Four candidate stages: I Settlement (traditional survival start), II Village (community, public buildings, basic industries, Activity and certification), III Town (specialization, stable trade and transport), IV City (multi-industry/cultural network and server-wide importance). Production capacity and public infrastructure unlock with development; later growth increasingly rewards server significance rather than raw output. Exact requirements/unlocks are not settled.

### Facilities and certification

Facility + Settlement + Villager produces goods or services. Examples: Farm/food, Lumber Yard/timber, Quarry or Stone Works/stone, Smithy/metal goods, Bakery/processed food, Port/maritime capacity, Railway Station/land transport.

Automatic checks may cover space, work area, accommodation, workstations, mobility/reachability, density, environment, and Settlement relationship. The aim is rejecting obvious prisons/exploits, not algorithmic aesthetic scoring. Administrative review handles reasonable building purpose, exploits, and consistent server standards, with review/revoke capability. First certification may need manual review and later upgrades mainly automatic checks; workflow remains open.

Facility productivity is constrained by Settlement capability rather than unlimited independent upgrades. The discussion selected hybrid production; concrete resource classifications, recipes, and capacity rules remain open.

Production may accrue while chunks are unloaded using saved valid Facility state, without pretending villagers continue ticking or loading chunks to make them work. Available inputs and output storage bound accrual. Validity changes, reservation/consumption of inputs, output delivery, and replay-safe accounting still need design; offline production does not contribute Activity.

### Goods and specialization

- Basic resources: timber, stone, food, clay, glass, basic metal; stable affordable supply should support construction.
- Processed goods: building materials, masonry/bricks, processed food, tools/components; intended to create industry chains and trade.
- Rare goods: Elytra, Dragon Egg, Wind Burst III, special equipment/materials, event rewards, historical relics; exploration/events/history rather than unlimited ordinary Facility production.

Regions modify efficiency rather than hard-lock goods. Candidate influences include climate, terrain, crops, trees, livestock, and special city/NPC conditions. Mountains favor stone/metal, plains food/livestock, northern areas forestry, coasts fishing/shipping. Example multipliers 1.5/0.8 are illustrative, not selected tuning.

### Trade

Server Trade is a bounded basic-market backstop and economic anchor, not an unlimited quest-money shop. Global supply/demand may produce Cheap/Normal/Expensive bands with price limits. Player Trade may start with stalls/orders/procurement/bulk and rare-goods exchange; complex finance is excluded. Ledger currency has player wallets and Settlement treasuries. New currency comes from budgeted server purchases and exploration/event rewards. Market stock, price behavior, budget allocation/replenishment, and treasury authority remain open.

### Transport and teleport

Trade Routes abstract origin, destination, mode, capacity, and cost, backed by real roads/rails/ports. Do not simulate every box travelling physically.

Separate ordinary personal movement, progression-unlocked Settlement Teleport, and long-term bulk freight. Proposed teleport fee = base cost + cargo cost, intended to favor people teleporting and goods using routes. Exact cargo classification, containment handling, and prices remain open.

Freight payment has two explicit parts: operator revenue (a transfer) and a disclosed system fee (currency destruction). Teleport and market service fees are also sinks; ordinary player trade payments are transfers, not minting or burning. No recurring wages or membership taxes are added.

### Culture and history

Economic development (production/trade/logistics) and cultural development (architecture/history/landmarks) should both matter. Nonproductive castles, ruins, and monuments can gain cultural/server-attraction value without manufacturing productivity. Whether these are independent advancement tracks remains open.

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

## Discussion questions from the draft

### Progression and certification

- Settlement level count and each stage's experience.
- Minimal hard Facility eligibility requirements.
- Administrator review authority, workflow, and revocation boundaries.

### Production

- Concrete input classifications and recipes within the selected hybrid production model.
- Basic/processed commodity classifications.
- How Settlement capability limits Facility production.

### Economy

- Regional specialization's relationship to climate and seasons.
- Bounded Server Trade dynamic-price complexity.
- Initial Player Trade mechanism.
- Treasury authority and allocation/replenishment of issuance budgets; bounded purchases/rewards and service-fee sinks are selected.

### Transport

- Route capacity and cost abstraction.
- Carried-cargo classifications and understandable fee presentation within the selected soft-incentive transport model; shared storage bypasses are accepted.
- Progression-based transport unlocks.

### Culture, seasons, and performance

- Cultural progression alongside or within economic levels.
- First seasonal visual/production effects.
- Low-overhead scheduling, data ownership, and persistence boundaries.

No gameplay extension choices have yet been integrated into the accepted architecture.
