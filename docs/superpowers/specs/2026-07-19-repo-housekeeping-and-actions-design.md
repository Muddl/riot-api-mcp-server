# Repository housekeeping + Claude Code Actions — design

- **Date:** 2026-07-19
- **Status:** Approved (brainstorming), pending implementation plan
- **Scope:** A reusable "housekeeping" definition for keeping docs/KB/roadmap/skills/agents trim
  and current; a weekly automated run of it; a real fix for the no-op PR reviewer; and a metered-cost
  audit of everything that touches `ANTHROPIC_API_KEY`.

## Problem

Two recurring maintenance needs have no home:

1. **Docs, roadmap, knowledge base, skills, and agents drift.** They are maintained ad hoc, so
   staleness accumulates between feature cycles. Confirmed live examples at authoring time:
   - `CLAUDE.md` still says the LoL server exposes **six** tools; the roadmap records the surface
     grew to **13** in sub-project 1b.
   - `docs/knowledge/README.md`'s ADR list stops at **ADR-0013**, but **ADR-0014** exists on disk.

   There is no reusable, context-carrying procedure for catching this, so each pass re-derives what to
   check.

2. **Claude Code Actions is not doing useful work.** The PR reviewer (`claude-code-review.yml`) runs
   green on every PR but **never posts a review or comment** — verified against run `29673932693`
   (PR #55): Claude's own execution summary is `"num_turns": 2, "total_cost_usd": 0.157`, the action's
   posting step logs `No buffered inline comments`, and the workflow token grants only
   `PullRequests: read`. It spins, costs ~$0.16 of subscription usage per PR, and produces nothing.

Separately, a **cost audit** of `ANTHROPIC_API_KEY` usage is wanted, "especially for repetitive tasks
like the live eval reg tests and the newly created PR reviewers and scheduled cleanup."

## Key reframing: two credential buckets

Cost optimization hinges on a distinction the current wiring already encodes (see
[ADR-0012](../../knowledge/decisions/ADR-0012-live-eval-harness.md)):

| Credential | Billing | Consumed by |
|---|---|---|
| `ANTHROPIC_API_KEY` | **Metered** (pay-per-token) | `live-eval.yml` **only** |
| `CLAUDE_CODE_OAUTH_TOKEN` | **Flat** (Claude subscription seat) | `claude.yml`, `claude-code-review.yml`, and the new `housekeeping.yml` |

Consequence: the PR reviewer and the scheduled cleanup do **not** spend metered tokens as long as they
stay on the OAuth token — so "optimize their `ANTHROPIC_API_KEY` cost" is a non-goal. Their lever is
**invocation frequency** (conserves subscription rate-limit, not dollars). Only `live-eval` spends
metered tokens, and that is where the cost audit actually bites.

## Design

### 1. The `/housekeeping` skill — the reusable definition

A project skill at `.claude/skills/housekeeping/SKILL.md`, invoked by both a human (`/housekeeping`)
and the weekly Action headlessly. It carries the audit checklist as durable context so it survives
across sessions. Two modes:

- `--audit-only` — report findings, make no edits.
- default — apply the trimming/drift fixes.

**Checklist (what a pass verifies):**

- **Doc ↔ code drift** — tool counts, context/package names, and transport details in `README.md`,
  `ARCHITECTURE.md`, `CLAUDE.md`, and `CONTRIBUTING.md` against the actual source (e.g. the "six
  tools" staleness).
- **KB integrity** — every ADR on disk is listed in `docs/knowledge/README.md` (ADR-0014 is currently
  missing), ADR numbering is contiguous, superseded ADRs are marked `Superseded by …`, and every
  relative markdown link resolves.
- **Roadmap freshness** — the status table matches reality, dates are absolute, and deferred items are
  still true (e.g. the "Claude Code Actions integration rework" row, which this effort closes).
- **Skills & agents fit-for-purpose** — the skills and agents still reference real files, commands,
  and tool names; descriptions are accurate; nothing is stale.
- **CHANGELOG ↔ versions** — consistency between module versions and changelog entries.

**Explicitly hands-off:** `docs/superpowers/specs/` and `docs/superpowers/plans/` are dated, immutable
history — the skill never edits or trims them, matching the repo's own rule that specs are snapshots
of a decision at a moment.

**Reuse, don't duplicate:** the skill leans on the existing `docs-maintainer` agent for the
doc-writing/persist mechanics rather than restating them.

### 2. Weekly housekeeping Action → PR

New `.github/workflows/housekeeping.yml`:

- **Triggers:** `schedule` (weekly) + `workflow_dispatch`.
- **Commit gate:** run the skill only if commits landed on `master` since the last housekeeping run;
  otherwise exit clean. (Keeps quiet weeks free.)
- **Auth:** `CLAUDE_CODE_OAUTH_TOKEN` (flat-rate; not metered).
- **Output:** applies fixes on a `chore/housekeeping-YYYY-WW` branch and **opens a PR** for review.
  Nothing merges without a human.
- **Permissions:** `contents: write` + `pull-requests: write` (to branch and open the PR).

### 3. Fix the PR reviewer so it reviews and posts

Root cause is twofold (both must be fixed):

1. **No postable output.** `/code-review:code-review <PR>` as the prompt yields a ~2-turn run that
   emits nothing into the action's inline-comment buffer (`No buffered inline comments`). Replace it
   with the current supported automated-review configuration for `anthropics/claude-code-action@v1`:
   a direct review prompt plus `claude_args` granting the review/comment tools, so findings are
   actually produced and posted inline. **Exact prompt + tool-allow syntax will be pinned against the
   action's own docs during implementation (WebFetch), not guessed here.**
2. **Can't post.** Grant `pull-requests: write` on `claude-code-review.yml`, and
   `pull-requests: write` + `issues: write` on `claude.yml` (`@claude`) so it can respond on PRs and
   issues.

**Verification is part of "done":** open a throwaway PR after the change and confirm a real review
lands. A green run is not sufficient evidence — that is exactly the failure mode being fixed.

### 4. Metered-cost changes (all in `live-eval.yml`)

Current baseline is already lean: **Haiku 4.5** ($1/$5 per 1M — cheapest tier) for agent and judge,
`max_concurrency: 1`, `max_iterations: 4`, `retry_failed: false`. Keep all of that.

- **Keep the full agentic eval on both transports** (stdio + sse) — decision recorded; maximum
  redundancy retained.
- **Path-filter the `push` trigger** so docs-only / eval-only / housekeeping merges — which do not
  change server behavior — do not spend a full metered run. Keep `workflow_dispatch`.
- **Prompt caching is deliberately not pursued for this workload**, and this is a considered call, not
  an omission: each eval test is a short, independent ≤4-iteration conversation, the cacheable prefix
  (system + 13 small tool schemas) very likely falls under Haiku's **4096-token** minimum cacheable
  prefix (so `cache_control` would silently no-op), and it is unclear the `mcp-eval` harness even
  exposes cache breakpoints. If revisited, it must be validated empirically
  (`usage.cache_read_input_tokens > 0`) before any effort is spent.
- **Optional, not included by default:** a weekly `schedule` on `live-eval` so the canaries still catch
  Riot-side drift during commit-quiet weeks. Left out unless requested, since it adds runs.

### 5. This session dogfoods the skill

After the plan, implementation runs `/housekeeping` to fix today's real drift (the "six tools"
staleness, the missing ADR-0014 link, and whatever else the pass surfaces). This both cleans the repo
now and validates that the skill's checklist is correct.

## Net change set

| Artifact | Change |
|---|---|
| `.claude/skills/housekeeping/SKILL.md` | **New** — the reusable audit definition (2 modes). |
| `.github/workflows/housekeeping.yml` | **New** — weekly, commit-gated, opens a PR; OAuth auth. |
| `.github/workflows/claude-code-review.yml` | Real review prompt + `claude_args` tools; `pull-requests: write`. |
| `.github/workflows/claude.yml` | `pull-requests: write` + `issues: write`. |
| `.github/workflows/live-eval.yml` | Path-filter the `push` trigger (keep full × both transports). |
| Docs/KB (drift fixes) | Applied by dogfooding `/housekeeping` this cycle. |
| `docs/knowledge/decisions/ADR-00NN-*.md` | **New** ADR recording the housekeeping-automation decision. |
| `docs/knowledge/roadmap.md` | Close the "Claude Code Actions integration rework" deferred row. |

## Standing constraints (unchanged)

- The offline test suite remains the pre-merge gate and needs no keys; nothing here introduces
  key-requiring tests.
- The two credential buckets stay separate (`CLAUDE_CODE_OAUTH_TOKEN` for the interactive/housekeeping
  Actions; `ANTHROPIC_API_KEY` for the live eval), per ADR-0012.

## Out of scope

- Moving any interactive Action onto the metered `ANTHROPIC_API_KEY` (they stay on the flat seat).
- Editing dated specs/plans as part of housekeeping (they are immutable history).
- Prompt caching in the eval harness (deferred, gated on empirical evidence — see §4).
