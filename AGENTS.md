# trade-imports-ins-backend: notes for agents

Spring Boot service that aggregates notification events into a cross-journey read model. Build and test with `mvn verify` (integration tests need Docker). See `README.md` for running it, and `docs/architecture.md` for why the code is shaped as it is.

## Code quality

- A comment describes only the lines below it, and only when a name or extraction cannot. Never describe callers, other modules, a library, the UI or a test. No counts ("the one", "the only").
- Rationale goes in `docs/architecture.md`, never in a comment.
- No ticket keys, backlog or ruling ids, release names or session words in code, comments or test titles.
- No history: no before/after narration, no `REMOVED_*`, no `…BeforeTheChange` names, no "renamed from".
- A name predicts what the code returns and changes. No positional boolean flags, no metaphors. One concept, one name; a rename covers the whole vocabulary.
- Set-up used by two tests goes in the shared helper, such as `IntegrationBase`. Promoting a helper deletes the local copies.
- Every branch that changes an outcome logs once, with the ids support searches by. Log the throwable, never only `getMessage()`. No request or response bodies at INFO.
- A test title states what its assertions prove, and the test fails without the change it covers.
- Maven's `validate` phase runs Checkstyle with `config/checkstyle/code-quality.xml`. Fix a new violation; never add it to `config/checkstyle/suppressions.xml`.

Canonical text: `.claude/rules/code-quality.md` in the trade-imports-workspace repo.
