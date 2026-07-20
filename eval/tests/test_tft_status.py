"""TFT platform status live eval. Non-player-keyed: keyed only by platform, so
there is no discovery step -- the tool is called directly."""

from mcp_eval import task, with_agent, Expect


@with_agent("tft_tester")
@task("TFT platform status returns a usable status for NA1")
async def test_tft_status_platform(agent, session):
    response = await agent.generate_str(
        "Get the Teamfight Tactics platform status for NA1. "
        "Report whether there are any active incidents or maintenances."
    )
    await session.assert_that(
        Expect.tools.was_called("tft_status_platform"),
        name="tft_status_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports the TFT platform status for NA1 — either that "
                "there are active incidents/maintenances (with some detail) or that "
                "the platform is operational. It must not surface a raw error or stack trace."
            ),
            min_score=0.7,
        ),
        response=response,
        name="tft_status_usable",
    )
