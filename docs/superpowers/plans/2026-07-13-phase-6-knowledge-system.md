# Phase 6: Knowledge System + Context Cleanup — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Capture the reality created by Phases 1–5 in a committed, portfolio-visible knowledge system — an indexed `docs/knowledge/` (ADRs, patterns, gotchas, glossary), hexagon-aware `.claude/skills/` and `.claude/agents/`, and a hardened `.gitignore` — all wired together by a single hydrate/persist protocol.

**Architecture:** `docs/knowledge/README.md` is the single source of truth for the hydrate/persist loop; it indexes five ADRs under `decisions/`, three how-to guides under `patterns/`, `gotchas.md`, and `glossary.md`. Committed `.claude/skills/` operationalize the pattern guides; committed `.claude/agents/` embed the hydrate/persist protocol. `.gitignore` is hardened so `.superpowers/` and `.claude/settings.local.json` stay untracked while `.claude/skills/` and `.claude/agents/` are committed. This phase adds no Java and no build logic — it is pure documentation, tooling, and git-hygiene.

**Tech Stack:** Markdown (GitHub-rendered), YAML frontmatter (Claude Code skill/agent format), git ignore rules. No code, no Gradle changes. Verification is by file-exists / tree / frontmatter / `git check-ignore` / link-resolution checks.

## Global Constraints

- This phase runs on the **post-Phase-1..5 codebase**. All references to code use the post-Phase-1 top-level package structure: `com.wkaiser.riotapimcpserver.{account,summoner,match,spectator,analytics,shared}` with `domain/`, `application/`, `application/port/`, `adapter/in/mcp/`, `adapter/out/riot/` subpackages.
- Locked names referenced throughout (do not rename): `shared/config/RiotApiProperties`, `shared/http/RiotApiClient` (`regional(RiotApiRegionUri)`, `platform(RiotApiPlatformUri)`), `shared/exception/RiotApiException` (`getStatusCode()` → `int`), ports `RiotAccountPort`/`SummonerPort`/`MatchPort`/`SpectatorPort`, outbound adapters `RiotAccountRiotAdapter`/`RiotSummonerAdapter`/`RiotMatchAdapter`/`RiotSpectatorAdapter`, services `RiotAccountService`/`SummonerService`/`MatchService`/`SpectatorService`/`AnalyticsService`, tools `RiotAccountTool`/`SummonerTool`/`LiveGameTool`/`AnalyticsTool`.
- `@McpTool`/`@McpToolParam` live in package `org.springframework.ai.mcp.annotation`. MCP tool names are unchanged (e.g. `get_lol_summoner_by_name`, `get_current_game_by_summoner_name`).
- Versions (do not change, and quote these when documenting): Java 21, Spring Boot 4.1.0, Spring AI BOM 2.0.0, Gradle wrapper 9.6.1. WireMock `org.wiremock:wiremock-standalone:3.9.2` (testImplementation), added in Phase 1 Task 2.
- No source, no `build.gradle`, and no Gradle invocation is required for this phase. Do not run `./gradlew`. The "test" of each task is a concrete file/tree/link/ignore check with expected output, run from Git Bash.
- Every committed file created in this plan has its **complete content** shown in a code block — no placeholders.
- Commit messages use plain Conventional Commits (`docs:`, `chore:`, `build:`) with **no** co-author trailer.
- Reference (do not duplicate) content across files: patterns link to ADRs; ADRs link to patterns; `README.md` links to everything.

---

### Task 1: Knowledge base index + hydrate/persist protocol (`docs/knowledge/README.md`)

Create the front door of the knowledge system. This file is the single source of truth for the hydrate/persist loop that `CLAUDE.md` and every committed agent point at.

**Files:**
- Create: `docs/knowledge/README.md`

**Interfaces:**
- Produces: the canonical hydrate/persist protocol text and the index of `decisions/`, `patterns/`, `gotchas.md`, `glossary.md`. Consumed by `CLAUDE.md` (verified in Task 8), by every skill (Task 6), and by every agent (Task 7). The links it lists become the link-resolution contract verified across later tasks.

- [ ] **Step 1: Create the knowledge directory and README**

Create `docs/knowledge/README.md`:

```markdown
# Knowledge Base

The durable, committed memory for `riot-api-mcp-server`. It exists so that any
contributor — human or AI agent — can rehydrate the project's context fast and
persist new findings in a consistent place, instead of re-deriving decisions
from the code every time.

This README is the **single source of truth** for the hydrate/persist protocol.
`CLAUDE.md` and every committed agent point here rather than restating it.

## Contents

| Area | What lives here |
|------|-----------------|
| [`decisions/`](decisions/) | Architecture Decision Records (ADRs) — one decision per file |
| [`patterns/`](patterns/) | Copy-pasteable how-to guides for recurring procedures |
| [`gotchas.md`](gotchas.md) | Sharp edges and non-obvious pitfalls, newest appended at the bottom |
| [`glossary.md`](glossary.md) | Riot / League of Legends domain terms |

### Decisions (ADRs)

- [ADR-0001 — Bounded-context hexagonal architecture](decisions/ADR-0001-hexagonal.md)
- [ADR-0002 — Shared Riot HTTP client](decisions/ADR-0002-shared-riot-http-client.md)
- [ADR-0003 — WireMock + port-fake testing](decisions/ADR-0003-wiremock-testing.md)
- [ADR-0004 — ArchUnit architecture enforcement](decisions/ADR-0004-archunit-enforcement.md)
- [ADR-0005 — Committed knowledge system](decisions/ADR-0005-knowledge-system.md)

### Patterns

- [Add a bounded context](patterns/add-a-bounded-context.md)
- [Add an MCP tool](patterns/add-an-mcp-tool.md)
- [Add an adapter test](patterns/add-an-adapter-test.md)

## Hydrate / Persist protocol

### Hydrate (at the start of a task)

Before changing anything:

1. Read this `README.md`.
2. Read [`gotchas.md`](gotchas.md) — it is short and prevents the most common mistakes.
3. Read the ADRs relevant to the area you are touching (e.g. adding an outbound
   adapter → [ADR-0001](decisions/ADR-0001-hexagonal.md) and
   [ADR-0002](decisions/ADR-0002-shared-riot-http-client.md)).
4. If your task matches a pattern, follow the matching guide in [`patterns/`](patterns/).
   Unsure of a domain term? Check [`glossary.md`](glossary.md).

### Persist (at the end of a task)

Write findings back so the next person does not re-derive them. Keep every entry
**small and single-purpose**:

- **Made a new architectural decision?** Add a new ADR in `decisions/` using the
  next number (`ADR-000N-short-slug.md`) and the template below, then link it from
  this README's ADR list.
- **Established a new recurring procedure?** Add a new guide in `patterns/`, then
  link it from this README's Patterns list.
- **Hit a new pitfall?** Append a section to the bottom of [`gotchas.md`](gotchas.md).
- **Introduced a new domain term?** Add it to [`glossary.md`](glossary.md).

Do not edit an existing ADR to reverse a decision — supersede it with a new ADR
and set the old one's **Status** to `Superseded by ADR-000N`.

### ADR template

```
# ADR-000N: <title>

- **Status:** Accepted | Superseded by ADR-000M
- **Date:** YYYY-MM-DD

## Context
Why a decision was needed.

## Decision
What we chose.

## Consequences
What this makes easy, what it costs, and what to watch for.
```
```

- [ ] **Step 2: Verify the file exists and contains the protocol**

Run:

```bash
test -f docs/knowledge/README.md && grep -q "Hydrate / Persist protocol" docs/knowledge/README.md && echo "README ok"
```

Expected: `README ok`.

- [ ] **Step 3: Commit**

```bash
git add docs/knowledge/README.md
git commit -m "docs: add knowledge base index and hydrate/persist protocol"
```

> **Note:** The links this README lists to `decisions/`, `patterns/`, `gotchas.md`, and `glossary.md` are created in Tasks 2–4. Task 4's final step re-verifies that every link here resolves once all targets exist.

---

### Task 2: Architecture Decision Records (`docs/knowledge/decisions/`)

Five single-purpose ADRs, each grounded in the actual Phase 1–5 decisions. They use the template from Task 1.

