# ADR-0020 — The machine identity is a GitHub App, not a PAT

- **Status:** Accepted
- **Date:** 2026-08-01
- **Amends:** [ADR-0018](ADR-0018-housekeeping-pr-review-gate.md)

## Context

[ADR-0018](ADR-0018-housekeeping-pr-review-gate.md) established the right rule — a workflow-authored
PR intended for review must be opened by a **non-Actions identity**, because GitHub fires no
`pull_request` event for a PR opened with `GITHUB_TOKEN`. It shipped that rule on a fine-grained PAT
(`HOUSEKEEPING_TOKEN`) and named the App as "deferred, not rejected". Three costs came due:

- **A PAT acts as the person who owns it.** Every housekeeping PR was authored by `Muddl`, so `git
  log` and the PR list credited the maintainer with a pass he did not write.
- **That made the PR unapprovable.** GitHub forbids a PR author from approving their own pull
  request, unconditionally, and [ADR-0019](ADR-0019-gate-hardening-and-ruleset-topology.md)'s R2
  requires one approval on `master`. The only human who could approve was structurally barred from
  approving the machine's work — and *approving a PR is the one supervisory act that works well from
  a phone*, which is what makes supervised autonomy legitimate rather than a rubber stamp.
- **Every credential on this repo inherited the admin bypass.** A PAT minted under the admin account
  is an `exempt` bypass actor, so "machine identities cannot merge red" was not merely unverified but
  **unverifiable** (see `gotchas.md`, "`mergeStateStatus` is computed per-viewer").

