# CLAUDE.md

Guidance for AI coding agents (Claude Code and similar) working in this repository. Humans should
start with [README.md](README.md); the authoritative design reference is
[ARCHITECTURE.md](ARCHITECTURE.md).

## What this is

A Spring Boot 4.1 / Spring AI 2.0 (Java 21) **MCP server** that exposes the Riot Games API to AI
models as four MCP tools. It is a portfolio piece — the value is the clean bounded-context hexagonal
architecture and the disciplined tests, not feature breadth.

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
./gradlew bootRun        # run locally; needs RIOT_API_KEY (and ANTHROPIC_API_KEY on the classpath)
./gradlew clean          # remove build artifacts
```

The full test suite runs **offline with no Riot API key**. Do not introduce tests that require live
keys or network access; use WireMock (adapters) or port fakes (services) instead.

## Architecture summary

Bounded-context hexagons under `com.wkaiser.riotapimcpserver`: top-level contexts `account`,
`summoner`, `match`, `spectator`, `analytics`, and `shared`. Per context: `domain/` (Lombok DTOs),
`application/` (`<Context>Service` + `application/port/<Context>Port`), `adapter/in/mcp/`
(`<Context>Tool`, `@McpTool`), `adapter/out/riot/` (`Riot<Context>Adapter`, implements the port).
`analytics` composes the account/summoner/match services and has no Riot adapter; `match` has a port
and adapter but no tool. All HTTP/auth/error handling lives in `shared/http/RiotApiClient`.

**Dependency rule (ArchUnit-enforced):** `adapter → application → domain`, inward only. Only
`adapter.out.riot` uses `RestClient`; only `adapter.in.mcp` uses `@McpTool`; `application` never
depends on `adapter`; contexts do not touch each other's internals except `analytics`. Full detail
and diagrams: [ARCHITECTURE.md](ARCHITECTURE.md).

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

- Type: SYNC. SSE message endpoint: `/mcp/messages`. Server port: `8080`.
- Tools are auto-discovered via Spring AI's `@McpTool` annotation scanning.
- Four tool classes: `RiotAccountTool`, `SummonerTool`, `LiveGameTool`, `AnalyticsTool` (see the
  table in [README.md](README.md)).
