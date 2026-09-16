# Current Handoff

## State

- Core MVP Phases 0–8 are verified at `mvp-v1.0`. Native-client/user acceptance remains separate: 80 checklist items are still pending.
- The user rejected the growing design questionnaire. Architecture §30 is now the short implementation scope: WILD wholly outside Settlements, CLAIMED wholly inside one Settlement. Cross-boundary Buildings are unsupported.
- This work changes documentation only. Automatic release and the new WILD/CLAIMED runtime have not been implemented. Existing config 14, schema, APIs and JAR are unchanged.

## Completed

- Replaced fourteen post-MVP subsections with four compact sections: binary classification, minimal loop, retained rights/accounting and implementation discipline.
- Removed partial-building Decay, mixed ownership, ownership masks, split/merge and takeover-negotiation requirements. Invalid boundary operations reject rather than creating another subsystem.
- Retained whole RUINED release/automatic whole takeover, wild Decay with meaningful local support, evolving baselines, Holder consent, in-place containers, complete termination/history, manual historical Treasury collection and reconciled original-payer Repair refunds.
- Removed the implementation-blocking questionnaire. Reuse existing patterns/configurable defaults and validate with focused tests; no Civic Capacity or extra simulation in this increment.

## Changed files

- `ESMP MVP ARCHITECTURE 2026-09-12/ARCHITECTURE.md` §30 — replaces the overgrown post-MVP design; frozen MVP and appendices stay intact.
- `.agent/DECISIONS.md` — consolidates the superseded interview into one dated rationale; older implementation evidence is retained and detailed decision chronology remains in Git history.
- `.agent/OPEN_QUESTIONS.md` — short execution notes instead of a branching questionnaire.
- `README.md`, `AGENTS.md` — concise human/agent scope, no mixed-boundary feature requests.
- This handoff — current snapshot, not repeated transcripts of every design vote.

## Verification

- Documentation-only change; no build or Paper run is claimed for this pruning.
- Scope comparison passed: the post-MVP section shrank from 188 to 41 lines; frozen MVP text and appendices are unchanged. The decision preamble shrank from 122 to 14 lines; Phase 8 and older rationale/evidence are unchanged.
- Active summary links resolve, superseded branches no longer appear as active requirements, and the changed-file check confirms only six Markdown files changed.
- Prior core MVP evidence: 141 passing tests, Paper gameplay/migration/paired recovery and standalone deployment; details remain in `RELEASE.md`, `TEST_CHECKLIST.md` and the retained Phase 8 decision entry.
- Last verified unchanged JAR SHA-256: `1e87d89fab3c75ea098a34510debce0216cf917859b77991bd715d6f7941ef2f`.

## Next action

Implement the smallest WILD/CLAIMED classification and whole-transition slice with a simple boundary guard and focused regressions. Then connect release. Do not resume the edge-case voting loop or introduce mixed-ownership processing.

## Risks or blockers

- Unsupported crossing geometry is a rejection, not a new model to design. Balance tuning should use existing configurable defaults and evidence, not block implementation.
- Keep current permission/publication, player-edit and inventory/world/SQL reconciliation safeguards; reducing scope does not justify data loss.
- No code, schema, runtime data or acceptance checkboxes changed in this documentation task. No remote push or new feature release.
