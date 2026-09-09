# ESMP Agent Memory

This directory is the repository-local memory bank for work that must survive agent handovers.

## Source-of-truth order

1. The current user's explicit instruction.
2. `ARCHITECTURE.md` for accepted product and architecture decisions.
3. `.agent/DECISIONS.md` for a compact decision log and rationale.
4. `.agent/OPEN_QUESTIONS.md` for unresolved work; entries here are not accepted architecture.
5. `.agent/HANDOFF.md` for the current working state and verification evidence.

When sources conflict, update the lower-priority source instead of preserving two interpretations.

## Maintenance rules

- Put accepted architecture in `ARCHITECTURE.md` first.
- Mirror important accepted decisions in `DECISIONS.md` with a date and rationale.
- Keep speculation and alternatives in `OPEN_QUESTIONS.md`; never phrase them as settled.
- Update `HANDOFF.md` before ending non-trivial work.
- Maintain [TEST_CHECKLIST.md](TEST_CHECKLIST.md) during the current development batch: append completed gameplay milestones, their commits, concrete player checks and known limits. Agent verification does not mark user acceptance complete. Preserve reported user results across handoffs; remove/archive the temporary checklist only when the user requests it after testing.
- Remove resolved questions instead of leaving stale duplicates.
- Do not store secrets, credentials, local machine paths, generated files, or copied chat transcripts here.
