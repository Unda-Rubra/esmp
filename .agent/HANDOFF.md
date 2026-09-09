# Current Handoff

## State

- Git repository on `main`; initial commit `09766ae` establishes repository policies and canonical `ARCHITECTURE.md`. No remote configured or push requested. Commit coherent milestones regularly.
- Runnable Java 25 foundation uses package/group `xdu.undarubra.esmp`, five Gradle modules, and one deployable `esmp-plugin/build/libs/esmp-0.1.0-SNAPSHOT.jar`.
- Pins: Gradle Wrapper 9.7.1, Paper 26.2 build 121 / API `26.2.build.121-stable`, SQLite JDBC 3.53.4.0, JUnit Jupiter 5.12.2. Verification JDK: Temurin 25.0.4.1+1.
- Implemented: domain-owned world definitions, bounded serial SQLite I/O, schema version 1, persistent physical world identity, configuration validation, Paper lifecycle/world-load adapter, and `/esmp status`.
- Not implemented: claims/protection, membership gameplay, Building discovery, decay/repair, durable mutation jobs/outbox, Resource resets, or Factory gameplay. The status command states this explicitly.

## Completed

- Added Git ignore/text/editor policies and migrated all canonical architecture links.
- Created one Paper composition root and three independent realm-definition modules over a Paper-free core.
- Added transactional JDBC initialization, schema-version rejection, durable instance UUIDs, binding-conflict rejection, and bounded graceful worker shutdown.
- Added four storage regression tests and a checksum-verified loopback Paper development launcher requiring explicit EULA acceptance.
- Verified actual Paper boot, status, clean shutdown/restart, and fail-closed duplicate world mapping. Restored valid configuration and stopped the server.

## Changed files

- Root Gradle/settings/properties, wrapper, module build files, and dependency lockfiles: reproducible Java 25 build and one-JAR packaging.
- `esmp-core/src`: world records, SQLite store, and behavioral regression tests.
- `esmp-civilization/src`, `esmp-resource/src`, `esmp-factory/src`: five domain-owned world definitions.
- `esmp-plugin/src`: Paper bootstrap, config/manifest, loaded-world binding, and admin status command.
- `scripts/run-dev.sh`: local Paper build/download/checksum/start workflow; defaults to 127.0.0.1:25579.
- `ARCHITECTURE.md`, `README.md`, `AGENTS.md`, `.agent` memory: actual implemented scope, commands, pins, package name, commit policy, and remaining gates.

## Verification

- `./gradlew clean build --write-locks --no-daemon` with JDK 25: passed after correcting fat-JAR task dependencies. Core regression XML reported 4 tests, 0 failures/errors/skips.
- `ESMP_ACCEPT_EULA=true ./scripts/run-dev.sh`: actual Paper 26.2 build 121 startup on JDK 25; `esmp status` reported READY, schema 1, five definitions, and one registered MAIN instance.
- Console `stop`, restart, and `esmp status`: same MAIN instance UUID `75fa0879-8da5-427f-96cf-6af9c900372a`, still one stored instance; Resource/Factory worlds correctly reported not loaded.
- Temporarily mapped FACTORY to the MAIN physical name: startup rejected `Physical world name assigned twice: world` and disabled ESMP. Restored mapping, restarted to READY with unchanged identity, then stopped cleanly (exit 0).
- Official SHA-256 checks passed for downloaded JDK, Gradle distribution, and Paper server JAR. Local tooling and server data remain ignored.
- Final `./gradlew clean build --no-daemon --console=plain`: passed with dependency locks enforced; regression XML confirms 4 tests and zero failures/errors.
- Artifact inspection passed: all five module classes are Java 25 bytecode, manifest uses `xdu.undarubra.esmp.plugin.EsmpPlugin`, SQLite is embedded, Paper classes and old namespace are absent. Generated wrapper JAR matches official SHA-256.
- `bash -n scripts/run-dev.sh`, canonical-name/package grep, Markdown links/fences/30-section checks, and `git check-ignore` for tooling/runtime/database/build JAR: passed. Removed unused duplicate `.run/smoke/`; all supervised servers are stopped.

## Next action

Implement the first Settlement persistence/protection slice: define owning-domain tables and application APIs, then transaction/cache publication and Paper 26.2 action mappings before enabling claims. Reuse the verified foundation; do not introduce placeholder gameplay or synchronous world-thread SQL.

## Risks or blockers

- Paper's optional online version check timed out during a smoke run; plugin readiness/status and clean shutdown still succeeded. The wrapper's first network download also failed TLS; its cache was seeded with the separately downloaded and SHA-256-verified official distribution, without disabling TLS checks.
- Paper/JOML emits an upstream Unsafe deprecation warning on JDK 25. SQLite tests may emit the JDK native-access warning; the server launcher explicitly enables native access.
- Automated world mutations still require owning-domain recovery/accounting and safety work. Do not deploy this foundation as a claim-protection plugin.
- Local JDK and Gradle distribution are in ignored `.tooling/`; development server data is in ignored `.run/dev/`. Set JAVA_HOME to an installed JDK 25 on other machines.
