# Portfolio Sanitization — Design Spec

**Date:** 2026-07-13
**Status:** Approved
**Author:** Wade Kaiser (with Claude)

## Purpose

`riot-api-mcp-server` is a Spring Boot 4.1 / Spring AI 2.0 MCP server (Java 21) exposing
four MCP tools over the Riot Games API. Its **primary purpose is a portfolio / showcase
piece** — it must demonstrate clean architecture and disciplined engineering to a technical
reviewer.

This spec covers a six-phase "sanitization" that takes the project from a functional-but-noisy
state to a portfolio-grade codebase: a real hexagonal/DDD structure, HTTP-mocked tests that run
in CI without live keys, architecture enforced at build time, a refactored CI pipeline with
packaging, honest docs, and a committed knowledge system that lets AI agents rapidly rehydrate
context and persist findings.

## Current State (verified 2026-07-13)

- **Architecture:** Package-by-feature under `com.wkaiser.riotapimcpserver` — `riot/account`,
  `riot/lol/{summoner,match,analytics,spectator}`, `shared/{config,enums,exception}`. Four
  `@McpTool` adapters → services → DTOs.
- **HTTP duplication:** `SummonerService`, `SpectatorService`, `MatchService`, and
  `RiotAccountService` each hand-roll the same plumbing — a private `createPlatformClient()`,
  the `X-RIOT-TOKEN` header constant, `@Value("${riot.apiKey}")`, and a near-identical
  `try/catch (HttpClientErrorException) → RiotApiException`. Copy-pasted 4+ times.
- **Domain modeling:** Riot API JSON shapes (Lombok DTOs) are used directly as domain models.
- **Tests:** Unit tests exist for tools and `SpectatorService`; integration tests are
  `@Disabled` (require live keys). No HTTP-level mocking. A `CompilationVerificationTest`
  exists solely to guard Lombok builder wiring.
- **Docs:** ~1,900 lines across five files. `PLAN.md` (744 lines) and `FEATURES.md` (446)
  are aspirational/fictional (a "$2,000–5,000/month AWS" plan, "83 subagents", multi-agent
  "success stories"). `CLAUDE.md` (322) is largely marketing + stale "recent updates" logs.
- **CI:** `gradle.yml` (plain `./gradlew build` + dependency-submission), plus two Claude
  workflows. Dependabot is configured.
- **Context/tooling hygiene:** Contrary to the stale `CLAUDE.md`, `.idea/`, `.superpowers/`,
  and `.claude/` have **no tracked files** — all are already gitignored/untracked. The 83
  agents no longer exist in the repo. The real context problem is the bloated `CLAUDE.md`
  loaded every session, and the absence of any durable, committed knowledgebase.

## Goals

1. Real bounded-context hexagonal architecture with a single shared HTTP client.
2. Tests that exercise the HTTP adapters against a mock server, runnable in CI with no key.
3. Architecture, coverage, and formatting enforced at build time.
4. A refactored CI pipeline plus a deployable container image.
5. Honest, accurate, portfolio-grade documentation.
6. A committed knowledge system + curated agents/skills with a hydrate/persist protocol.

## Non-Goals (YAGNI)

- No anti-corruption layer / separate wire-vs-domain DTOs (explicitly declined; the API is
  read-only). DTOs are relocated, not split.
- No conversion of DTOs to records in this effort (noted as an optional future follow-up).
- No CodeQL / SAST workflow — Dependabot covers CVEs; ArchUnit covers structural rules.
- No AWS/production infrastructure work (the fictional `PLAN.md` is deleted, not implemented).
- No new Riot API endpoints or features.

---

## Design

### Decision 1 — Architecture: bounded-context hexagons

Each Riot context becomes a self-contained mini-hexagon. The **outbound port** (interface) is
the architectural boundary. MCP tools call application services directly — no inbound-port
interface (that would be ceremony for a showcase of this size).

Target package tree:

