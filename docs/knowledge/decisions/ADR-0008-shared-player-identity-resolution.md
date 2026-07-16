# ADR-0008: Shared player-identity resolution

- **Status:** Accepted
- **Date:** 2026-07-16

## Context

Every Riot game server needs to turn a caller-supplied player reference — a `GameName#TAG` Riot ID
or a raw PUUID — into a PUUID before it can call a game endpoint. If that logic lived in each game's
contexts, every server (LoL, TFT, Valorant, LoR) would re-implement it, and each player-keyed tool
call would cost an extra Riot request. account-v1 is already cross-game and already lives in
`riot-account-core`.

## Decision

**`PlayerIdentityResolver` lives in `riot-account-core`.** It accepts a `GameName#TAG` or a raw
PUUID (disambiguated on the `#`) and returns a PUUID. Registered via the module's existing
auto-configuration, so a server gets it by declaring the dependency and nothing else.

**It returns a plain PUUID string, not a `RiotAccount`.** This is load-bearing. The account **domain**
stays confined (only `analytics` and the account tool may touch it, ArchUnit-enforced), while identity
resolution is the deliberately-open surface every context may use. If the resolver returned a
`RiotAccount`, every caller would touch the account domain through its return type and the confinement
would be defeated through the back door. Contexts that want account *data* still go through
`RiotAccountService` and remain subject to the deny-by-default list.

**The ArchUnit rule splits rather than widening.** `only_analytics_and_the_account_tool_use_the_
account_domain` confines `..riot.account..` but carves out `..riot.account.identity..`. Both halves
are controlled: a domain violation still fails the rule, and a legal resolver dependency provably does
not (see `HexagonalArchitectureNegativeControlTest`).

**Resolution is cached with Caffeine, bounded and TTL'd, on an injected `Ticker`.** The cache keys on
**Riot ID → PUUID**. PUUIDs are stable, so a raw PUUID needs no lookup; Riot IDs are **mutable**, so
`expireAfterWrite` (default 5 minutes) bounds staleness and `maximumSize` (default 10 000) bounds
memory. Caffeine's `Ticker` is injected so tests advance time by hand and never really wait. A failed
lookup is not cached (Caffeine's `get(key, loader)` stores nothing when the loader throws). This is
the first stateful component in the codebase.

**Why Caffeine rather than a hand-rolled cache.** The first draft of this work hand-rolled a
`synchronized LinkedHashMap` TTL cache (~30 lines). Caffeine replaced it deliberately: it is the
de-facto standard JVM cache, its version is managed by the Spring Boot BOM (a one-line dependency),
and its `Ticker` seam meets the deterministic-test requirement exactly as a hand-rolled injected clock
would. The hand-rolled version was correct-on-read but naive on eviction (insertion-order, not
recency/frequency) and carried a global lock — reimplementing, slightly worse, what a managed
dependency does correctly. "Don't roll your own cache" applies: the only thing hand-rolling bought was
one fewer dependency, and this repo already uses standard libraries (Spring, WireMock, ArchUnit,
Lombok, AssertJ) freely, so that saving did not justify the correctness and idiom cost. Caffeine stays
an **implementation detail** — it appears in the resolver's constructor and the auto-configuration,
never in `resolvePuuid`'s signature or any server's code.

## Consequences

**Makes easy:** a single `player` parameter on every tool across every server (the contract Plan C
and 1b build on), with the model never chaining account → summoner → match itself — which is both a
rate-limit multiplier and a common way models flail. One Riot call amortized across repeat lookups.

**Costs:** the first stateful thing to reason about, and one managed dependency (Caffeine).

**Watch for:**

- **Raising the TTL trades hit-rate for staleness.** Riot IDs change; the TTL is that trade made
  explicit. See `gotchas.md`.
- **Do not return `RiotAccount` from the resolver** — it reopens the confined domain through the back
  door.
- **Do not cache PUUID inputs or failed lookups** — the first needs no lookup, the second should
  re-check.
- **`maximumSize` is approximate and eviction is asynchronous** — do not write a test asserting exact
  size-based eviction without a same-thread executor; it will be flaky (see `gotchas.md`). TTL expiry
  on the injected ticker, by contrast, is deterministic.
