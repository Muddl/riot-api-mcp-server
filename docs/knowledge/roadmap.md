# Program roadmap

The **living** plan for taking `riot-api-mcp-server` from one League of Legends server to a
monorepo of per-game MCP servers over a shared core. Read this during
[hydrate](README.md#hydrate--persist-protocol) to know where the program stands.

This file is the **source of truth for scope and sequencing**. Specs under
`docs/superpowers/specs/` are dated snapshots of a decision at a moment — they are history and are
not edited retroactively. When scope moves, it moves here.

## Status

| # | Sub-project | Status | Spec |
|---|---|---|---|
| 0 | Monorepo restructure + extract `riot-api-core` | ✅ Done | [2026-07-15](../superpowers/specs/2026-07-15-monorepo-restructure-design.md) |
| **1a** | **LoL parity — foundation** | ✅ Done | [2026-07-15](../superpowers/specs/2026-07-15-lol-parity-foundation-design.md) |
| 1b | LoL parity — breadth | ✅ Done | [2026-07-18](../superpowers/specs/2026-07-18-lol-parity-breadth-design.md) |
| 2 | TFT server | ✅ Done | [2026-07-19](../superpowers/specs/2026-07-19-tft-server-design.md) |
| 3 | Valorant server | ⏳ Not started | — |
| 4 | LoR server | ⏳ Not started | — |

## Scope

### 0 — Monorepo restructure ✅

Pure structural refactor. Extracted `riot-api-core` (HTTP, routing, errors) and `riot-account-core`
(the cross-game account context) as auto-configured libraries, with `lol-mcp-server` as the first
server. No endpoints added, no behaviour changed. See
[ADR-0006](decisions/ADR-0006-monorepo-split.md).

### 1a — LoL parity: foundation ✅

The first feature work on the new structure, and therefore the template the other four servers
inherit. Organizing principle: **everything a second game server would otherwise copy gets built
once, in a library, in this cycle.**

- Coordinates and package rename (`com.wkaiser` → `com.muddl`)
- Release engineering: per-module versioning, module-scoped tags, the version ↔ changelog gate,
  provenance stamping
- Core hardening: 429 retry honouring `Retry-After`, actionable error messages
- Shared identity: `PlayerIdentityResolver` in `riot-account-core`, TTL-cached
- LoL correctness: PUUID migration, spectator v4 → v5, removal of the three dead by-name tools
- **League** as the single exemplar context
- Tool contract sweep: `<game>_<context>_<action>`, the single `player` param
- Per-module docs and the monorepo sanity check

**Progress:** ✅ Complete. Plans A (coordinates + release engineering), B (library hardening: retry,
error taxonomy, identity resolver), C (LoL server: correctness, the League exemplar, the tool-contract
sweep), and D (per-module docs + the monorepo sanity check) all landed. Every module documents itself,
enforced by `verifyModuleDocs`; the sanity pass confirmed the convention plugin, dependency scopes,
and the absence of live `com.wkaiser` references. The handoff contract for 1b is in
[1a's spec](../superpowers/specs/2026-07-15-lol-parity-foundation-design.md#handoff-contract--what-1b-inherits).
This "Done" covers all planned implementation plus the automatable checks (unit/ArchUnit/JaCoCo/
Spotless, and `McpToolInventoryTest` asserting the seven-tool inventory). Two standing gates remain
**pending / user-owed**, carried forward rather than claimed passed: the live transport handshake
(stdio + sse, `initialize` → `tools/list` → one `tools/call`, asserting stdout JSON purity) and the
Spectator-V5 / League-V4 endpoint-path verification against the live Riot developer portal — both
require a live `RIOT_API_KEY` and a human in the loop, per the Standing constraints below.

**Split from 1b deliberately.** Sub-project 1 originally bundled correctness, six new contexts, the
contract sweep, and conventions. Sub-project 0's lesson was that mixing pure motion with behaviour
change destroys the ability to tell which one broke something; the same holds for mixing a public
contract break with wide addition. Foundation-first also means the break lands once, on a small
surface, and 1b's tools are born correct.

### 1b — LoL parity: breadth ⏳

The remaining five contexts — champion-mastery, champion, challenges, status, clash — built
mechanically against 1a's template.

Also **a tool for the existing `match` context**. Match has a domain, service, port, and
WireMock-tested outbound adapter, but no inbound adapter — `analytics` is its only consumer, so
match data is reachable only through an analytics summary and never directly. Exposing it is
exactly the mechanical add-a-tool work 1b exists to do.

**1a's success criterion is falsifiable here:** *1b must add five contexts without modifying either
library.* If 1b needs to touch `riot-api-core` or `riot-account-core`, 1a under-delivered — record
that as a finding rather than absorbing it quietly.

The handoff contract 1b works from is stated in
[1a's spec](../superpowers/specs/2026-07-15-lol-parity-foundation-design.md#handoff-contract--what-1b-inherits).

**Progress:** ✅ Complete. Five contexts added — `champion` (rotation), `status`, `championmastery`,
`challenges`, `clash` — plus the `match` context's first inbound tools (`lol_match_ids_by_player`,
`lol_match_by_id`). The tool surface grew from 6 to 13. Non-player-keyed tools extend the contract
([ADR-0014](decisions/ADR-0014-non-player-keyed-tools.md)). **1a's falsifiable criterion held:** the
five contexts and the match tools landed with no change to `riot-api-core` or `riot-account-core`.
`lol-mcp-server` released as 0.2.0.

### 2 — TFT server ✅

The first real test of whether the core generalizes to a second game. TFT reuses LoL's
platform/region host schemes, so it exercises the module template and the shared libraries without
forcing a routing abstraction.

**Progress:** ✅ Complete. `tft-mcp-server` shipped with 6 bounded contexts (`account` tool-only,
`summoner`, `league`, `match`, `status`, `analytics`) and **11** MCP tools, including a superset of
LoL's league surface (paged tier entries, league-by-id, and the Hyper Roll rated ladder) and a
TFT-native `tft_analytics_player_matches` (average placement, top-4 rate, most-played traits/units —
no KDA, since TFT has none). Full offline suite green: WireMock adapter tests for summoner, league (all
five endpoints), match, and status; port-fake service tests including `AnalyticsService`'s
zero-games and single-game/all-top-4 edge cases; `HexagonalArchitectureTest` (reusing the shared
`HexagonRules`) plus a negative control; `McpToolInventoryTest` asserting the 11-tool inventory.
Released as `tft-mcp-server 0.1.0`.

**The falsifiable criterion held.** `tft-mcp-server` shipped with **zero changes** to `riot-api-core`
or `riot-account-core` — verified across every implementation task, including the dedicated Task 8
ArchUnit/architecture review: no file under either library was modified to build the second server.
The only non-Java changes were net-new Python tooling in `eval/` (generalizing the live-eval harness
to N servers), which the spec explicitly carves out as not counting against this criterion. This is
the program's first real evidence the sub-project-0/1a core generalizes, not just a repeated claim.

Two standing gates remain **pending / user-owed**, carried forward rather than claimed passed, exactly
as for 1a/1b: the live transport handshake (stdio + sse) and TFT-v1 endpoint-path verification against
the live Riot developer portal (continuously re-verified by dispatched runs of the live eval harness once its
TFT generalization lands) — both require a live `RIOT_API_KEY` and a human in the loop.

### 3 — Valorant server ⏳

Introduces a **third host-routing scheme** (AP/BR/EU/KR/LATAM/NA/ESPORTS) — this is what forces the
generalized routing abstraction that sub-project 0 deliberately deferred. Also the first server
needing **production-key gating**: `val-match-v1` / `val-ranked-v1` return 403 to a personal dev
key.

### 4 — LoR server ⏳

Smallest surface. **Verify LoR is not in maintenance before investing.** Parts of it need a
production key, and deck endpoints need Bearer/RSO auth, which `RiotApiClient` does not support.

## Cross-cutting: the software factory ⏳

A second track, orthogonal to the per-game sub-projects above. Those grow *what the repo does*; this
grows *how change reaches `master`*.

The reference model is Cole Murray's [software factory](https://murraycole.com/posts/software-factory)
— *"a repeatable system for turning defined work into production software through standardized
inputs, shared tooling, automated quality gates, and measurable output"* — with agents planning,
implementing, testing, and reviewing while humans set intent and acceptance criteria. Its loop is
**signal → intake → context → plan → build → test and review → deploy → monitor → learn**, and the
role shift is from being *in* the loop to *on* it. Stripe's
[minions](https://stripe.dev/blog/minions-stripes-one-shot-end-to-end-coding-agents.md)
([part 2](https://stripe.dev/blog/minions-stripes-one-shot-end-to-end-coding-agents-part-2.md)) are
the same shape at scale: unattended agents in isolated devboxes, an agent loop **interleaved with
deterministic steps** for git, lint, and test, hard iteration limits, and human review before every
merge.

Local target loop: **GitHub Issue → superpowers-driven development → Claude Code Review → merge.**

### The governing constraint

*"Generation is cheap and getting cheaper, so your ceiling is set by how fast and how trustworthily
you can verify output."* The bottleneck is verification, not authorship — a restatement of this
repo's standing constraint that green tests do not prove the server serves. Every step below is
judged on whether it strengthens verification, not on how much it automates.

### The primitives, and which ones exist here

Convergent-evolution argument from
[the walkthrough](https://www.youtube.com/watch?v=lx0Eaane4Ng) (reference implementation:
[Warren](https://app.warren.run)): independently, the companies doing this at scale have landed on
the same handful of parts. Four in the main path — a **work queue**, a **control plane**, a
**sandbox**, and a **handoff to a human** — over two substrate layers, an **event stream** and
**durable memory**. Two framings there are worth stealing outright: work arrives as an *issue* that
an agent is assigned, not as a hand-written prompt; and durable memory exists because the sandbox
dies every run, so anything worth keeping must be git-tracked before the machine is destroyed.

Auditing this repo against them — the gaps are intake and observability, not execution:

| Primitive | State here |
|---|---|
| **Sandbox** — ephemeral, per-agent, destroyed after the task | ✅ A GitHub-hosted runner per run, discarded on completion. `--allowedTools` is the permission surface. |
| **Durable memory** — git-tracked, survives the sandbox | ✅ `docs/knowledge/` plus the hydrate/persist protocol. This repo's strongest primitive; it is already the thing the pattern asks for. |
| **Handoff to a human** — the PR is the output unit | ✅ [ADR-0018](decisions/ADR-0018-housekeeping-pr-review-gate.md) made this real rather than nominal. |
| **Control plane** — queue, orchestrate, observe | ◐ GitHub Actions is a thin one: schedule, dispatch, a concurrency group. No cross-run view of what agents did or where they fail. |
| **Work queue** — issues, assigned | ⏳ Issues are not yet the intake path. This is the next real step. |
| **Event stream** — traces to steer, pause, fork, and analyse in aggregate | ⏳ Weakest. Only Actions logs, and `claude-code-action` hides SDK output by default (`show_full_output`), so a run's reasoning is not inspectable after the fact. Without this there is no way to find *systematic* agent failure, only individual ones. |

The near-term ambition is explicitly **not** a dark factory. The argument against it is not
capability but maturity: a human review step at the end is what keeps the system legible to the
people responsible for it, which matches this repo's premise that the discipline is the product.

### Substrate already in place

Beyond the primitives, the quality gates the pattern depends on are largely built.

| Factory component | Here today |
|---|---|
| Automated quality gates | `./gradlew build` — tests, ArchUnit, JaCoCo, Spotless — plus the dispatch-only live eval harness ([ADR-0012](decisions/ADR-0012-live-eval-harness.md)) |
| Shared tooling | Agents run the same Gradle build and the same project skills a human runs; there is no agent-only path |
| Standardized inputs | KB hydrate/persist protocol, project skills, `CLAUDE.md` |
| Back-pressure before review | ArchUnit, Spotless, and the negative-control tests fail locally, pre-PR — Stripe's "shift feedback left" |
| Measurable output | JaCoCo coverage; eval token cost via `eval/tools/report-cost.py` |
| Replayability | ADRs + immutable `docs/superpowers/specs|plans/` record why a change looks the way it does |

### Sequencing

Incremental; each step is useful alone, and none is scheduled against a game sub-project.

| Step | State | Notes |
|---|---|---|
| Reviewer posts real, reviewable feedback | ✅ Shipped | [ADR-0015](decisions/ADR-0015-repo-maintenance-automation.md). Was green-but-silent before. |
| Scheduled agent work opens a PR rather than committing | ✅ Shipped | `housekeeping.yml` ([ADR-0015](decisions/ADR-0015-repo-maintenance-automation.md)). |
| Agent-authored PRs actually receive CI + review | ✅ Shipped | [ADR-0018](decisions/ADR-0018-housekeeping-pr-review-gate.md). The `GITHUB_TOKEN` form opened PRs no workflow ever saw — the gate was intent, not mechanism. |

The remaining work is decomposed into six sub-projects by the
[2026-08-01 decomposition spec](../superpowers/specs/2026-08-01-software-factory-decomposition-design.md),
which also **corrects three claims this section previously made** (see below).

| # | Sub-project | State | Notes |
|---|---|---|---|
| **F0** | Harden the gate | ✅ Shipped and applied | [ADR-0019](decisions/ADR-0019-gate-hardening-and-ruleset-topology.md). Ruleset `8769144` split into R1 (`~ALL`: deletion, non_fast_forward, id preserved so the phone rollback stays valid) + R2 (`~DEFAULT_BRANCH`, new id `20203635`: `pull_request{1}` + `required_status_checks["Build & verify", integration_id 15368]`), **applied live 2026-08-01** and reconciled against GitHub's echoed defaults; `check-drift.sh` reports both matching. Also shipped: fork PRs unblocked first; timeouts, `FACTORY_ENABLED` kill switch, `@Muddl` failure alerts, the local-only apply script, and a daily drift check. #71 closed and the stale-doc sweep done. **Caveat, now closed by F1:** the configuration was verified, the *property* was not — see the `mergeStateStatus` gotcha; every credential on this repo inherited the admin bypass, so "machine identities cannot merge red" was untestable until **F1** verified it (see [ADR-0020](decisions/ADR-0020-machine-identity-github-app.md)'s Consequences). |
| **F1** | Machine identity (GitHub App) | ✅ Shipped and applied | [ADR-0020](decisions/ADR-0020-machine-identity-github-app.md). Retires the PAT: `housekeeping.yml` mints a 1-hour installation token via `actions/create-github-app-token@v3` and PRs/commits are authored by `muddlbot[bot]`, not `Muddl`. `HOUSEKEEPING_TOKEN` was deleted post-merge. Verified end to end on PR #87: the App reads `mergeStateStatus: BLOCKED` (R2's approval gate applying for real) while the exempt admin reads `UNSTABLE`/`MERGEABLE` on the same PR seconds apart, and a probe push touching a workflow file was rejected at the remote for lacking the `workflows` permission. |
| **F2** | Issue intake, two-phase | ⏳ Not started — [plan](../superpowers/plans/2026-08-01-factory-f2-issue-intake.md) | `agent:plan` commits a plan and posts its SHA; `agent:go` implements *that SHA*, checked by a deterministic `git diff --exit-code`. The work-queue primitive. **Needs F0 first** — the current `~ALL` PR-required rule rejects `agent:go`'s second push to an existing branch. |
| **F3** | Machine-emitted run traces | ⏳ Not started | The event stream, restricted to facts non-agent steps emit. Blocked on #71. |
| **F4** | Review loop-back | ⏳ Not started | `agent:revise` label; two-round cap counted from the append-only issue timeline; enforced by a tool-less `gate` job. |
| **F5** | Tiered autonomy by blast radius | ⏳ Not started | Docs/KB changes need less ceremony than a change to `RiotApiClient` or a tool contract. A CI check verifies changed paths against the declared tier; `file_path_restriction` push rules are org-only. |

**Four corrections this section previously got wrong**, all found by designing against it:

- **Branch protection was never "not started."** Ruleset `8769144` has been active since 2025-10-09
  with `pull_request{1 approval}`, `deletion`, and `non_fast_forward` — but **no required status
  checks**, scoped to `~ALL` branches rather than the default branch, and with the repository admin
  as an `exempt` bypass actor. So the sole human is invisibly exempt from the only rule that exists,
  the merge button is live on a red PR, and every feature branch is PR-gated in a way that will
  break the first non-exempt identity to push a second commit to one.
- **Review loop-back is not blocked on suspend/resume.** Session state dies with the ephemeral
  runner and nothing restores it, but the action's tag mode already re-derives context each run from
  the issue/PR body, every comment, and the diff. Re-derivation is the mechanism, not a workaround;
  the "notable gap" does not apply here. What loop-back actually needs is a trigger that is not
  `pull_request_review: submitted` — PR #67 received **nine** submitted reviews from `claude`, since
  each inline comment posts as its own review.
- **`show_full_output` is withdrawn as the cheap starting point.** It dumps every tool result,
  secrets included, into the Actions log and auto-enables under `ACTIONS_STEP_DEBUG`. This
  repository, its logs, and its artifacts are **public**. Uploading the `execution_file` artifact is
  the same content class in a different container and is rejected on the same grounds.
- **The live eval never ran on merge.** `live-eval.yml` is `workflow_dispatch:` only — the `push`
  trigger was removed and never replaced. The stale claim was live in this file (below, under
  sub-projects 2 and 3 and in the deferred/standing-constraint tables), in `CLAUDE.md`, in
  `README.md`, in `CONTRIBUTING.md`, and inside accepted **ADR-0012** and **ADR-0017**. F0's sweep
  ([ADR-0019](decisions/ADR-0019-gate-hardening-and-ruleset-topology.md)) corrected the prose
  everywhere outside immutable history; ADR-0012 and ADR-0017 got dated amendment blockquotes rather
  than a silent edit. The practical consequence is the one that matters regardless of doc wording:
  **merges get no live coverage unless someone dispatches the workflow by hand**, and `gotchas.md`
  records three bug classes that only the live eval has ever caught, every one of which passed the
  offline suite.

### Failure modes to design against

Named in the sources, and **two are already evidenced in this repo** — which is the argument for
treating them as design constraints rather than hypotheticals:

- **Generation outpacing verification.** The reason the governing constraint above is first.
- **Agents as lenient self-graders.** Observed 2026-08-01: a workflow validation run reported
  `conclusion: success` while the step under test had silently skipped itself, and the "passing" run
  proved nothing. See `gotchas.md`.
- **Silent failure that looks like success.** Observed twice in the same incident — the 2026-07-19
  housekeeping run went green only because the pass produced no diff and the broken push was never
  reached ([ADR-0018](decisions/ADR-0018-housekeeping-pr-review-gate.md)).
- **Velocity theater.** Throughput is not a goal here; this repo's value is the discipline, so
  cycle-time metrics stay subordinate to the gates.

### Attribution: automated PRs now author as a bot — **Shipped** ([ADR-0020](decisions/ADR-0020-machine-identity-github-app.md))

~~`HOUSEKEEPING_TOKEN` is a fine-grained PAT, and a PAT *acts as the person who owns it*. So the
housekeeping PR is authored by `Muddl` — not because a human triggered that run, but inherently: the
unattended Monday run will attribute itself the same way.~~ This was the F1 problem statement; it no
longer holds. `housekeeping.yml` now mints a short-lived GitHub App installation token
(`actions/create-github-app-token@v3`) immediately before pushing and opening the PR, and both the
commit and the PR are authored by `muddlbot[bot]`, not `Muddl`. `HOUSEKEEPING_TOKEN` was deleted and
the underlying PAT revoked post-merge — see ADR-0020 for the end-to-end verification (PR #87).

The two costs this closed:

- **Provenance.** Machine-authored work is now recorded as the bot's, not the maintainer's —
  "who wrote this?" is answerable from the PR again.
- **Branch protection is satisfiable.** GitHub does not let the author of a PR approve it, so a
  housekeeping PR authored by the repo's only human maintainer could never be approved by them.
  Bot authorship removes that self-approval bar; ADR-0020 confirms `viewerDidAuthor: false` for the
  maintainer on a bot-authored PR.

`claude-code-review.yml`'s `allowed_bots` was updated (`'dependabot,muddlbot'`) in the same commit as
the identity swap, since an App-authored PR arrives as that App's bot and is refused by the reviewer
— loudly, per ADR-0020, not silently — unless the App is already listed.

**Known leftover scope** (per ADR-0020's Consequences, still real): the `housekeeping` job still
grants the ambient `GITHUB_TOKEN` `contents: write` and `pull-requests: write`, which nothing now
uses. Trimming it cannot be validated before merge, so it is deferred to a standalone PR with a
post-merge dispatch.

### Review-cycle cap: two revision rounds per PR

A PR in this flow gets **at most two rounds of acting on reviewer findings**. After the second
round, it merges as long as what remains is non-blocking; leftover suggestions are filed as
follow-up issues rather than fixed in place. Mirrors the two-CI-round limit minions uses.

- **Blocking findings are exempt.** Correctness, security, and "this would break on merge" are fixed
  regardless of round count. The cap bounds polish, never soundness.
- **Why:** reviewer findings have sharply diminishing returns — round one catches real defects, round
  three suggests comment rewording. An unbounded revise-until-the-reviewer-is-silent loop spends
  usage chasing asymptotic perfection, and optimizes for reviewer silence rather than correctness.
  Each round also costs a full review run.
- This is the same stop condition the automated loop-back step above will need; applying it to
  human-triggered PRs now means the rule is established before it is automated.

**Standing constraint for this track:** automation may *propose* but never *approve*. The repo-level
"Allow GitHub Actions to create and approve pull requests" setting stays off, and no workflow holds
an identity that can approve its own work ([ADR-0018](decisions/ADR-0018-housekeeping-pr-review-gate.md)).

## Deferred, with a home

Real and wanted, deliberately not scheduled. Recorded so they are not re-derived:

| Item | Where it lands |
|---|---|
| Proactive / token-bucket rate limiting | Own cycle, if evidence demands it. 1a ships reactive 429 retry, which is the behaviour Riot actually specifies. |
| Bearer / RSO auth | Arrives when a server needs it — `accounts/me`, or LoR decks (sub-project 4). YAGNI until then. |
| Generalized host-routing abstraction | Forced by sub-project 3. TFT reuses LoL's hosts, so one data point is not enough to design from. |
| Publishing libraries as Maven artifacts | Not planned. Libraries are versioned for provenance and consumed by project reference — see ADR-0010. |
| Aggregate coverage report | When more than one server exists. |
| ~~Claude Code Actions integration rework~~ | **Shipped** ([ADR-0015](decisions/ADR-0015-repo-maintenance-automation.md)). The PR reviewer now posts real reviews, `@claude` can respond, and a weekly `/housekeeping` cron opens maintenance PRs — all on `CLAUDE_CODE_OAUTH_TOKEN`, separate from live-eval's `ANTHROPIC_API_KEY`. |
| ~~Automated endpoint-path verification~~ | **Shipped** as part of the live eval harness (see [ADR-0012](decisions/ADR-0012-live-eval-harness.md)). Live agent-driven evals call every tool against the real Riot API on each dispatched run; a wrong path returns 404 and fails the eval, so paths are verified by running the harness rather than by a human opening the portal. |
| ~~Automated transport-handshake verification~~ | **Shipped** as part of the live eval harness (see [ADR-0012](decisions/ADR-0012-live-eval-harness.md)). The suite runs over both stdio and sse each dispatched run; a successful stdio session is the stdout-purity check. |

## Standing constraints

These hold across every sub-project:

- **The suite runs offline with no Riot API key.** WireMock for outbound adapters, port fakes for
  application services. Non-negotiable.
- **Riot endpoint paths are verified against the live developer portal**, never assumed from
  Context7 or model knowledge. Sub-project 0 found Context7 returned mostly Data Dragon and
  Valorant/TFT material when asked for a structured LoL reference. *As of
  [ADR-0012](decisions/ADR-0012-live-eval-harness.md) this is automated by the live eval
  harness (`eval/`), over both transports, on dispatch rather than on merge; the offline suite
  remains the pre-merge gate.*
- **Green tests do not prove the server serves.** Every cycle verifies both transports with a real
  MCP handshake, including stdio's stdout purity. *As of
  [ADR-0012](decisions/ADR-0012-live-eval-harness.md) this is automated by the live eval
  harness (`eval/`), over both transports, on dispatch rather than on merge; the offline suite
  remains the pre-merge gate.*
- **The intended consumer is a third party** installing against their own Riot API key. That raises
  the bar on tool naming, error messages, and key-gating behaviour.
