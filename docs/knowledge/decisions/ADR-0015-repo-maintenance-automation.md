# ADR-0015 — Repository maintenance automation

- **Status:** Accepted
- **Date:** 2026-07-19

## Context

Docs, KB, roadmap, skills, and agents drifted between feature cycles with no reusable procedure to
catch it (e.g. `CLAUDE.md` said "six tools" long after the surface reached 13; ADR-0014 was missing
from the KB index). Separately, the PR reviewer (`claude-code-review.yml`) ran green on every PR but
posted nothing: it used a plugin slash-command prompt with no comment tools and `pull-requests:
read`, so Claude buffered no comments and could not post — ~$0.16 of subscription usage per PR for
zero output.

## Decision

- A project skill, `/housekeeping`, is the single reusable definition for the maintenance pass
  (doc↔code drift, KB integrity, roadmap freshness, skill/agent fitness, changelog↔versions), with
  `docs/superpowers/specs|plans` treated as immutable history.
- A weekly, commit-gated `housekeeping.yml` runs that skill on the flat-rate `CLAUDE_CODE_OAUTH_TOKEN`
  seat and opens a PR — never merging unreviewed.
- The PR reviewer and `@claude` workflows are moved to the action's canonical posting form with
  `pull-requests: write` (and `issues: write` for `@claude`).
- Metered cost is scoped to `live-eval` (the only `ANTHROPIC_API_KEY` consumer): a `paths-ignore`
  filter skips no-op doc/skill merges; the full eval on both transports is retained. Prompt caching
  was evaluated and rejected for this workload (short independent Haiku conversations under the
  4096-token cacheable minimum).

## Consequences

- Maintenance is repeatable and partly automated; the weekly PR keeps drift bounded.
- The reviewer now produces real, reviewable feedback; verification requires observing a live PR, not
  a green run.
- The two credential buckets stay separated (ADR-0012): interactive/housekeeping on the flat seat,
  eval on the metered key. Moving any interactive Action onto the metered key is explicitly out of
  scope.
