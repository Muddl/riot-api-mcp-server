# ADR-0003: WireMock + port-fake testing

- **Status:** Accepted
- **Date:** 2026-07-13

## Context

Originally, integration tests were `@Disabled` because they required live Riot API
keys, and a `CompilationVerificationTest` existed solely to guard Lombok builder wiring.
There was no HTTP-level mocking, so the adapters' real behavior (URL assembly, auth
header, JSON→DTO parsing, error mapping) was untested and CI could not exercise it
without a secret.

## Decision

Two complementary test styles, both runnable in CI with **no** `RIOT_API_KEY`:

- **Outbound adapter tests (WireMock).** Each `Riot*Adapter` is tested against a local
  WireMock server. `RiotApiProperties.setBaseUrlOverride("http://localhost:<port>")`
  points the real `RestClient` at WireMock. Tests assert the request URL, the
  `X-RIOT-TOKEN` header, JSON→DTO parsing, and error mapping — including spectator
  `404 → null` and other `4xx/5xx → RiotApiException` with the status preserved. Canned
  JSON fixtures live in `src/test/resources/fixtures/`. Dependency:
  `org.wiremock:wiremock-standalone:3.9.2` (`testImplementation`).
- **Application-service tests (port fakes).** Hand-written in-memory fakes implement the
  port interfaces — fast, no HTTP. `AnalyticsService` is tested with fake
  account/summoner/match collaborators, covering the edge cases (zero games; zero-deaths
  KDA).

Cleanup: remove all `@Disabled`; delete `CompilationVerificationTest` (real tests now
cover what it proxied).

## Consequences

- `./gradlew build` passes offline with no key — a hard requirement for CI and for a
  reviewer cloning the repo.
- The port interface is the seam that makes both styles cheap.
- Adds one test dependency (WireMock); no production dependency.
- To write an adapter test, follow
  [patterns/add-an-adapter-test.md](../patterns/add-an-adapter-test.md).
