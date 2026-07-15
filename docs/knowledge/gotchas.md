# Gotchas

Sharp edges specific to this codebase. Append new ones at the bottom, newest last, each
as its own small section (per the
[hydrate/persist protocol](README.md#hydrate--persist-protocol)).

## Lombok nested `@Builder` static classes need `@NoArgsConstructor` + `@AllArgsConstructor`

Every DTO uses `@Data @Builder @NoArgsConstructor @AllArgsConstructor` (with
`@JsonIgnoreProperties(ignoreUnknown = true)` where the shape is a Riot response). The
**inner static classes** must carry the same four annotations. If a nested `@Builder`
class omits `@NoArgsConstructor` and `@AllArgsConstructor`, Lombok generates a
conflicting all-args constructor and the builder wiring breaks `:compileJava` /
`:compileTestJava` (the class that historically bit us is `Perks` in
`spectator/domain`). Symptom: "constructor … cannot be applied" or a missing builder
method at compile time. Fix: put all four annotations on the nested class too. Jackson
also needs the no-args constructor to deserialize.

## Spectator `404` means "not in a game" — map it to `null`, not an exception

`RiotApiClient` throws `RiotApiException` for **any** non-2xx response. The Spectator API
returns `404` when a summoner is simply not currently in a game — a normal outcome, not
an error. `RiotSpectatorAdapter.getCurrentGameInfo(...)` therefore catches
`RiotApiException`, and when `getStatusCode() == 404` returns `null`; any other status is
rethrown. This rule lives in the adapter (context-specific), **not** in the shared
`RiotApiClient` status handler. When you test it, stub a `404` and assert `null`.

## Region vs platform routing — do not mix them up

Two different host schemes, two different `RiotApiClient` entry points:

- **Region** (`RiotApiRegionUri`: `AMERICAS`, `ASIA`, `EUROPE`, `SEA`, hosts like
  `americas.api.riotgames.com`) routes **account** and **match** endpoints. Use
  `riotApiClient.regional(region)`.
- **Platform** (`RiotApiPlatformUri`: `NA1`, `EUW1`, `KR`, … hosts like
  `na1.api.riotgames.com`) routes **summoner** and **spectator** endpoints. Use
  `riotApiClient.platform(platform)`.

Calling a match endpoint on a platform host (or a summoner endpoint on a region host)
returns 404/wrong data. See [glossary](glossary.md#platform-vs-region).

## `@McpTool` discovery

MCP tools are auto-discovered by Spring AI's annotation scanner — there is no manual
registry. For a tool to appear:

- the class must be a Spring bean (`@Component`) in a scanned package under
  `com.wkaiser.riotapimcpserver`;
- `@McpTool`/`@McpToolParam` must be imported from
  `org.springframework.ai.mcp.annotation`;
- each `@McpTool` `name` must be **unique** across the whole app (a duplicate silently
  shadows or fails discovery);
- by the architecture rules, `@McpTool` may appear **only** in `..adapter.in.mcp..`
  (ArchUnit fails the build otherwise — see
  [ADR-0004](decisions/ADR-0004-archunit-enforcement.md)).

If a new tool does not show up, check the bean annotation and the package first.
