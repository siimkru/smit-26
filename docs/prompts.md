## Prompt 1 — Scaffold the application

Read `AGENTS.md`, `docs/assignment.md`, `docs/architecture.md`, and `docs/requirements-matrix.md` before making changes.

Implement the first minimal vertical foundation for the project.

Scope:
- Create the Gradle/Spring Boot project using Java 21 and Gradle Wrapper.
- Add the required Spring Boot and Spring AI dependencies.
- Pin compatible stable dependency versions.
- Add the application entry point.
- Add typed configuration for:
  - `OPENAI_API_KEY`
  - `OPENAI_MODEL`
  - temperature
  - maximum question length
  - session limits
- The application must be able to start without `OPENAI_API_KEY`.
- Implement `GET /api/v1/health`.
- Health must return `{"status":"UP"}` without invoking OpenAI.
- Add `.gitignore` entries needed to prevent secrets and generated reports from being committed.
- Add `.env.example` containing placeholders only. Any model value must be clearly identified as an example rather than an organization-approved default.

Do not implement the agent, knowledge-base search, or session behavior yet.

Follow the package boundaries defined in `docs/architecture.md`.

Tests:
- Add API-03 with the exact ID in `@DisplayName`.
- Verify health works without an API key.
- Verify no model/provider interaction occurs.

Before finishing:
1. Run `./gradlew test`.
2. Inspect the diff.
3. Report files changed.
4. Report commands executed and test results.
5. Identify remaining requirements; do not claim the assignment is complete.

---

## Prompt 2 — Implement the immutable knowledge base

Read the project documentation and current implementation first.

Implement the knowledge-base repository defined by the architecture.

Scope:
- Create the five synthetic Markdown knowledge-base documents:
  - `gitlab-access.md`
  - `kubernetes-deploy.md`
  - `cicd.md`
  - `code-review.md`
  - `access-management.md`
- Content must be synthetic and in Estonian.
- Store the files under the classpath knowledge-base resources.
- Implement immutable knowledge records/passages with stable IDs.
- Load only an explicit manifest of approved files.
- Missing or malformed configured documents must fail startup.
- Never accept a user-provided filesystem path.
- Do not implement generic file access.
- Do not add databases, embeddings, vector stores, or external services.

Implement deterministic search:
- normalized token matching;
- curated Estonian/English aliases;
- title/alias matches ranked ahead of passage matches;
- deterministic tie-breaking;
- maximum five returned passages.

Implement topic listing using canonical topic-description passages.

Tests first or alongside implementation:
- repository loading;
- exact approved fixture set;
- topic listing;
- GitLab lookup;
- Kubernetes lookup;
- indirect code-review lookup;
- Estonian/English aliases;
- no-match behavior;
- deterministic ordering;
- five-result bound;
- traversal/path-like input cannot access arbitrary files.

Run `./gradlew test`, inspect the diff, and report results and remaining requirements.

---

## Prompt 3 — Implement the allowlisted Spring AI tools

Implement the knowledge-base tool boundary.

The model must have exactly two application-approved read-only tools:

- `listTopics()`
- `searchKnowledgeBase(query)`

Requirements:
- Use Spring AI tool calling APIs.
- Explicitly register the two tools; do not automatically expose arbitrary Spring beans.
- Tools may access only the immutable knowledge repository.
- Search accepts a bounded query string, never a filesystem path.
- No generic file-reading tool.
- No shell/process execution.
- No database/network/email/LDAP/external-system calls.
- Returned values must use canonical `KnowledgePassage` records containing passage ID, file, title, and text.
- Tool execution must make it possible for the application to record every passage retrieved during the current request.

Add `ToolAllowlistTest`.
Assert exactly the intended tools are registered.

Add security tests covering path/traversal-like tool input.

Preserve relevant SEC-* IDs in test display names where applicable.

Run `./gradlew test`, inspect the diff, and report results.

---

## Prompt 4 — Implement API request validation and security gate

Implement the REST input and pre-LLM security boundary.

Create:
- request DTO for `POST /api/v1/agent/ask`;
- required nonblank `question`;
- optional `sessionId`;
- maximum question length of 2,000 characters;
- session ID validation according to `docs/architecture.md`;
- global HTTP error handling.

Implement `RequestSecurityService`.

