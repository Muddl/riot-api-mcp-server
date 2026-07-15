# ADR-0002: Shared Riot HTTP client

- **Status:** Accepted
- **Date:** 2026-07-13

## Context

Before the refactor, `SummonerService`, `SpectatorService`, `MatchService`, and
`RiotAccountService` each hand-rolled the same plumbing: a private
`createPlatformClient()`, the `X-RIOT-TOKEN` header constant, `@Value("${riot.apiKey}")`,
and a near-identical `try/catch (HttpClientErrorException) → RiotApiException`. This was
copy-pasted 4+ times, and the match client was missing the auth header entirely.

## Decision

All HTTP/auth/error plumbing collapses into one `@Component`,
`com.wkaiser.riotapimcpserver.shared.http.RiotApiClient`, which exposes pre-configured
clients:

```java
RestClient regional(RiotApiRegionUri region);   // account, match
RestClient platform(RiotApiPlatformUri platform); // summoner, spectator
```

Each returns a `RestClient` with the `X-RIOT-TOKEN` header set from
`RiotApiProperties.getApiKey()`, the base URL assembled from the region/platform host
(or `RiotApiProperties.getBaseUrlOverride()` when set, for tests), and a
`defaultStatusHandler(HttpStatusCode::isError, …)` that throws
`RiotApiException(message, statusCode)` for any non-2xx response.

Typed config replaces every scattered `@Value("${riot.apiKey}")` with a single
`@ConfigurationProperties(prefix = "riot")` `RiotApiProperties` (`apiKey`, `region`
defaulting to `AMERICAS`, nullable `baseUrlOverride`). `application.yml` reads
`riot.apiKey: ${RIOT_API_KEY:}` for 12-factor portability.

Outbound adapters inject `RiotApiClient` and only make calls. Context-specific error
rules stay in the adapter: the spectator adapter maps `404 → null` (not in game) by
catching `RiotApiException` and checking `getStatusCode() == 404`, distinct from the
shared handler (see [gotchas](../gotchas.md)).

## Consequences

- One place to change auth, base-URL assembly, or error mapping.
- Match requests are now correctly authenticated (a latent bug fixed by construction).
- Adapters are trivially testable against a local mock server via `baseUrlOverride`
  (see [ADR-0003](ADR-0003-wiremock-testing.md) and
  [patterns/add-an-adapter-test.md](../patterns/add-an-adapter-test.md)).
- ArchUnit forbids `RestClient` references outside `..adapter.out.riot..`, keeping the
  centralization honest ([ADR-0004](ADR-0004-archunit-enforcement.md)).
