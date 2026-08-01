#!/usr/bin/env bash
# Compare the live rulesets against the committed JSON. READ ONLY — makes no writes at all.
# Runs both locally and from ruleset-drift.yml so there is exactly one implementation.
#
# `.github/rulesets/` is not a GitHub-recognised path; nothing on GitHub's side reads it. Without
# this check the committed JSON is documentation cosplaying as configuration — ADR-0018's exact
# failure shape, artifact present, property absent.
set -euo pipefail

REPO="${RULESET_REPO:-Muddl/riot-api-mcp-server}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.github/rulesets" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
status=0

# Server-generated fields that are not part of the desired state.
STRIP='del(.id, .node_id, ._links, .created_at, .updated_at, .source, .source_type, .current_user_can_bypass)'

for file in R1-all-branches.json R2-default-branch.json; do
  name="$(jq -r .name "$DIR/$file")"
  ids="$(gh api "repos/$REPO/rulesets" --jq ".[] | select(.name == \"$name\") | .id")"
  if [ -z "$ids" ]; then
    echo "::error title=Ruleset drift::No live ruleset named \"$name\". Expected from $file."
    status=1
    continue
  fi
  count="$(printf '%s\n' "$ids" | wc -l)"
  if [ "$count" -ne 1 ]; then
    echo "::error title=Ruleset drift::Found $count live rulesets named \"$name\" (ambiguous, expected exactly one). IDs: $(printf '%s ' $ids)"
    status=1
    continue
  fi
  id="$ids"
  gh api "repos/$REPO/rulesets/$id" | jq -S "$STRIP" > "$TMP/live.json"
  jq -S . "$DIR/$file" > "$TMP/want.json"
  if diff -u "$TMP/want.json" "$TMP/live.json" > "$TMP/diff.txt"; then
    echo "ok   $file (id $id) matches live"
  else
    echo "::error title=Ruleset drift::Live ruleset $id (\"$name\") differs from $file"
    cat "$TMP/diff.txt"
    status=1
  fi
done

# The loop above only confirms the two tracked rulesets match; it says nothing about whether a
# third, untracked ruleset also exists live. GitHub documents nothing about how bypass_actors are
# evaluated when multiple rulesets apply to the same ref, so an extra ruleset — added by hand, or
# left behind live after a committed JSON was renamed (which makes the applier POST a new one
# instead of updating the old) — is a route to weakening the exact property this repo relies on,
# invisibly to the loop above. Assert the live set of ruleset names is exactly {R1, R2}, no more.
expected_names="$(printf '%s\n' "$(jq -r .name "$DIR/R1-all-branches.json")" "$(jq -r .name "$DIR/R2-default-branch.json")" | sort)"
live_names="$(gh api "repos/$REPO/rulesets" --jq '.[].name' | sort)"
if [ -n "$live_names" ]; then
  extra_names="$(comm -23 <(printf '%s\n' "$live_names") <(printf '%s\n' "$expected_names"))"
else
  extra_names=""
fi
if [ -n "$extra_names" ]; then
  echo "::error title=Ruleset drift::Unexpected live ruleset(s) not tracked by any committed JSON:"
  printf '%s\n' "$extra_names" | sed 's/^/  - /'
  status=1
fi

if [ "$status" -ne 0 ]; then
  echo
  echo "Live branch protection no longer matches the committed intent. Either someone hand-edited"
  echo "a ruleset in the UI, or the committed JSON was changed without applying it. Reconcile with"
  echo "scripts/rulesets/apply-rulesets.sh (locally, at a desk) or update the JSON to match."
fi
exit "$status"
