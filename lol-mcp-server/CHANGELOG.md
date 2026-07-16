# Changelog — `lol-mcp-server`

The League of Legends MCP server. Published as `ghcr.io/muddl/lol-mcp-server`.

Scoped to this module. Repo-wide changes live in the [root CHANGELOG](../CHANGELOG.md); the
libraries keep their own. Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/);
versioning: [SemVer](https://semver.org/spec/v2.0.0.html), pre-1.0 (breaking → minor).

## [0.1.0] - unreleased

First independently versioned release. Previously this module shared one `0.0.2-SNAPSHOT` with the
whole repo — see [ADR-0010](../docs/knowledge/decisions/ADR-0010-versioning-and-coordinates.md).

### Changed
- **Breaking:** coordinates are now `com.muddl`, package root `com.muddl.riot.lol`.

<!--
The MCP tool contract break (ten tools -> seven, single `player` param) lands in Plan C of
sub-project 1a and is logged here when it does. Do not pre-announce it: this file describes what
has shipped, not what is planned. The roadmap covers plans.
-->