**Files:**
- Create: `docs/knowledge/decisions/ADR-0001-hexagonal.md`
- Create: `docs/knowledge/decisions/ADR-0002-shared-riot-http-client.md`
- Create: `docs/knowledge/decisions/ADR-0003-wiremock-testing.md`
- Create: `docs/knowledge/decisions/ADR-0004-archunit-enforcement.md`
- Create: `docs/knowledge/decisions/ADR-0005-knowledge-system.md`

**Interfaces:**
- Consumes: the ADR template and README link list from Task 1.
- Produces: five ADR files. `ADR-0001`/`ADR-0002` are referenced by `patterns/add-a-bounded-context.md` and `patterns/add-an-mcp-tool.md`; `ADR-0003` by `patterns/add-an-adapter-test.md`; `ADR-0004` by the `check-architecture` skill.

- [ ] **Step 1: Create ADR-0001 (hexagonal architecture)**

Create `docs/knowledge/decisions/ADR-0001-hexagonal.md`:

```markdown
# ADR-0001: Bounded-context hexagonal architecture

- **Status:** Accepted
- **Date:** 2026-07-13

## Context

The project began as package-by-feature under `com.wkaiser.riotapimcpserver.riot.*`
(`riot/account`, `riot/lol/{summoner,match,analytics,spectator}`). Application logic,
HTTP calls, and MCP wiring were entangled in the same service classes. As a portfolio
piece the code needs to *demonstrate* clean architecture, not just work.

## Decision

Each Riot context (`account`, `summoner`, `match`, `spectator`, `analytics`) becomes a
self-contained mini-hexagon as a **top-level** package under
`com.wkaiser.riotapimcpserver`:

```
<context>/
  domain/                       relocated Lombok DTOs (no framework deps)
  application/
    <Name>Service               pure logic, depends on its port
    port/<Name>Port             outbound port interface (the boundary)
  adapter/
    in/mcp/<Name>Tool           @McpTool inbound adapter
    out/riot/Riot<Name>Adapter  RestClient adapter implementing the port
```

`analytics` is a **composing** context: it has no outbound Riot adapter; its
`AnalyticsService` depends on the `account`, `summoner`, and `match` application
services. MCP tools call application services directly — there is deliberately no
inbound-port interface (ceremony for a showcase of this size).

**The dependency rule (enforced by [ADR-0004](ADR-0004-archunit-enforcement.md)):**
`adapter → application → domain`, inward only. `domain` depends on nothing outward and
on no framework. `application` depends on `domain` and its own `port` only, never on
`adapter`. Only `adapter/out/riot` knows `RestClient`; only `adapter/in/mcp` knows
`@McpTool`. Cross-context references are forbidden **except** `analytics` depending on
other contexts' application services.

Per YAGNI, DTOs are **relocated, not split** — no separate wire-vs-domain models and no
anti-corruption layer, because the Riot API is read-only. DTOs keep the established
Lombok pattern (see [gotchas](../gotchas.md)).

## Consequences

- A reviewer can read one context top-to-bottom and see ports/adapters in action.
- The port interface is a natural seam for tests: WireMock for adapters, in-memory
  fakes for services (see [ADR-0003](ADR-0003-wiremock-testing.md)).
- More packages/files per context than a flat layout — accepted for clarity.
- The rules are only worth having if enforced, hence ArchUnit
  ([ADR-0004](ADR-0004-archunit-enforcement.md)).
- To add a context, follow [patterns/add-a-bounded-context.md](../patterns/add-a-bounded-context.md).
```

- [ ] **Step 2: Create ADR-0002 (shared Riot HTTP client)**

Create `docs/knowledge/decisions/ADR-0002-shared-riot-http-client.md`:

```markdown
# ADR-0002: Shared Riot HTTP client

- **Status:** Accepted
- **Date:** 2026-07-13

## Context

Before the refactor, `SummonerService`, `SpectatorService`, `MatchService`, and
`RiotAccountService` each hand-rolled the same plumbing: a private
`createPlatformClient()`, the `X-RIOT-TOKEN` header constant, `@Value("${riot.apiKey}")`,
and a near-identical `try/catch (HttpClientErrorException) → RiotApiException`. This was
copy-pasted 4+ times, and the match client was missing the auth header entirely.

## Decision

All HTTP/auth/error plumbing collapses into one `@Component`,
`com.wkaiser.riotapimcpserver.shared.http.RiotApiClient`, which exposes pre-configured
clients:

```java
RestClient regional(RiotApiRegionUri region);   // account, match
RestClient platform(RiotApiPlatformUri platform); // summoner, spectator
```

Each returns a `RestClient` with the `X-RIOT-TOKEN` header set from
`RiotApiProperties.getApiKey()`, the base URL assembled from the region/platform host
(or `RiotApiProperties.getBaseUrlOverride()` when set, for tests), and a
`defaultStatusHandler(HttpStatusCode::isError, …)` that throws
`RiotApiException(message, statusCode)` for any non-2xx response.

Typed config replaces every scattered `@Value("${riot.apiKey}")` with a single
`@ConfigurationProperties(prefix = "riot")` `RiotApiProperties` (`apiKey`, `region`
defaulting to `AMERICAS`, nullable `baseUrlOverride`). `application.yml` reads
`riot.apiKey: ${RIOT_API_KEY:}` for 12-factor portability.

Outbound adapters inject `RiotApiClient` and only make calls. Context-specific error
rules stay in the adapter: the spectator adapter maps `404 → null` (not in game) by
catching `RiotApiException` and checking `getStatusCode() == 404`, distinct from the
shared handler (see [gotchas](../gotchas.md)).

## Consequences

- One place to change auth, base-URL assembly, or error mapping.
- Match requests are now correctly authenticated (a latent bug fixed by construction).
- Adapters are trivially testable against a local mock server via `baseUrlOverride`
  (see [ADR-0003](ADR-0003-wiremock-testing.md) and
  [patterns/add-an-adapter-test.md](../patterns/add-an-adapter-test.md)).
- ArchUnit forbids `RestClient` references outside `..adapter.out.riot..`, keeping the
  centralization honest ([ADR-0004](ADR-0004-archunit-enforcement.md)).
```

- [ ] **Step 3: Create ADR-0003 (WireMock + port-fake testing)**

Create `docs/knowledge/decisions/ADR-0003-wiremock-testing.md`:

```markdown
# ADR-0003: WireMock + port-fake testing

- **Status:** Accepted
- **Date:** 2026-07-13

## Context

Originally, integration tests were `@Disabled` because they required live Riot API
keys, and a `CompilationVerificationTest` existed solely to guard Lombok builder wiring.
There was no HTTP-level mocking, so the adapters' real behavior (URL assembly, auth
header, JSON→DTO parsing, error mapping) was untested and CI could not exercise it
without a secret.

## Decision

Two complementary test styles, both runnable in CI with **no** `RIOT_API_KEY`:

- **Outbound adapter tests (WireMock).** Each `Riot*Adapter` is tested against a local
  WireMock server. `RiotApiProperties.setBaseUrlOverride("http://localhost:<port>")`
  points the real `RestClient` at WireMock. Tests assert the request URL, the
  `X-RIOT-TOKEN` header, JSON→DTO parsing, and error mapping — including spectator
  `404 → null` and other `4xx/5xx → RiotApiException` with the status preserved. Canned
  JSON fixtures live in `src/test/resources/fixtures/`. Dependency:
  `org.wiremock:wiremock-standalone:3.9.2` (`testImplementation`).
- **Application-service tests (port fakes).** Hand-written in-memory fakes implement the
  port interfaces — fast, no HTTP. `AnalyticsService` is tested with fake
  account/summoner/match collaborators, covering the edge cases (zero games; zero-deaths
  KDA).

Cleanup: remove all `@Disabled`; delete `CompilationVerificationTest` (real tests now
cover what it proxied).

## Consequences

- `./gradlew build` passes offline with no key — a hard requirement for CI and for a
  reviewer cloning the repo.
- The port interface is the seam that makes both styles cheap.
- Adds one test dependency (WireMock); no production dependency.
- To write an adapter test, follow
  [patterns/add-an-adapter-test.md](../patterns/add-an-adapter-test.md).
```

- [ ] **Step 4: Create ADR-0004 (ArchUnit enforcement)**

Create `docs/knowledge/decisions/ADR-0004-archunit-enforcement.md`:

