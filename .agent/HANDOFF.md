# Current Handoff

## State

- Playable Java 25 / Paper 26.2 Settlements remain implemented under `xdu.undarubra.esmp`; four modules, one JAR, SQLite Core/Civilization schemas both version 1.
- Local development defaults and existing `.run/dev/server.properties` now use `online-mode=false` and `enforce-secure-profile=false`, still bound to `127.0.0.1:25579`.
- The user had already changed the launcher's online-mode default; that edit was preserved and completed rather than overwritten.
- Automatic movement ActionBars now compare Settlement identities. Same-Settlement chunk crossings and wilderness-to-wilderness movement are silent; entry/exit messages occur once. Explicit command feedback remains.
- The user's unmanaged server was running on port 25579 during this work and was deliberately not stopped or hot-reloaded. Full stop/restart is required to load the changed properties and newly built JAR.

## Completed

- Updated existing and new local-server authentication settings for multiple named offline test clients.
- Replaced chunk-based movement announcements with previous/next Settlement ID comparison.
- Applied the same comparison to teleports and respawns; removed the duplicate PlayerChangedWorldEvent announcement path.
- Updated architecture, README, and decision rationale, including offline UUID/impersonation risks.

## Changed files

- `scripts/run-dev.sh` — offline, unsigned-profile local defaults, preserving the loopback bind.
- `esmp-plugin/.../ClaimVisualization.java` — transition-only movement feedback.
- `ARCHITECTURE.md`, `README.md`, `.agent/DECISIONS.md` — behavior and restart/security guidance.
- `.agent/HANDOFF.md` — current state and verification.
- Ignored `.run/dev/server.properties` — updated the existing local server as well as the template.

## Verification

- Gameplay extension work is discussion-only: preserved the user's draft summary and selected hybrid production, ledger accounts, and soft freight incentives in `.agent/GAMEPLAY_EXTENSION_PROPOSAL.md`. No code or accepted architecture/roadmap was changed, and no gameplay checks were rerun to reconfirm the user's Settlement test report.
- Follow-up proposal check: bounded offline accrual, budgeted purchases/rewards, and split freight payments are recorded consistently. Production timing is resolved within the proposal; treasury authority and issuance-budget mechanics remain open. No code or accepted-roadmap changes.
- Progression/certification discussion selected four economic stages, auto-checked basics with reviewed higher production, and parallel cultural capabilities. These are proposal-only refinements, not code or accepted-roadmap changes.
- Facility discussion selected shared reachable housing with functional/density checks, a shared Settlement production budget, and Activity-based pause after a configurable grace period. Stocks and unused inputs are preserved; exact thresholds and operational/accounting details remain open.
- Stock/trade discussion selected bounded Facility ledger stock at physical depots, physical player market stalls, and Owner-controlled treasury withdrawals/budgets with budgeted Manager operational spending. Recovery, stock permissions, and detailed spending controls remain open.
- Commodity discussion selected unrestricted vanilla crafting, mined inputs for Facility metal production, and basic-commodity-only Server Trade. Exact buy/sell lists and treatment of vanilla farm outputs remain open; no provenance tracker or farm ban is implicitly approved.
- Server Trade discussion selected global bounded price bands, finite stock with controlled replenishment, and separate NPC buying/selling eligibility lists. Temporary shortages are accepted; exact lists, spreads, quantities, and quote behavior remain open.
- Freight discussion selected timed ledger shipments, initial physical-connection review plus targeted revalidation, and disruption blocking new departures without deleting in-transit goods. Unavailable destinations hold cargo for the original recipient; recovery/storage and payment details remain open.
- Seasons discussion selected Minecraft-day counting: one game day equals one calendar day, 365/366 days per year, starting at 0 A.D. Natural biome/terrain specialization and bounded native effects are selected; full seasonal palette is a deferred future feature. Exact leap/season boundaries and time-rewind handling remain open.
- `./gradlew build --no-daemon --console=plain` with JDK 25: passed.
- Live Paper 26.2 fixture on port 25580 passed 10 ActionBar cases: entering a different Settlement, same-Settlement cross-chunk teleport, same-chunk teleport, adjacent same-owner identity change, exit, wilderness chunk movement, actual walking over an internal chunk edge, single cross-world exit/entry, and same-Settlement respawn.
- Observed actual `action_bar` packets using the temporary protocol-1.21.11 test client through ViaVersion/ViaBackwards 5.11.0. The bot's high-level ActionBar event omitted that packet type, so the test read the decoded packet directly. This is not a native desktop rendering check.
- Verified both script and existing properties contain offline mode, disabled secure-profile enforcement, and loopback binding. Temporary test script and translator JARs were removed after the fixture stopped with exit 0.

## Next action

Activity implementation remains paused for `.agent/GAMEPLAY_EXTENSION_PROPOSAL.md`. Calendar units and epoch are clarified as one Minecraft day per calendar day, 365/366-day years, starting 0 A.D. Next finish major transport unlock/capacity and certification workflow boundaries, then consolidate the proposal for explicit integration approval. Do not implement or change the accepted roadmap yet.

## Risks or blockers

- Offline mode permits name impersonation and is only for the loopback development server. Do not expose it publicly or carry it into production authentication policy.
- Online/offline player UUIDs differ. Existing inventories, OP entries, and Settlement ownership are not reassigned automatically by matching names; migration requires an explicit mapping and backup. Existing data was not deleted.
- Settlement testing is now user-confirmed; do not rerun checks merely to confirm their report. Their exact test coverage was not enumerated. Keep the manual checklist for future regressions.
- The extension is discussion-only. Existing Ender Chest sharing, Building Era semantics, and current-world reclaim baselines remain accepted constraints unless explicitly revised. Chat ergonomics remain deferred.
