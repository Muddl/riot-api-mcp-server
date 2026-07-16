# Changelog — `riot-api-core`

The shared Riot HTTP kernel: `RiotApiClient`, routing enums, `RiotApiProperties`, `RiotApiException`,
and the auto-configuration that registers them.

Scoped to this module. Repo-wide changes live in the [root CHANGELOG](../CHANGELOG.md); the other
modules keep their own. Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/);
versioning: [SemVer](https://semver.org/spec/v2.0.0.html), pre-1.0 (breaking → minor).

## [0.1.0] - unreleased

First independently versioned release. Previously this module shared one `0.0.2-SNAPSHOT` with the
whole repo — see [ADR-0010](../docs/knowledge/decisions/ADR-0010-versioning-and-coordinates.md).

### Changed
- **Breaking:** coordinates are now `com.muddl`, package root `com.muddl.riot.core`.
