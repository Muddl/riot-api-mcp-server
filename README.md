# Riot API MCP Server

[![CI](https://github.com/Muddl/riot-api-mcp-server/actions/workflows/ci.yml/badge.svg)](https://github.com/Muddl/riot-api-mcp-server/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-blue.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 4.1.0](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A Gradle monorepo of [Model Context Protocol](https://modelcontextprotocol.io) (MCP) servers that
expose the [Riot Games API](https://developer.riotgames.com/) to AI models as small, typed toolsets —
two shared libraries plus one Spring Boot server per Riot game (currently League of Legends). It is a
**portfolio piece**: the point is the engineering — a clean bounded-context hexagonal architecture, a
single shared HTTP client, HTTP-mocked tests that run in CI with no API key, and architecture rules
enforced at build time (some by ArchUnit, some by Gradle's module graph itself).

## Modules

| Module | What it is | Docs |
|---|---|---|
| [`riot-api-core`](riot-api-core/README.md) | Shared Riot HTTP kernel — `RiotApiClient`, routing enums, `RiotApiException`, config | [README](riot-api-core/README.md) · [ARCHITECTURE](riot-api-core/ARCHITECTURE.md) |
| [`riot-account-core`](riot-account-core/README.md) | Cross-game account context + the player-identity resolver | [README](riot-account-core/README.md) · [ARCHITECTURE](riot-account-core/ARCHITECTURE.md) |
| [`lol-mcp-server`](lol-mcp-server/README.md) | The League of Legends MCP server (seven tools; stdio + sse) | [README](lol-mcp-server/README.md) · [ARCHITECTURE](lol-mcp-server/ARCHITECTURE.md) |

**Dependency rule:** `lol-mcp-server` → `riot-account-core` → `riot-api-core`, never back — enforced
by Gradle at compile time (a library simply has no dependency on a game module). Each Riot context
inside a server is a self-contained hexagon: an inbound MCP adapter calls an application service,
which depends on an outbound **port** implemented by a Riot adapter; all HTTP, auth, retry, and error
handling live in one place, `riot-api-core`'s `RiotApiClient`. See
**[ARCHITECTURE.md](ARCHITECTURE.md)** for the full rationale and
[ADR-0006](docs/knowledge/decisions/ADR-0006-monorepo-split.md) for why the monorepo split happened.

## Quick start

Prerequisites: **Java 21** and, only to *run* a server, a **Riot API key** (a development key from
<https://developer.riotgames.com/>). No key is needed to build or test — the suite is fully offline.

```bash
./gradlew build          # compile + all tests + ArchUnit + JaCoCo + Spotless — the CI gate
```

To run a server, see its README — e.g. [`lol-mcp-server`](lol-mcp-server/README.md#quick-start) for
the stdio/sse commands and the Docker image.

## Testing

Tests run **offline with no Riot API key** — CI proves it. Outbound adapters are exercised against a
local [WireMock](https://wiremock.org/) server; application services against in-memory port fakes.
Architecture, coverage, and formatting are checked in the same run.

```bash
./gradlew test           # tests only
./gradlew spotlessApply  # auto-format sources (run before committing)
```

## Documentation

| Document | Purpose |
|---|---|
| **[ARCHITECTURE.md](ARCHITECTURE.md)** | Shared hexagonal design, the dependency rule, routing, enforcement, testing strategy, transports |
| **[CONTRIBUTING.md](CONTRIBUTING.md)** | Build/test/format commands, conventions, how to add a context or tool |
| **[CLAUDE.md](CLAUDE.md)** | Guidance for AI coding agents working in this repo |
| **Per-module READMEs** | [`riot-api-core`](riot-api-core/README.md) · [`riot-account-core`](riot-account-core/README.md) · [`lol-mcp-server`](lol-mcp-server/README.md) |
| **[docs/knowledge/](docs/knowledge/)** | Committed knowledge base — ADRs, patterns, gotchas, glossary, roadmap |

## License

Released under the MIT License — see [LICENSE](LICENSE).
