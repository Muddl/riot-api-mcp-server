"""Champion rotation live eval. Non-player-keyed: keyed only by platform, so
there is no discovery step -- the tool is called directly."""

from mcp_eval import task, Expect


@task("Champion rotation returns a populated free-to-play list")
async def test_champion_rotation(agent, session):
    response = await agent.generate_str(
        "Get the current free-to-play champion rotation for NA1. How many "
        "champions are in the rotation, and name one of them?"
    )
    await session.assert_that(
        Expect.tools.was_called("lol_champion_rotation"),
        name="champion_rotation_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports a non-zero number of champions in the "
                "free-to-play rotation and names at least one champion. It "
                "does not report a tool error or an empty rotation."
            ),
            min_score=0.7,
        ),
        response=response,
        name="champion_rotation_populated",
    )