```markdown
# ADR-0004: ArchUnit architecture enforcement

- **Status:** Accepted
- **Date:** 2026-07-13

## Context

The hexagonal rules from [ADR-0001](ADR-0001-hexagonal.md) are only valuable if they
cannot silently rot. Code review alone does not reliably catch a service importing an
adapter or a stray `RestClient` outside the outbound package.

## Decision

Encode the rules as an ArchUnit test suite
(`com.tngtech.archunit:archunit-junit5`, `testImplementation`) under an `architecture/`
test package, running as part of `./gradlew test` / `build` — no separate CI workflow.
Rules:

- Layered dependency rule: `domain ⇸ application ⇸ adapter`, inward only.
- `RestClient` is referenced only within `..adapter.out.riot..`.
- `@McpTool` is present only within `..adapter.in.mcp..`.
- Ports are interfaces residing in `..application.port..`.
- No context package depends on another context's internals, **except** `analytics`.
- Naming: `*Service` in `application`, `*Tool` in `adapter.in.mcp`, `*Adapter` in
  `adapter.out.riot`, `*Port` interfaces in `application.port`.

Alongside ArchUnit, JaCoCo measures coverage (report on `test`, a conservative/soft
threshold — the signal is "coverage is visible," not an arbitrary gate) and Spotless
(`spotlessCheck` wired into `check`/`build`) fails the build on formatting drift.

## Consequences

- A dependency-rule or naming violation fails the build, so the architecture stays true
  over time — a strong portfolio signal.
- No CodeQL/SAST workflow is needed for structure; Dependabot covers CVEs.
- New contributors get immediate, precise feedback. The `check-architecture` skill runs
  this suite and interprets the failures.
- When adding a context or tool, expect ArchUnit to enforce the package/naming rules in
  [patterns/add-a-bounded-context.md](../patterns/add-a-bounded-context.md) and
  [patterns/add-an-mcp-tool.md](../patterns/add-an-mcp-tool.md).
```

- [ ] **Step 5: Create ADR-0005 (committed knowledge system)**

Create `docs/knowledge/decisions/ADR-0005-knowledge-system.md`:

```markdown
# ADR-0005: Committed knowledge system

- **Status:** Accepted
- **Date:** 2026-07-13

## Context

The prior `CLAUDE.md` was ~322 lines of marketing, fictional infrastructure, and dated
"recent updates" logs, loaded every session — high token cost, low signal. There was no
durable, committed place for decisions or procedures, so context was re-derived from
code each time and AI agents had nothing reliable to rehydrate from.

## Decision

Commit a knowledge system to the repo (portfolio-visible, GitHub-rendered):

- `docs/knowledge/` — `README.md` (index + hydrate/persist protocol, the single source
  of truth), `decisions/` (ADRs), `patterns/` (how-to guides), `gotchas.md`,
  `glossary.md`.
- `.claude/skills/` (committed) — `scaffold-bounded-context`, `add-mcp-tool`,
  `add-adapter-test`, `check-architecture`; each operationalizes a `patterns/` guide.
- `.claude/agents/` (committed) — a small purpose-built set:
  `riot-context-architect`, `test-author`, `docs-maintainer`; each embeds the
  hydrate/persist protocol.

The **hydrate/persist protocol** (defined once in `docs/knowledge/README.md`,
referenced by `CLAUDE.md` and every agent): hydrate by reading the README + relevant
decisions/patterns/gotchas before acting; persist by writing findings back (new
decision → new ADR; new procedure → new pattern; new pitfall → append to `gotchas.md`),
keeping entries small and single-purpose.

Git hygiene: an explicit `.superpowers/` line in the root `.gitignore`; `.claude/`
committed **except** `.claude/settings.local.json`.

## Consequences

- `CLAUDE.md` shrinks to accurate essentials and defers depth to the KB — lower
  per-session token cost, higher signal.
- Decisions and procedures are versioned with the code and visible to reviewers.
- The system only works if people persist — the protocol makes that the explicit last
  step of every task.
- Superseding, not editing, is the way to reverse a decision (keeps history honest).
```

- [ ] **Step 6: Verify all five ADRs exist and each has Status + Decision**

Run:

```bash
ls docs/knowledge/decisions/ && \
for f in ADR-0001-hexagonal ADR-0002-shared-riot-http-client ADR-0003-wiremock-testing ADR-0004-archunit-enforcement ADR-0005-knowledge-system; do \
  grep -q "**Status:**" "docs/knowledge/decisions/$f.md" && grep -q "## Decision" "docs/knowledge/decisions/$f.md" && echo "$f ok" || echo "$f MISSING SECTION"; \
done
```

Expected: five files listed, then `ADR-0001-hexagonal ok` … `ADR-0005-knowledge-system ok`.

- [ ] **Step 7: Commit**

```bash
git add docs/knowledge/decisions/
git commit -m "docs: add five architecture decision records"
```

---

### Task 3: Pattern guides (`docs/knowledge/patterns/`)

Three copy-pasteable how-to guides referencing the real post-Phase-1 structure and names.

**Files:**
- Create: `docs/knowledge/patterns/add-a-bounded-context.md`
- Create: `docs/knowledge/patterns/add-an-mcp-tool.md`
- Create: `docs/knowledge/patterns/add-an-adapter-test.md`

**Interfaces:**
- Consumes: ADR-0001/0002/0003 (Task 2) for rationale links.
- Produces: three guides. Each is operationalized by a matching skill in Task 6
  (`scaffold-bounded-context`, `add-mcp-tool`, `add-adapter-test`).

- [ ] **Step 1: Create `add-a-bounded-context.md`**

Create `docs/knowledge/patterns/add-a-bounded-context.md`:

````markdown
# Pattern: Add a bounded context

Use this when adding a new Riot API area as its own mini-hexagon. Rationale:
[ADR-0001](../decisions/ADR-0001-hexagonal.md) and
[ADR-0002](../decisions/ADR-0002-shared-riot-http-client.md).

Substitute `<context>` (lowercase, e.g. `champion`) and `<Name>` (PascalCase, e.g.
`Champion`) throughout. Base path: `src/main/java/com/wkaiser/riotapimcpserver/`.

## 1. Create the package skeleton

```bash
ctx=<context>
base=src/main/java/com/wkaiser/riotapimcpserver/$ctx
mkdir -p $base/domain \
         $base/application/port \
         $base/adapter/in/mcp \
         $base/adapter/out/riot
```

(Omit `adapter/out/riot` for a composing context like `analytics`; omit
`adapter/in/mcp` for a context with no MCP tool, like `match`.)

## 2. Domain DTO — `<context>/domain/<Name>.java`

Plain Lombok DTO, **no framework imports**. Keep the established pattern (see
[gotchas](../gotchas.md) for the nested-builder rule):

```java
package com.wkaiser.riotapimcpserver.<context>.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class <Name> {
    private String id;
    // ...fields matching the Riot JSON shape
}
```

## 3. Outbound port — `<context>/application/port/<Name>Port.java`

The architectural boundary. An interface, in `application.port`:

```java
package com.wkaiser.riotapimcpserver.<context>.application.port;

import com.wkaiser.riotapimcpserver.<context>.domain.<Name>;

public interface <Name>Port {
    <Name> get<Name>ById(String id);
}
```

## 4. Outbound adapter — `<context>/adapter/out/riot/Riot<Name>Adapter.java`

Injects `RiotApiClient`; makes calls only. Use `regional(...)` for region-routed
endpoints (account, match), `platform(...)` for platform-routed (summoner, spectator).
The `X-RIOT-TOKEN` header, base URL, and non-2xx → `RiotApiException` mapping are
already handled by `RiotApiClient` — do **not** re-implement them here.

```java
package com.wkaiser.riotapimcpserver.<context>.adapter.out.riot;

import com.wkaiser.riotapimcpserver.<context>.application.port.<Name>Port;
import com.wkaiser.riotapimcpserver.<context>.domain.<Name>;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.shared.http.RiotApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Riot<Name>Adapter implements <Name>Port {

    private final RiotApiClient riotApiClient;

    @Override
    public <Name> get<Name>ById(String id) {
        return riotApiClient.platform(RiotApiPlatformUri.NA1).get()
                .uri("/lol/<area>/v4/<name>/{id}", id)
                .retrieve()
                .body(<Name>.class);
    }
}
```

## 5. Application service — `<context>/application/<Name>Service.java`

