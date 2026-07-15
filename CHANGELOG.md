# Changelog

All notable changes to the Riot API MCP Server are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-07-13

Portfolio sanitization: a real hexagonal architecture, HTTP-mocked tests, build-time enforcement, a
container pipeline, and honest documentation. Also releases the previously unreleased Spring Boot 4.1
/ Spring AI 2.0 modernization.

### Added
- **Bounded-context hexagonal architecture** under `com.wkaiser.riotapimcpserver` — top-level
  `account`, `summoner`, `match`, `spectator`, `analytics`, `shared` contexts, each with
  `domain` / `application` (+ `application.port`) / `adapter.in.mcp` / `adapter.out.riot`.
- **`shared/http/RiotApiClient`** — a single component holding all Riot HTTP/auth/error handling,
  exposing `regional(...)` and `platform(...)` `RestClient` factories.
- **Typed configuration** `RiotApiProperties` (`@ConfigurationProperties(prefix = "riot")`),
  replacing scattered `@Value("${riot.apiKey}")` usage; the key is read from `RIOT_API_KEY`.
- **WireMock adapter tests** for every outbound adapter and **in-memory port fakes** for application
  services — the full suite runs offline with no API key.
- **Build-time quality gates:** ArchUnit rules (dependency direction + naming/placement), JaCoCo
  coverage reporting, and Spotless formatting, all wired into `./gradlew build`.
- **CI/CD:** `ci.yml` (build + test + ArchUnit + JaCoCo + Spotless, with PR annotations) and
  `release.yml` (multi-stage `Dockerfile` → image published to GHCR on `v*` tags).
- **Documentation:** `ARCHITECTURE.md`, `CONTRIBUTING.md`, and a committed knowledge base under
  `docs/knowledge/` (ADRs, patterns, gotchas, glossary).

### Changed
- **Spring Boot 4.1.0 / Spring AI 2.0.0** (from Spring Boot 3.4.4 / Spring AI 1.0.0-M6, both past
  end of life); Gradle wrapper 8.13 → 9.6.1; Spring AI starter artifact IDs updated.
- All four MCP tool classes migrated from `@Tool`/`@ToolParam` to `@McpTool`/`@McpToolParam`.
- Riot DTOs relocated into per-context `domain/` packages (no shape changes).
- `README.md` and `CLAUDE.md` rewritten to describe the real project.

### Removed
- `PLAN.md` and `FEATURES.md` (fictional AWS production plan and aspirational roadmap).
- `CompilationVerificationTest` (superseded by real adapter/service tests).
- Copy-pasted HTTP/auth/error plumbing from the four services (now centralized in `RiotApiClient`).
- Fictional documentation claims — "83 subagents", the AWS "$2,000–5,000/month" plan, and
  multi-agent "success stories".

### Fixed
- Match endpoints now send the `X-RIOT-TOKEN` header (previously omitted), since all requests route
  through `RiotApiClient`.

## [1.0.0] - 2025-01-20

### Added
- **Live Game (Spectator v4) tool** — `LiveGameTool` with four MCP methods for live-game and
  featured-game data, backed by `SpectatorService`.
- Spectator DTO suite: `CurrentGameInfo`, `CurrentGameParticipant`, `BannedChampion`, `Observer`,
  `FeaturedGames`, `Perks`, `GameCustomizationObject`.
- Graceful handling of the "summoner not in game" case (Spectator `404`).

## [0.3.0] - 2024-07

### Added
- GitHub Actions: Claude Code Review and Claude PR Assistant workflows; Gradle build automation.

### Fixed
- `gradlew` execute-permission issues in CI.

## [0.2.0] - 2024-07

### Added
- Core MCP tools: `RiotAccountTool`, `SummonerTool`, `AnalyticsTool`.
- Service layer for the account, summoner, match, and analytics features.

## [0.1.0] - 2024-07

### Added
- Initial Spring Boot MCP server (Java 21) with Riot API integration.
- Regional architecture via `RiotApiRegionUri` and `RiotApiPlatformUri` enums.
- `RiotApiException` and `GlobalExceptionHandler` for consistent error handling.
- Gradle build with the Spring AI MCP starter, Lombok, and JUnit 5.

---

## Change types

**Added** new features · **Changed** existing behavior · **Deprecated** soon-to-be removed ·
**Removed** now-removed features · **Fixed** bug fixes · **Security** vulnerability fixes.

For the architecture behind these changes, see [ARCHITECTURE.md](ARCHITECTURE.md).
