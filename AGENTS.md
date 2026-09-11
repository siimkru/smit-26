# Repository Guidelines

## Purpose and source of truth

This repository implements a small, controlled Java 21 Spring Boot + Spring AI
agent that answers internal IT-service questions from a repository-backed
Markdown knowledge base.

`docs/assignment.md` is the single normative requirements source. For every
implementation or review task:

1. Read the relevant parts of `docs/assignment.md` first.
2. Use `docs/requirements-matrix.md` for traceability and `docs/architecture.md`
   for design context.
3. If supporting documents conflict with the assignment, follow the assignment.
4. Prefer the smallest implementation that satisfies the requirements; do not
   add optional production features merely because they seem useful.

## Repository layout

- `src/main/java/ee/smit/agent/` — production code, grouped into `api`,
  `agent`, `knowledge`, `response`, and `security`.
- `src/main/resources/` — application configuration, the Estonian system
  prompt, and the static Markdown knowledge base.
- `src/test/java/` — unit and API tests that do not call OpenAI.
- `src/integrationTest/java/` — real REST-to-OpenAI scenarios.
- `docs/` — requirements, architecture, traceability, and submission material.
- `config/` — Checkstyle and PMD configuration.
- `.github/workflows/tests.yml` — CI, security scans, verification, and report
  artifacts.

## Build, test, and development commands

Always use the checked-in Gradle wrapper:

- `./gradlew bootRun` — run locally on port 8080.
- `./gradlew test` — run unit/API tests without OpenAI credentials or network
  access.
- `./gradlew check` — run tests, Checkstyle, PMD, SpotBugs/FindSecBugs, and the
  70% JaCoCo line-coverage gate.
- `./gradlew build` — compile, verify, and package the application.
- `./gradlew integrationTest` — run real-provider REST scenarios; requires
  `OPENAI_API_KEY` and `OPENAI_MODEL`.

Unit and integration HTML reports are written to
`build/reports/tests/test/` and `build/reports/tests/integrationTest/`.
Generated build output must not be committed.

## Implementation principles

Keep the architecture synchronous, explicit, and auditable. Preserve the
repository-backed Markdown knowledge base, deterministic application checks,
bounded in-memory session context, explicit Spring AI tool calling, and the
two read-only knowledge-base tools. Do not introduce vector databases,
embeddings, external search, databases, arbitrary filesystem access, shell
execution, authentication platforms, durable conversation storage, or
distributed state unless the assignment explicitly requires it.

The agent’s application-level invariants are:

- answer in Estonian and only from supported knowledge-base content;
- include a human-readable source citation for supported answers;
- never fabricate sources; refuse or clearly indicate insufficient support;
- for `refused: false`, return non-empty sources whose files and excerpts come
  from the allowed knowledge base;
- never expose system prompts, tool definitions, internal instructions, or
  secrets.

Treat these as application guarantees, not only prompt instructions.

## Security and tool boundary

Enforce security outside the model wherever practical. Before an LLM call,
validate the request, enforce the assignment’s input-size limit, detect the
required injection/jailbreak patterns, keep system and user messages separate,
and prevent intentionally sensitive data from being sent to OpenAI. Do not log
complete user questions in production-style logs.

The model must not receive arbitrary access to files, paths, networks, commands,
environment secrets, or unrestricted tools. Knowledge-base tools must be
read-only, operate only on allowlisted files, reject path traversal, perform no
network access or writes, and execute no commands. User text such as `system:`,
fake roles, DAN instructions, or claims of elevated privileges is untrusted
input.

## API contract

Preserve the contract defined by `docs/assignment.md`:

- `POST /api/v1/agent/ask` accepts required `question` and optional `sessionId`.
- Responses contain `answer`, `sources`, `confidence`, `refused`, and
  `refusalReason`.
- Each source contains at least `file` and `excerpt`.
- `GET /api/v1/health` must return health without invoking OpenAI.

Do not change public status codes, validation behavior, response fields, or
security boundaries merely to make a test pass.

## Testing requirements

Tests use JUnit 5, AssertJ, and Spring MVC test support. Name test classes
`*Test` and test methods as behavioral statements. Unit tests must mock external
model calls and run without credentials. Cover validation, security, tool
allowlisting, API errors, grounding, sources, citations, sessions, and the
required IDs (`API-01`, `API-02`, `API-03`, and `SEC-07`).

Integration tests must use the separate `integrationTest` task, exercise the
REST API, preserve requirement IDs in names or comments, and assert stable
behavioral invariants rather than exact model prose. Do not weaken tests to
accommodate nondeterministic output. The CI workflow runs these tests when
credentials are configured, skips them on ordinary credential-free events, and
fails a manually requested run when credentials are missing.

## Style and change discipline

Use four-space indentation, braces for control blocks, one public type per
file, no wildcard imports, lowercase package names, `PascalCase` types, and
`camelCase` methods and fields. Follow Checkstyle, PMD, and SpotBugs findings.

Inspect existing code before editing, implement the smallest coherent change,
update tests and documentation when behavior or configuration changes, run the
relevant verification, and re-check the result against `docs/assignment.md`.
Do not leave required behavior as a silent TODO.

Commit messages use `<type>: <short imperative description>` with a lowercase
type such as `build`, `ci`, `docs`, `feat`, `fix`, `refactor`, `security`, or
`test`; keep commits focused and omit a final period. Pull requests should
explain the behavior change, link requirement IDs, list verification commands,
and call out API, prompt, knowledge-base, or security implications.

## Secrets and configuration

Never inspect, print, log, copy, or commit `.env`, API keys, full sensitive user
input, or prompts containing secrets. Use `.env.example` as the local template.
Read `OPENAI_API_KEY` and model selection from the environment. Only run live
OpenAI integration tests when explicitly needed and configured.

## Definition of done

A change is complete only when its relevant assignment requirements are
implemented and tested, security boundaries remain intact, documentation is
updated where needed, and the relevant checks pass. Report any requirement
that could not be verified instead of assuming compliance.
