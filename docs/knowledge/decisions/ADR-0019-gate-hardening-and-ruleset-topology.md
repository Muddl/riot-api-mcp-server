# ADR-0019 — Gate hardening: ruleset topology, local-only application, and factory bounds

- **Status:** Accepted
- **Date:** 2026-08-01

## Context

Repository ruleset `8769144` ("one ruleset to rule them all") had been active since 2025-10-09
carrying `deletion`, `non_fast_forward`, and `pull_request{required_approving_review_count: 1}` —
with **no required status checks at all** and with
`{"actor_type": "RepositoryRole", "actor_id": 5, "bypass_mode": "exempt"}`. `exempt` is stronger than
`always`: rules are not evaluated for that actor and no bypass audit entry is written. So the one
human was invisibly exempt from the only rule that existed, nothing required `Build & verify` to be
green before merge, and the merge button was live on a red PR. Its `ref_name.include` was `~ALL`
rather than the default branch, so a hypothetical agent branch was governed by the same
PR-required rule as `master` — unreached only because the maintainer is exempt and `housekeeping.yml`
performs a single branch *creation*.

Separately, `ci.yml` could not pass on a fork PR at all: `GITHUB_TOKEN` is read-only for
`pull_request` from a fork regardless of `permissions:`, and both reporting steps wrote through it
with no `continue-on-error`. And no agent job had a timeout, a kill switch, or a failure alert.

## Decision

- **Fork PRs are fixed before any check becomes required.** Making `Build & verify` required while it
  failed on every outside contribution would permanently block contributions to a public portfolio
  repo. Reporting steps are non-fatal; the build step never is.
- **Two rulesets, identical bypass.** R1 (`~ALL`) keeps `deletion` + `non_fast_forward` —
  `non_fast_forward` stays repo-wide because F4 pushes revision rounds to live PR branches, where a
  force-push erases round evidence and outdates every inline comment. R2 (`~DEFAULT_BRANCH`) carries
  `pull_request{1 approval}` + `required_status_checks["Build & verify"]`. `8769144` is **updated in
  place (PUT)** into R1 so its id — and therefore the rollback command — stays stable; R2 is created
  first so `master` is never momentarily without an approval requirement.
- **Bypass stays identical on both.** GitHub documents that rules across rulesets are aggregated with
  "the most restrictive version of the rule applies" but documents **nothing** about how bypass is
  evaluated when several rulesets match a ref, and `enforcement: "evaluate"` (dry-run) is
  GitHub-Enterprise-only. An undocumented behaviour, no dry-run, and a sole maintainer who cannot
  approve his own PRs is the combination not to experiment on. The tighter `bypass_actors: []`
  variant is deferred, gated on testing layered bypass on a **throwaway probe repo**.
- **Rulesets are applied locally and verified by CI, never applied by CI.** A workflow able to
  rewrite rulesets could delete the approval requirement, which is strictly stronger than approving
  its own work. Writes use the maintainer's own credential (`administration: write`) from
  `scripts/rulesets/apply-rulesets.sh`, which refuses to run under `GITHUB_ACTIONS`. `ruleset-drift.yml`
  reads with a fine-grained PAT scoped to **Administration: read** and nothing else, and fails loudly
  when that secret is absent rather than skipping green.
- **Bounds and control.** `timeout-minutes` on every job running `claude-code-action` (the action
  exposes no timeout of its own). `cancel-in-progress: false` on `ci.yml` — a cancelled check run
  never satisfies a required check — and `true` on `claude-code-review.yml`. Every agent job gates on
  `vars.FACTORY_ENABLED == 'true'`; `ci.yml` and `ruleset-drift.yml` deliberately do not. Failures
  post an `@Muddl` mention, the only channel GitHub Mobile pushes for `issues`- and
  `pull_request`-triggered runs.

## Consequences

- **Machine identities cannot merge red.** That is the property this buys, and it is bounded: the
  maintainer remains `exempt` and can still merge red. This residual risk is **stated, not solved**;
  closing it for the human is a post-trip item.
- Branch protection now has a committed, diffable definition, and a hand-edit in the GitHub UI turns a
  daily workflow red within 24 hours instead of going unnoticed indefinitely.
- Three phone-reachable rungs exist for stopping the factory
  ([pattern guide](../patterns/factory-kill-switch.md)), and a ruleset lock-out is undone by one
  copy-pasteable `gh api --method PUT ... -f enforcement=disabled`.
- Every workflow file touched here contains `claude-code-action` or is adjacent to it, so the PR that
  ships this receives **no Claude review** — the action refuses to run when the workflow file differs
  from the default-branch copy and records that as an annotation on a *successful* job.
- The live-eval "post-merge" claim is corrected everywhere outside immutable history; ADR-0012 and
  ADR-0017 are amended rather than edited, per the knowledge-base reversal rule.
- **The applier and comparator are hardened beyond this plan's original script text**, on a review
  finding and an explicit maintainer ruling. Both scripts now fail loudly on an ambiguous
  ruleset-name lookup rather than silently taking the first match; `check-drift.sh` additionally
  asserts that R1 and R2 are the *only* live rulesets, closing the route by which a hand-added
  third ruleset carrying an `exempt` bypass would weaken the property F0 buys while the check
  stayed green; and `apply-rulesets.sh` rejects unrecognised arguments rather than treating a
  mistyped `--dry-run` as a live apply. A known limitation is recorded in the
  [kill-switch pattern guide](../patterns/factory-kill-switch.md): GitHub disables `schedule`
  triggers after 60 days of repository inactivity, so the drift check decays silently in exactly
  the quiet period it exists to cover.
