---
name: scaffold-bounded-context
description: Scaffold a new Riot bounded context as a mini-hexagon (domain, application + port, in/mcp and out/riot adapters) following the project's hexagonal architecture. Use when adding a new Riot API area to riot-api-mcp-server.
---

# Scaffold a bounded context

Operationalizes `docs/knowledge/patterns/add-a-bounded-context.md`.

## Hydrate first

Read `docs/knowledge/README.md`, `docs/knowledge/gotchas.md`, and
`docs/knowledge/decisions/ADR-0001-hexagonal.md` +
`ADR-0002-shared-riot-http-client.md`.

## Steps

1. Ask for `<context>` (lowercase) and `<Name>` (PascalCase), and whether it is
   user-facing (needs an MCP tool) and/or composing (no outbound adapter).
2. Create the package skeleton under
   `lol-mcp-server/src/main/java/com/muddl/riot/lol/<context>/` (or the equivalent path in
   another game server module):
   `domain/`, `application/port/`, `adapter/in/mcp/` (if user-facing),
   `adapter/out/riot/` (unless composing).
3. Generate, from the pattern guide's templates:
   - `domain/<Name>.java` — Lombok DTO, no framework imports (mind the nested-builder
     gotcha).
   - `application/port/<Name>Port.java` — interface in `application.port`.
   - `adapter/out/riot/Riot<Name>Adapter.java` — `@Component` injecting `RiotApiClient`;
     `regional(...)` for account/match-style routing, `platform(...)` for
     summoner/spectator-style routing. Never re-implement auth/error handling.
   - `application/<Name>Service.java` — `@Service`, depends on the port only.
   - Optionally `adapter/in/mcp/<Name>Tool.java` (delegate to the add-mcp-tool skill).
4. Add tests: a WireMock adapter test (delegate to add-adapter-test) and a
   port-fake service test.
5. Run the check-architecture skill so ArchUnit validates naming and layering.

## Persist

If you discovered a reusable step or pitfall, update `patterns/` or `gotchas.md` per the
hydrate/persist protocol before finishing.
