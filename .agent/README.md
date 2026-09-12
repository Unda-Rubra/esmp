# ESMP Agent Memory

This directory is the repository-local memory bank for work that must survive agent handovers.

## Source-of-truth order

1. The current user's explicit instruction.
2. `ESMP MVP ARCHITECTURE 2026-09-12/ARCHITECTURE.md` and `MVP_REQUIREMENTS_FREEZE.md` for the frozen design/scope; the bundle's `IMPLEMENTATION_ROADMAP.md` controls phase order.
3. `.agent/HANDOFF.md` for actual implementation state and current phase. Old tests passing does not mark frozen phases complete.
4. `.agent/DECISIONS.md` for rationale; pre-freeze entries are historical where superseded.
5. `.agent/OPEN_QUESTIONS.md` for current-phase implementation issues, never an alternate backlog for frozen-out features.

When sources conflict, update the lower-priority source instead of preserving two interpretations.

## Maintenance rules

- Follow the frozen bundle; do not recreate root `ARCHITECTURE.md`. Change a frozen invariant only under its explicit change-control rules and identify affected acceptance tests.
- Mirror important accepted decisions in `DECISIONS.md` with a date and rationale.
- Keep speculation and alternatives in `OPEN_QUESTIONS.md`; never phrase them as settled.
- Update `HANDOFF.md` before ending non-trivial work.
- Maintain [TEST_CHECKLIST.md](TEST_CHECKLIST.md) during the current development batch: append completed gameplay milestones, their commits, concrete player checks and known limits. Agent verification does not mark user acceptance complete. Preserve reported user results across handoffs; remove/archive the temporary checklist only when the user requests it after testing.
- Remove resolved questions instead of leaving stale duplicates.
- Do not store secrets, credentials, local machine paths, generated files, or copied chat transcripts here.
