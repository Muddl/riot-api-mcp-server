# Changelog — `lol-mcp-server`

The League of Legends MCP server. Published as `ghcr.io/muddl/lol-mcp-server`.

Scoped to this module. Repo-wide changes live in the [root CHANGELOG](../CHANGELOG.md); the
libraries keep their own. Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/);
versioning: [SemVer](https://semver.org/spec/v2.0.0.html), pre-1.0 (breaking → minor).

## [0.2.0] - unreleased

Sub-project 1b — LoL parity: breadth. Five new contexts plus the match context's first inbound
tools, built against the `league` template. See
[the 1b spec](../docs/superpowers/specs/2026-07-18-lol-parity-breadth-design.md).

### Added
- `lol_champion_rotation` — the current free-to-play champion rotation for a platform (Champion-V3).
  The first non-player-keyed context (ADR-0014).
- `lol_champion_mastery_by_player` — a player's champion masteries sorted by points, with an optional
  top-N `count` (Champion-Mastery-V4).
- `lol_challenges_by_player` — a player's challenge standing: totals, category points, and
  per-challenge progress (LoL-Challenges-V1).
- `lol_clash_by_player` — a player's active Clash team registrations (Clash-V1).
- `lol_status_platform` — a platform's current maintenances and incidents (LoL-Status-V4).
- `lol_match_ids_by_player` and `lol_match_by_id` — the match context's first inbound tools: a
  player's recent match IDs and full match detail by ID (Match-V5). `lol_match_by_id` is
  non-player-keyed (ADR-0014).

## [0.1.0] - unreleased

First independently versioned release. Previously this module shared one `0.0.2-SNAPSHOT` with the
whole repo — see [ADR-0010](../docs/knowledge/decisions/ADR-0010-versioning-and-coordinates.md).

### Added
- `lol_league_entries_by_player` and `lol_league_apex_by_tier` — ranked-league entries by player and
  the apex leagues (challenger/grandmaster/master), the exemplar League context.
- Module-local `README.md` and `ARCHITECTURE.md` — the tool surface, run/Docker instructions,
  the LoL bounded contexts, and the updated tool diagram now live with the server (moved down from
  the repo root under the one-altitude rule; sub-project 1a Phase 7).

### Changed
- **Breaking:** coordinates are now `com.muddl`, package root `com.muddl.riot.lol`.
- **Breaking:** every tool is renamed to `<game>_<context>_<action>`, and every player-keyed tool
  takes a single `player` param accepting a Riot ID (`GameName#TAG`) or a raw PUUID. The two account
  tools collapse into `lol_account_by_player`. See
  [ADR-0009](../docs/knowledge/decisions/ADR-0009-mcp-tool-contract.md).
- **Breaking:** the spectator active-game lookup moved to Spectator-V5 (PUUID-keyed).

### Removed
- **Breaking:** `get_lol_summoner_by_name`, `get_lol_summoner_by_id`,
  `get_current_game_by_summoner_name`, and `check_if_summoner_in_game` — the first three routed
  through Riot-ID-era paths that no longer resolve or through `encryptedSummonerId`, which Riot is
  stripping; the fourth is redundant (a null current game answers it).
- **Breaking:** `lol_spectator_featured_games` and its `FeaturedGames` type — Riot's current
  Spectator-V5 API exposes only `active-games/by-summoner/{puuid}`; the featured-games endpoint was
  retired (v5 never carried it; v4 is deprecated), so the tool always returned 403. The live eval
  suite caught it. The surface is now six tools. See
  [ADR-0013](../docs/knowledge/decisions/ADR-0013-remove-featured-games.md).
