---
name: add-mcp-tool
description: Add a Spring AI @McpTool inbound adapter that exposes an application service method to AI models, placed correctly in adapter/in/mcp with a unique tool name. Use when adding or moving an MCP tool in riot-api-mcp-server.
---

# Add an MCP tool

Operationalizes `docs/knowledge/patterns/add-an-mcp-tool.md`.

## Hydrate first

Read `docs/knowledge/README.md`, `docs/knowledge/gotchas.md` (the `@McpTool discovery`
section), and `docs/knowledge/decisions/ADR-0001-hexagonal.md`.

## Steps

1. Confirm the target `<context>` already has an `application/<Name>Service`. The tool
   depends on the service, never on a port or `RestClient`.
2. Create `<context>/adapter/in/mcp/<Name>Tool.java` as a `@Component`:
   - Import `@McpTool`/`@McpToolParam` from `org.springframework.ai.mcp.annotation`.
   - Give the method a snake_case, unique `name` (mirror existing names like
     `get_lol_summoner_by_name`).
   - Every `@McpToolParam` gets a `description`. Accept platform/region as `String` and
     parse via `RiotApiPlatformUri.valueOf(platformStr.toUpperCase())`.
   - Delegate to the service; log at info with an `MCP Tool -` prefix.
3. Do not register the tool anywhere — discovery is automatic via annotation scanning.
4. Run the check-architecture skill: ArchUnit requires `@McpTool` only inside
   `..adapter.in.mcp..` and enforces the `*Tool` name.

## Common failures

- Tool not discovered → missing `@Component`, wrong package, or a duplicate `name`
  (see gotchas).
- ArchUnit failure → `@McpTool` used outside `adapter.in.mcp`, or class not named
  `*Tool`.

## Persist

Record any new pitfall in `gotchas.md` per the hydrate/persist protocol.