Pure logic; depends on the port, never on `RestClient` or an adapter:

```java
package com.wkaiser.riotapimcpserver.<context>.application;

import com.wkaiser.riotapimcpserver.<context>.application.port.<Name>Port;
import com.wkaiser.riotapimcpserver.<context>.domain.<Name>;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class <Name>Service {

    private final <Name>Port <context>Port;

    public <Name> get<Name>ById(String id) {
        log.info("Fetching <name> for id: {}", id);
        return <context>Port.get<Name>ById(id);
    }
}
```

## 6. (Optional) inbound MCP tool

If the context is user-facing, add a tool per
[add-an-mcp-tool.md](add-an-mcp-tool.md).

## 7. Tests

- Adapter: WireMock test per [add-an-adapter-test.md](add-an-adapter-test.md).
- Service: a fast test with a hand-written in-memory `<Name>Port` fake
  ([ADR-0003](../decisions/ADR-0003-wiremock-testing.md)).

## 8. Verify against the architecture rules

Run the `check-architecture` skill (or `./gradlew test`). ArchUnit
([ADR-0004](../decisions/ADR-0004-archunit-enforcement.md)) enforces the naming
(`*Service`/`*Tool`/`*Adapter`/`*Port`), the package placement, and the inward-only
dependency rule.

## 9. Persist