```
com.wkaiser.riotapimcpserver
├── summoner/
│   ├── domain/                     Summoner                      (relocated DTO)
│   ├── application/
│   │   ├── SummonerService         pure logic, depends on port
│   │   └── port/SummonerPort       outbound interface
│   └── adapter/
│       ├── in/mcp/SummonerTool         @McpTool inbound adapter
│       └── out/riot/RiotSummonerAdapter implements SummonerPort
├── account/     (same shape)
├── match/       (same shape)
├── spectator/   (same shape)
├── analytics/                      composing context
│   ├── domain/PlayerMatchAnalytics
│   ├── application/AnalyticsService depends on account/summoner/match APPLICATION services
│   └── adapter/in/mcp/AnalyticsTool
└── shared/
    ├── config/    RiotApiProperties (@ConfigurationProperties), RestClient bean config
    ├── http/      RiotApiClient   — the ONE place HTTP/auth/error mapping lives
    ├── enums/     RiotApiRegionUri, RiotApiPlatformUri
    └── exception/ RiotApiException, GlobalExceptionHandler
```

**The central refactor — `shared/http/RiotApiClient`:** all duplicated HTTP plumbing collapses
into one component that exposes pre-configured clients, e.g.:

```java
RestClient regional(RiotApiRegionUri region);   // baseUrl + X-RIOT-TOKEN + error handler
RestClient platform(RiotApiPlatformUri platform);
```

The `X-RIOT-TOKEN` header, base-URL assembly, and the `HttpStatusCode::isError → RiotApiException`
handler are defined once. Outbound adapters inject `RiotApiClient` and only make calls. The
spectator adapter keeps its context-specific rule: `404 → null` (not in game) is handled in the
adapter, distinct from the shared error handler.

**Typed config:** replace every scattered `@Value("${riot.apiKey}")` with a single
`@ConfigurationProperties(prefix = "riot")` `RiotApiProperties` (fields: `apiKey`, `region`,
and any default platform). `application.yml` reads `riot.apiKey: ${RIOT_API_KEY:}` for
12-factor portability (the key is already externalized — this formalizes it).

**Domain DTOs:** kept as Lombok DTOs per the established convention
(`@Data @Builder @NoArgsConstructor @AllArgsConstructor`), simply relocated into each context's
`domain/` package. No behavioral change.

**Dependency rule (the invariant Decision 3 enforces):**
`adapter → application → domain`. `domain` depends on nothing outward and on no framework.
`application` depends on `domain` and its own `port` only — never on `adapter`. Only
`adapter/out/riot` knows `RestClient`; only `adapter/in/mcp` knows `@McpTool`. Cross-context
references are forbidden **except** `analytics` depending on other contexts' application services.

### Decision 2 — Testability: WireMock + port fakes

- **Outbound adapter tests (WireMock):** each `Riot*Adapter` is tested against a local WireMock
  server. The real `RestClient` hits `localhost`; tests assert request URL, the `X-RIOT-TOKEN`
  header, JSON→DTO parsing, and error mapping (spectator `404 → null`; other `4xx/5xx →
  RiotApiException` with status preserved). Canned JSON fixtures live in
  `src/test/resources/fixtures/`.
- **Application-service tests:** hand-written in-memory fakes implementing the port interfaces —
  fast, no HTTP. `AnalyticsService` is tested with fake account/summoner/match collaborators,
  covering the existing edge cases (zero games, zero deaths KDA).
- **Cleanup:** remove all `@Disabled`; delete `CompilationVerificationTest` (real tests now
  cover what it proxied).
- **Dependency:** `testImplementation 'org.wiremock:wiremock-standalone'` (version via BOM /
  latest compatible with Spring Boot 4.1).

### Decision 3 — Quality gates: ArchUnit + JaCoCo + Spotless

All three run under `./gradlew test` / `build`, so CI enforces them with no extra workflow.

