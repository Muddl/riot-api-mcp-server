# `tft-mcp-server`

The Teamfight Tactics MCP server: a Spring Boot app that exposes the Riot TFT API to AI models as a
small set of typed [MCP](https://modelcontextprotocol.io) tools. Published as
`ghcr.io/muddl/tft-mcp-server`.

It is built on the two shared libraries — [`riot-api-core`](../riot-api-core/README.md) (HTTP,
routing, errors) and [`riot-account-core`](../riot-account-core/README.md) (the account context and
the player-identity resolver) — and adds the TFT-specific bounded contexts. It is sub-project 2's
proof that the shared core generalizes to a second Riot game: **zero changes** were made to either
library to ship it (see [ADR-0006](../docs/knowledge/decisions/ADR-0006-monorepo-split.md) and the
[roadmap](../docs/knowledge/roadmap.md#2--tft-server-)).

## MCP tools

Six bounded contexts expose **11** tools. Every player-keyed tool takes a single `player` parameter
accepting either a Riot ID (`GameName#TAG`) or a raw PUUID, resolved internally — the model never has
to chain `account → summoner → match` itself (see
[ADR-0009](../docs/knowledge/decisions/ADR-0009-mcp-tool-contract.md)). A few tools are
non-player-keyed (platform- or tier-scoped) — see
[ADR-0014](../docs/knowledge/decisions/ADR-0014-non-player-keyed-tools.md).

| Tool class (`adapter.in.mcp`) | MCP tool names | Purpose |
|---|---|---|
| **RiotAccountTool** | `tft_account_by_player` | Riot account by player |
| **SummonerTool** | `tft_summoner_by_player` | TFT summoner profile by player |
| **MatchTool** | `tft_match_ids_by_player`, `tft_match_by_id` | Recent match IDs for a player (region-routed, paged); full detail of one match by ID |
| **LeagueTool** | `tft_league_entries_by_player`, `tft_league_apex_by_tier`, `tft_league_entries_by_tier`, `tft_league_by_id`, `tft_league_rated_ladder_by_queue` | Ranked entries by player; apex league (CHALLENGER/GRANDMASTER/MASTER) by tier; one page of ranked entries by tier + division; a league by its league ID; the rated (Hyper Roll) ladder by queue |
| **StatusTool** | `tft_status_platform` | Platform status and incidents (non-player-keyed) |
| **AnalyticsTool** | `tft_analytics_player_matches` | Aggregated recent-match analytics (average placement, top-4 rate, most-played traits and units) |

## Quick start

Prerequisites: **Java 21** and a **Riot API key** (a development key from
<https://developer.riotgames.com/>). No Anthropic key or any other credential is needed to build,
test, or run.

```bash
export RIOT_API_KEY="RGAPI-your-key-here"

# stdio (default) — what local MCP clients expect; the client spawns the process and talks
# JSON-RPC over its stdin/stdout
./gradlew :tft-mcp-server:bootRun

# ...or over SSE, for a client that connects over HTTP
./gradlew :tft-mcp-server:bootRun --args='--spring.profiles.active=sse'
```

Over `sse`, the server starts on `http://localhost:8080`; the MCP message endpoint is
`/mcp/messages`, and liveness is `curl http://localhost:8080/actuator/health`. Over `stdio` there is
no port — `application-stdio.yml` disables the banner and console logging so nothing but protocol
frames reaches stdout (see [`docs/knowledge/gotchas.md`](../docs/knowledge/gotchas.md) before
touching stdio logging).

## Docker

A multi-stage `Dockerfile` (at the repo root) builds one server module on a slim JRE 21, selected via
`--build-arg SERVER_MODULE=` (default `lol-mcp-server` — pass `tft-mcp-server` for this server; one
image per game server). The container reads `RIOT_API_KEY` and always runs the `sse` profile (a
container has no controlling terminal for `stdio` to spawn into).

```bash
docker build --build-arg SERVER_MODULE=tft-mcp-server -t tft-mcp-server .
docker run --rm -p 8080:8080 -e RIOT_API_KEY="RGAPI-your-key-here" tft-mcp-server
```

Tagging a `tft-mcp-server/v*` release publishes the image to `ghcr.io/muddl/tft-mcp-server` via
`release.yml`.

## Architecture

Shared hexagon rationale: [repository root](../ARCHITECTURE.md). This server's own contexts, their
boundaries, and its slice rule are in [ARCHITECTURE.md](ARCHITECTURE.md).

## Testing

Tests run **offline with no Riot API key** — CI proves it. Outbound adapters run against local
[WireMock](https://wiremock.org/) (asserting URL, `X-RIOT-TOKEN`, JSON→DTO parsing, and error
mapping); application services run against in-memory port fakes.

```bash
./gradlew :tft-mcp-server:test    # this module's tests
./gradlew build                   # the whole-repo CI gate
```
