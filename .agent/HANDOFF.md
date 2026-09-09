# Current Handoff

## State

- Git repository on `main`; commit coherent verified milestones regularly. No remote/push requested.
- Runnable Java 25 foundation: `xdu.undarubra.esmp`, four Gradle modules (`esmp-core`, `esmp-civilization`, `esmp-resource`, `esmp-plugin`), one deployable JAR.
- Current gameplay scope is permanent Civilization/Main plus renewable Resource worlds. Factory/industrial realm design and implementation are removed, not retained as a dormant module. No new redstone ban is imposed on the remaining worlds.
- Pins remain Gradle 9.7.1, Paper 26.2 build 121 / API `26.2.build.121-stable`, SQLite JDBC 3.53.4.0, JUnit Jupiter 5.12.2; JDK verification used Temurin 25.0.4.1+1.
- SQLite schema remains version 1. Snapshots/register operations use only definitions supplied by current modules; removed-definition history remains stored without active support.
- Gameplay claims/protection, Building discovery, decay/repair, durable outbox/jobs, and Resource resets remain unimplemented. `/esmp status` reports foundation scope explicitly.

## Completed

- Removed the Factory module, build dependency, world definition, default configuration, and status entry.
- Added one-time configuration migration: unversioned/version-1 files are backed up to `config-v1.yml`, obsolete `worlds.FACTORY` is removed, and version 2 is saved. Version-2 unknown roles are still rejected.
- Made registry definition retirement generic and non-destructive. Removed definitions cannot register, their old Bukkit UUIDs cannot be reassigned silently, and historical identities survive.
- Removed Factory architecture, schemas, permission policy, runtime design, and development milestone; renumbered remaining architecture to 28 sections and 44 current decisions. Recorded the team's workload/balance/performance rationale.
- Updated README, agent rules, open questions, and decisions. No physical world folder or historical database row was deleted.

## Changed files

- `settings.gradle.kts`, `esmp-plugin/build.gradle.kts`, deleted `esmp-factory/`: four-module build.
- `esmp-plugin/src`: current definitions/status, version-2 configuration and legacy migration.
- `esmp-core/src`: active-definition snapshot/registration boundary; regression for retired identities and strengthened transactional rollback check.
- `ARCHITECTURE.md`, `README.md`, `AGENTS.md`, `.agent` memory: current scope, migration behavior, roadmap, and rationale.

## Verification

- `./gradlew clean build --write-locks --no-daemon --console=plain`: passed after the runtime cutover; five storage tests passed with zero failures/errors.
- Final `./gradlew clean build --no-daemon --console=plain`: passed with locks enforced.
- Actual Paper upgrade using the existing unversioned config and five stored definitions: migrated once, READY with schema 1 / four active definitions / one active-definition instance. MAIN retained UUID `75fa0879-8da5-427f-96cf-6af9c900372a`.
- Exact-byte comparison verified `config-v1.yml` preserves the original config. SQL comparisons verified every preexisting definition and instance row remained unchanged.
- Reintroduced FACTORY under config version 2: Paper rejected the unknown definition and disabled ESMP. Removed the test entry and restarted successfully without repeating migration; backup and historical DB rows remained unchanged.
- Artifact/document checks passed: no Factory classes/module/config in the deployable JAR; 28 numbered sections and 44 decisions; valid local links and balanced fences. Historical-removal notes and one-time migration code are the intentional remaining references.
- All smoke servers stopped with exit 0. Removed the obsolete module's empty directories and stale build artifacts; `.run/dev/` retains the valid version-2 development environment.

## Next action

Implement the first Settlement persistence/protection slice. Define owning tables/application APIs, transaction/cache publication, and Paper 26.2 protection mappings before enabling claims. Do not restore removed industrial scope from Git history without explicit approval.

## Risks or blockers

- Config backups and removed-role DB history are retained deliberately; they do not imply runtime support. No automatic physical world deletion is provided.
- Paper/JOML and SQLite may emit upstream JDK warnings; the initial foundation already documents them. Claims are still not implemented: this JAR must not be advertised as protecting player construction.
- Local JDK/tooling and server data are ignored. Set JAVA_HOME to JDK 25; the tested development launcher remains `ESMP_ACCEPT_EULA=true ./scripts/run-dev.sh` after EULA acceptance.
