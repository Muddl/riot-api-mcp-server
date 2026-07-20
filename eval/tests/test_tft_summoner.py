"""TFT summoner live eval. Subject is discovered from the CHALLENGER apex
league, so nothing is hardcoded to rot."""

from mcp_eval import task, with_agent, Expect


@with_agent("tft_tester")
@task("TFT summoner resolves for a discovered apex player")
async def test_tft_summoner_for_discovered_player(agent, session):
    response = await agent.generate_str(
        "Get the CHALLENGER TFT apex league on NA1, pick any one player, "
        "look up that player's TFT summoner on NA1, and report the summoner "
        "level."
    )
    await session.assert_that(
        Expect.tools.was_called("tft_league_apex_by_tier"),
        name="tft_apex_called",
    )
    await session.assert_that(
        Expect.tools.was_called("tft_summoner_by_player"),
        name="tft_summoner_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports a positive summoner level for the "
                "selected player. It must not report a tool error or "
                "fabricate a level."
            ),
            min_score=0.7,
        ),
        response=response,
        name="tft_summoner_level_reported",
    )
