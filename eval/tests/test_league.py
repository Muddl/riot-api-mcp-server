"""League ranked-data live evals. Subjects are discovered at runtime from the
CHALLENGER apex league, so nothing is hardcoded to rot."""

from mcp_eval import task, Expect


@task("Apex league returns a populated CHALLENGER ladder")
async def test_apex_challenger(agent, session):
    response = await agent.generate_str(
        "Get the CHALLENGER apex league for the RANKED_SOLO_5x5 queue on NA1. "
        "The tool caps the entries it returns, so report the total number of "
        "players in the league (the totalEntries field), and name one player "
        "from the entries you received."
    )
    await session.assert_that(
        Expect.tools.was_called("lol_league_apex_by_tier"),
        name="apex_tool_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports a CHALLENGER league whose total size is well "
                "above the number of entries actually returned — a real ladder has "
                "on the order of hundreds of players, so a reported total of only "
                "about ten means the truncated list was mistaken for the whole "
                "league and must fail. It also identifies at least one entry. A "
                "PUUID, summoner id, or LP/rank is sufficient identification — Riot "
                "no longer exposes human-readable summoner names in league entries, "
                "so do not require a display name. It does not report an error or "
                "an empty league."
            ),
            min_score=0.7,
        ),
        response=response,
        name="apex_reports_true_ladder_size",
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
