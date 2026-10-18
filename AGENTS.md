# trade-imports-ins-backend: notes for agents

Spring Boot service that aggregates notification events into a cross-journey read model. Build and test with `mvn verify` (integration tests need Docker). See `README.md` for running it.

## Code quality

- A comment describes only the lines below it, and only when a name or extraction cannot. No counts ("the one", "the only").
- No ticket keys, backlog or ruling ids, release names or session words in code, comments or test names.
- No history: no before/after narration, no `REMOVED_*`, no "renamed from".
- A name predicts its return value and side effects. No positional boolean flags, no metaphors. One concept, one name; a rename covers the whole vocabulary.
- Set-up used by two tests goes in the shared helper (`IntegrationBase` for ITs).
- Every branch that changes an outcome logs once, with the ids support searches by. Log the throwable, never only `getMessage()`. No request or response bodies at INFO.
- A test name states what its assertions prove, and the test fails without the change it covers.
- Checkstyle (`config/checkstyle/checkstyle.xml`) enforces the mechanical parts in the `validate` phase. Fix a violation; do not add a suppression for new code.

Canonical text: `.claude/rules/code-quality.md` in the trade-imports-workspace repo.
