# ADR-0007: Core hardening boundary — what `riot-api-core` is allowed to know

- **Status:** Accepted
- **Date:** 2026-07-16

## Context

`riot-api-core` is the shared HTTP kernel every game server inherits. Sub-project 1a hardens it
twice: automatic retry on HTTP 429, and an actionable error taxonomy. Both must land without letting
the kernel learn about any game's domain — [ADR-0006](ADR-0006-monorepo-split.md) warned about core
becoming a junk drawer.

Two concrete defects motivated the work. Errors surfaced raw: `RiotApiException("Riot API error: " +
rawBody, status)` went straight to the model, and the intended consumer is a third party on their own
key. And a 429 was a hard failure, though Riot documents a `Retry-After` header that says exactly how
long to wait.

## Decision

**The boundary: `riot-api-core` knows HTTP and Riot's error protocol, never a game's domain.** No
game DTOs, no game routing beyond the existing platform/region enums.

**429 retry lives in a `ClientHttpRequestInterceptor`, not the status handler.** The handler throws,
so by the time it runs the decision to fail is made. The interceptor retries on 429 up to
`riot.max-retries` (default 3), waiting the response's `Retry-After` when present and
`riot.retry-backoff` (default 1s) otherwise. It reads only the status code and that one header —
never the body — so it does not consume the stream the status handler reads next. When attempts are
exhausted the final 429 flows through and maps to the taxonomy's message.

**This is reactive retry, not a proactive rate limiter.** Riot documents `Retry-After` on 429, so
reacting to it has a specification behind it. A token bucket needs a real design (per-method limits,
shared buckets across contexts, concurrency) and would be guessing at this stage — it stays deferred
(see the roadmap).

**The error taxonomy lives on `RiotApiException`.** `forStatus(status, rawBody)` sets an actionable,
status-derived message (403 explains the 24-hour dev-key expiry; 404/429/503 each get their own),
keeps the raw body on `getRawBody()`, and is the single home of the status→message table.

**Backoff timing is injected (`BackoffSleeper`).** The retry path is deterministic and the suite
never really sleeps.

## Consequences

**Makes easy:** every future server inherits correct 429 handling and actionable errors for free.
Message wording lives in one place. Tests are fast and deterministic.

**Costs:** one more collaborator on `RiotApiClient` (the sleeper) and two more config knobs.

**Watch for:**

- **The interceptor must never read the response body** — doing so consumes the stream the status
  handler needs, and the failure is a confusing empty/failed error mapping rather than a loud one.
- **`Retry-After` is delta-seconds.** Riot sends an integer count of seconds; anything else falls back
  to the default backoff.
- **Junk-drawer pressure.** Any temptation to put a game DTO or game-specific routing here violates
  this ADR — that belongs in a server module.
