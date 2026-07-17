# Changelog — `lol-mcp-server`

The League of Legends MCP server. Published as `ghcr.io/muddl/lol-mcp-server`.

Scoped to this module. Repo-wide changes live in the [root CHANGELOG](../CHANGELOG.md); the
libraries keep their own. Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/);
versioning: [SemVer](https://semver.org/spec/v2.0.0.html), pre-1.0 (breaking → minor).

## [0.1.0] - unreleased

First independently versioned release. Previously this module shared one `0.0.2-SNAPSHOT` with the
whole repo — see [ADR-0010](../docs/knowledge/decisions/ADR-0010-versioning-and-coordinates.md).

### Added
- `lol_league_entries_by_player` and `lol_league_apex_by_tier` — ranked-league entries by player and
  the apex leagues (challenger/grandmaster/master), the exemplar League context.

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
