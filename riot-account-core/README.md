# `riot-account-core`

The cross-game **account-v1** context and the shared **player-identity resolver**. account-v1 is not
game-specific (a Riot account spans League, TFT, and Valorant), so it lives in its own library that
every game server consumes. It ships **no `@McpTool`** by design — each server owns its own inbound
adapter so tool names can be namespaced per game and never collide inside an MCP client.

Consumed via a Gradle project reference and Spring auto-configuration; versioned for provenance but
**not published** (see [ADR-0010](../docs/knowledge/decisions/ADR-0010-versioning-and-coordinates.md)).

## Public API

- **`PlayerIdentityResolver`** (`com.muddl.riot.account.identity`) — the open, cross-cutting surface
  every player-keyed context depends on:

  ```java
  String resolvePuuid(String player);  // accepts "GameName#TAG" or a raw PUUID → returns a PUUID
  ```

  Riot IDs (`GameName#TAG`) are mutable, so the resolver caches `Riot ID → PUUID` in a bounded,
  TTL-expiring cache; a raw PUUID passes straight through. It returns a **plain PUUID string**, not a
  `RiotAccount`, so depending on it does not open the account domain. See
  [ADR-0008](../docs/knowledge/decisions/ADR-0008-shared-player-identity-resolution.md).

- **`RiotAccountService`** (`com.muddl.riot.account.application`) — account data by PUUID or Riot ID,
  over `RiotAccountPort`. Reaching for account **data** (not just a PUUID) is deny-by-default: only a
  server's `analytics` and `account` contexts may depend on it.

- **`RiotAccount`** (`com.muddl.riot.account.domain`) — the account DTO.

## Consuming it

```groovy
dependencies {
    implementation project(':riot-account-core')  // transitively brings riot-api-core
}
```

Auto-configuration (`RiotAccountAutoConfiguration`) registers the service and the resolver; a server
gets them by declaring the dependency and nothing else.

## Configuration

Inherits the `riot.*` configuration from [`riot-api-core`](../riot-api-core/README.md#configuration)
(the API key and routing). The resolver's cache is bounded and TTL-expiring on an injected ticker —
see [ADR-0008](../docs/knowledge/decisions/ADR-0008-shared-player-identity-resolution.md).

## Architecture

Shared hexagon rationale: [repository root](../ARCHITECTURE.md). This module's own internals — the
resolver, the cache, the two-altitude confinement — are in [ARCHITECTURE.md](ARCHITECTURE.md).

## Build and test

```bash
./gradlew :riot-account-core:test      # port-fake + WireMock tests, offline, no key
./gradlew build                        # the whole-repo CI gate
```
