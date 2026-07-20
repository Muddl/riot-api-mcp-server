"""TFT account live eval. Resolves a real, currently-existing player discovered
from the TFT ladder, rather than hardcoding a Riot ID (which would rot as
players rename), exercising the Riot ID/PUUID -> account path."""

from mcp_eval import task, with_agent, Expect


@with_agent("tft_tester")
@task("TFT account resolves for a discovered player and round-trips to a PUUID")
async def test_tft_account_roundtrip(agent, session):
    response = await agent.generate_str(
        "Get the CHALLENGER TFT apex league on NA1 and pick any one player. "
        "Look up the Riot account for that player and report the PUUID."
    )
    await session.assert_that(
        Expect.tools.was_called("tft_account_by_player"),
        name="tft_account_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports an account with a PUUID (a long "
                "alphanumeric identifier) for the selected player, OR clearly "
                "states the account could not be found. Either is a valid "
                "outcome. It must not fabricate a PUUID or surface a raw tool "
                "error."
            ),
            min_score=0.7,
        ),
        response=response,
        name="tft_account_resolved",
    )