A probe on 2026-08-01 (run `30725565375`, PR #87) exercised the App end to end before merge, and
disproved one assumption the F1 spec carried over from ADR-0018 almost verbatim: that
`claude-code-action` declines an unlisted bot the same way it declines a workflow-file mismatch — by
recording an annotation on a job that still goes green. It does not. See Consequences.

## Decision

`housekeeping.yml` mints a **1-hour installation token** with `actions/create-github-app-token@v3`,
immediately before the step that pushes and opens the PR; the action revokes it in its own post-step.

- **App:** `MuddlBot`, slug `muddlbot`, app id `4459262`, installation id `150619173`, bot user
  `muddlbot[bot]` (id `311914422`), owned by `Muddl`, installed on **this repository only**.
  Credentials: `vars.APP_CLIENT_ID` + `secrets.APP_PRIVATE_KEY`. `owner`/`repositories` are omitted so
  the token scopes to the current repository.
- **Permissions:** Contents write, Pull requests write, Issues write, Actions read, Metadata read.
  **Workflows is withheld**, matching ADR-0018's deliberate choice for the PAT: an automated pass
  maintains docs and the KB and must never be able to rewrite CI. This is now *tested* rather than
  asserted — see Consequences.
- **Commits are authored as the bot**, `muddlbot[bot] <311914422+muddlbot[bot]@users.noreply.github.com>`,
  so the commit author and the PR's "opened by" agree. Verified on the **first** probe attempt
  (branch `probe/f1-app-identity-30724960866`, PR #86 — distinct from PR #87, which the rest of this
  ADR's evidence comes from):

  ```
  gh api repos/Muddl/riot-api-mcp-server/commits/probe/f1-app-identity-30724960866 \
    --jq '{commit_author: .commit.author, api_author: .author.login}'
  → {"api_author":"muddlbot[bot]",
     "commit_author":{"name":"muddlbot[bot]",
                      "email":"311914422+muddlbot[bot]@users.noreply.github.com",
                      "date":"2026-08-02T00:35:10Z"}}
  ```

  `author.login` resolving to `muddlbot[bot]` at all is the linkage: GitHub only resolves a commit's
  noreply address to an account when its numeric prefix matches a real account, so this confirms the
  id in the git identity above is correct, not merely plausible.
- **The slug is asserted at runtime, as defence in depth, not as the only safeguard.**
  `claude-code-review.yml` pins the literal slug in `allowed_bots`; a mismatch there does not go
  unnoticed — it fails the review job red and pages the maintainer (see Consequences). The runtime
  guard in `housekeeping.yml` (`steps.app-token.outputs.app-slug == 'muddlbot'`) exists so a slug drift
  is caught earlier, at the pass itself, with a message that names the exact cause — faster diagnosis,
  not the only thing standing between the repo and silence.
- **`allowed_bots: 'dependabot,muddlbot'` ships in the same commit** as the identity swap. A later
  commit would open a window in which every one of the bot's PRs fails its review job red and burns a
  maintainer alert — a real cost even though it is a loud one, not the silent gap ADR-0018's original
  failure was.
- **Fail loudly, never fall back** (ADR-0018's rule, retargeted): a missing App token exits non-zero
  rather than reaching for `GITHUB_TOKEN`, which would produce a PR no workflow ever sees.

## Consequences

- Provenance is restored and the maintainer can approve machine PRs — the supervisory act that
  actually works from a phone. Verified on PR #87 via GraphQL `viewerDidAuthor: false` for the
  maintainer: the self-approval bar does not apply to a bot-authored PR, so this property held, not
  just assumed.
- A 90-day PAT is replaced by a token that lives for minutes. `HOUSEKEEPING_TOKEN` was deleted and
  the PAT itself revoked once the swap was verified post-merge; the App's private key is now the
  only long-lived secret, rotatable at <https://github.com/settings/apps/muddlbot>. (This sentence was written before 2026-08-02 and read as history by the next automated pass; see gotchas.md, "A past-tense sentence in a KB document becomes a fact the moment an agent reads it".)
- **The Workflows boundary is verified, not assumed.** A probe (run `30725565375`) pushed a commit
  touching `.github/workflows/ci.yml` with the App token and GitHub **rejected** it at the push,
  verbatim: `! [remote rejected] probe/f1-workflows-denied-30725565375 ->
  probe/f1-workflows-denied-30725565375 (refusing to allow a GitHub App to create or update workflow
  `.github/workflows/ci.yml` without `workflows` permission)`. An automated pass that needs to edit CI
  therefore fails at the push — loudly, which is the intended behaviour.
- **This is the first identity on this repo whose merge-state reading is evidence.** The App is
  `actor_type: Integration` and is not a bypass actor: on PR #87 it reported `mergeStateStatus:
  BLOCKED` (R2's approval requirement), while the exempt admin account read, seconds apart on the same
  PR, `mergeStateStatus: UNSTABLE, mergeable: MERGEABLE`. `Build & verify` itself **passed** on that
  PR (2m4s) — the required check does report on machine PRs, so R2 does not render them permanently
  unmergeable, it genuinely gates on the missing approval. That closes the caveat ADR-0019 left open —
  the gate is not merely configured, it *applies* to the identities that run unattended. It remains
  true that the admin can still merge red by hand.
- **The unlisted-bot decline is a loud failure, not a silent one — the F1 spec's central assumption
  was wrong.** Verified on run `30725572647` (the review job on bot PR #87, with `master`'s
  `allowed_bots: 'dependabot'` still in force), verbatim:

  ```
  Checking permissions for actor: muddlbot[bot]
  Actor is a GitHub App: muddlbot[bot]
  Actor type: Bot
  ##[error]Action failed with error: Workflow initiated by non-human actor: muddlbot (type: Bot). Add bot to allowed_bots list or use '*' to allow all bots.
  ##[error]Process completed with exit code 1.
  ```

  The job failed red, and `claude-code-review.yml`'s own `Alert on failure` step then posted an
  `@Muddl` comment on the PR. So shipping `allowed_bots` in the same commit as the identity swap is
  still correct — it is still worth avoiding a window where every machine PR's review job goes red and
  burns an alert — but that is the reason now, not "otherwise it goes unreviewed and unnoticed". The
  silent-green-decline behaviour is real, but belongs to a different mechanism entirely: the
  `claude-code-action` workflow-validation skip (unchanged by this ADR; see `gotchas.md`). The F1 spec
  conflated the two; this ADR does not repeat that conflation.
- **The F1 PR itself received no Claude review**, because it edits `claude-code-review.yml` and the
  action refuses to run when its workflow file differs from the default branch. Expected, stated in
  the PR body, and not evidence of a broken reviewer.
- **F2 inherits the pattern but not this identity.** `agent:go` PRs are opened from inside the
  action step with its *own* App installation token and are authored by `claude[bot]` — a different
  bot that must be added to `allowed_bots` separately.
- Known leftover scope, unchanged from ADR-0018: the `housekeeping` job still grants the ambient
  `GITHUB_TOKEN` `contents: write` and `pull-requests: write`, which nothing uses. Trimming it still
  cannot be validated before merge, so it stays for a standalone PR with a post-merge dispatch.