- **ArchUnit** (`com.tngtech.archunit:archunit-junit5`, `testImplementation`): an
  `architecture/` test suite encoding Decision 1's rules:
  - layered dependency rule (domain ⇸ application ⇸ adapter, inward only);
  - `RestClient` referenced only within `..adapter.out.riot..`;
  - `@McpTool` present only within `..adapter.in.mcp..`;
  - ports are interfaces residing in `..application.port..`;
  - no context package depends on another context's internals, except `analytics`;
  - naming: `*Service` in application, `*Tool` in `adapter.in.mcp`, `*Adapter` in
    `adapter.out.riot`, `*Port` interfaces in `application.port`.
- **JaCoCo:** report generated on `test`; summary published to the PR by CI. A coverage
  threshold is configured but set conservatively (soft/low) to avoid blocking legitimate work —
  the signal is "coverage is measured and visible," not an arbitrary gate.
- **Spotless** (`com.diffplug.spotless`, palantir-java-format or google-java-format):
  `spotlessCheck` wired into `check`/`build` so formatting drift fails the build; `spotlessApply`
  available locally.
- **`build.gradle`** gains the `jacoco` and `spotless` plugins and the two test dependencies.

### Decision 4 — CI pipeline + packaging

- **`ci.yml`** (refactor of `gradle.yml`): triggers on push/PR to `master`.
  Steps: checkout → `setup-java@v4` (21, zulu) → `gradle/actions/setup-gradle` (cache) →
  `./gradlew build` (unit + WireMock + **ArchUnit** + JaCoCo + `spotlessCheck` all gate here) →
  publish JUnit results as PR annotations → publish JaCoCo summary as a PR comment →
  `gradle/actions/dependency-submission` retained.
- **`release.yml`**: triggers on `v*` tags. Builds a **multi-stage `Dockerfile`** (Gradle build
  stage → slim JRE 21 runtime stage; container reads `RIOT_API_KEY` from the environment) and
  pushes the image to **GHCR** (`ghcr.io/<owner>/riot-api-mcp-server`) using the built-in
  `GITHUB_TOKEN` with `packages: write`.
- **Dockerfile** at repo root — readable multi-stage, documenting the required env var.
- Existing `claude-code-review.yml` and `claude.yml` are left untouched.
- **Dependabot** remains the CVE watch (no CodeQL).

### Decision 5 — Documentation

- **Delete:** `PLAN.md`, `FEATURES.md` (fictional; net-negative for a portfolio).
- **Rewrite `README.md`:** accurate front door — one-paragraph what/why, a Mermaid diagram of
  the *real* hexagon, quick start (set `RIOT_API_KEY`, `./gradlew bootRun`), the four-tool table,
  testing (WireMock, no key needed), Docker usage. Build + coverage badges.
- **New `ARCHITECTURE.md`:** the centerpiece — hexagonal rationale, the bounded contexts, the
  dependency rule, ports/adapters, how ArchUnit enforces it, and the testing strategy.
- **New `CONTRIBUTING.md`:** build/test/format commands, package conventions, the Lombok DTO
  pattern, how to add a context/tool (pointing at `docs/knowledge/patterns/`).
- **Slim `CLAUDE.md` hard:** accurate build/test commands, a short architecture summary that
  defers to `ARCHITECTURE.md`, the DTO + port conventions, the testing pattern, and the
  knowledge-base hydrate/persist protocol (Decision 6). Remove all multi-agent marketing,
  fictional infra, and dated "recent updates" logs.
- **`CHANGELOG.md`:** prune fictional entries; add one honest entry describing this
  sanitization. Keep the Keep-a-Changelog format going forward.

### Decision 6 — Knowledge system + context management

**Committed knowledgebase** (`docs/knowledge/`, portfolio-visible, GitHub-rendered):

```
docs/knowledge/
  README.md        index + the hydrate/persist protocol (single source of truth)
  decisions/       ADRs — ADR-0001-hexagonal, ADR-0002-shared-riot-http-client,
                   ADR-0003-wiremock-testing, ADR-0004-archunit-enforcement,
                   ADR-0005-knowledge-system
  patterns/        how-to guides — add-a-bounded-context.md, add-an-mcp-tool.md,
                   add-an-adapter-test.md
  gotchas.md       Lombok nested-builder conflicts; spectator 404→null; region-vs-platform
                   URIs; @McpTool discovery
  glossary.md      PUUID, Riot ID (gameName#tagLine), summoner, platform vs region, spectator
```

