# CLAUDE.md

Guidance for AI coding agents (Claude Code and similar) working in this repository. Humans should
start with [README.md](README.md); the authoritative design reference is
[ARCHITECTURE.md](ARCHITECTURE.md).

## What this is

A Gradle monorepo built on Spring Boot 4.1 / Spring AI 2.0 (Java 21): shared libraries plus one
**MCP server** per Riot game, currently `lol-mcp-server`, exposing the Riot Games API to AI models
as 13 MCP tools across 11 tool classes. It is a portfolio piece — the value is the clean
bounded-context hexagonal architecture and the disciplined tests, not feature breadth.

## Knowledge base — hydrate / persist protocol

This repo carries a committed knowledge base at [`docs/knowledge/`](docs/knowledge/). Use it every
session:

- **Hydrate (before acting):** read [`docs/knowledge/README.md`](docs/knowledge/README.md) (the
  index + protocol), then the relevant `decisions/` (ADRs), `patterns/` (how-to guides), and
  [`docs/knowledge/gotchas.md`](docs/knowledge/gotchas.md) for the task at hand.
- **Persist (before finishing):** write findings back — a new architectural decision becomes a new
  ADR under `decisions/`; a new repeatable procedure becomes a new `patterns/` guide; a newly
  discovered pitfall is appended to `gotchas.md`. Keep entries small and single-purpose.

## Build and test commands

```bash
./gradlew build          # compile + all tests + ArchUnit + JaCoCo + Spotless check (the CI gate)
./gradlew test           # tests only
./gradlew spotlessApply  # auto-format sources
./gradlew bootRun        # run locally; needs RIOT_API_KEY
./gradlew :lol-mcp-server:bootRun --args='--spring.profiles.active=sse'   # run the LoL server over SSE
./gradlew clean          # remove build artifacts
```

The full test suite runs **offline with no Riot API key**. Do not introduce tests that require live
keys or network access; use WireMock (adapters) or port fakes (services) instead.

The above is the offline CI gate and needs no keys. A separate **live eval harness** (`eval/`,
Python + mcp-eval) runs agent-driven tests against the real Riot API post-merge over both transports
— see [`docs/knowledge/patterns/live-eval-harness.md`](docs/knowledge/patterns/live-eval-harness.md)
and [ADR-0012](docs/knowledge/decisions/ADR-0012-live-eval-harness.md). It never gates a merge and
needs `ANTHROPIC_API_KEY` (a separate credential bucket from `CLAUDE_CODE_OAUTH_TOKEN`):

```bash
cd eval && uv sync && cp mcpeval.stdio.yaml mcpeval.yaml && uv run mcp-eval run tests/ -v
```

## Architecture summary

A Gradle monorepo. Two libraries and one server per game:

- **`riot-api-core`** (`com.muddl.riot.core`) — `RiotApiClient` (all HTTP/auth/error handling),
  `RiotApiProperties`, routing enums, `RiotApiException`. Auto-configured, never
  component-scanned. Its test fixtures hold the shared `HexagonRules` and `Fixtures`.
- **`riot-account-core`** (`com.muddl.riot.account`) — the cross-game account-v1 context.
  Domain + service + outbound adapter, **no `@McpTool`** (ArchUnit-enforced).
- **`lol-mcp-server`** (`com.muddl.riot.lol`) — bounded-context hexagons `summoner`, `match`,
  `spectator`, `analytics`, plus a tool-only `account` package. Per context: `domain/`,
  `application/` (+ `application/port/`), `adapter/in/mcp/`, `adapter/out/riot/`.

**Dependency rule:** servers → `riot-account-core` → `riot-api-core`, never back. Gradle enforces
this at compile time. Within a module, ArchUnit enforces `adapter → application → domain` (inward
only), `RestClient` only in `adapter.out.riot`, `@McpTool` only in `adapter.in.mcp`, and context
independence via a slice rule (exceptions: analytics→summoner, analytics→match).
A separate rule confines use of the account library to the `analytics` and `account` packages, since
extracting account outside the LoL package root moved it outside that slice matcher.

**Transport:** every server ships `stdio` (default) and `sse` profiles. See `gotchas.md` before
touching stdio logging. Full detail and diagrams: [ARCHITECTURE.md](ARCHITECTURE.md).

## Conventions

- **DTOs:** `@Data @Builder @NoArgsConstructor @AllArgsConstructor` + `@JsonIgnoreProperties(ignoreUnknown = true)`.
  Nested `@Builder` static classes also need `@NoArgsConstructor @AllArgsConstructor` (see
  `gotchas.md`).
- **Ports:** interfaces in `<context>.application.port`, named `<Context>Port`. Services depend on
  the port, never on a `RestClient`.
- **Tools:** `@McpTool` methods in `<context>.adapter.in.mcp` with stable snake_case names; delegate
  to the application service. Do not change existing tool names or `@McpToolParam` descriptions
  without reason — they are the public MCP contract.
- **Config:** the Riot API key is read from `RIOT_API_KEY` via `RiotApiProperties`
  (`@ConfigurationProperties(prefix = "riot")`). Never hard-code or log it.

## Testing pattern

- Outbound adapters → WireMock tests (assert URL, `X-RIOT-TOKEN`, JSON→DTO parsing, error mapping;
  spectator `404 → null`).
- Application services → in-memory port fakes; `AnalyticsService` covers zero-games and zero-death
  KDA edge cases.
- TDD: write the failing test first, keep the build green at every commit.

See [CONTRIBUTING.md](CONTRIBUTING.md) for the step-by-step recipes and
[`docs/knowledge/patterns/`](docs/knowledge/patterns/) for the detailed how-to guides.

## MCP server details

- Type: SYNC. Transports: `stdio` (default) and `sse` (message endpoint `/mcp/messages`, port
  `8080`).
- Tools are auto-discovered via Spring AI's `@McpTool` annotation scanning.
- `lol-mcp-server` has 11 tool classes under `com.muddl.riot.lol`, one per context:
  `RiotAccountTool` (`account.adapter.in.mcp`), `SummonerTool` (`summoner.adapter.in.mcp`),
  `LiveGameTool` (`spectator.adapter.in.mcp`), `AnalyticsTool` (`analytics.adapter.in.mcp`),
  `LeagueTool` (`league.adapter.in.mcp`), `ChampionTool` (`champion.adapter.in.mcp`),
  `StatusTool` (`status.adapter.in.mcp`), `ChampionMasteryTool` (`championmastery.adapter.in.mcp`),
  `ChallengesTool` (`challenges.adapter.in.mcp`), `ClashTool` (`clash.adapter.in.mcp`), and
  `MatchTool` (`match.adapter.in.mcp`) — Plan C renamed the tool surface to the
  `<game>_<context>_<action>` convention (see [ADR-0009](docs/knowledge/decisions/ADR-0009-mcp-tool-contract.md));
  it is now **13** tools after sub-project 1b added the five breadth contexts plus match tools and
  `lol_spectator_featured_games` was removed (see
  [ADR-0013](docs/knowledge/decisions/ADR-0013-remove-featured-games.md) and
  [ADR-0014](docs/knowledge/decisions/ADR-0014-non-player-keyed-tools.md)).
