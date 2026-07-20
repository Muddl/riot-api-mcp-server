"""TFT league ranked-data live eval. Subject is discovered at runtime from the
CHALLENGER apex league, so nothing is hardcoded to rot."""

from mcp_eval import task, with_agent, Expect


@with_agent("tft_tester")
@task("TFT apex league returns a populated CHALLENGER ladder")
async def test_tft_apex_challenger(agent, session):
    response = await agent.generate_str(
        "Get the CHALLENGER TFT apex league on NA1. Report that it lists "
        "ranked players with league points."
    )
    await session.assert_that(
        Expect.tools.was_called("tft_league_apex_by_tier"),
        name="tft_apex_tool_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports a non-empty CHALLENGER TFT apex ladder and "
                "shows league point (LP) values for at least one player. It "
                "does not report an error or an empty league."
            ),
            min_score=0.7,
        ),
        response=response,
        name="tft_apex_populated",
    )
