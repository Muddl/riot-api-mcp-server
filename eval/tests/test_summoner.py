"""Summoner live eval. The subject is discovered from the CHALLENGER ladder."""

from mcp_eval import task, Expect


@task("Summoner resolves for a discovered top player")
async def test_summoner_for_discovered_player(agent, session):
    response = await agent.generate_str(
        "Get the CHALLENGER apex league for RANKED_SOLO_5x5 on NA1, pick any one "
        "player, then look up that player's summoner information on NA1. "
        "Report the summoner level."
    )
    await session.assert_that(
        Expect.tools.was_called("lol_summoner_by_player"),
        name="summoner_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports a summoner with a positive summoner level "
                "(an integer >= 1). It does not report a tool error or a missing "
                "summoner."
            ),
            min_score=0.7,
        ),
        response=response,
        name="summoner_has_level",
    )
