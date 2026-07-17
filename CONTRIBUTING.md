# Contributing

Thanks for looking at the code. This is a portfolio project, so the bar is "clean, tested, and
consistent with the [architecture](ARCHITECTURE.md)" rather than feature breadth. Read
[ARCHITECTURE.md](ARCHITECTURE.md) first — the dependency rule and package layout are enforced
(some by ArchUnit, some by Gradle's module graph), so a PR that ignores them will fail the build.

This is a Gradle monorepo: `riot-api-core` and `riot-account-core` are libraries, and
`lol-mcp-server` is the (currently only) Spring Boot game server. Figure out which module a change
belongs in before writing it:

| Change | Module |
|--------|--------|
| HTTP/auth/error handling, routing enums, `RiotApiException` | `riot-api-core` |
| Anything about the cross-game account context (Riot ID ↔ PUUID) | `riot-account-core` |
| A League of Legends context (`summoner`, `match`, `spectator`, `analytics`, `league`), or that thin `account` tool | `lol-mcp-server` |
| A new game entirely | a new server module depending on both libraries |

Each module documents its own public surface and internals — see its `README.md` and
`ARCHITECTURE.md` ([`riot-api-core`](riot-api-core/README.md), [`riot-account-core`](riot-account-core/README.md),
[`lol-mcp-server`](lol-mcp-server/README.md)). This file and the root [ARCHITECTURE.md](ARCHITECTURE.md)
cover only what is shared across every module.

## Prerequisites

- **Java 21** (a Gradle toolchain resolves it; the wrapper pins Gradle 9.6.1).
- A **Riot API key** is only needed to *run* a server, never to build or test — the test suite is
  fully offline. No other credential (in particular, no Anthropic key) is needed for anything.

## Build, test, format

```bash
./gradlew build          # compile + all tests + ArchUnit + JaCoCo + Spotless check
./gradlew test           # tests only
./gradlew spotlessApply  # auto-format all sources (run before committing)
./gradlew spotlessCheck  # verify formatting (part of build; fails on drift)
./gradlew :lol-mcp-server:bootRun                                            # run locally, stdio (needs RIOT_API_KEY)
./gradlew :lol-mcp-server:bootRun --args='--spring.profiles.active=sse'      # run locally, sse
./gradlew clean          # remove build artifacts
```

`./gradlew build` is the gate that CI runs. If it is green locally with no API key, it will be green
in CI.

## Package conventions

New code within a game server goes into a top-level bounded context under
`com.muddl.riot.<game>.<context>` (e.g. `com.muddl.riot.lol.summoner`), using the fixed
internal shape:

| Kind | Package | Naming |
|------|---------|--------|
| DTO / domain model | `<context>.domain` | plain nouns (`Summoner`, `Match`) |
| Application service | `<context>.application` | `<Context>Service` |
| Outbound port (interface) | `<context>.application.port` | `<Context>Port` |
| Outbound Riot adapter | `<context>.adapter.out.riot` | `Riot<Context>Adapter` |
| Inbound MCP tool | `<context>.adapter.in.mcp` | `<Context>Tool` |

The invariants — from `HexagonRules` in `riot-api-core`'s test fixtures, shared by every module's
architecture test (also enforced by ArchUnit): only `adapter.out.riot` may use `RestClient`; only
`adapter.in.mcp` may use `@McpTool`; `application` never depends on `adapter`; contexts do not
reference each other's internals except `analytics`; and only `analytics` and this server's thin
`account` tool may depend on `riot-account-core`.

## The DTO / Lombok pattern

Domain models are Riot JSON shapes kept as Lombok DTOs. Every DTO uses the full annotation set so it
compiles cleanly and Jackson can deserialize it:

```java
package com.muddl.riot.lol.summoner.domain;

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
public class Summoner {
    private String id;
    private String puuid;
    private String name;
    private long summonerLevel;
}
```

Rules:

- Always `@Data @Builder @NoArgsConstructor @AllArgsConstructor`. The two constructor annotations are
  required alongside `@Builder` so Lombok generates a usable no-arg constructor for Jackson.
- Add `@JsonIgnoreProperties(ignoreUnknown = true)` so new Riot fields do not break deserialization.
- **Nested static classes** that use `@Builder` must **also** carry `@NoArgsConstructor` and
  `@AllArgsConstructor`, or the generated builders conflict (this bit us in `Perks`; see
  [`docs/knowledge/gotchas.md`](docs/knowledge/gotchas.md)).

## Testing expectations

- **Outbound adapters** are tested against WireMock (assert URL, `X-RIOT-TOKEN`, parsing, error
  mapping). See the recipe in
  [`docs/knowledge/patterns/add-an-adapter-test.md`](docs/knowledge/patterns/add-an-adapter-test.md).
- **Application services** are tested against in-memory port fakes — no HTTP.
- Write the failing test first, then the implementation. Keep the build green at every commit.

## Adding a bounded context

Full recipe: [`docs/knowledge/patterns/add-a-bounded-context.md`](docs/knowledge/patterns/add-a-bounded-context.md).
In short: create `domain/`, `application/` + `application/port/`, `adapter/out/riot/`, and (if the
context is exposed to MCP) `adapter/in/mcp/`; define the `<Context>Port` interface; implement it with
a `Riot<Context>Adapter` that injects `RiotApiClient`; add a WireMock adapter test and a service test.

## Adding an MCP tool

Full recipe: [`docs/knowledge/patterns/add-an-mcp-tool.md`](docs/knowledge/patterns/add-an-mcp-tool.md).
In short: add a `@McpTool`-annotated method to the context's `<Context>Tool` in `adapter.in.mcp`,
give it a stable snake_case `name` and clear `@McpToolParam` descriptions, delegate to the
application service, and keep all HTTP concerns out of the tool.

## Commit and PR conventions

- Use [Conventional Commits](https://www.conventionalcommits.org/) prefixes: `feat:`, `refactor:`,
  `test:`, `docs:`, `ci:`, `build:`, `chore:`.
- Run `./gradlew spotlessApply && ./gradlew build` before pushing.
- PRs target `master`; CI must be green (tests + ArchUnit + JaCoCo + Spotless).
