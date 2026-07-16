# Pattern: Add a bounded context

Use this when adding a new Riot API area as its own mini-hexagon. Rationale:
[ADR-0001](../decisions/ADR-0001-hexagonal.md) and
[ADR-0002](../decisions/ADR-0002-shared-riot-http-client.md).

Substitute `<context>` (lowercase, e.g. `champion`) and `<Name>` (PascalCase, e.g.
`Champion`) throughout. Base path: `<server-module>/src/main/java/com/muddl/riot/<game>/`
(e.g. `lol-mcp-server/src/main/java/com/muddl/riot/lol/` for the LoL server).

## 1. Create the package skeleton

```bash
ctx=<context>
base=lol-mcp-server/src/main/java/com/muddl/riot/lol/$ctx
mkdir -p $base/domain \
         $base/application/port \
         $base/adapter/in/mcp \
         $base/adapter/out/riot
```

(Omit `adapter/out/riot` for a composing context like `analytics`; omit
`adapter/in/mcp` for a context with no MCP tool, like `match`.)

## 2. Domain DTO — `<context>/domain/<Name>.java`

Plain Lombok DTO, **no framework imports**. Keep the established pattern (see
[gotchas](../gotchas.md) for the nested-builder rule):

```java
package com.muddl.riot.lol.<context>.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class <Name> {
    private String id;
    // ...fields matching the Riot JSON shape
}
```

## 3. Outbound port — `<context>/application/port/<Name>Port.java`

The architectural boundary. An interface, in `application.port`:

```java
package com.muddl.riot.lol.<context>.application.port;

import com.muddl.riot.lol.<context>.domain.<Name>;

public interface <Name>Port {
    <Name> get<Name>ById(String id);
}
```

## 4. Outbound adapter — `<context>/adapter/out/riot/Riot<Name>Adapter.java`

Injects `RiotApiClient`; makes calls only. Use `regional(...)` for region-routed
endpoints (account, match), `platform(...)` for platform-routed (summoner, spectator).
The `X-RIOT-TOKEN` header, base URL, and non-2xx → `RiotApiException` mapping are
already handled by `RiotApiClient` — do **not** re-implement them here.

```java
package com.muddl.riot.lol.<context>.adapter.out.riot;

import com.muddl.riot.lol.<context>.application.port.<Name>Port;
import com.muddl.riot.lol.<context>.domain.<Name>;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.http.RiotApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Riot<Name>Adapter implements <Name>Port {

    private final RiotApiClient riotApiClient;

    @Override
    public <Name> get<Name>ById(String id) {
        return riotApiClient.platform(RiotApiPlatformUri.NA1).get()
                .uri("/lol/<area>/v4/<name>/{id}", id)
                .retrieve()
                .body(<Name>.class);
    }
}
```

## 5. Application service — `<context>/application/<Name>Service.java`

Pure logic; depends on the port, never on `RestClient` or an adapter:

```java
package com.muddl.riot.lol.<context>.application;

import com.muddl.riot.lol.<context>.application.port.<Name>Port;
import com.muddl.riot.lol.<context>.domain.<Name>;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class <Name>Service {

    private final <Name>Port <context>Port;

    public <Name> get<Name>ById(String id) {
        log.info("Fetching <name> for id: {}", id);
        return <context>Port.get<Name>ById(id);
    }
}
```

## 6. (Optional) inbound MCP tool

If the context is user-facing, add a tool per
[add-an-mcp-tool.md](add-an-mcp-tool.md).

## 7. Tests

- Adapter: WireMock test per [add-an-adapter-test.md](add-an-adapter-test.md).
- Service: a fast test with a hand-written in-memory `<Name>Port` fake
  ([ADR-0003](../decisions/ADR-0003-wiremock-testing.md)).

## 8. Verify against the architecture rules

Run the `check-architecture` skill (or `./gradlew test`). ArchUnit
([ADR-0004](../decisions/ADR-0004-archunit-enforcement.md)) enforces the naming
(`*Service`/`*Tool`/`*Adapter`/`*Port`), the package placement, and the inward-only
dependency rule.

## 9. Persist

If you learned something reusable, update the KB per the
[hydrate/persist protocol](../README.md#hydrate--persist-protocol).
