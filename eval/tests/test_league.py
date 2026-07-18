"""League ranked-data live evals. Subjects are discovered at runtime from the
CHALLENGER apex league, so nothing is hardcoded to rot."""

from mcp_eval import task, Expect


@task("Apex league returns a populated CHALLENGER ladder")
async def test_apex_challenger(agent, session):
    response = await agent.generate_str(
        "Get the CHALLENGER apex league for the RANKED_SOLO_5x5 queue on NA1. "
        "How many entries does it contain, and name one player in it?"
    )
    await session.assert_that(
        Expect.tools.was_called("lol_league_apex_by_tier"),
        name="apex_tool_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports a CHALLENGER league with a non-zero number "
                "of entries and names at least one player. It does not report an "
                "error or an empty league."
            ),
            min_score=0.7,
        ),
        response=response,
        name="apex_populated",
    )


@task("Ranked entries resolve for a discovered top player")
async def test_entries_for_discovered_player(agent, session):
    response = await agent.generate_str(
        "Get the CHALLENGER apex league for RANKED_SOLO_5x5 on NA1, pick any one "
        "player from it, then look up that same player's ranked league entries. "
        "Report the player's tier and rank."
    )
    await session.assert_that(
        Expect.tools.was_called("lol_league_apex_by_tier"),
        name="apex_called",
    )
    await session.assert_that(
        Expect.tools.was_called("lol_league_entries_by_player"),
        name="entries_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer states a ranked tier and division (e.g. CHALLENGER, "
                "or a numeric LP/rank) for the selected player, OR clearly states "
                "the player is unranked in that queue. It must not report a tool "
                "error."
            ),
            min_score=0.7,
        ),
        response=response,
        name="entries_report_rank",
    )
