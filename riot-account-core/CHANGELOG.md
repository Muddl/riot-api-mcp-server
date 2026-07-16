# Changelog — `riot-account-core`

The cross-game account-v1 context: `RiotAccount`, `RiotAccountService`, `RiotAccountPort`, and its
outbound adapter. Ships no `@McpTool` by design (ArchUnit-enforced) — each game server owns its own
inbound adapter so tool names can be namespaced per game.

Scoped to this module. Repo-wide changes live in the [root CHANGELOG](../CHANGELOG.md); the other
modules keep their own. Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/);
versioning: [SemVer](https://semver.org/spec/v2.0.0.html), pre-1.0 (breaking → minor).

## [0.1.0] - unreleased

First independently versioned release. Previously this module shared one `0.0.2-SNAPSHOT` with the
whole repo — see [ADR-0010](../docs/knowledge/decisions/ADR-0010-versioning-and-coordinates.md).

### Added
- `PlayerIdentityResolver`: resolves a `GameName#TAG` Riot ID or a raw PUUID to a PUUID, cached
  (Riot ID → PUUID) in a bounded, TTL-expiring Caffeine cache on an injected ticker. The open,
  cross-cutting identity surface every game server depends on — see
  [ADR-0008](../docs/knowledge/decisions/ADR-0008-shared-player-identity-resolution.md).

### Changed
- **Breaking:** coordinates are now `com.muddl`, package root `com.muddl.riot.account`.
