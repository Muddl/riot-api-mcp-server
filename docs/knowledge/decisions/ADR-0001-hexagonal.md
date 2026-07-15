# ADR-0001: Bounded-context hexagonal architecture

- **Status:** Accepted
- **Date:** 2026-07-13
- **Amended by:** [ADR-0006](ADR-0006-monorepo-split.md) — package roots are now
  `com.wkaiser.riot.{core,account,lol}`; this ADR's decision is unchanged.

## Context

The project began as package-by-feature under `com.wkaiser.riotapimcpserver.riot.*`
(`riot/account`, `riot/lol/{summoner,match,analytics,spectator}`). Application logic,
HTTP calls, and MCP wiring were entangled in the same service classes. As a portfolio
piece the code needs to *demonstrate* clean architecture, not just work.

## Decision

Each Riot context (`account`, `summoner`, `match`, `spectator`, `analytics`) becomes a
self-contained mini-hexagon as a **top-level** package under
`com.wkaiser.riotapimcpserver`:

```
<context>/
  domain/                       relocated Lombok DTOs (no framework deps)
  application/
    <Name>Service               pure logic, depends on its port
    port/<Name>Port             outbound port interface (the boundary)
  adapter/
    in/mcp/<Name>Tool           @McpTool inbound adapter
    out/riot/Riot<Name>Adapter  RestClient adapter implementing the port
```

`analytics` is a **composing** context: it has no outbound Riot adapter; its
`AnalyticsService` depends on the `account`, `summoner`, and `match` application
services. MCP tools call application services directly — there is deliberately no
inbound-port interface (ceremony for a showcase of this size).

**The dependency rule (enforced by [ADR-0004](ADR-0004-archunit-enforcement.md)):**
`adapter → application → domain`, inward only. `domain` depends on nothing outward and
on no framework. `application` depends on `domain` and its own `port` only, never on
`adapter`. Only `adapter/out/riot` knows `RestClient`; only `adapter/in/mcp` knows
`@McpTool`. Cross-context references are forbidden **except** `analytics` depending on
other contexts' application services.

Per YAGNI, DTOs are **relocated, not split** — no separate wire-vs-domain models and no
anti-corruption layer, because the Riot API is read-only. DTOs keep the established
Lombok pattern (see [gotchas](../gotchas.md)).

## Consequences

- A reviewer can read one context top-to-bottom and see ports/adapters in action.
- The port interface is a natural seam for tests: WireMock for adapters, in-memory
  fakes for services (see [ADR-0003](ADR-0003-wiremock-testing.md)).
- More packages/files per context than a flat layout — accepted for clarity.
- The rules are only worth having if enforced, hence ArchUnit
  ([ADR-0004](ADR-0004-archunit-enforcement.md)).
- To add a context, follow [patterns/add-a-bounded-context.md](../patterns/add-a-bounded-context.md).