Fail fast before any model call for documented:
- prompt-injection/rewrite patterns;
- system-role imitation;
- prompt/tool disclosure attempts;
- traversal-like input;
- obvious credential/secret/personal-code patterns.

Cover both English and documented Estonian attack patterns.

Security logging must never contain the complete user question. Log only safe metadata such as request/request-correlation ID, category, input length/digest, result, and timing.

Behavior:
- malformed/invalid requests → HTTP 400;
- suspicious but structurally valid requests → deterministic HTTP 200 refusal;
- no provider invocation for fail-fast refusals.

Implement tests for:
- API-01
- API-02
- SEC-01 through SEC-08 where the pre-model security layer is responsible;
- especially SEC-07 with 3000+ characters and proof that no provider call occurs.

Every test must contain the exact API-* or SEC-* ID in `@DisplayName`.

Do not weaken production detection to make later integration tests easier.

Run `./gradlew test`, inspect the diff, and report results.

---

## Prompt 5 — Implement trusted grounding and response assembly

Implement the most important application invariant:

The LLM must never construct trusted public citations, source excerpts, or unrestricted factual prose.

Implement the three separate contracts from the architecture:

1. `KnowledgePassage`
2. internal `AgentDecision`
3. public `AgentResponse`

`AgentDecision` must support only the documented actions:
- `ANSWER`
- `LIST_TOPICS`
- `CLARIFY`
- `REFUSE`

Implement a request-local evidence ledger.

Implement `GroundingValidator` and `ResponseAssembler`.

Rules:
- Only passage IDs actually retrieved through allowlisted tools during the current request are valid.
- An ID that exists in the repository but was not retrieved this request is invalid.
- Session history is not evidence.
- Never trust model-generated citations/excerpts.
- Public factual answers are assembled from exact canonical selected passages.
- Append `[allikas: filename]` to selected passages.
- Construct `sources.file`, `sources.title`, and `sources.excerpt` from canonical repository records.
- Deduplicate sources.
- Unsupported, malformed, fabricated, stale, or unretrieved passage selections must become deterministic refusals.
- Never repair unsupported output by guessing.

Confidence:
- `high` for validated answers/topic lists;
- `low` only for the documented grounded ambiguous clarification;
- `null` for refusals;
- never emit `medium`.

Add extensive unit tests for:
- fabricated passage IDs;
- existing-but-unretrieved IDs;
- evidence from a previous request;
- empty evidence;
- canonical answer construction;
- canonical source construction;
- citations;
- multiple sources;
- deduplication;
- clarification;
- refusal;
- arbitrary model prose cannot escape into a successful factual response.

Run `./gradlew test`, inspect the diff, and report results.

---

## Prompt 6 — Implement the Spring AI/OpenAI orchestration

Implement the agent/model integration while preserving the existing trusted boundaries.

Create an application-owned model gateway so application logic does not directly depend on OpenAI-specific implementation details.

Use Spring AI `ChatClient` and OpenAI behind that gateway.

Requirements:
- fixed/versioned trusted system prompt resource;
- system and user messages remain separate roles;
- never interpolate user input into the system prompt;
- expose only the two approved tools;
- collect current-request tool evidence;
- parse the model response only as an untrusted `AgentDecision`;
- pass that decision and evidence through `GroundingValidator` and `ResponseAssembler`;
- never return raw model factual prose.

System prompt must define:
- internal IT information-agent role;
- Estonian response behavior;
- knowledge-base-only factual scope;
- mandatory tool lookup for factual questions;
- refusal behavior;
- prompt/tool/system-instruction nondisclosure;
- allowed decision format.

Implement bounded execution:
- maximum four model calls;
- maximum six tool invocations;
- 30-second total request deadline;
- unknown tools/malformed arguments/exhausted limits fail safely.

Provider unavailable/timeout behavior must map to sanitized HTTP 503.

Unit tests must use deterministic fakes/mocks and require no API key.

Run `./gradlew test`, inspect the diff, and report results.

---

## Prompt 7 — Implement bounded sessions

Implement the session behavior from `docs/architecture.md`.

Requirements:
- optional caller-managed `sessionId`;
- session IDs provide context, not authentication;
- store only accepted user questions and validated application responses;
- maximum four exchanges per session;
- 15-minute idle TTL;
- maximum 1,000 sessions;
- oldest-idle eviction;
- serialize requests within one session;
- allow independent sessions concurrently;
- refused and failed turns are not stored.