**Project skills** (`.claude/skills/`, committed): `scaffold-bounded-context`, `add-mcp-tool`,
`add-adapter-test`, `check-architecture` (runs ArchUnit + coverage and interprets failures).
Each skill operationalizes a `docs/knowledge/patterns/` guide.

**Curated agents** (`.claude/agents/`, committed): a small purpose-built set tuned to the
hexagon — `riot-context-architect`, `test-author`, `docs-maintainer`. (The stale "83 agents"
already do not exist in the repo; this establishes the intended small set fresh.)

**Hydrate/persist protocol** (encoded in `CLAUDE.md` + each agent's prompt):
- **Hydrate (startup):** before acting, read `docs/knowledge/README.md`, then the relevant
  `decisions/`, `patterns/`, and `gotchas.md` for the task at hand.
- **Persist (finish):** write findings back — a new decision → new ADR; a new recurring
  procedure → new `patterns/` guide; a newly-discovered pitfall → append to `gotchas.md`. Entries
  stay small and single-purpose.

**Context/git hygiene:** verified minimal — `.idea/`, `.superpowers/`, `.claude/` are already
untracked. Harden by adding an explicit `.superpowers/` line to the root `.gitignore` (rather
than relying on the nested `.superpowers/sdd/.gitignore`), and confirm `.claude/skills/` and
`.claude/agents/` are *not* ignored (only `.claude/settings.local.json` is) so the committed
tooling lands in git. The primary context win is the slimmed `CLAUDE.md` + the new committed KB.

---

## Phases (dependency-ordered; each becomes its own implementation plan)

1. **Architecture refactor** — `RiotApiProperties` → `RiotApiClient` → bounded-context
   restructure → ports + outbound adapters. Backbone; everything depends on it.
2. **Testability** — WireMock adapter tests + port fakes; remove `@Disabled`; delete
   `CompilationVerificationTest`. Depends on Phase 1's ports/adapters.
3. **Quality gates** — ArchUnit rule suite, JaCoCo, Spotless wired into the build. Depends on
   the final structure (1) and tests (2).
4. **CI + packaging** — `ci.yml`, `release.yml`, multi-stage `Dockerfile`, GHCR. Depends on 3
   so the pipeline has real gates to run.
5. **Docs** — delete `PLAN.md`/`FEATURES.md`; rewrite `README.md`, `CLAUDE.md`; new
   `ARCHITECTURE.md`, `CONTRIBUTING.md`; honest `CHANGELOG.md`. Last among code work so docs
   describe the final state.
6. **Knowledge system + context cleanup** — `docs/knowledge/` (index, ADRs, patterns, gotchas,
   glossary), `.claude/skills/`, curated `.claude/agents/`, hydrate/persist protocol,
   `.gitignore` hardening. Captures the reality created by 1–5.

## Success Criteria

- `./gradlew build` passes offline with **no** `RIOT_API_KEY`, running unit + WireMock +
  ArchUnit tests plus JaCoCo and `spotlessCheck`.
- No duplicated HTTP/auth/error code remains; all of it lives in `shared/http/RiotApiClient`.
- ArchUnit fails the build on any dependency-rule or naming violation.
- CI annotates test results + coverage on PRs; a `v*` tag publishes a GHCR image.
- `PLAN.md` and `FEATURES.md` are gone; `README`/`ARCHITECTURE`/`CONTRIBUTING`/`CLAUDE`/
  `CHANGELOG` accurately describe the real project.
- `docs/knowledge/` exists with a working index and at least the five ADRs and three pattern
  guides; `CLAUDE.md` documents the hydrate/persist loop; committed `.claude/skills/` and
  `.claude/agents/` are present and hexagon-aware.
