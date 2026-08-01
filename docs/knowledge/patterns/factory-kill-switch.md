# Stopping the factory (escalation ladder)

Every rung is reachable from a **mobile browser** with no laptop, no local checkout, and no
`gh` install. Rungs are ordered least-destructive first; each is strictly stronger than the one
above it. Use the lowest rung that solves the problem.

## Rung 1 — flip `FACTORY_ENABLED` (seconds, reversible, no side effects)

`https://github.com/Muddl/riot-api-mcp-server/settings/variables/actions` → `FACTORY_ENABLED` →
Update → set to anything other than `true` (use `false`).

Every agent job is gated on `vars.FACTORY_ENABLED == 'true'`, so this stops
`claude.yml`, `claude-code-review.yml`, and `housekeeping.yml` at the job level: they report
`skipped`, spend nothing, and create no branches.

**What keeps running on purpose:** `ci.yml` (it produces the required `Build & verify` check —
gating it would make every PR unmergeable), `ruleset-drift.yml` (a safety net must survive the
kill switch), and `live-eval.yml` (manual dispatch only, metered bucket).

Runs already **in flight** are not affected — the gate is evaluated at job start. Cancel them
from the Actions tab if that matters.

## Rung 2 — disable the workflow from the Actions tab (survives a variable being re-set)

`https://github.com/Muddl/riot-api-mcp-server/actions` → pick the workflow → `···` →
**Disable workflow**.

Stronger than rung 1 because it is not a value another automation could set back, and because it
prevents the run from being *created* rather than skipping it after creation.

## Rung 3 — revoke `CLAUDE_CODE_OAUTH_TOKEN` (fails every agent run closed)

`https://github.com/Muddl/riot-api-mcp-server/settings/secrets/actions` → delete
`CLAUDE_CODE_OAUTH_TOKEN`, then revoke it at the Claude Code account level.

Every `claude-code-action` step then fails authentication. This is the rung to use when the
concern is "the agent is doing something harmful", not "the agent is wasting money": it is the
only rung that cannot be undone by anything that already has repo write.

Does **not** touch `ANTHROPIC_API_KEY`, which belongs to `live-eval.yml` alone (ADR-0012).

## Ruleset rollback (separate axis — protection, not agents)

If a ruleset change locks the repository — e.g. a required check that never reports, so nothing
can merge — disable the ruleset rather than editing it. Copy-pasteable, and it works from the
`gh` CLI on a phone or from any shell:

```bash
# R1 (~ALL: deletion, non_fast_forward) — id is stable and known
gh api --method PUT repos/Muddl/riot-api-mcp-server/rulesets/8769144 -f enforcement=disabled

# R2 (~DEFAULT_BRANCH: PR approval + Build & verify) — id resolved by name
gh api --method PUT \
  "repos/Muddl/riot-api-mcp-server/rulesets/$(gh api repos/Muddl/riot-api-mcp-server/rulesets \
    --jq '.[] | select(.name | startswith("R2")) | .id')" \
  -f enforcement=disabled
```

Browser equivalent: `https://github.com/Muddl/riot-api-mcp-server/settings/rules` → the ruleset →
Enforcement status → **Disabled** → Save.

`enforcement: "evaluate"` (dry-run) is **GitHub Enterprise only** and is not available on this
repository — there is no "try it safely" middle setting. Disabled or active.

Re-enable by re-running `scripts/rulesets/apply-rulesets.sh` from a desk, which restores the
committed JSON exactly and is then confirmed by `ruleset-drift.yml`.

## After using any rung

Say so on the alert thread (`vars.FACTORY_ALERT_ISSUE`) — an unexplained silent factory is
indistinguishable from a broken one, which is the failure mode this whole sub-project exists to
remove.