If you learned something reusable, update the KB per the
[hydrate/persist protocol](../README.md#hydrate--persist-protocol).
````

- [ ] **Step 2: Create `add-an-mcp-tool.md`**

Create `docs/knowledge/patterns/add-an-mcp-tool.md`:

````markdown
# Pattern: Add an MCP tool

Use this to expose an application service method to AI models as an MCP tool. Tools are
the inbound adapters of the hexagon ([ADR-0001](../decisions/ADR-0001-hexagonal.md)):
they live in `<context>/adapter/in/mcp/`, are annotated `@McpTool`, and call the
application service directly (there is no inbound-port interface).

## 1. Location and class

A tool is a `@Component` named `<Name>Tool` in
`com.wkaiser.riotapimcpserver.<context>.adapter.in.mcp`. It depends on the application
`<Name>Service`, never on a port or `RestClient`. Existing tools to copy from:
`SummonerTool`, `LiveGameTool`, `RiotAccountTool`, `AnalyticsTool`.

## 2. The annotation

`@McpTool` and `@McpToolParam` come from `org.springframework.ai.mcp.annotation`. Tool
`name`s are stable and snake_case (e.g. `get_lol_summoner_by_name`,
`get_current_game_by_summoner_name`). Every `@McpToolParam` needs a `description`;
platform/region are passed as `String` and parsed to the enum inside the method.

```java
package com.wkaiser.riotapimcpserver.<context>.adapter.in.mcp;

import com.wkaiser.riotapimcpserver.<context>.application.<Name>Service;
import com.wkaiser.riotapimcpserver.<context>.domain.<Name>;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class <Name>Tool {

    private final <Name>Service <context>Service;

    @McpTool(name = "get_<name>_by_id",
            description = "Get <Name> information by id")
    public <Name> get<Name>ById(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The <name> id", required = true) String id) {
        log.info("MCP Tool - Fetching <name> {} on {}", id, platformStr);
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        return <context>Service.get<Name>ById(platform, id);
    }
}
```

## 3. Discovery

Tools are auto-discovered by Spring AI's `@McpTool` annotation scanner — no manual
registration. The `@Component` must be in a package scanned by the application (any
subpackage of `com.wkaiser.riotapimcpserver`). See
[gotchas](../gotchas.md) for the discovery pitfalls (missing `@Component`, tool
outside `adapter.in.mcp`, duplicate `name`).

## 4. Verify

- ArchUnit ([ADR-0004](../decisions/ADR-0004-archunit-enforcement.md)) requires
  `@McpTool` only in `..adapter.in.mcp..` and the `*Tool` name — run the
  `check-architecture` skill.
- Confirm the tool name is unique and unchanged if you moved an existing tool.

## 5. Persist

Record anything reusable per the
[hydrate/persist protocol](../README.md#hydrate--persist-protocol).
````

- [ ] **Step 3: Create `add-an-adapter-test.md`**

Create `docs/knowledge/patterns/add-an-adapter-test.md`:

````markdown
# Pattern: Add an adapter test (WireMock)

Use this to test an outbound `Riot*Adapter` against a local mock Riot server — no live
key, runs in CI. Rationale: [ADR-0003](../decisions/ADR-0003-wiremock-testing.md).
WireMock (`org.wiremock:wiremock-standalone:3.9.2`) is already a `testImplementation`
dependency.

## What to assert

1. The request path/query is what the Riot endpoint expects.
2. The `X-RIOT-TOKEN` header carries the configured key.
3. A 2xx JSON body parses into the domain DTO.
4. Error mapping: a `4xx/5xx` becomes `RiotApiException` with the status preserved; the
   spectator adapter maps `404 → null` (not in game).

## Wiring

Point the real `RestClient` at WireMock via `RiotApiProperties.setBaseUrlOverride`, then
build the adapter with a real `RiotApiClient`:

```java
package com.wkaiser.riotapimcpserver.<context>.adapter.out.riot;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.wkaiser.riotapimcpserver.<context>.domain.<Name>;
import com.wkaiser.riotapimcpserver.shared.config.RiotApiProperties;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import com.wkaiser.riotapimcpserver.shared.http.RiotApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class Riot<Name>AdapterTest {

    private WireMockServer wireMock;
    private Riot<Name>Adapter adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        RiotApiProperties props = new RiotApiProperties();
        props.setApiKey("test-key-123");
        props.setBaseUrlOverride("http://localhost:" + wireMock.port());

        adapter = new Riot<Name>Adapter(new RiotApiClient(props));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void parses_body_and_sends_token_header() {
        stubFor(get(urlEqualTo("/lol/<area>/v4/<name>/abc"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"abc\"}")));

        <Name> result = adapter.get<Name>ById("abc"); // platform-routed adapters take a platform arg

        assertThat(result.getId()).isEqualTo("abc");
        verify(getRequestedFor(urlEqualTo("/lol/<area>/v4/<name>/abc"))
                .withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }
}
```

## Fixtures

Put larger canned JSON in `src/test/resources/fixtures/` and load it into the stub body
rather than inlining big literals.

## Spectator 404 → null

For the spectator adapter, stub a `404` and assert the method returns `null` (not a
thrown exception) — this is the one context-specific rule, handled in the adapter, not
the shared `RiotApiClient` handler. See [gotchas](../gotchas.md).

## Persist

Add reusable learnings per the
[hydrate/persist protocol](../README.md#hydrate--persist-protocol).
````

- [ ] **Step 4: Verify the three guides exist and cross-link ADRs**

Run:

```bash
ls docs/knowledge/patterns/ && \
grep -l "decisions/ADR-0001-hexagonal.md" docs/knowledge/patterns/add-a-bounded-context.md && \
grep -l "decisions/ADR-0003-wiremock-testing.md" docs/knowledge/patterns/add-an-adapter-test.md && \
echo "patterns ok"
```

Expected: three files listed, the two `grep -l` lines echo their filenames, then `patterns ok`.

- [ ] **Step 5: Commit**

```bash
git add docs/knowledge/patterns/
git commit -m "docs: add how-to pattern guides for contexts, tools, and adapter tests"
```

---

### Task 4: Gotchas and glossary (`docs/knowledge/gotchas.md`, `glossary.md`)

The two remaining KB leaf files, plus a full link-resolution check of the whole KB now that all targets exist.

**Files:**
- Create: `docs/knowledge/gotchas.md`
- Create: `docs/knowledge/glossary.md`

**Interfaces:**
- Consumes: nothing new.
- Produces: `gotchas.md` (referenced by ADR-0002, ADR-0001, and all three patterns) and `glossary.md` (referenced by `README.md`). Completes the KB link graph verified in Step 4.

- [ ] **Step 1: Create `gotchas.md`**

Create `docs/knowledge/gotchas.md`:

````markdown
# Gotchas

Sharp edges specific to this codebase. Append new ones at the bottom, newest last, each
as its own small section (per the
[hydrate/persist protocol](README.md#hydrate--persist-protocol)).

## Lombok nested `@Builder` static classes need `@NoArgsConstructor` + `@AllArgsConstructor`

Every DTO uses `@Data @Builder @NoArgsConstructor @AllArgsConstructor` (with
`@JsonIgnoreProperties(ignoreUnknown = true)` where the shape is a Riot response). The
**inner static classes** must carry the same four annotations. If a nested `@Builder`
class omits `@NoArgsConstructor` and `@AllArgsConstructor`, Lombok generates a
conflicting all-args constructor and the builder wiring breaks `:compileJava` /
`:compileTestJava` (the class that historically bit us is `Perks` in
`spectator/domain`). Symptom: "constructor … cannot be applied" or a missing builder
method at compile time. Fix: put all four annotations on the nested class too. Jackson
also needs the no-args constructor to deserialize.

## Spectator `404` means "not in a game" — map it to `null`, not an exception

`RiotApiClient` throws `RiotApiException` for **any** non-2xx response. The Spectator API
returns `404` when a summoner is simply not currently in a game — a normal outcome, not
an error. `RiotSpectatorAdapter.getCurrentGameInfo(...)` therefore catches
`RiotApiException`, and when `getStatusCode() == 404` returns `null`; any other status is
rethrown. This rule lives in the adapter (context-specific), **not** in the shared
`RiotApiClient` status handler. When you test it, stub a `404` and assert `null`.

## Region vs platform routing — do not mix them up

Two different host schemes, two different `RiotApiClient` entry points:

- **Region** (`RiotApiRegionUri`: `AMERICAS`, `ASIA`, `EUROPE`, `SEA`, hosts like
  `americas.api.riotgames.com`) routes **account** and **match** endpoints. Use
  `riotApiClient.regional(region)`.
- **Platform** (`RiotApiPlatformUri`: `NA1`, `EUW1`, `KR`, … hosts like
  `na1.api.riotgames.com`) routes **summoner** and **spectator** endpoints. Use
  `riotApiClient.platform(platform)`.

Calling a match endpoint on a platform host (or a summoner endpoint on a region host)
returns 404/wrong data. See [glossary](glossary.md#platform-vs-region).

## `@McpTool` discovery

MCP tools are auto-discovered by Spring AI's annotation scanner — there is no manual
registry. For a tool to appear:

- the class must be a Spring bean (`@Component`) in a scanned package under
  `com.wkaiser.riotapimcpserver`;
- `@McpTool`/`@McpToolParam` must be imported from
  `org.springframework.ai.mcp.annotation`;
- each `@McpTool` `name` must be **unique** across the whole app (a duplicate silently
  shadows or fails discovery);
- by the architecture rules, `@McpTool` may appear **only** in `..adapter.in.mcp..`
  (ArchUnit fails the build otherwise — see
  [ADR-0004](decisions/ADR-0004-archunit-enforcement.md)).

If a new tool does not show up, check the bean annotation and the package first.
````

- [ ] **Step 2: Create `glossary.md`**

Create `docs/knowledge/glossary.md`:

```markdown
# Glossary

Riot / League of Legends domain terms used throughout the codebase.

## PUUID

**P**layer **U**niversally **U**nique **ID**entifier. A stable, encrypted, global player
id that is the same across all Riot games and regions. It is the primary key we thread
between contexts: `account` resolves a Riot ID to a PUUID, `summoner` and `match` are
then queried by PUUID. Modeled as a `String`.

## Riot ID (`gameName#tagLine`)

A player's human-readable, cross-game handle, written `gameName#tagLine` (e.g.
`Faker#KR1`). The `gameName` is the display name; the `tagLine` is a short discriminator.
The account context resolves a Riot ID to an account (and its PUUID) via
`/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}`. In tests use a placeholder
like `GameName#TAG`.

## Summoner

A League-of-Legends-specific profile on a particular **platform** (server): summoner
name, level, profile icon, encrypted summoner id, and PUUID. Retrieved from the
Summoner-V4 API. Note: a summoner exists per platform, whereas a PUUID/Riot ID is global.

## Platform vs region

Riot's API uses two routing schemes:

- **Platform** — a specific game server, e.g. `NA1`, `EUW1`, `KR` (`RiotApiPlatformUri`).
  Used for **summoner** and **spectator** endpoints, which are server-scoped.
- **Region** — a super-region aggregating platforms: `AMERICAS`, `EUROPE`, `ASIA`, `SEA`
  (`RiotApiRegionUri`). Used for **account** and **match** endpoints, which are
  region-scoped.

Choosing the wrong one yields 404s. See
[gotchas](gotchas.md#region-vs-platform-routing--do-not-mix-them-up).

## Spectator (live game)

The Spectator-V4 API exposes **currently in-progress** games: participants, champion
bans, and game metadata. It intentionally does **not** provide real-time CS, KDA, gold,
item builds, or positions. A `404` from the by-summoner endpoint means the player is not
in a game right now (mapped to `null` in the adapter, not an error).
```

- [ ] **Step 3: Verify both files exist**

Run:

```bash
test -f docs/knowledge/gotchas.md && test -f docs/knowledge/glossary.md && \
grep -q "Lombok nested" docs/knowledge/gotchas.md && grep -q "PUUID" docs/knowledge/glossary.md && \
echo "gotchas+glossary ok"
```

Expected: `gotchas+glossary ok`.

- [ ] **Step 4: Verify every relative markdown link in the KB resolves**

This confirms the full `docs/knowledge/` link graph (README → decisions/patterns/gotchas/glossary, and the cross-links between them) has no broken targets. Run from Git Bash:

```bash
cd docs/knowledge && \
broken=0; \
while IFS= read -r line; do \
  src="${line%%:*}"; rest="${line#*:}"; target="${rest%%|*}"; \
  tpath="$(dirname "$src")/${target%%#*}"; \
  if [ ! -e "$tpath" ]; then echo "BROKEN: $src -> $target"; broken=1; fi; \
done < <(grep -rnoE '\]\([^)]+\)' . | grep -vE '\]\((https?:|#)' | sed -E 's/\]\(([^)]+)\)/|\1/; s/:[0-9]+:/:/' | awk -F'|' '{print $1":"$2}'); \
[ "$broken" = 0 ] && echo "ALL LINKS RESOLVE"; cd - >/dev/null
```

Expected: `ALL LINKS RESOLVE` with no `BROKEN:` lines. (If a `BROKEN:` line appears, fix the offending link's path — every intra-KB link is relative to the linking file's directory.)

- [ ] **Step 5: Commit**

```bash
git add docs/knowledge/gotchas.md docs/knowledge/glossary.md
git commit -m "docs: add gotchas and glossary to knowledge base"
```

---

### Task 5: Committed project skills (`.claude/skills/`)

Four committed skills, each a directory with a `SKILL.md` (YAML frontmatter: `name`, `description`) that operationalizes the matching `patterns/` guide. `check-architecture` runs the Phase 3 quality gates and interprets failures.

**Files:**
- Create: `.claude/skills/scaffold-bounded-context/SKILL.md`
- Create: `.claude/skills/add-mcp-tool/SKILL.md`
- Create: `.claude/skills/add-adapter-test/SKILL.md`
- Create: `.claude/skills/check-architecture/SKILL.md`

**Interfaces:**
- Consumes: the pattern guides (Task 3), ADR-0004 (Task 2).
- Produces: four `SKILL.md` files with valid frontmatter. Committed (not ignored) — verified in Task 7.

- [ ] **Step 1: Create `scaffold-bounded-context/SKILL.md`**

Create `.claude/skills/scaffold-bounded-context/SKILL.md`:

```markdown
---
name: scaffold-bounded-context
description: Scaffold a new Riot bounded context as a mini-hexagon (domain, application + port, in/mcp and out/riot adapters) following the project's hexagonal architecture. Use when adding a new Riot API area to riot-api-mcp-server.
---

# Scaffold a bounded context

Operationalizes `docs/knowledge/patterns/add-a-bounded-context.md`.

## Hydrate first

Read `docs/knowledge/README.md`, `docs/knowledge/gotchas.md`, and
`docs/knowledge/decisions/ADR-0001-hexagonal.md` +
`ADR-0002-shared-riot-http-client.md`.

## Steps

1. Ask for `<context>` (lowercase) and `<Name>` (PascalCase), and whether it is
   user-facing (needs an MCP tool) and/or composing (no outbound adapter).
2. Create the package skeleton under
   `src/main/java/com/wkaiser/riotapimcpserver/<context>/`:
   `domain/`, `application/port/`, `adapter/in/mcp/` (if user-facing),
   `adapter/out/riot/` (unless composing).
3. Generate, from the pattern guide's templates:
   - `domain/<Name>.java` — Lombok DTO, no framework imports (mind the nested-builder
     gotcha).
   - `application/port/<Name>Port.java` — interface in `application.port`.
   - `adapter/out/riot/Riot<Name>Adapter.java` — `@Component` injecting `RiotApiClient`;
     `regional(...)` for account/match-style routing, `platform(...)` for
     summoner/spectator-style routing. Never re-implement auth/error handling.
   - `application/<Name>Service.java` — `@Service`, depends on the port only.
   - Optionally `adapter/in/mcp/<Name>Tool.java` (delegate to the add-mcp-tool skill).
4. Add tests: a WireMock adapter test (delegate to add-adapter-test) and a
   port-fake service test.
5. Run the check-architecture skill so ArchUnit validates naming and layering.

## Persist

If you discovered a reusable step or pitfall, update `patterns/` or `gotchas.md` per the
hydrate/persist protocol before finishing.
```

- [ ] **Step 2: Create `add-mcp-tool/SKILL.md`**

Create `.claude/skills/add-mcp-tool/SKILL.md`:

```markdown
---
name: add-mcp-tool
description: Add a Spring AI @McpTool inbound adapter that exposes an application service method to AI models, placed correctly in adapter/in/mcp with a unique tool name. Use when adding or moving an MCP tool in riot-api-mcp-server.
---

# Add an MCP tool

Operationalizes `docs/knowledge/patterns/add-an-mcp-tool.md`.

## Hydrate first

Read `docs/knowledge/README.md`, `docs/knowledge/gotchas.md` (the `@McpTool discovery`
section), and `docs/knowledge/decisions/ADR-0001-hexagonal.md`.

## Steps

1. Confirm the target `<context>` already has an `application/<Name>Service`. The tool
   depends on the service, never on a port or `RestClient`.
2. Create `<context>/adapter/in/mcp/<Name>Tool.java` as a `@Component`:
   - Import `@McpTool`/`@McpToolParam` from `org.springframework.ai.mcp.annotation`.
   - Give the method a snake_case, unique `name` (mirror existing names like
     `get_lol_summoner_by_name`).
   - Every `@McpToolParam` gets a `description`. Accept platform/region as `String` and
     parse via `RiotApiPlatformUri.valueOf(platformStr.toUpperCase())`.
   - Delegate to the service; log at info with an `MCP Tool -` prefix.
3. Do not register the tool anywhere — discovery is automatic via annotation scanning.
4. Run the check-architecture skill: ArchUnit requires `@McpTool` only inside
   `..adapter.in.mcp..` and enforces the `*Tool` name.

## Common failures

- Tool not discovered → missing `@Component`, wrong package, or a duplicate `name`
  (see gotchas).
- ArchUnit failure → `@McpTool` used outside `adapter.in.mcp`, or class not named
  `*Tool`.

## Persist

Record any new pitfall in `gotchas.md` per the hydrate/persist protocol.
```

- [ ] **Step 3: Create `add-adapter-test/SKILL.md`**

Create `.claude/skills/add-adapter-test/SKILL.md`:

```markdown
---
name: add-adapter-test
description: Write a WireMock test for an outbound Riot*Adapter that asserts request URL, the X-RIOT-TOKEN header, JSON to DTO parsing, and error mapping (including spectator 404 to null) with no live API key. Use when testing an outbound adapter in riot-api-mcp-server.
---

# Add an adapter test

Operationalizes `docs/knowledge/patterns/add-an-adapter-test.md`.

## Hydrate first

Read `docs/knowledge/README.md`, `docs/knowledge/decisions/ADR-0003-wiremock-testing.md`,
and the spectator note in `docs/knowledge/gotchas.md`.

## Steps

1. Create `Riot<Name>AdapterTest` in the same package as the adapter
   (`<context>/adapter/out/riot`), under `src/test/java/...`.
2. In `@BeforeEach`: start a `WireMockServer(options().dynamicPort())`; build a
   `RiotApiProperties` with a fake `apiKey` and
   `setBaseUrlOverride("http://localhost:" + wireMock.port())`; construct the adapter
   with `new RiotApiClient(props)`.
3. Write, at minimum, these assertions (write the failing test first, TDD):
   - path/query is correct;
   - `X-RIOT-TOKEN` header equals the fake key;
   - a 2xx JSON body parses into the DTO;
   - a `4xx/5xx` throws `RiotApiException` with the status preserved.
4. For the spectator adapter, add a case: stub `404`, assert the method returns `null`.
5. Put large JSON bodies in `src/test/resources/fixtures/`.
6. `@AfterEach`: `wireMock.stop()`.

WireMock (`org.wiremock:wiremock-standalone:3.9.2`) is already a `testImplementation`
dependency — do not add it again.

## Persist

Update `gotchas.md`/`patterns/` if you find a reusable testing wrinkle.
```

- [ ] **Step 4: Create `check-architecture/SKILL.md`**

Create `.claude/skills/check-architecture/SKILL.md`:

```markdown
---
name: check-architecture
description: Run the build-time architecture and quality gates (ArchUnit rules, JaCoCo coverage, Spotless formatting) and interpret failures against the project's hexagonal rules. Use before committing structural changes or when a build fails on ArchUnit/coverage/formatting in riot-api-mcp-server.
---

# Check architecture

Operationalizes the quality gates from
`docs/knowledge/decisions/ADR-0004-archunit-enforcement.md`.

## Hydrate first

Read `docs/knowledge/decisions/ADR-0004-archunit-enforcement.md` and
`ADR-0001-hexagonal.md`.

## Run the gates

From Git Bash:

```bash
./gradlew test        # runs the ArchUnit suite (architecture/) + unit + WireMock tests
./gradlew check       # adds spotlessCheck; build also runs these
```

The JaCoCo report is written under `build/reports/jacoco/`.

## Interpret failures

- **Layered dependency violation** (`application` importing `adapter`, or `domain`
  importing anything outward): move the offending dependency inward — depend on the port,
  not the adapter.
- **`RestClient` outside `..adapter.out.riot..`**: only outbound adapters may touch
  `RestClient`; route the call through `RiotApiClient` inside an adapter.
- **`@McpTool` outside `..adapter.in.mcp..`**: move the tool into `adapter/in/mcp`.
- **Port not an interface / not in `..application.port..`**: make it an interface and
  relocate it.
- **Cross-context internals**: only `analytics` may depend on other contexts'
  application services; otherwise decouple.
- **Naming rule**: ensure `*Service` / `*Tool` / `*Adapter` / `*Port` land in their
  required packages.
- **Spotless**: run `./gradlew spotlessApply` to auto-fix formatting, then re-run.
- **Coverage**: the threshold is intentionally soft — a failure usually means a whole
  new class has no test; add one rather than lowering the bar.

## Persist

If a failure taught you a non-obvious rule interaction, append it to
`docs/knowledge/gotchas.md`.
```

- [ ] **Step 5: Verify all four skills exist with frontmatter `name` + `description`**

Run:

```bash
for s in scaffold-bounded-context add-mcp-tool add-adapter-test check-architecture; do \
  f=".claude/skills/$s/SKILL.md"; \
  test -f "$f" && head -1 "$f" | grep -q '^---$' && grep -q "^name: $s$" "$f" && grep -q "^description: " "$f" && echo "$s ok" || echo "$s BAD"; \
done
```

Expected: `scaffold-bounded-context ok` … `check-architecture ok`.

- [ ] **Step 6: Commit**

```bash
git add .claude/skills/
git commit -m "chore: add committed project skills for hexagon workflows"
```

---

### Task 6: Curated agents (`.claude/agents/`)

Three purpose-built agents. Each is a markdown file with frontmatter (`name`, `description`, `tools`) and a body that embeds the hydrate/persist protocol.

**Files:**
- Create: `.claude/agents/riot-context-architect.md`
- Create: `.claude/agents/test-author.md`
- Create: `.claude/agents/docs-maintainer.md`

**Interfaces:**
- Consumes: `docs/knowledge/README.md` protocol (Task 1), the skills (Task 5).
- Produces: three agent files with valid frontmatter. Committed (not ignored) — verified in Task 7.

- [ ] **Step 1: Create `riot-context-architect.md`**

Create `.claude/agents/riot-context-architect.md`:

```markdown
---
name: riot-context-architect
description: Designs and scaffolds Riot bounded contexts and their ports/adapters, keeping the hexagonal dependency rules intact. Use for adding or restructuring a context, port, or adapter in riot-api-mcp-server.
tools: Read, Grep, Glob, Edit, Write, Bash
---

You are the architecture-owner for `riot-api-mcp-server`, a Spring Boot 4.1 / Spring AI
2.0 MCP server (Java 21) built as bounded-context hexagons.

## Hydrate (do this before acting)

1. Read `docs/knowledge/README.md`.
2. Read `docs/knowledge/gotchas.md`.
3. Read `docs/knowledge/decisions/ADR-0001-hexagonal.md` and
   `ADR-0002-shared-riot-http-client.md`.
4. If scaffolding, follow `docs/knowledge/patterns/add-a-bounded-context.md` (or invoke
   the `scaffold-bounded-context` skill).

## Rules you enforce

- Top-level context packages under `com.wkaiser.riotapimcpserver`:
  `domain/`, `application/` (+ `port/`), `adapter/in/mcp/`, `adapter/out/riot/`.
- Dependency rule: `adapter → application → domain`, inward only. `domain` has no
  framework deps; `application` depends on its port, never on an adapter.
- Only `adapter/out/riot` touches `RestClient`, always via `RiotApiClient`
  (`regional(...)` for account/match, `platform(...)` for summoner/spectator). Never
  reintroduce per-service HTTP/auth/error plumbing.
- Only `adapter/in/mcp` uses `@McpTool`. Cross-context references are forbidden except
  `analytics` depending on other contexts' application services.
- Naming: `*Service` / `*Tool` / `*Adapter` / `*Port` in their required packages.
- After changes, run the `check-architecture` skill; ArchUnit must pass.

## Persist (before you finish)

Write findings back per the hydrate/persist protocol: a new decision → a new ADR in
`docs/knowledge/decisions/`; a new procedure → a `docs/knowledge/patterns/` guide; a new
pitfall → append to `docs/knowledge/gotchas.md`. Keep entries small and single-purpose.
```

- [ ] **Step 2: Create `test-author.md`**

Create `.claude/agents/test-author.md`:

```markdown
---
name: test-author
description: Writes fast, key-free tests for riot-api-mcp-server - WireMock tests for outbound adapters and in-memory port-fake tests for application services. Use when adding or fixing tests.
tools: Read, Grep, Glob, Edit, Write, Bash
---

You author tests for `riot-api-mcp-server`. All tests must run in CI with **no**
`RIOT_API_KEY`.

## Hydrate (do this before acting)

1. Read `docs/knowledge/README.md`.
2. Read `docs/knowledge/decisions/ADR-0003-wiremock-testing.md`.
3. Read `docs/knowledge/gotchas.md` (spectator `404 → null`; nested Lombok builders).
4. For adapter tests, follow `docs/knowledge/patterns/add-an-adapter-test.md` (or invoke
   the `add-adapter-test` skill).

## How you test

- **Outbound adapters** (`Riot*Adapter`): WireMock against `localhost`, wired via
  `RiotApiProperties.setBaseUrlOverride`. Assert URL, the `X-RIOT-TOKEN` header,
  JSON→DTO parsing, and error mapping (`4xx/5xx → RiotApiException` with status;
  spectator `404 → null`). Fixtures live in `src/test/resources/fixtures/`.
- **Application services**: hand-written in-memory fakes implementing the port
  interface — no HTTP. Cover edge cases (e.g. analytics: zero games, zero-deaths KDA).
- Practice TDD: write the failing test first, then make it pass.
- WireMock (`org.wiremock:wiremock-standalone:3.9.2`) is already a `testImplementation`
  dependency — do not add it again.

## Persist (before you finish)

If you hit a reusable testing wrinkle, append it to `docs/knowledge/gotchas.md` or refine
`docs/knowledge/patterns/add-an-adapter-test.md`, per the hydrate/persist protocol.
```

- [ ] **Step 3: Create `docs-maintainer.md`**

Create `.claude/agents/docs-maintainer.md`:

```markdown
---
name: docs-maintainer
description: Keeps the docs and knowledge base accurate and honest - README, ARCHITECTURE, CONTRIBUTING, CHANGELOG, and docs/knowledge (ADRs, patterns, gotchas, glossary). Use when documenting a change or persisting a finding.
tools: Read, Grep, Glob, Edit, Write, Bash
---

You are the documentation and knowledge-base owner for `riot-api-mcp-server`. Docs must
describe the **real** project — no aspirational or fictional content.

## Hydrate (do this before acting)

1. Read `docs/knowledge/README.md` (the hydrate/persist protocol lives here).
2. Skim the relevant `docs/knowledge/decisions/` and `patterns/` so docs stay consistent
   with the recorded decisions.

## What you maintain

- Top-level docs: `README.md`, `ARCHITECTURE.md`, `CONTRIBUTING.md`, `CHANGELOG.md`,
  and the slim `CLAUDE.md` (which must point at `docs/knowledge/README.md` for the
  hydrate/persist loop).
- The knowledge base under `docs/knowledge/`.

## Persist protocol (this is your core job)

Turn findings into durable KB entries, each small and single-purpose:

- New architectural decision → new ADR `docs/knowledge/decisions/ADR-000N-<slug>.md`
  using the template in the README; then add it to the README's ADR list. Reverse a
  decision by **superseding** (new ADR + set the old Status to
  `Superseded by ADR-000M`), never by editing history.
- New recurring procedure → new `docs/knowledge/patterns/<name>.md`; link it from the
  README.
- New pitfall → append a section to the bottom of `docs/knowledge/gotchas.md`.
- New domain term → add it to `docs/knowledge/glossary.md`.

## Verify before finishing

- Every relative markdown link resolves (no broken targets).
- `CLAUDE.md` still references `docs/knowledge/README.md`.
- No fictional claims (infra, agent counts, "success stories") crept back in.
```

- [ ] **Step 4: Verify all three agents exist with `name`, `description`, `tools`**

Run:

```bash
for a in riot-context-architect test-author docs-maintainer; do \
  f=".claude/agents/$a.md"; \
  test -f "$f" && grep -q "^name: $a$" "$f" && grep -q "^description: " "$f" && grep -q "^tools: " "$f" && grep -q "Hydrate" "$f" && grep -q "Persist" "$f" && echo "$a ok" || echo "$a BAD"; \
done
```

Expected: `riot-context-architect ok`, `test-author ok`, `docs-maintainer ok`.

- [ ] **Step 5: Commit**

```bash
git add .claude/agents/
git commit -m "chore: add curated hexagon-aware agents with hydrate/persist protocol"
```

---

### Task 7: `.gitignore` hardening

Add an explicit `.superpowers/` ignore line, and verify the committed `.claude/skills/` and `.claude/agents/` are tracked while `.claude/settings.local.json` stays ignored.

**Files:**
- Modify: `.gitignore`

**Interfaces:**
- Consumes: the skills (Task 5) and agents (Task 6) that must remain trackable.
- Produces: an updated root `.gitignore`. No new symbols.

- [ ] **Step 1: Read the current `.gitignore` `### Claude Code ###` section**

Run:

```bash
grep -n "Claude Code\|.claude\|.superpowers" .gitignore || echo "no claude/superpowers lines yet"
```

Expected (current state): the `### Claude Code ###` header and `.claude/settings.local.json` on the following line; **no** `.superpowers` line. Confirm only `.claude/settings.local.json` is ignored under `.claude/` — nothing broader like `.claude/` or `.claude/*`.

- [ ] **Step 2: Append the `.superpowers/` ignore line**

The existing file ends with:

```
### Claude Code ###
.claude/settings.local.json
```

Add a new `### Superpowers ###` section after it. Edit `.gitignore` so its final lines read exactly:

```
### Claude Code ###
.claude/settings.local.json

### Superpowers ###
.superpowers/
```

Do **not** add any `.claude/` or `.claude/skills`/`.claude/agents` line — those must stay trackable.

- [ ] **Step 3: Verify the ignore behavior with `git check-ignore`**

Run from Git Bash:

```bash
echo "--- should be IGNORED ---"
git check-ignore .superpowers/anything && echo "OK: .superpowers ignored" || echo "FAIL: .superpowers not ignored"
git check-ignore .claude/settings.local.json && echo "OK: settings.local ignored" || echo "FAIL: settings.local not ignored"
echo "--- should NOT be ignored (exit 1 = not ignored) ---"
git check-ignore .claude/skills/scaffold-bounded-context/SKILL.md; [ $? -eq 1 ] && echo "OK: skills tracked" || echo "FAIL: skills ignored"
git check-ignore .claude/agents/riot-context-architect.md; [ $? -eq 1 ] && echo "OK: agents tracked" || echo "FAIL: agents ignored"
```

Expected:
```
--- should be IGNORED ---
.superpowers/anything
OK: .superpowers ignored
.claude/settings.local.json
OK: settings.local ignored
--- should NOT be ignored (exit 1 = not ignored) ---
OK: skills tracked
OK: agents tracked
```

- [ ] **Step 4: Confirm the committed tooling is actually staged/tracked**

Run:

```bash
git ls-files .claude/skills .claude/agents | sort
```

Expected: the four `SKILL.md` paths and the three agent `.md` paths are listed (they were committed in Tasks 5–6), and `.claude/settings.local.json` is **not** among them.

- [ ] **Step 5: Commit**

```bash
git add .gitignore
git commit -m "chore: harden gitignore with explicit .superpowers ignore"
```

---

### Task 8: `CLAUDE.md` knowledge-base cross-reference (verify; add pointer only if missing)

Phase 5 rewrote `CLAUDE.md` and is responsible for wiring the hydrate/persist reference. This task **verifies** that pointer exists to avoid conflicting with Phase 5; it only adds a single line if Phase 5 somehow omitted it.

**Files:**
- Modify (only if the verify fails): `CLAUDE.md`

**Interfaces:**
- Consumes: `docs/knowledge/README.md` (Task 1).
- Produces: a guaranteed reference from `CLAUDE.md` to `docs/knowledge/README.md`.

- [ ] **Step 1: Verify `CLAUDE.md` references the knowledge base**

Run:

```bash
grep -n "docs/knowledge/README.md" CLAUDE.md && echo "PRESENT" || echo "MISSING"
```

Expected (Phase 5 done correctly): a matching line and `PRESENT`. If `PRESENT`, **skip Steps 2–3** — do not modify `CLAUDE.md`; there is nothing to commit for this task.

- [ ] **Step 2: (Only if MISSING) Add the single pointer line**

Only if Step 1 printed `MISSING`, append this section to the end of `CLAUDE.md`:

```markdown
## Knowledge base

Before acting and before finishing, follow the hydrate/persist protocol in
[`docs/knowledge/README.md`](docs/knowledge/README.md): hydrate by reading the README
plus the relevant `decisions/`, `patterns/`, and `gotchas.md`; persist findings back
(new decision → new ADR; new procedure → new pattern; new pitfall → append to
`gotchas.md`).
```

- [ ] **Step 3: (Only if you edited in Step 2) Re-verify and commit**

Run:

```bash
grep -q "docs/knowledge/README.md" CLAUDE.md && echo "PRESENT"
```

Expected: `PRESENT`. Then:

```bash
git add CLAUDE.md
git commit -m "docs: point CLAUDE.md at the knowledge base hydrate/persist protocol"
```

- [ ] **Step 4: Final knowledge-system tree check**

Confirm the whole Phase 6 deliverable is present. Run:

```bash
echo "=== docs/knowledge ===" && find docs/knowledge -type f | sort
echo "=== .claude/skills ===" && find .claude/skills -name SKILL.md | sort
echo "=== .claude/agents ===" && find .claude/agents -name '*.md' | sort
```

Expected:
```
=== docs/knowledge ===
docs/knowledge/README.md
docs/knowledge/decisions/ADR-0001-hexagonal.md
docs/knowledge/decisions/ADR-0002-shared-riot-http-client.md
docs/knowledge/decisions/ADR-0003-wiremock-testing.md
docs/knowledge/decisions/ADR-0004-archunit-enforcement.md
docs/knowledge/decisions/ADR-0005-knowledge-system.md
docs/knowledge/glossary.md
docs/knowledge/gotchas.md
docs/knowledge/patterns/add-a-bounded-context.md
docs/knowledge/patterns/add-an-adapter-test.md
docs/knowledge/patterns/add-an-mcp-tool.md
=== .claude/skills ===
.claude/skills/add-adapter-test/SKILL.md
.claude/skills/add-mcp-tool/SKILL.md
.claude/skills/check-architecture/SKILL.md
.claude/skills/scaffold-bounded-context/SKILL.md
=== .claude/agents ===
.claude/agents/docs-maintainer.md
.claude/agents/riot-context-architect.md
.claude/agents/test-author.md
```

---

## Self-Review

### Spec-requirement → task mapping (Decision 6 + git hygiene)

| Spec requirement (Decision 6) | Task |
|---|---|
| `docs/knowledge/README.md` — index + hydrate/persist protocol (single source of truth) | Task 1 |
| `decisions/` — five ADRs (hexagonal, shared-http-client, wiremock, archunit, knowledge-system) | Task 2 |
| `patterns/` — add-a-bounded-context, add-an-mcp-tool, add-an-adapter-test | Task 3 |
| `gotchas.md` — Lombok nested-builder, spectator 404→null, region-vs-platform, @McpTool discovery | Task 4 (Step 1) |
| `glossary.md` — PUUID, Riot ID, summoner, platform vs region, spectator | Task 4 (Step 2) |
| `.claude/skills/` — scaffold-bounded-context, add-mcp-tool, add-adapter-test, check-architecture | Task 5 |
| `.claude/agents/` — riot-context-architect, test-author, docs-maintainer (embed hydrate/persist) | Task 6 |
| `.gitignore` hardening — explicit `.superpowers/`; skills/agents tracked, settings.local ignored | Task 7 |
| `CLAUDE.md` points at `docs/knowledge/README.md` (verify, don't conflict with Phase 5) | Task 8 |
| Reference (not duplicate) ADRs from patterns and vice versa | Tasks 2–4 cross-links; verified Task 4 Step 4 |

### Placeholder scan

None. Every committed file (`README.md`, five ADRs, three patterns, `gotchas.md`,
`glossary.md`, four `SKILL.md`, three agent `.md`) has complete content shown in a code
block. Every verification step gives an exact Git-Bash command and its expected output.
No "TBD", "add appropriate X", "similar to Task N", or "write X for the above".

### Type/name consistency with Phase 1–5

- Package root and top-level contexts (`account`, `summoner`, `match`, `spectator`,
  `analytics`, `shared`) match Phase 1's Global Constraints and move table.
- Component names used in ADRs/patterns/skills/agents match Phase 1 exactly:
  `RiotApiProperties` (`getApiKey`/`getRegion` default `AMERICAS`/`getBaseUrlOverride`),
  `RiotApiClient` (`regional(RiotApiRegionUri)`/`platform(RiotApiPlatformUri)`),
  `RiotApiException.getStatusCode()` → `int`, ports `*Port`, adapters `Riot*Adapter`
  (incl. `RiotAccountRiotAdapter`), services `*Service`, tools `*Tool`.
- Enum members quoted (`RiotApiRegionUri`: `AMERICAS/ASIA/EUROPE/SEA`;
  `RiotApiPlatformUri`: `NA1/EUW1/KR/...`) match `shared/enums`.
- `@McpTool`/`@McpToolParam` package `org.springframework.ai.mcp.annotation` and real
  tool names (`get_lol_summoner_by_name`, `get_current_game_by_summoner_name`) match the
  post-Phase-1 tools.
- WireMock coordinate `org.wiremock:wiremock-standalone:3.9.2` matches Phase 1 Task 2;
  the plan reuses it and never re-adds it. Versions (Java 21, Boot 4.1.0, Spring AI
  2.0.0, Gradle 9.6.1) are quoted, not changed.
- Spectator `404 → null` is described as an adapter-level rule distinct from the shared
  `RiotApiClient` handler, matching Phase 1 Task 7.
- No Gradle build logic or source is modified; ArchUnit/JaCoCo/Spotless are referenced as
  Phase 3 gates the `check-architecture` skill runs, not defined here — consistent with
  phase boundaries.
```
