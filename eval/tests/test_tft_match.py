"""TFT match live eval. Subject is discovered from the CHALLENGER apex league;
the match-detail task chains through the match-IDs tool to discover a real
match ID rather than hardcoding one, since tft_match_by_id is keyed by a
match ID, not a player."""

from mcp_eval import task, with_agent, Expect


@with_agent("tft_tester")
@task("TFT match placement resolves for a discovered player's recent match")
async def test_tft_match_placement_for_discovered_player(agent, session):
    response = await agent.generate_str(
        "Get the CHALLENGER TFT apex league on NA1, pick a player, get their "
        "recent TFT match IDs in AMERICAS, then fetch one match and report "
        "that player's placement (1-8)."
    )
    await session.assert_that(
        Expect.tools.was_called("tft_match_ids_by_player"),
        name="tft_match_ids_called",
    )
    await session.assert_that(
        Expect.tools.was_called("tft_match_by_id"),
        name="tft_match_by_id_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports a placement between 1 and 8 inclusive for "
                "the selected player in the fetched match. If the player had "
                "no match history, it clearly says so instead. It must not "
                "report a tool error or an out-of-range placement."
            ),
            min_score=0.7,
        ),
        response=response,
        name="tft_match_placement_reported",
    )
