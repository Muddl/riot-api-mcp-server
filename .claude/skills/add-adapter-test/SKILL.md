---
name: add-adapter-test
description: Write a WireMock test for an outbound Riot*Adapter that asserts request URL, the X-RIOT-TOKEN header, JSON to DTO parsing, and error mapping (including spectator 404 to null) with no live API key. Use when testing an outbound adapter in riot-api-mcp-server.
---

# Add an adapter test

Operationalizes `docs/knowledge/patterns/add-an-adapter-test.md`.

## Hydrate first

Read `docs/knowledge/README.md`, `docs/knowledge/decisions/ADR-0003-wiremock-testing.md`,
and the spectator note in `docs/knowledge/gotchas.md`.

## Steps

1. Create `Riot<Name>AdapterTest` in the same package as the adapter
   (`<context>/adapter/out/riot`), under `src/test/java/...`.
2. In `@BeforeEach`: start a `WireMockServer(options().dynamicPort())`; build a
   `RiotApiProperties` with a fake `apiKey` and
   `setBaseUrlOverride("http://localhost:" + wireMock.port())`; construct the adapter
   with `new RiotApiClient(props)`.
3. Write, at minimum, these assertions (write the failing test first, TDD):
   - path/query is correct;
   - `X-RIOT-TOKEN` header equals the fake key;
   - a 2xx JSON body parses into the DTO;
   - a `4xx/5xx` throws `RiotApiException` with the status preserved.
4. For the spectator adapter, add a case: stub `404`, assert the method returns `null`.
5. Put large JSON bodies in `src/test/resources/fixtures/`.
6. `@AfterEach`: `wireMock.stop()`.

WireMock (`org.wiremock:wiremock-standalone:3.9.2`) is already a `testImplementation`
dependency — do not add it again.

## Persist

Update `gotchas.md`/`patterns/` if you find a reusable testing wrinkle.
