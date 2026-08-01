# ADR-0018 — Automated PRs are authored by a non-Actions identity so the review gate applies

- **Status:** Accepted
- **Date:** 2026-08-01
- **Amends:** [ADR-0015](ADR-0015-repo-maintenance-automation.md)

## Context

[ADR-0015](ADR-0015-repo-maintenance-automation.md) specified that the weekly housekeeping pass
"opens a PR — never merging unreviewed." The implementation opened that PR with the automatic
`GITHUB_TOKEN`, which does not deliver on the intent:

- GitHub deliberately does not fire `pull_request` / `push` events for refs and PRs created with
  `GITHUB_TOKEN` (a recursion guard). So a housekeeping PR got **no `ci.yml` run and no
  `claude-code-review.yml` run**. The PR existed and looked reviewable; nothing had reviewed it.
- `claude-code-review.yml` additionally gates on actor type (`allowed_bots: 'dependabot'`), so even
  a hypothetical event from `github-actions[bot]` would be refused.

The intent was therefore unmet in a way that was invisible at a glance: the artifact ADR-0015 asked
for (a PR) was present, while the property it wanted (review before merge) was absent. A green
workflow run and an open PR both indicated success.

This surfaced only after fixing an unrelated defect that had masked it — the PR step's `git push`
had never once succeeded, because `claude-code-action` deletes the credential `actions/checkout`
persists, repoints `origin` at its own installation token, and revokes that token when its step
ends (see `gotchas.md`). Every run with changes to push died at the push, so the PR-creation path
had never been reached.

A repo-level setting ("Allow GitHub Actions to create and approve pull requests") makes
`GITHUB_TOKEN` able to *create* the PR, and was briefly enabled here. It was rejected on reflection:
it does not restore the missing `pull_request` event, so the PR still receives no CI and no review —
and it grants every workflow in the repo the ability to approve pull requests, weakening the very
gate it was invoked to support.

## Decision

**Any workflow-authored PR that is intended to be reviewed must be opened by a non-Actions
identity.** For `housekeeping.yml` that is a fine-grained PAT stored as the `HOUSEKEEPING_TOKEN`
repository secret, used for both the branch push and `gh pr create`, so the PR is authored by a
human identity and receives the ordinary gate: `ci.yml` plus `claude-code-review.yml`.

Supporting rules:

- **Fail loudly, never fall back.** If `HOUSEKEEPING_TOKEN` is absent the step exits non-zero with
  an actionable message. Falling back to `GITHUB_TOKEN` would still produce a PR — an unreviewed
  one — reintroducing the original failure in its most deceptive form.
- **Least privilege on the PAT.** Contents: read/write and Pull requests: read/write, scoped to this
  repository only. Notably *not* Workflows: the housekeeping pass maintains docs, the KB, the
  roadmap, skills, and agents, and must not be able to rewrite CI. A pass that needs to edit
  `.github/workflows/` should fail and be done by hand.
- **The repo-level Actions PR-creation setting stays off** (`can_approve_pull_request_reviews:
  false`), so no workflow can create or approve PRs on its own authority.

## Consequences

- Housekeeping PRs are built and reviewed exactly like human PRs; ADR-0015's "never merging
  unreviewed" is now enforced by mechanism rather than asserted by intent.
- A PAT is a credential with an expiry and a human owner — an operational cost this repo did not
  previously carry. When it expires the pass fails loudly at the guard, which is the intended
  behaviour, not a regression to debug.
- The pattern generalizes: any future automation opening a PR for review (the GitHub Issues →
  superpowers → review cycle on the roadmap) inherits this rule and this secret rather than
  rediscovering the problem.
- A GitHub App (via `actions/create-github-app-token`) would remove the expiry and narrow the
  blast radius further. Deferred, not rejected — it is the natural upgrade once more than one
  workflow needs an authoring identity.
