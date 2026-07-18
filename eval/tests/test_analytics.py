"""Analytics live eval. Asserts data-shape invariants that hold regardless of
which player is discovered or how they have been playing."""

from mcp_eval import task, Expect


@task("Analytics returns coherent aggregates for a discovered player")
async def test_analytics_invariants(agent, session):
    response = await agent.generate_str(
        "Get the CHALLENGER apex league for RANKED_SOLO_5x5 on NA1, pick any one "
        "player, then analyze that player's 5 most recent matches on platform NA1 "
        "and region AMERICAS. Report their average KDA and win rate as a "
        "percentage."
    )
    await session.assert_that(
        Expect.tools.was_called("lol_analytics_player_matches"),
        name="analytics_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports an average KDA that is a non-negative number "
                "and a win rate between 0% and 100% inclusive, over at most 5 "
                "matches. If the player has no recent matches, it clearly says so. "
                "It must not report impossible values (negative KDA, win rate > "
                "100%) or a tool error."
            ),
            min_score=0.7,
        ),
        response=response,
        name="analytics_invariants_hold",
    )
