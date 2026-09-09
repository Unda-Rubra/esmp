# Current Handoff

## State

- Java 25 / Paper 26.2 build 121 plugin under `xdu.undarubra.esmp`; four Gradle modules and one deployable JAR. Git milestone `e628b60` introduced the tested Settlement domain; commit coherent milestones regularly, without pushing unless requested.
- Playable Settlements now provide creation/contiguous claiming, fixed roles, expiring offers, ownership transfer, leave/removal/demotion, rename, confirmed abandonment, history, event publication, action-specific protection, and private borders.
- Core world schema remains version 1. Civilization independently uses `civilization_schema` version 1 with owning tables, offers, and historical/outbox records in the same SQLite database.
- Building discovery, Activity scoring, decay/repair, automatic release, and Resource reset gameplay remain disabled. Factory remains out of scope.
- Native development server is `.run/dev`, loopback `127.0.0.1:25579`, online authentication enabled. Test servers are stopped. Native Minecraft 26.2 player/UI verification is the user's next step.

## Completed

- Added database-only transactional work on the existing bounded SQLite worker; Civilization owns its SQL and invariants.
- Enforced edge connectivity, cap, exclusivity, last-chunk safety, consent, live authority revalidation, atomic transfer, and revision-bound abandonment; retained released history and idempotent outbox acknowledgement.
- Added main-thread index publication with pending-territory reservations and early rejection of unauthorized command admissions. Open containers close on permission revocation.
- Added Paper protection for direct item/block/entity/container actions and indirect fluid/piston/hopper/explosion/growth effects, with private 10-second border rendering capped at 256 particles per update.
- Added fatal-persistence stop behavior and disallowed hot reload instead of exposing unprotected claims. Updated README's launch, commands, configuration, and two-account manual checklist.

## Changed files

- `esmp-core`: `SqlWork` and domain transaction entry point on the existing worker.
- `esmp-civilization`: Settlement models, service/schema/history/outbox, spatial index, and behavioral tests.
- `esmp-plugin`: lifecycle integration, commands, protection, visualization, committed-event notification, manifest and settings.
- `scripts/run-dev.sh`: zero vanilla spawn protection for newly created local test servers, so it does not mask Settlement role testing.
- `ARCHITECTURE.md`, `README.md`, `AGENTS.md`, `.agent` memory: implemented scope, publication/permission boundaries, verification and follow-up.

## Verification

- Final `./gradlew clean build --no-daemon --console=plain` with JDK 25 passed. XML results: 14 tests, 0 failures/errors/skips (5 Core storage, 7 Settlement transaction/workflow, 2 spatial-index regressions).
- Real Paper 26.2 server with three protocol-connected test accounts passed 28 scenarios: private border packets, denied Visitor/implicit-OP modification, explicit bypass, public doors, bucket/brush restrictions and Owner brushing, containers, invitations/revocation, topology and Manager authority, fluids/pistons/hoppers, explosions, Resource-world rejection, transfer, abandonment and reclaim.
- A real server restart preserved owned IDs, owners, members, claimed chunks and acknowledged outbox rows. Subsequent live-client checks passed restored roles, denied Visitor placement, Member placement and actual inventory withdrawal, denied Visitor entity damage and allowed Owner damage.
- Client automation used Mineflayer 4.39.0 on protocol 1.21.11 through temporary ViaVersion/ViaBackwards 5.11.0, because the library lacks full 26.2 data. Its stale dust decoder was corrected in the isolated harness using the actual server INT-color/FLOAT-scale codec. Test assertions used authoritative server conditions/NBT rather than client prediction. This is not a native 26.2 visual-rendering test.
- Native Paper without translators started successfully through `ESMP_ACCEPT_EULA=true ./scripts/run-dev.sh`: `Settlements=0 | protection=true`; the original developer MAIN instance UUID remained `75fa0879-8da5-427f-96cf-6af9c900372a`. Clean console `stop` exited 0.
- Artifact inspection found the playable classes/permissions and no ViaVersion or JUnit classes bundled. Throwaway client scripts and temporary translator JARs were removed after testing; normal `.run/dev` was never changed to offline mode.

## Next action

Run README's native Minecraft 26.2 checklist with two real accounts on `127.0.0.1:25579`. Start with `/settlement create Oakfall`, `/settlement view`, and a teammate invitation; verify actual client rendering and access before production deployment.

## Risks or blockers

- No desktop/native 26.2 client rendering was inspected; protocol packet checks and native server boot do not substitute for that manual check.
- Protection is spatial, not a new PvP, loose-item ownership, or natural entity-movement system. Trusted console/world-editing plugins require their own controls. Other-plugin interoperability needs deployment-specific review.
- Outbox acknowledgement confirms publication; third-party listeners must deduplicate and handle their own failures/replay from retained history. Automated block-mutation recovery/accounting remains a later gate.
- Mojang public-key/version requests intermittently timed out in the environment. Keep normal developer/production online authentication enabled; only the isolated test fixture used offline accounts.
- Local JDK/tooling and scratch server data remain ignored. No production world data was deleted or used as a destructive fixture.
