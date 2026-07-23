#!/usr/bin/env python3
"""Summarise the token spend of a live-eval run.

Reads an mcp-eval JSON report and prints per-test and per-tool token tables plus
a priced total.

Deliberately ignores the report's own ``cost_estimate`` field: it prices at a
~$0.50/MTok fallback rate, understating Claude Haiku 4.5 by roughly 2x. We
price from the report's raw token counts at Haiku 4.5's real rates instead,
so the number moves for the same reason the bill does.

That fixes the *rate* error only. The raw metrics this script reads are
judge-blind: they cover the agent loop, not the separate LLM-judge call each
``Expect.judge.llm(...)`` assertion makes. Judge token spend is therefore
unmeasured by both ``cost_estimate`` and this script's totals — a remaining
limitation, not something this tool solves.

Usage:
    cd eval && uv run python tools/report-cost.py test-reports/stdio.json
    cd eval && uv run python tools/report-cost.py test-reports/stdio.json test-reports/sse.json
"""

from __future__ import annotations

import json
import sys
from collections import defaultdict
from pathlib import Path

# Claude Haiku 4.5 list rates, USD per million tokens.
INPUT_PER_MTOK = 1.00
OUTPUT_PER_MTOK = 5.00


def price(input_tokens: int, output_tokens: int) -> float:
    """Cost in USD of the given token counts at Haiku 4.5 rates."""
    return (input_tokens * INPUT_PER_MTOK + output_tokens * OUTPUT_PER_MTOK) / 1_000_000


def token_counts(test: dict) -> tuple[int, int]:
    """Input and output tokens for one test, tolerating both metric layouts."""
    metrics = test.get("metrics") or {}
    llm = metrics.get("llm_metrics") or {}
    return (
        llm.get("input_tokens", metrics.get("input_tokens", 0)) or 0,
        llm.get("output_tokens", metrics.get("output_tokens", 0)) or 0,
    )


def report(path: Path) -> tuple[int, int]:
    """Print the tables for one report file; return its (input, output) totals."""
    tests = json.loads(path.read_text(encoding="utf-8")).get("decorator_tests", [])
    if not tests:
        print(f"{path}: no decorator_tests found", file=sys.stderr)
        return 0, 0

    rows = []
    tool_sizes: dict[str, list[int]] = defaultdict(list)
    total_in = total_out = 0

    for test in tests:
        tokens_in, tokens_out = token_counts(test)
        total_in += tokens_in
        total_out += tokens_out
        metrics = test.get("metrics") or {}
        calls = metrics.get("tool_calls") or []
        rows.append(
            (
                price(tokens_in, tokens_out),
                tokens_in,
                tokens_out,
                len(calls),
                metrics.get("iteration_count"),
                bool(test.get("passed")),
                test.get("test_name", "?"),
            )
        )
        for call in calls:
            # Approximate the emitted result size in tokens (chars/4). This is a rough
            # proxy computed from the call's JSON result, not a metered token count like
            # the priced totals above — close enough to rank tools by payload weight, but
            # not directly comparable to the llm_metrics-based numbers in the tables above.
            tool_sizes[call.get("name", "?")].append(len(json.dumps(call.get("result", {}))) // 4)

    print(f"\n=== {path.name} ===")
    print(
        f"{len(tests)} tasks | in {total_in:,} | out {total_out:,} "
        f"| ${price(total_in, total_out):.4f} @ Haiku 4.5 rates"
    )

    print(f"\n{'cost':>9}  {'in':>9}  {'out':>6}  {'calls':>5}  {'iter':>4}  ok  test")
    for cost, tokens_in, tokens_out, calls, iterations, passed, name in sorted(rows, reverse=True):
        print(
            f"${cost:>8.4f}  {tokens_in:>9,}  {tokens_out:>6,}  {calls:>5}  "
            f"{str(iterations or '-'):>4}  {'y' if passed else 'N':>2}  {name}"
        )

    if tool_sizes:
        print(f"\n{'tool':<42} {'n':>3}  {'approx tok/call (chars/4)':>26}  {'approx total (chars/4)':>22}")
        for name, sizes in sorted(tool_sizes.items(), key=lambda kv: -sum(kv[1])):
            print(
                f"{name:<42} {len(sizes):>3}  {sum(sizes) // len(sizes):>26,}  {sum(sizes):>22,}"
            )

    return total_in, total_out


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__, file=sys.stderr)
        return 2

    grand_in = grand_out = 0
    reported = 0
    for arg in argv[1:]:
        path = Path(arg)
        if not path.is_file():
            print(f"skipping missing file: {path}", file=sys.stderr)
            continue
        tokens_in, tokens_out = report(path)
        grand_in += tokens_in
        grand_out += tokens_out
        reported += 1

    if reported > 1:
        print("\n=== combined ===")
        print(
            f"in {grand_in:,} | out {grand_out:,} "
            f"| ${price(grand_in, grand_out):.4f} @ Haiku 4.5 rates"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
