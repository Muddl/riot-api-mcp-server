#!/usr/bin/env bash
# Apply the committed ruleset JSON to GitHub. LOCAL ONLY — never from CI.
#
# Why local: a workflow able to rewrite rulesets could delete the approval requirement, which is
# strictly stronger than approving its own work and violates the standing constraint that
# automation may propose but never approve. Ruleset writes need `administration: write`; this
# script uses the maintainer's own `gh` credential (an OAuth token with `repo` scope, or a
# fine-grained PAT with Administration: read and write). No such credential exists in Actions,
# and none is ever added.
#
# Usage:  scripts/rulesets/apply-rulesets.sh [--dry-run]
set -euo pipefail

REPO="${RULESET_REPO:-Muddl/riot-api-mcp-server}"
R1_ID=8769144                       # existing ruleset, updated in place so its id stays stable
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.github/rulesets" && pwd)"
DRY_RUN=false
[ "${1:-}" = "--dry-run" ] && DRY_RUN=true

if [ -n "${GITHUB_ACTIONS:-}" ]; then
  echo "REFUSING: this script must never run in GitHub Actions. See ADR-0019." >&2
  exit 1
fi

command -v gh >/dev/null || { echo "gh CLI not found" >&2; exit 1; }
command -v jq >/dev/null || { echo "jq not found" >&2; exit 1; }
gh auth status >/dev/null || { echo "gh is not authenticated" >&2; exit 1; }

ruleset_id_by_name() {
  gh api "repos/$REPO/rulesets" --jq ".[] | select(.name == \"$1\") | .id" | head -n1
}

apply() {  # apply <json-file> [known-id]
  local file="$DIR/$1" id="${2:-}" name
  name="$(jq -r .name "$file")"
  [ -n "$id" ] || id="$(ruleset_id_by_name "$name")"
  if $DRY_RUN; then
    echo "would ${id:+PUT $id}${id:+ }${id:-POST} <- $1 ($name)"
    return 0
  fi
  if [ -n "$id" ]; then
    echo "PUT  $REPO/rulesets/$id  <- $1"
    gh api --method PUT "repos/$REPO/rulesets/$id" --input "$file" >/dev/null
  else
    echo "POST $REPO/rulesets      <- $1"
    gh api --method POST "repos/$REPO/rulesets" --input "$file" >/dev/null
  fi
}

# ORDER IS LOAD-BEARING. R2 carries the approval requirement that R1 is about to lose; creating
# R2 first means the default branch is never, even briefly, without one.
apply R2-default-branch.json
apply R1-all-branches.json "$R1_ID"

echo
echo "Applied. Verifying against the committed JSON:"
exec "$(dirname "${BASH_SOURCE[0]}")/check-drift.sh"