Critical grounding rule:
Session history provides conversational context only. Every follow-up factual response must retrieve its evidence again during the current request.

Add unit tests for:
- same-session context;
- different-session isolation;
- maximum history;
- TTL expiry;
- eviction;
- refused turn not stored;
- failed turn not stored;
- previous-turn evidence cannot satisfy current-turn grounding.

Prepare the implementation for UC-06 and UC-13.

Run `./gradlew test`, inspect the diff, and report results.

---

## Prompt 8 — Complete the REST agent endpoint

Wire the implemented components into:

`POST /api/v1/agent/ask`

Preserve the documented public contract:
- `answer`
- `sources`
- `confidence`
- `refused`
- `refusalReason`

Implement the documented HTTP semantics:
- invalid request → 400;
- safe deterministic refusal → 200;
- grounded success → 200;
- provider unavailable/timeout → 503;
- unexpected errors → sanitized 500.

Ensure:
- `refused:false` always has non-empty validated sources;
- successful responses have `refusalReason:null`;
- factual answers contain human-readable source citations;
- refusals do not leak model/system/tool internals.

Complete API-04 using a deterministic model fake at unit/component level where appropriate.

Run the entire keyless unit suite with:

`./gradlew test`

Inspect the diff and report test results and outstanding requirements.

---

## Prompt 9 — Add real-model integration tests

Create a separate Gradle `integrationTest` source set/task.

Integration tests must exercise the real flow:

REST → security → agent → Spring AI → OpenAI → tools → grounding → response.

Requirements:
- integration tests require `OPENAI_API_KEY`;
- skip/disable cleanly when it is unavailable;
- unit tests remain completely keyless;
- use configured `OPENAI_MODEL`;
- test behavior and provenance, not exact LLM wording.

Implement integration coverage for:
- API-04
- UC-01 through UC-13
- SEC-01 through SEC-06
- SEC-08

Every test must contain the exact ID in `@DisplayName`.

For SEC-01–06 and SEC-08:
- test the original attack through REST;
- where the production pre-filter blocks it, assert no provider call;
- also add a semantically equivalent synthetic adversarial variant that passes the unchanged detector;
- for that variant verify a real provider call occurs and the final safety behavior still holds;
- never weaken/disable the production security filter to make the test reach OpenAI.

Keep SEC-07 exclusively in keyless validation tests.

Generate separate HTML reports for `test` and `integrationTest`.

Run `./gradlew test`.

If `OPENAI_API_KEY` is available, also run `./gradlew integrationTest`.
Otherwise verify and report that integration tests skip cleanly.

Inspect the diff and report results.

---

## Prompt 10 — CI, README, ADRs, and delivery

Finish the repository documentation and delivery infrastructure without changing the core architecture unless a documented requirement forces it.

Add GitHub Actions that:
- always runs `./gradlew test`;
- runs integrations when the required OpenAI secret/configuration is available;
- uploads unit-test HTML reports separately;
- uploads integration-test HTML reports separately;
- never exposes secrets.

Complete README with:
- purpose;
- architecture overview;
- Java/Gradle requirements;
- configuration;
- `OPENAI_API_KEY`;
- `OPENAI_MODEL`;
- local startup;
- health endpoint;
- agent API request/response examples;
- knowledge-base/tool restrictions;
- grounding design;
- security mechanisms;
- session behavior;
- data processing/logging;
- unit tests;
- integration tests;
- HTML report locations;
- CI;
- known limitations.

Document major architectural decisions/ADRs required by the existing design.

Create the maximum-one-page submission summary covering:
- architecture;
- security controls;
- grounding strategy;
- major rationale;
- known limitations.

Do not claim tests passed unless they were actually executed.

Run `./gradlew test` and, if credentials are available, `./gradlew integrationTest`.

Inspect the final diff and compare the implementation systematically against every row in `docs/requirements-matrix.md`.

Produce a final report containing:
1. commands executed;
2. unit-test result;
3. integration-test result or reason not run;
4. requirements satisfied;
5. requirements still unmet;
6. known limitations;
7. files that should be reviewed before submission.

Do not add infrastructure outside the documented architecture merely to make the project look production-ready.
