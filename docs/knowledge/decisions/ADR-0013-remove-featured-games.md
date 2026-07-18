# ADR-0013: Remove the featured-games tool (endpoint retired by Riot)

- **Status:** Accepted
- **Date:** 2026-07-18

## Context

`lol_spectator_featured_games` called `GET /lol/spectator/v5/featured-games`. The live eval suite
([ADR-0012](ADR-0012-live-eval-harness.md)) showed the tool returning **403** on every run, while
the same key authenticated on every other endpoint — including spectator `active-games/by-summoner`.
The Riot developer portal confirmed the cause: the current **Spectator-V5** API exposes only
`active-games/by-summoner/{encryptedPUUID}`. There is no featured-games endpoint in v5, and the v4
endpoint is deprecated. The tool targeted an endpoint that no longer exists in the supported surface.

## Decision

Remove the `lol_spectator_featured_games` tool entirely: the `@McpTool` method, the `FeaturedGames`
domain type, and the `getFeaturedGames` methods on the spectator service, port, and outbound adapter,
along with their unit/WireMock tests and fixtures.

- The MCP tool inventory drops from **seven to six** (`McpToolInventoryTest` updated). This is a
  breaking change to the public contract, recorded in the (unreleased) `lol-mcp-server` `0.1.0`
  changelog.
- `lol_spectator_current_game_by_player` (active-games) is unaffected and remains.
- **Live-game eval coverage:** without featured-games there is no way to force a guaranteed in-game
  subject, so current-game is exercised via the "in a game OR cleanly not in a game" invariant
  (seeded from the apex ladder). That is an accepted reduction, not a gap to fix.

## Consequences

- The public surface only exposes tools that actually work — the standard for a third-party
  consumer. A tool that always 403s has no place in the contract.
- Supersedes, in part, [ADR-0009](ADR-0009-mcp-tool-contract.md): its seven-tool inventory table is
  now six; the naming convention it established is unchanged.
- Confirms the live eval harness's core value: it caught a shipped tool pointing at a removed
  endpoint — something the offline WireMock suite (frozen stubs) never could.
