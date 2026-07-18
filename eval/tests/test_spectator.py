"""Spectator live evals. Featured games always exist and guarantee a live
in-game subject to feed the current-game tool."""

from mcp_eval import task, Expect


@task("Featured games returns a non-empty list on NA1")
async def test_featured_games(agent, session):
    response = await agent.generate_str(
        "Get the current featured games on NA1. How many are there, and name a "
        "champion being played in one of them?"
    )
    await session.assert_that(
        Expect.tools.was_called("lol_spectator_featured_games"),
        name="featured_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports one or more featured games and names at least "
                "one champion or participant. It does not report an error."
            ),
            min_score=0.7,
        ),
        response=response,
        name="featured_non_empty",
    )


@task("Current-game resolves for a player currently in a featured game")
async def test_current_game_for_featured_player(agent, session):
    response = await agent.generate_str(
        "Get the current featured games on NA1, pick one participant from a "
        "featured game, then look up that participant's current live game on NA1. "
        "Confirm whether they are in an active game and, if so, the game mode."
    )
    await session.assert_that(
        Expect.tools.was_called("lol_spectator_current_game_by_player"),
        name="current_game_called",
    )
    # Invariant, not a fixed state: a featured participant is usually still in a
    # game, but the tool legitimately returns "not in a game" if it just ended.
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer either describes an active live game (game mode / "
                "participants) OR clearly states the player is not currently in a "
                "game. It must NOT surface a raw error, stack trace, or unmapped "
                "failure — 'not in a game' is a valid, clean outcome."
            ),
            min_score=0.7,
        ),
        response=response,
        name="current_game_clean_outcome",
    )
