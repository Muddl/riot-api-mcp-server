# ADR-0006: Monorepo of per-game MCP servers over a shared core

- **Status:** Accepted
- **Date:** 2026-07-15

## Context

The project set out to reach full Riot API parity. Across LoL, TFT, Valorant, and LoR that is
~60–70 endpoints, and one `@McpTool` per endpoint means ~65 tools in a single server — past the
point where MCP clients pick tools reliably, and paid for in context on every request.

An intermediate design kept one server and gated tools with `@ConditionalOnProperty` "game packs"
so the default surface stayed small. The gating was simulating a boundary that already existed:
installing the TFT server *is* the opt-in. Other forces pointed the same way — Valorant needs a
third host-routing scheme and a production API key that a dev key cannot substitute for, and
neither concern should reach the LoL server.

## Decision

Restructure as a Gradle monorepo: `riot-api-core` (HTTP, routing, errors) and
`riot-account-core` (the cross-game account context) as libraries, with one Spring Boot MCP
server per game depending on both. Libraries are consumed via `@AutoConfiguration`, never
component-scanned.

`riot-account-core` deliberately ships **no** `@McpTool`. account-v1 is cross-game, so a tool
there would appear in every installed server and collide by name inside the client. Each server
owns a thin inbound adapter instead, which also lets tool names be namespaced per game.

## Consequences

**Makes easy:** each server's tool surface is naturally small, so no gating machinery. The
dependency rule is enforced by Gradle at compile time rather than by an ArchUnit test — core
cannot depend on a game context because it has no dependency on one. Valorant's key-tier
weirdness stays in the Valorant module. Adding a game is a new module, not a new set of
conditionals.

**Costs:** every file moved and the package root was renamed (`com.wkaiser.riotapimcpserver` was
a server name doing a library's job). Per-module build wiring, now centralized in a `buildSrc`
convention plugin. One Docker image per server rather than one for the repo.

**Watch for:** `riot-api-core` becoming a junk drawer. It holds plumbing only — the account
context is a separate module precisely to keep domain out of it. Deferred deliberately, because a
monorepo makes them cheap to revise later: rate limiting, error-message quality, Bearer/RSO auth,
and a generalized host-routing abstraction (Valorant forces the last one; TFT reuses LoL's hosts).

**Asymmetry to know about:** each server's `account` package holds a tool with no domain or
application layer of its own — those live in the library. That is intentional; the alternative is
duplicating the account domain per server to preserve a shape.
