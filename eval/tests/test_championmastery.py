"""Champion mastery live eval. The subject is discovered from the CHALLENGER
ladder, exercising the player-keyed path plus the optional top-N count param.

The count param is asserted structurally, not through the judge: the prompt asks
for a single champion, so a rubric that graded "reports 3 entries" would penalise
the agent for obeying the prompt. See gotchas.md."""

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
    # Structural, not judged: proves the top-N count param reached the tool.
    await session.assert_that(
        Expect.tools.called_with("lol_champion_mastery_by_player", {"count": 3}),
        name="champion_mastery_count_passed",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer names the top champion by mastery points and gives "
                "its mastery point value, which must be non-negative. If the "
                "player has no mastery data, it clearly says so instead. It "
                "must not report a tool error. Reporting only the single top "
                "champion is correct and expected — do not require additional "
                "champions to be listed."
            ),
            min_score=0.7,
        ),
        response=response,
        name="champion_mastery_reported",
    )
