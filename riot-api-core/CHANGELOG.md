# Changelog — `riot-api-core`

The shared Riot HTTP kernel: `RiotApiClient`, routing enums, `RiotApiProperties`, `RiotApiException`,
and the auto-configuration that registers them.

Scoped to this module. Repo-wide changes live in the [root CHANGELOG](../CHANGELOG.md); the other
modules keep their own. Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/);
versioning: [SemVer](https://semver.org/spec/v2.0.0.html), pre-1.0 (breaking → minor).

## [0.1.0] - unreleased

First independently versioned release. Previously this module shared one `0.0.2-SNAPSHOT` with the
whole repo — see [ADR-0010](../docs/knowledge/decisions/ADR-0010-versioning-and-coordinates.md).

### Added
- Automatic retry on HTTP 429, honouring the `Retry-After` header (falling back to a configurable
  `riot.retry-backoff`, default 1s) up to `riot.max-retries` attempts (default 3), with each wait
  capped at `riot.max-retry-backoff` (default 120s) so a hostile or erroneous header cannot stall a
  thread. This is reactive retry, not a proactive rate limiter — see [ADR-0007](../docs/knowledge/decisions/ADR-0007-core-hardening-boundary.md).

### Changed
- **Breaking:** coordinates are now `com.muddl`, package root `com.muddl.riot.core`.
- **Breaking:** `RiotApiException` messages are now actionable, status-derived text (e.g. a 403
  explains that development keys expire every 24 hours) instead of the raw Riot body. The raw body
  moves to `RiotApiException.getRawBody()`. See [ADR-0007](../docs/knowledge/decisions/ADR-0007-core-hardening-boundary.md).
