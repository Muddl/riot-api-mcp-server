"""Champion mastery live eval. The subject is discovered from the CHALLENGER
ladder, exercising the player-keyed path plus the optional top-N count param."""

from mcp_eval import task, Expect


@task("Champion mastery resolves for a discovered player")
async def test_champion_mastery_for_discovered_player(agent, session):
    response = await agent.generate_str(
        "Get the CHALLENGER apex league for RANKED_SOLO_5x5 on NA1, pick any "
        "one player, then get that player's champion mastery on NA1, limited "
        "to their top 3 champions by mastery points. Report the top champion "
        "and its mastery point value."
    )
    await session.assert_that(
        Expect.tools.was_called("lol_champion_mastery_by_player"),
        name="champion_mastery_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports at most 3 champion mastery entries, each "
                "with a non-negative mastery point value, and names the top "
                "champion by points. If the player has no mastery data, it "
                "clearly says so. It must not report impossible values "
                "(negative points) or a tool error."
            ),
            min_score=0.7,
        ),
        response=response,
        name="champion_mastery_reported",
    )
