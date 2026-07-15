# Pattern: Add an MCP tool

Use this to expose an application service method to AI models as an MCP tool. Tools are
the inbound adapters of the hexagon ([ADR-0001](../decisions/ADR-0001-hexagonal.md)):
they live in `<context>/adapter/in/mcp/`, are annotated `@McpTool`, and call the
application service directly (there is no inbound-port interface).

## 1. Location and class

A tool is a `@Component` named `<Name>Tool` in
`com.wkaiser.riotapimcpserver.<context>.adapter.in.mcp`. It depends on the application
`<Name>Service`, never on a port or `RestClient`. Existing tools to copy from:
`SummonerTool`, `LiveGameTool`, `RiotAccountTool`, `AnalyticsTool`.

## 2. The annotation

`@McpTool` and `@McpToolParam` come from `org.springframework.ai.mcp.annotation`. Tool
`name`s are stable and snake_case (e.g. `get_lol_summoner_by_name`,
`get_current_game_by_summoner_name`). Every `@McpToolParam` needs a `description`;
platform/region are passed as `String` and parsed to the enum inside the method.

```java
package com.wkaiser.riotapimcpserver.<context>.adapter.in.mcp;

import com.wkaiser.riotapimcpserver.<context>.application.<Name>Service;
import com.wkaiser.riotapimcpserver.<context>.domain.<Name>;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiPlatformUri;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class <Name>Tool {

    private final <Name>Service <context>Service;

    @McpTool(name = "get_<name>_by_id",
            description = "Get <Name> information by id")
    public <Name> get<Name>ById(
            @McpToolParam(description = "The Riot platform, e.g. NA1, EUW1", required = true) String platformStr,
            @McpToolParam(description = "The <name> id", required = true) String id) {
        log.info("MCP Tool - Fetching <name> {} on {}", id, platformStr);
        RiotApiPlatformUri platform = RiotApiPlatformUri.valueOf(platformStr.toUpperCase());
        return <context>Service.get<Name>ById(platform, id);
    }
}
```

## 3. Discovery

Tools are auto-discovered by Spring AI's `@McpTool` annotation scanner — no manual
registration. The `@Component` must be in a package scanned by the application (any
subpackage of `com.wkaiser.riotapimcpserver`). See
[gotchas](../gotchas.md) for the discovery pitfalls (missing `@Component`, tool
outside `adapter.in.mcp`, duplicate `name`).

## 4. Verify

- ArchUnit ([ADR-0004](../decisions/ADR-0004-archunit-enforcement.md)) requires
  `@McpTool` only in `..adapter.in.mcp..` and the `*Tool` name — run the
  `check-architecture` skill.
- Confirm the tool name is unique and unchanged if you moved an existing tool.

## 5. Persist

Record anything reusable per the
[hydrate/persist protocol](../README.md#hydrate--persist-protocol).
