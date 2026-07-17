# `riot-api-core`

The shared Riot HTTP kernel for every server in this monorepo: one place for HTTP, auth, retry, and
Riot's error protocol. It knows **nothing** about any game's domain — no League, TFT, or Valorant
DTO lives here (that boundary is [ADR-0007](../docs/knowledge/decisions/ADR-0007-core-hardening-boundary.md)).

Consumed by every other module via a Gradle project reference and Spring auto-configuration; it is
versioned for provenance but **not published** as a Maven artifact (see
[ADR-0010](../docs/knowledge/decisions/ADR-0010-versioning-and-coordinates.md)).

## Public API

- **`RiotApiClient`** (`com.muddl.riot.core.http`) — the only HTTP entry point. Two pre-configured
  `RestClient` factories, split by Riot's two host families:

  ```java
  RestClient regional(RiotApiRegionUri region);     // account, match — region-routed
  RestClient platform(RiotApiPlatformUri platform);  // summoner, spectator, league — platform-routed
  ```

  Each returned client already carries the `X-RIOT-TOKEN` header, the assembled base URL, automatic
  retry on HTTP 429 (honouring `Retry-After`), and a status handler that maps any non-2xx response
  to `RiotApiException`.

- **`RiotApiException`** (`com.muddl.riot.core.exception`) — `getStatusCode()` and an actionable,
  status-derived message (e.g. a 403 explains that development keys expire every 24 hours). The raw
  Riot body is still reachable via `getRawBody()`.

- **Routing enums** (`com.muddl.riot.core.enums`) — `RiotApiRegionUri` (`AMERICAS`, `EUROPE`,
  `ASIA`, `SEA`) and `RiotApiPlatformUri` (`NA1`, `EUW1`, `KR`, …). Picking the wrong family yields
  a 404 from Riot, so the split makes the correct choice a compile-time decision.

- **`RiotApiProperties`** (`com.muddl.riot.core.config`) — typed `riot.*` configuration (below).

## Consuming it

Add the project reference; auto-configuration (`RiotApiAutoConfiguration`) registers `RiotApiClient`
and `RiotApiProperties`. No component scanning, no package coupling.

```groovy
dependencies {
    implementation project(':riot-api-core')
}
```

## Configuration

Bound from the `riot.*` namespace (`RiotApiProperties`):

| Property | Env / default | Purpose |
|---|---|---|
| `riot.api-key` | `RIOT_API_KEY` | Riot API key; sent as `X-RIOT-TOKEN`. Never logged. |
| `riot.region` | `AMERICAS` | Default region for region-routed endpoints. |
| `riot.base-url-override` | *(unset)* | Points every client at a given base URL — how tests hit a local mock server. |
| `riot.max-retries` | `3` | Attempts on HTTP 429 before surfacing the error. |
| `riot.retry-backoff` | `1s` | Backoff when a 429 carries no usable `Retry-After`. |
| `riot.max-retry-backoff` | `120s` | Upper bound on a single 429 wait, even if `Retry-After` asks for longer. |

## Architecture

The shared hexagon rationale lives at the [repository root](../ARCHITECTURE.md). This module's own
internals — the HTTP client, the error taxonomy, the deliberately held boundary — are in
[ARCHITECTURE.md](ARCHITECTURE.md).

## Build and test

Part of the monorepo; there is no standalone build.

```bash
./gradlew :riot-api-core:test          # this module's tests (WireMock, offline, no key)
./gradlew build                        # the whole-repo CI gate
```
