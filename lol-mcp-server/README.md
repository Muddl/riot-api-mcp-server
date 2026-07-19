# `lol-mcp-server`

The League of Legends MCP server: a Spring Boot app that exposes the Riot LoL API to AI models as a
small set of typed [MCP](https://modelcontextprotocol.io) tools. Published as
`ghcr.io/muddl/lol-mcp-server`.

It is built on the two shared libraries — [`riot-api-core`](../riot-api-core/README.md) (HTTP,
routing, errors) and [`riot-account-core`](../riot-account-core/README.md) (the account context and
the player-identity resolver) — and adds the LoL-specific bounded contexts.

## MCP tools

Eleven inbound adapters expose **13** tools. Every player-keyed tool takes a single `player`
parameter accepting either a Riot ID (`GameName#TAG`) or a raw PUUID, resolved internally — the model
never has to chain `account → summoner → match` itself (see
[ADR-0009](../docs/knowledge/decisions/ADR-0009-mcp-tool-contract.md)). A few tools are
non-player-keyed (platform- or region-scoped) — see
[ADR-0014](../docs/knowledge/decisions/ADR-0014-non-player-keyed-tools.md).

| Tool class (`adapter.in.mcp`) | MCP tool names | Purpose |
|---|---|---|
| **RiotAccountTool** | `lol_account_by_player` | Riot account by player |
| **SummonerTool** | `lol_summoner_by_player` | Summoner profile by player |
| **LiveGameTool** | `lol_spectator_current_game_by_player` | Live-game (Spectator-V5) data; `null` when not in a game |
| **AnalyticsTool** | `lol_analytics_player_matches` | Aggregated recent-match analytics (composes account + summoner + match) |
| **LeagueTool** | `lol_league_entries_by_player`, `lol_league_apex_by_tier` | Ranked entries by player; apex league (CHALLENGER/GRANDMASTER/MASTER) by tier + queue |
| **ChampionTool** | `lol_champion_rotation` | Current free-to-play champion rotation for a platform (non-player-keyed) |
| **StatusTool** | `lol_status_platform` | Platform status and incidents (Status-V4, non-player-keyed) |
| **ChampionMasteryTool** | `lol_champion_mastery_by_player` | Champion mastery for a player; optional top-N by mastery points |
| **ChallengesTool** | `lol_challenges_by_player` | Challenges progress and points for a player |
| **ClashTool** | `lol_clash_by_player` | Clash tournament registrations for a player |
| **MatchTool** | `lol_match_ids_by_player`, `lol_match_by_id` | Recent match IDs for a player (region-routed, paged); full detail of one match by ID |

## Quick start

Prerequisites: **Java 21** and a **Riot API key** (a development key from
<https://developer.riotgames.com/>). No Anthropic key or any other credential is needed to build,
test, or run.

```bash
export RIOT_API_KEY="RGAPI-your-key-here"

# stdio (default) — what local MCP clients expect; the client spawns the process and talks
# JSON-RPC over its stdin/stdout
./gradlew :lol-mcp-server:bootRun

# ...or over SSE, for a client that connects over HTTP
./gradlew :lol-mcp-server:bootRun --args='--spring.profiles.active=sse'
```

Over `sse`, the server starts on `http://localhost:8080`; the MCP message endpoint is
`/mcp/messages`, and liveness is `curl http://localhost:8080/actuator/health`. Over `stdio` there is
no port — `application-stdio.yml` disables the banner and console logging so nothing but protocol
frames reaches stdout (see [`docs/knowledge/gotchas.md`](../docs/knowledge/gotchas.md) before
touching stdio logging).

## Docker

A multi-stage `Dockerfile` (at the repo root) builds one server module on a slim JRE 21, selected via
`--build-arg SERVER_MODULE=` (default `lol-mcp-server` — one image per game server). The container
reads `RIOT_API_KEY` and always runs the `sse` profile (a container has no controlling terminal for
`stdio` to spawn into).

```bash
docker build -t lol-mcp-server .
docker run --rm -p 8080:8080 -e RIOT_API_KEY="RGAPI-your-key-here" lol-mcp-server
```

Tagging a release publishes the image to `ghcr.io/muddl/lol-mcp-server` via `release.yml`.

## Architecture

Shared hexagon rationale: [repository root](../ARCHITECTURE.md). This server's own contexts, their
boundaries, and its slice rule are in [ARCHITECTURE.md](ARCHITECTURE.md).

## Testing

Tests run **offline with no Riot API key** — CI proves it. Outbound adapters run against local
[WireMock](https://wiremock.org/) (asserting URL, `X-RIOT-TOKEN`, JSON→DTO parsing, and error
mapping including the spectator `404 → null` rule); application services run against in-memory port
fakes.

```bash
./gradlew :lol-mcp-server:test    # this module's tests
./gradlew build                   # the whole-repo CI gate
```
