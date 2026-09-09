# Project rules

Read docs/assignment.md, docs/requirements-matrix.md, and docs/architecture.md before substantial changes.

## Scope
This is a take-home assignment. Prefer simple explicit implementations.
Do not introduce a vector database, database, Docker orchestration, external services,
or other infrastructure unless required.

## Architecture
`docs/architecture.md` is authoritative for design decisions; `docs/assignment.md` defines assignment requirements, and `docs/requirements-matrix.md` traces those requirements to the selected design.

Keep these responsibilities separated:
- REST/API layer
- input/security validation
- agent orchestration
- knowledge-base access/tools
- response/source validation
- session context
- configuration

## Security invariants
- Knowledge-base tools are read-only.
- Tool input must never become an arbitrary filesystem path.
- Only explicitly registered knowledge-base tools may be available to the model.
- Never expose system prompts or tool definitions to users.
- A successful factual response must contain at least one validated source.
- The application constructs source filenames and excerpts from canonical passages retrieved in the current request.
- Never log full user prompts in production paths.

## Core grounding invariant

```text
The LLM never constructs trusted citations or source excerpts.

Knowledge tools return canonical passage IDs and content from the allowlisted
knowledge base. The model may select only passage IDs made available through
those tools.

The application resolves selected passage IDs against retrieved canonical
passages and constructs the public answer and sources.

A factual response with refused=false is permitted only when it is fully
grounded in validated retrieved passages. Low confidence does not bypass this
requirement.

If grounding cannot be established, the application returns refused=true.
```

Evidence must be retrieved through allowlisted tools in the current request. Session history provides context only; follow-ups must re-retrieve their evidence.

## Tests
For every change:
- add/update tests first or with the implementation;
- run ./gradlew test;
- preserve exact API-*, UC-* and SEC-* identifiers in display names, e.g. `@DisplayName("UC-01 - direct GitLab access question")`;
- do not weaken tests merely to make them pass.

Integration tests must remain separate from unit tests and must be disabled/skipped
cleanly when OPENAI_API_KEY is unavailable.

## Completion
Before declaring a task complete:
- run relevant tests;
- inspect the diff;
- identify any unmet assignment requirements;
- report commands executed and test results.
