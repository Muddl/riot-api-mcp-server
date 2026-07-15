# Pattern: Add an adapter test (WireMock)

Use this to test an outbound `Riot*Adapter` against a local mock Riot server — no live
key, runs in CI. Rationale: [ADR-0003](../decisions/ADR-0003-wiremock-testing.md).
WireMock (`org.wiremock:wiremock-standalone:3.9.2`) is already a `testImplementation`
dependency.

## What to assert

1. The request path/query is what the Riot endpoint expects.
2. The `X-RIOT-TOKEN` header carries the configured key.
3. A 2xx JSON body parses into the domain DTO.
4. Error mapping: a `4xx/5xx` becomes `RiotApiException` with the status preserved; the
   spectator adapter maps `404 → null` (not in game).

## Wiring

Point the real `RestClient` at WireMock via `RiotApiProperties.setBaseUrlOverride`, then
build the adapter with a real `RiotApiClient`:

```java
package com.wkaiser.riot.lol.<context>.adapter.out.riot;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.wkaiser.riot.lol.<context>.domain.<Name>;
import com.wkaiser.riot.core.config.RiotApiProperties;
import com.wkaiser.riot.core.enums.RiotApiPlatformUri;
import com.wkaiser.riot.core.http.RiotApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class Riot<Name>AdapterTest {

    private WireMockServer wireMock;
    private Riot<Name>Adapter adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        RiotApiProperties props = new RiotApiProperties();
        props.setApiKey("test-key-123");
        props.setBaseUrlOverride("http://localhost:" + wireMock.port());

        adapter = new Riot<Name>Adapter(new RiotApiClient(props));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void parses_body_and_sends_token_header() {
        stubFor(get(urlEqualTo("/lol/<area>/v4/<name>/abc"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"abc\"}")));

        <Name> result = adapter.get<Name>ById("abc"); // platform-routed adapters take a platform arg

        assertThat(result.getId()).isEqualTo("abc");
        verify(getRequestedFor(urlEqualTo("/lol/<area>/v4/<name>/abc"))
                .withHeader("X-RIOT-TOKEN", equalTo("test-key-123")));
    }
}
```

## Fixtures

Put larger canned JSON in `src/test/resources/fixtures/` and load it into the stub body
rather than inlining big literals.

## Spectator 404 → null

For the spectator adapter, stub a `404` and assert the method returns `null` (not a
thrown exception) — this is the one context-specific rule, handled in the adapter, not
the shared `RiotApiClient` handler. See [gotchas](../gotchas.md).

## Persist

Add reusable learnings per the
[hydrate/persist protocol](../README.md#hydrate--persist-protocol).
