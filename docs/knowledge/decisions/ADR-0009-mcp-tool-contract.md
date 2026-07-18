# ADR-0009: MCP tool contract — naming and the single `player` param

- **Status:** Accepted (inventory partly superseded by [ADR-0013](ADR-0013-remove-featured-games.md)
  — `lol_spectator_featured_games` was later removed; the naming convention here is unchanged)
- **Date:** 2026-07-17

## Context

The pre-1a tool surface had grown ad hoc: mixed name shapes (`get_lol_summoner_by_puuid`,
`get_current_game_by_summoner_name`, `get_riot_account_by_riot_id`), paired by-riot-id/by-puuid
tools, and three tools whose underlying Riot path no longer resolves. Tool-selection accuracy was
the reason for the monorepo ([ADR-0006](ADR-0006-monorepo-split.md)); an inconsistent surface works
against it. Sub-project 1a is the place to take the break, on a small surface, before four more
servers copy the shape.

## Decision

**Naming: every tool is `<game>_<context>_<action>`** — e.g. `lol_summoner_by_player`,
`lol_league_entries_by_player`, `lol_account_by_player`.

**Boundary identity: every player-keyed tool takes one required `player` param** accepting a
`GameName#TAG` Riot ID or a raw PUUID, disambiguated on `#`, resolved internally via
`PlayerIdentityResolver` ([ADR-0008](ADR-0008-shared-player-identity-resolution.md)). The model
never chains `account → summoner → match` itself — that chaining is both a rate-limit multiplier and
a common way models flail.

**One param, not two optional ones.** "Exactly one of `riot_id` / `puuid`" is not expressible in
JSON Schema, so it degrades to a runtime check models routinely get wrong by filling both or
neither. One param keeps the schema small across a surface 1b will grow. An unparseable value
returns an actionable error naming both accepted forms.

**Resolution lives in the application service**, not the tool (the handoff-contract shape 1b
copies): a player-keyed service depends on its own port and `PlayerIdentityResolver`, and resolves
`player` itself. The **account tool** is the sole exception — it disambiguates `#` locally and calls
`RiotAccountService` directly, because it needs account *data* for both forms and is allow-listed
for the account domain; routing a Riot ID through the resolver would double the Riot calls.

**No aliases.** Pre-1.0, with no public consumers, the break is taken deliberately and documented
rather than preserved behind aliases that would double the surface.

> **Extended by [ADR-0014](ADR-0014-non-player-keyed-tools.md):** the single `player` param applies
> only to player-keyed endpoints; non-player-keyed tools (e.g. `lol_champion_rotation`,
> `lol_status_platform`, `lol_match_by_id`) take domain-appropriate params and no resolver.

**The surface shrinks from ten tools to seven while capability grows:**

| After | From |
|---|---|
| `lol_account_by_player` | `get_riot_account_by_puuid` + `get_riot_account_by_riot_id` (collapsed) |
| `lol_summoner_by_player` | `get_lol_summoner_by_puuid` |
| `lol_spectator_current_game_by_player` | `get_current_game_by_summoner_id` (+ dead by-name/in-game) |
| `lol_spectator_featured_games` | `get_featured_games` (rename) |
| `lol_analytics_player_matches` | `get_lol_player_match_analytics` (rename) |
| `lol_league_entries_by_player` | new (League exemplar) |
| `lol_league_apex_by_tier` | new (League exemplar) |

Removed as dead or superseded: `get_lol_summoner_by_name`, `get_lol_summoner_by_id`,
`get_current_game_by_summoner_name`, `check_if_summoner_in_game`.

## Consequences

**Makes easy:** a uniform, small surface that 1b extends by copying League; better tool-selection
accuracy; one Riot call per player-keyed invocation (amortized by the resolver cache).

**Costs:** a one-time public break with no aliases; `McpToolInventoryTest` must be updated in lockstep
with any future tool change (it is the enforced contract).

**Watch for:**

- **The account tool's local disambiguation is deliberate** — do not "consistency-fix" it to route
  through the resolver; that reopens a second Riot call and buys nothing.
- **New player-keyed tools resolve in the service, not the tool** — keep tools thin.
- **`<game>_<context>_<action>` is the shape every server inherits** — a new tool that breaks it
  should be caught in review and by the inventory test's intent.
