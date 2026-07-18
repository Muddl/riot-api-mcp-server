"""Match live evals. Subjects are discovered from the CHALLENGER apex league;
the match-detail task chains through the match-IDs tool to discover a real
match ID rather than hardcoding one, since lol_match_by_id is keyed by a
match ID, not a player."""

from mcp_eval import task, Expect


@task("Match IDs resolve for a discovered player")
async def test_match_ids_for_discovered_player(agent, session):
    response = await agent.generate_str(
        "Get the CHALLENGER apex league for RANKED_SOLO_5x5 on NA1, pick any "
        "one player, then get that player's 5 most recent match IDs on "
        "region AMERICAS. Report how many match IDs were returned and one "
        "example match ID."
    )
    await session.assert_that(
        Expect.tools.was_called("lol_match_ids_by_player"),
        name="match_ids_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports a count of match IDs (0 to 5 inclusive) "
                "and, if any were returned, shows at least one example match "
                "ID. If the player has no match history, it clearly says so. "
                "It must not report a tool error."
            ),
            min_score=0.7,
        ),
        response=response,
        name="match_ids_reported",
    )


@task("Match detail resolves for a discovered match ID")
async def test_match_detail_for_discovered_match(agent, session):
    response = await agent.generate_str(
        "Get the CHALLENGER apex league for RANKED_SOLO_5x5 on NA1, pick any "
        "one player, get that player's most recent match ID on region "
        "AMERICAS, then get the full match detail for that match ID on "
        "region AMERICAS. Report the game duration and how many "
        "participants were in the match."
    )
    await session.assert_that(
        Expect.tools.was_called("lol_match_ids_by_player"),
        name="match_ids_called_for_detail",
    )
    await session.assert_that(
        Expect.tools.was_called("lol_match_by_id"),
        name="match_by_id_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports a positive game duration and a positive "
                "participant count (most LoL match modes field 10, but the "
                "exact count is not fixed since the queue is not filtered), "
                "unless the player genuinely had no match history, in which "
                "case it clearly says so instead. It must not report a tool "
                "error."
            ),
            min_score=0.7,
        ),
        response=response,
        name="match_detail_reported",
    )
