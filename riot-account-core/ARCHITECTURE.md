# `riot-account-core` — Architecture

The one cross-game **domain** context, extracted into its own library. It is a bounded context, not
infrastructure — which is exactly why it is its own module rather than part of `riot-api-core`. The
shared bounded-context hexagon is described at the [repository root](../ARCHITECTURE.md); this
document covers only what is specific to the account library.

## Internal layout

```
com.muddl.riot.account
├── domain/            RiotAccount
├── application/       RiotAccountService
├── application/port/  RiotAccountPort
├── adapter/out/riot/  RiotAccountRiotAdapter
├── identity/          PlayerIdentityResolver  (the open, cross-cutting surface)
└── config/            RiotAccountAutoConfiguration
```

There is **no `adapter/in/mcp/`** here, by design (below).

## The identity resolver

`PlayerIdentityResolver.resolvePuuid(String)` disambiguates its argument on the `#`: a `GameName#TAG`
is resolved through account-v1; a raw PUUID passes through untouched. Because Riot IDs are mutable
but PUUIDs are stable, it caches `Riot ID → PUUID` in a bounded, TTL-expiring Caffeine cache on an
**injected ticker** (so tests are deterministic and fast — no real sleeps). This is the first
stateful thing in the codebase: bounded size, TTL, injected clock, no cross-request assumptions. See
[ADR-0008](../docs/knowledge/decisions/ADR-0008-shared-player-identity-resolution.md).

It returns a **plain PUUID string, not a `RiotAccount`** — a deliberate choice. If it returned an
account, every context that resolved a player would touch the account domain through the return type,
defeating the confinement rule through the back door.

## Two altitudes, one library

This library holds two things the rest of the monorepo treats differently:

- **The account domain** (`RiotAccount`, `RiotAccountService`) — genuinely a bounded context.
  **Deny-by-default:** only a server's `analytics` and `account` contexts may depend on it.
- **Identity resolution** (`PlayerIdentityResolver`) — deliberately cross-cutting; every
  player-keyed context is *supposed* to depend on it.

A server enforces this split with a single ArchUnit rule that confines `..riot.account..` but
excludes `..riot.account.identity..` — see the root
[Enforcement](../ARCHITECTURE.md#enforcement) section and, in `lol-mcp-server`, the negative-control
test that proves both halves still bite.

## No `@McpTool`

Enforced by `AccountArchitectureTest`'s `no_mcp_tools_in_this_library`. account-v1 is cross-game: a
tool declared here would appear, identically named, in every installed game server and collide inside
the MCP client. Each server owns a thin inbound `account` adapter of its own instead.
