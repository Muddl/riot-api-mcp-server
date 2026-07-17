# `riot-api-core` — Architecture

This module is the monorepo's **shared kernel**: it owns HTTP and Riot's error protocol and nothing
game-specific. The bounded-context hexagon that every *server* is built from is described once at the
[repository root](../ARCHITECTURE.md) — this document does not restate it; it covers only what is
particular to the kernel.

## Internal layout

```
com.muddl.riot.core
├── config/        RiotApiProperties, RiotApiAutoConfiguration
├── enums/         RiotApiRegionUri, RiotApiPlatformUri
├── exception/     RiotApiException
├── http/          RiotApiClient — all HTTP/auth/retry/error handling
└── (testFixtures) HexagonRules, Fixtures — shared across every module's tests
```

## The HTTP client

`RiotApiClient` is the single seam between our code and Riot. It exposes `regional(...)` and
`platform(...)` `RestClient` factories; each returned client carries the `X-RIOT-TOKEN` header (from
typed `RiotApiProperties`), the assembled base URL (`https://<host>` in production, or
`base-url-override` when set), automatic 429 retry, and a status handler mapping non-2xx to
`RiotApiException(message, statusCode)`. This replaced what used to be copy-pasted into four
services (a private client factory, the header constant, a `@Value("${riot.apiKey}")`, a
near-identical `try/catch`).

## Retry, not a rate limiter

429 handling is **reactive**: it honours Riot's `Retry-After` header, falls back to `retry-backoff`
when the header is absent, bounds attempts by `max-retries`, and caps any single wait at
`max-retry-backoff` so a hostile or erroneous header cannot stall a thread. It is deliberately **not**
a proactive token-bucket limiter — that needs a real design (per-method limits, shared buckets,
concurrency) and would be guessing at this stage. See
[ADR-0007](../docs/knowledge/decisions/ADR-0007-core-hardening-boundary.md).

## The error taxonomy

`RiotApiException` carries an actionable, status-derived message; the raw Riot body moves off the
message and onto `getRawBody()`. The intended consumer is a third party installing against their own
key, and a clear 403/404/429/503 message is most of the difference between a good and a bad install
experience.

## The boundary, held deliberately

The kernel knows about **HTTP and Riot's error protocol**, never a game's domain. League's DTOs stay
in `lol-mcp-server`. [ADR-0006](../docs/knowledge/decisions/ADR-0006-monorepo-split.md) warned about
core becoming a junk drawer; ADR-0007 exists to hold this line, and the sub-project 1a sanity pass
audits it (`api` vs `implementation`, no game type leaking in).

## Shared test fixtures

`riot-api-core`'s `testFixtures` source set holds `HexagonRules` (the ArchUnit rule set every
module's architecture test declares) and `Fixtures` (canned-JSON loading). A new game server
inherits the architecture rules instead of copy-pasting them — see the root
[Enforcement](../ARCHITECTURE.md#enforcement) section.
