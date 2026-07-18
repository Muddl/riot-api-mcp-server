# ADR-0014 — Non-player-keyed tools extend the tool contract

**Status:** Accepted (sub-project 1b, 2026-07-18)

## Context

[ADR-0009](ADR-0009-mcp-tool-contract.md) established the tool contract on the `league` exemplar:
every tool is named `<game>_<context>_<action>`, and every **player-keyed** tool takes a single
`player` param (a Riot ID `GameName#TAG` or a raw PUUID) resolved to a PUUID in the application
service via `PlayerIdentityResolver`.

Sub-project 1b added the first tools that are keyed by something other than a player:

- `lol_champion_rotation` and `lol_status_platform` — keyed only by platform.
- `lol_match_by_id` — keyed by a match ID.

The `player` param convention has nothing to say about these, and forcing one would be nonsense.

## Decision

The `player` param convention of ADR-0009 applies **only to player-keyed endpoints**. A tool takes
**domain-appropriate params**: platform alone, a match ID, a tier, and so on. A non-player-keyed
context's service does **not** depend on `PlayerIdentityResolver`.

Everything else in ADR-0009 still holds for every tool: the `<game>_<context>_<action>` name, the
thin tool delegating to a service, the WireMock-adapter-plus-port-fake test pair, and portal-verified
endpoint paths.

## Consequences

- The handoff contract is no longer implicitly "player-keyed only." Servers 2–4 inherit both shapes.
- `McpToolInventoryTest` no longer assumes every tool carries a `player` param.
- The `add-a-bounded-context` pattern documents both the player-keyed (`league`) and non-player-keyed
  (`champion`/`status`) variants.
- **1a's falsifiable criterion held:** 1b added five contexts and the match tools with **no change to
  `riot-api-core` or `riot-account-core`**. Non-player contexts simply omit the resolver; player-keyed
  contexts reuse it exactly as `league` does.
