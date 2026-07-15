# Riot API MCP Server

[![CI](https://github.com/Muddl/riot-api-mcp-server/actions/workflows/ci.yml/badge.svg)](https://github.com/Muddl/riot-api-mcp-server/actions/workflows/ci.yml)
[![Coverage](https://img.shields.io/badge/coverage-JaCoCo-brightgreen.svg)](ARCHITECTURE.md#testing-strategy)
[![Java 21](https://img.shields.io/badge/Java-21-blue.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 4.1.0](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A [Model Context Protocol](https://modelcontextprotocol.io) (MCP) server that exposes the
[Riot Games API](https://developer.riotgames.com/) to AI models as a small set of typed tools.
It is a Spring Boot 4.1 / Spring AI 2.0 application (Java 21) built as a **portfolio piece**:
the point is the engineering — a clean bounded-context hexagonal architecture, a single shared
HTTP client, HTTP-mocked tests that run in CI with no API key, and architecture rules enforced at
build time. An MCP client (e.g. Claude Desktop) connects over SSE and can look up Riot accounts and
League of Legends summoners, inspect live games, and pull aggregated match analytics.

## Architecture at a glance

Each Riot context is a self-contained hexagon: an inbound MCP adapter calls an application service,
which depends on an outbound **port**; a Riot adapter implements that port. All HTTP, auth, and
error handling live in one place — `shared/http/RiotApiClient`. `analytics` is a composing context
that calls the `account`, `summoner`, and `match` application services and has no Riot adapter of
its own.

```mermaid
flowchart LR
    AI["AI model / MCP client"]

    subgraph in["Inbound adapters · adapter.in.mcp"]
        AT["RiotAccountTool"]
        ST["SummonerTool"]
        LT["LiveGameTool"]
        NT["AnalyticsTool"]
    end

    subgraph app["Application services · application"]
        AS["RiotAccountService"]
        SS["SummonerService"]
        MS["MatchService"]
        PS["SpectatorService"]
        NS["AnalyticsService"]
    end

    subgraph port["Outbound ports · application.port"]
        AP["RiotAccountPort"]
        SP["SummonerPort"]
        MP["MatchPort"]
        SPP["SpectatorPort"]
    end

    subgraph out["Outbound adapters · adapter.out.riot"]
        AA["RiotAccountRiotAdapter"]
        SA["RiotSummonerAdapter"]
        MA["RiotMatchAdapter"]
        SPA["RiotSpectatorAdapter"]
    end

    RC["shared/http · RiotApiClient"]
    RIOT[("Riot Games API")]

    AI --> AT & ST & LT & NT
    AT --> AS
    ST --> SS
    LT --> PS
    NT --> NS
    NS --> AS & SS & MS
    AS --> AP
    SS --> SP
    MS --> MP
    PS --> SPP
    AP -. implemented by .-> AA
    SP -. implemented by .-> SA
    MP -. implemented by .-> MA
    SPP -. implemented by .-> SPA
    AA & SA & MA & SPA --> RC
    RC --> RIOT
```

See **[ARCHITECTURE.md](ARCHITECTURE.md)** for the full rationale, the dependency rule, and how it
is enforced.

## Quick start

Prerequisites: **Java 21** and a **Riot API key** (get a development key at
<https://developer.riotgames.com/>).

```bash
# 1. Provide your Riot API key (read from the environment by application.yml)
export RIOT_API_KEY="RGAPI-your-key-here"

# 2. Run the server
./gradlew bootRun
```

The server starts on `http://localhost:8080`; the MCP SSE message endpoint is `/mcp/messages`.
Point your MCP client at it, or check liveness with `curl http://localhost:8080/actuator/health`.

> **Note:** the Spring AI Anthropic starter is on the classpath, so `bootRun` also expects
> `ANTHROPIC_API_KEY` to be set. It is **not** needed to build or to run the test suite — only to
> start the application. `export ANTHROPIC_API_KEY="sk-ant-..."` before `bootRun` if you hit a
> startup placeholder error.

## MCP tools

Four inbound adapters expose the Riot API to MCP clients:

| Tool (`adapter.in.mcp`) | MCP tool names | Purpose |
|-------------------------|----------------|---------|
| **RiotAccountTool** | `get_riot_account_by_riot_id`, `get_riot_account_by_puuid` | Cross-game Riot account lookup (Riot ID ↔ PUUID) |
| **SummonerTool** | `get_lol_summoner_by_name`, `get_lol_summoner_by_puuid`, `get_lol_summoner_by_id` | League of Legends summoner profiles |
| **LiveGameTool** | `get_current_game_by_summoner_name`, `get_current_game_by_summoner_id`, `get_featured_games`, `check_if_summoner_in_game` | Live-game (Spectator v4) data; returns `null`/`false` when not in a game |
| **AnalyticsTool** | `get_lol_player_match_analytics` | Aggregated recent-match analytics, composing the account, summoner, and match services |

## Testing

Tests run **offline with no Riot API key** — CI proves it. Outbound adapters are exercised against a
local [WireMock](https://wiremock.org/) server (asserting request URLs, the `X-RIOT-TOKEN` header,
JSON→DTO parsing, and error mapping including the spectator `404 → null` rule); application services
are tested against in-memory port fakes. Architecture, coverage, and formatting are checked in the
same run.

```bash
./gradlew build          # compile + all tests + ArchUnit + JaCoCo + Spotless check
./gradlew test           # tests only
./gradlew spotlessApply  # auto-format sources
```

## Docker

A multi-stage `Dockerfile` builds the app and runs it on a slim JRE 21. The container reads
`RIOT_API_KEY` from the environment.

```bash
docker build -t riot-api-mcp-server .
docker run --rm -p 8080:8080 \
  -e RIOT_API_KEY="RGAPI-your-key-here" \
  -e ANTHROPIC_API_KEY="sk-ant-..." \
  riot-api-mcp-server
```

Tagging a release (`v*`) publishes an image to GHCR at
`ghcr.io/muddl/riot-api-mcp-server` via the `release.yml` workflow.

## Documentation

| Document | Purpose |
|----------|---------|
| **[ARCHITECTURE.md](ARCHITECTURE.md)** | Hexagonal design, bounded contexts, dependency rule, testing strategy |
| **[CONTRIBUTING.md](CONTRIBUTING.md)** | Build/test/format commands, conventions, how to add a context or tool |
| **[CLAUDE.md](CLAUDE.md)** | Guidance for AI coding agents working in this repo |
| **[CHANGELOG.md](CHANGELOG.md)** | Version history (Keep a Changelog) |
| **[docs/knowledge/](docs/knowledge/)** | Committed knowledge base — ADRs, patterns, gotchas, glossary |

## License

Released under the MIT License — see [LICENSE](LICENSE).
