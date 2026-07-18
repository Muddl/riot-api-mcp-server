"""Account live eval. Resolves a real, currently-existing player discovered from
the ladder, exercising the Riot ID -> PUUID path."""

from mcp_eval import task, Expect


@task("Account resolves for a discovered player and round-trips to a PUUID")
async def test_account_roundtrip(agent, session):
    response = await agent.generate_str(
        "Get the CHALLENGER apex league for RANKED_SOLO_5x5 on NA1 and pick any "
        "one player. Look up that player's account information. Report their PUUID "
        "and, if available, their Riot ID (GameName#TAG)."
    )
    await session.assert_that(
        Expect.tools.was_called("lol_account_by_player"),
        name="account_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports an account with a PUUID (a long alphanumeric "
                "identifier). It does not report that the account was not found or "
                "a tool error."
            ),
            min_score=0.7,
        ),
        response=response,
        name="account_resolved",
    )
