# Spring Boot 4.1 / Spring AI 2.0 Migration — Design

## Context

The project has been dormant for several months. Two modernization needs surfaced:

1. Dependabot has ~60 open alerts against the project (Tomcat, Spring Framework, Jackson, Logback, commons-compress, commons-lang3, httpclient5, artemis, assertj — all transitive, pulled in via the Spring Boot managed BOM).
2. Spring Boot `3.4.4` and Spring AI `1.0.0-M6` are both stale. Checking `endoflife.date`, **Spring Boot 3.4.x reached EOL 2025-12-31, and 3.5.x reached EOL 2026-06-30** — both already past end-of-life as of today (2026-07-12). The only actively-supported Spring Boot lines are 4.0 (EOL 2026-12-31) and 4.1 (EOL 2027-07-31, latest).

This document covers the Spring Boot / Spring AI migration only. GitHub Actions and Dependabot config modernization is a separate, smaller sub-project designed afterward.

A related but independent issue was found and already fixed during this session: `application.yml` had live Anthropic and Riot API keys committed in plaintext since the repo's first commit, on a public GitHub repo. Both keys have been rotated by the user and the config externalized to environment variables (commit `f4eacb8`). No further action needed here.

## Goal & Target State

Move from Spring Boot `3.4.4` / Spring AI `1.0.0-M6` to **Spring Boot `4.1.0`** / **Spring AI `2.0.0` GA** — a matched pair, since Spring AI 2.0 targets Spring Framework 7 / Boot 4.x.

This is a deliberate full major-version jump rather than a stopgap patch within the 3.x line, because:
- Every 3.x line is already past OSS EOL — patching within 3.x just delays the same migration.
- Spring Boot 4.1 has the longest support runway available today.
- `htmx-spring-boot` is already pinned at `5.1.0` in `build.gradle` (merged via a prior Dependabot PR), and that library's 5.x line *requires* a Spring Boot 4.x baseline per its own release notes — the build is arguably already version-mismatched. (The library is unused in application code — no `Hx*` annotations appear anywhere in `src/` — so there's no functional htmx migration work, just correcting the version mismatch as a side effect of the Boot bump.)

Closing the current Dependabot alerts is a side effect of this jump, not a goal pursued independently — Boot 4.1's managed BOM brings forward all the flagged transitive dependencies.

## Migration Areas

### a. Build config (`build.gradle`)

- Spring Boot Gradle plugin: `3.4.4` → `4.1.0`.
- Java toolchain: stays at `21` (Boot 4 supports Java 17–26).
- `springAiVersion` ext property: `1.0.0-M6` → `2.0.0`.
- Dependency artifact IDs change with Spring AI 2.0's starter naming convention (confirmed via Maven Central — the old artifact IDs stop receiving new versions after the M6/1.x line):
  - `org.springframework.ai:spring-ai-anthropic-spring-boot-starter` → `org.springframework.ai:spring-ai-starter-model-anthropic`
  - `org.springframework.ai:spring-ai-mcp-server-webmvc-spring-boot-starter` → `org.springframework.ai:spring-ai-starter-mcp-server-webmvc`
- `io.github.wimdeblauwe:htmx-spring-boot` stays at `5.1.0` (now correctly aligned with Boot 4.x).

### b. MCP tool annotations (the core code migration)

Spring AI 2.0 replaces `@Tool` / `@ToolParam` (`org.springframework.ai.tool.annotation.*`) with `@McpTool` / `@McpToolParam` for methods exposed through an MCP server. Auto-configuration in 2.0 scans for `@McpTool`-annotated beans specifically.

This touches all four tool classes — annotation and import swap only, method bodies unchanged:
- `src/main/java/com/wkaiser/riotapimcpserver/riot/account/tool/RiotAccountTool.java`
- `src/main/java/com/wkaiser/riotapimcpserver/riot/lol/summoner/tool/SummonerTool.java`
- `src/main/java/com/wkaiser/riotapimcpserver/riot/lol/analytics/tool/AnalyticsTool.java`
- `src/main/java/com/wkaiser/riotapimcpserver/riot/lol/spectator/tool/LiveGameTool.java`

### c. Config properties (`application.yml`)

The `spring.ai.mcp.server.*` namespace gains new sub-properties in 2.0 (e.g. `annotation-scanner.enabled`). The existing `name`, `version`, `type`, `sse-message-endpoint` keys need to be verified against 2.0's property set during implementation — add any newly-required keys, confirm no renames broke the existing ones.

### d. Transitive CVE resolution

No direct dependency edits — resolved by Boot 4.1's dependency-management BOM pulling forward current Tomcat, Jackson, Spring Framework, Logback, and commons-* versions. Verified in testing, not asserted here.

## Testing & Validation

The existing test structure (JUnit 5 unit tests with mocked Riot API responses, `@Disabled` integration tests requiring live keys) is unchanged by this migration — the goal is to keep it green through the upgrade, not expand coverage.

- `./gradlew build` must pass after each major step (build config bump, then MCP annotation migration) — catch breaks incrementally rather than in one big-bang diff.
- Manual MCP smoke test: `./gradlew bootRun`, confirm clean startup and that all four MCP tools are still discoverable/callable. A green build does not prove the `@McpTool` swap actually wired tools up correctly — this needs an actual run.
- Re-check Dependabot alerts post-migration (`gh api repos/:owner/:repo/dependabot/alerts`) — expect the current ~60 open alerts to drop to zero or to only entries with no available fixed version yet.
- Integration tests remain `@Disabled`, consistent with current project convention (no live-key testing in this pass).

## Out of Scope

- GitHub Actions / Dependabot config modernization — separate sub-project.
- Git history scrub of the previously-leaked API keys — optional later cleanup; keys are already rotated and dead, so this is hygiene, not urgency.
- htmx feature work — dependency is unused in code; this migration only fixes its version alignment.
- Full Jackson 2 → Jackson 3 migration — Boot 4.0 deprecates a Jackson-2-based test helper but doesn't force a Jackson 3 cutover yet. Flagged as a watch-item for implementation, not a required change in this pass.
