# Reviewer probe (temporary)

This is a throwaway PR to validate that the fixed `claude-code-review.yml` actually posts a
review on a pull request now that it is live on `master`. It will be closed unmerged.

```bash
# trivial sample for the reviewer to summarize
greet() {
  local name="$1"
  echo "hello, ${name}"
}
greet "world"
```
