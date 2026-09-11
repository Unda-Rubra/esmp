# Current Handoff

## State

- The user-supplied `ARCHITECTURE_ADDENDUM.md` is merged into canonical `ARCHITECTURE.md`. The final razor review (§§21–23 of the source) governs conflicts with earlier provisional economy scope; all 18 explicit merge requirements are accounted for in `.agent/DECISIONS.md`.
- Target currency is now selected: one scarce integer ledger unit, non-negative Wallet/Treasury accounts, typed idempotent transactions and budgeted issuance. LandValueProfile-based paid Claims, personal subsidies, two-state certification and administrator-approved acquisition are next implementation work.
- Current runtime remains the existing Settlement/Parcel/GameClock/Activity implementation, including `f675a15`/`e823189` UI fixes. Core schema 1, Civilization schema 3, config 4; actual Claims are still numeric-only, naming uses the existing toggle, and ownership transfer still uses the existing two-party flow. This work changes documentation only.
- The core sequence is economic land foundations → Building identity/baseline/geometry → durable Decay/Repair/History → required Dynamic Settlement Cycle. Server Trade/lightweight Facility recognition, Resource automation and optional advanced systems follow afterward.
- All 30 pending player checks and prior acceptance facts remain. New economy/certification/acquisition behavior has not been added as completed gameplay. The supplied source addendum and runtime/server data were left intact.

## Completed

- Replaced the provisional currency-choice wait and area-only target pricing with one low-denomination ledger currency, separate display identity, non-negative integer accounts, TRANSFER/ISSUANCE/SINK, stable transaction IDs/reasons, budgeted issuance and development/service sinks.
- Added first-generation immutable minimal LandValueProfile, configurable land/portfolio/size/light-Activity pricing, founding-only own-nearby surcharge, fixed first-ever founding price 25 and non-resettable/non-transferable personal subsidies. Approximately 100 starting funds and other example scales remain tuning targets, not wealth caps.
- Added UNVERIFIED/VERIFIED eligibility/application/admin review and naming/public named-listing gates. Removed the global economic-stage ladder and deferred Cultural Standing.
- Replaced initial autonomous ownership acquisition with recipient acceptance, a simple land-based fee and administrator approval before atomic transfer. Kept private compensation social, treasury attached to the Settlement, and strong receiving-capacity/full revaluation models deferred.
- Reduced Building topology continuity to obvious identity preservation or retire/rediscover with lightweight links. Kept renovation safety/history, reduced collapse to DROP/CAVE_IN, made live villagers optional and Era lightweight. Facility begins as site identity/capability, not production/workforce/depot simulation.
- Deferred freight, public teleport and gameplay seasons; retained GameClock and optional calendar presentation. Permitted verified operator-driven Resource procedures before full automation. Kept outbox semantics without speculative event-class catalogues.
- Defined post-core Server Trade as finite normalized categories/lots with simultaneous budget/quota/stock admission, periodic discrete bands/spread and observational monitoring. Continuous pricing/hysteresis/automatic policy remain evidence-driven later options.
- Rewrote the canonical execution order and memory gates, updated Chinese onboarding summaries/agent invariants/navigation links, and marked existing acceptance checks as version-specific rather than pretending the target has shipped.

## Changed files

- `ARCHITECTURE.md` — merged economy rules, certification/acquisition, scope reductions, state ownership, glossary and §§27–28 execution order; obsolete positive commitments replaced instead of kept as parallel designs.
- `AGENTS.md` — selected target invariants, lean core dependencies and revised read-order heading.
- `README.md` — current numeric-only runtime versus accepted ledger target, key economic rules and Chinese execution summary.
- `.agent/DECISIONS.md` — dated rationale, supersession and an 18-item source-to-canonical merge map.
- `.agent/OPEN_QUESTIONS.md` — remaining concrete ledger/valuation/certification/migration gates, lean Building/Repair scope and post-core sequencing; retained useful research leads.
- `.agent/GAMEPLAY_EXTENSION_PROPOSAL.md` — canonical economy/land and execution-plan pointers; old currency-wait/near-term optional-system assumptions retired.
- `.agent/TEST_CHECKLIST.md` — planning-boundary notice; 30 existing unchecked player tests preserved, no unimplemented milestone added.
- `.agent/HANDOFF.md` — this current plan/state and verification evidence.

## Verification

- Read the full 1575-line addendum, including final razor amendments and the explicit 18-item merge checklist, and the affected canonical architecture/notes.
- Inline document validation passed all 18 merge requirements plus ledger invariants, superseded-positive-wording checks and Dynamic-cycle-before-Server-Trade ordering.
- Local Markdown targets/heading anchors and fenced blocks passed checks across seven updated documents preceding this handoff. Acceptance check count remains exactly 30.
- Source addendum SHA-256: `721755c0ef71c3208e36dfc8ccbd28d1bf25a884d60334f8a0d0e081b1050cc0`.
- No compilation, gameplay scenario, monetary grant, schema migration or server operation was performed for this documentation integration. Earlier runtime proof remains in history and the versioned player checklist.

## Next action

Specify the bounded integer account/transaction/budget contract, then implement the first ledger slice with non-negative balances, stable IDs, typed movements, authorization and audit. Budget enforcement belongs with the first issuance; starting funds/balance feedback follow. Resolve legacy grant eligibility before enabling onboarding, and legacy natural-land backfill before charging Claims.

## Risks or blockers

- Starting-fund treatment for existing players, monetary period/tier/carry-over behavior, transaction reversal rules and checked integer rounding need implementation choices. The currency model itself is settled; do not reopen physical-item versus ledger currency.
- LandValueProfile cannot be reconstructed honestly by silently sampling already player-modified legacy chunks. Define approved backfill/versioning and missing-profile behavior before paid Claims.
- Governor payer versus assessed-player/subsidy identity, distance metric, quote revisions, current naming-toggle migration and pending-transfer migration must be explicit. Preserve history and avoid resetting personal subsidy usage.
- Final razor choices supersede earlier alternatives: no permanent peer receiving ceiling, no mandatory continuous price/hysteresis model, no automatic monetary policy, no perfect Building split/merge solver, no villager prerequisite, and no optional production/transport/season dependency ahead of Dynamic Settlement Cycles.
