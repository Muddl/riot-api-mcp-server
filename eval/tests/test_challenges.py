"""Challenges live eval. The subject is discovered from the CHALLENGER
ladder."""

from mcp_eval import task, Expect


@task("Challenges data resolves for a discovered player")
async def test_challenges_for_discovered_player(agent, session):
    response = await agent.generate_str(
        "Get the CHALLENGER apex league for RANKED_SOLO_5x5 on NA1, pick any "
        "one player, then look up that player's challenges data on NA1. "
        "Report their total challenge points."
    )
    await session.assert_that(
        Expect.tools.was_called("lol_challenges_by_player"),
        name="challenges_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports a total challenge points/score that is a "
                "non-negative number, OR clearly states that no challenges "
                "data was found for the player. It must not report a "
                "negative score or a tool error."
            ),
            min_score=0.7,
        ),
        response=response,
        name="challenges_reported",
    )
