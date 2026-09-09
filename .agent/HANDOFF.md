# Current Handoff

## State

- Documentation-only repository: no plugin source, build files, tests, or executable project commands.
- `ARCHITECTURE.md` is canonical. Major domain boundaries are settled for implementation planning; unfinished feature details are explicitly gated in §§28–30.
- Deploy one modular Paper plugin JAR using SQLite. Accepted baseline: Java plugin source, Minecraft/Paper 26.2, JDK 25, Gradle Kotlin DSL. Concrete build/dependency pins remain open.
- Accepted gameplay remains unchanged; `.agent/DECISIONS.md` retains rationale. Do not repeat the completed architecture questionnaire.
- Initialized Git on `main`, added ignore/text/editor policies, and migrated canonical architecture links to `ARCHITECTURE.md`. User requests regular coherent commits; no remote or push requested.

## Completed

- Consolidated unresolved work into prerequisites for a runnable plugin, durable claims, automated mutation, and Resource/Factory enablement.
- Aligned the development roadmap and Simplified Chinese README with these gates.
- Required ownership/history capture from the first committed ownership changes; later History work is presentation, not retroactive reconstruction.
- Replaced cumulative handoff logs with this current snapshot and added maintenance instructions to `AGENTS.md`.
- Recorded the user's runtime/language approval in the canonical draft, Chinese README, agent rules, and prerequisite index.

## Settled architecture reference

- Settlements: fixed roles, protected contiguous claims, free claiming, configurable default 64-chunk cap; adjacent identities remain separate.
- Buildings: automatic discovery, one Settlement per tracked footprint, automatic authorized-edit baseline revisions; reclaim preserves history but uses current-state repair targets.
- Activity/release: very small meaningful Visitor credit, no passerby credit; recovery stops decay without healing; automatic release requires inactivity and ruin, while confirmed Owner abandonment may release intact land.
- Execution: naturally loaded chunks, durable jobs, affected-work replanning, transactional outbox, and idempotent effects; no cross-store atomicity claim.
- Resource/Factory: prepare one shared three-dimension Resource cycle before cutover; independent contiguous Factory Plots and role policies, ordinary Paper ticking, compatibility first.

## Changed files

- `ARCHITECTURE.md` — feature gates, implementation roadmap, and first functional slice.
- `README.md` — Chinese operator/developer starting path and enablement boundaries.
- `AGENTS.md` — current-snapshot handover and scope discipline.
- `.agent/DECISIONS.md` — rationale for gate-based implementation and early history capture.
- `.agent/OPEN_QUESTIONS.md` — unresolved work indexed by feature gate.
- `.agent/HANDOFF.md` — current state rather than accumulated conversation history.

## Verification

- Directory inspection confirmed only the architecture/guidance documents and `.agent` memory files; no executable project exists.
- Python document checks passed: sections 1–30 ordered; all 48 Current Decisions unchanged byte-for-byte from the pre-consolidation snapshot; 7 local links resolve; code fences balanced; required handoff sections present; feature gates and early ownership-history ordering verified.
- No plugin build, test, startup, or gameplay scenario has been run. No runtime versions or libraries were selected during consolidation.
- Runtime research on 2026-09-09: [Mojang's release manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) identifies 26.2 as latest release and 26.3-pre-3 as snapshot. [Paper downloads](https://papermc.io/downloads/paper) advertises 26.2 build 121; [build metadata](https://fill.papermc.io/v3/projects/paper/versions/26.2/builds/121) marks it STABLE. [Version metadata](https://fill.papermc.io/v3/projects/paper/versions/26.2) marks 26.2 SUPPORTED and requires minimum Java 25.
- [Paper project setup](https://docs.papermc.io/paper/dev/project-setup/) uses Java source and Gradle Kotlin DSL with Java 25. The user has now approved this baseline for Paper/Minecraft 26.2. Build 121 was verified as STABLE during research, but an exact server/API build pin still needs recording when project files are created.
- Accepted-baseline document check passed in Python: Java, Paper/Minecraft 26.2, JDK 25, and Kotlin DSL agree across six documents; sections 1–30 and decisions 1–48 are ordered; fences, local links, and handoff sections pass. Search found no superseded runtime-selection questions. No project build or runtime executed.

## Next action

Verify and pin compatible Gradle wrapper, JDK 25 distribution/patch, Paper 26.2 API/server build, and dependencies, then create and smoke-test the first runnable modular plugin. No further platform/language questionnaire is needed.

## Risks or blockers

- Exact wrapper, JDK distribution/patch, Paper build, SQLite driver, and migration-tool pins remain open; the platform/language/DSL baseline is settled.
- SQLite tooling/schema and target-version protection mappings gate durable claims, not the architecture discussion.
- Building identity/restoration safety, recovery/accounting, and configured release eligibility gate automated mutation. Resource operational recovery and Factory policy details gate their own features.
