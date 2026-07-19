"""Platform status live eval. Non-player-keyed: keyed only by platform, so
there is no discovery step -- the tool is called directly."""

from mcp_eval import task, Expect


@task("Platform status returns a coherent status report")
async def test_status_platform(agent, session):
    response = await agent.generate_str(
        "Get the current platform status for NA1. Summarize whether there "
        "are any active incidents or scheduled maintenance."
    )
    await session.assert_that(
        Expect.tools.was_called("lol_status_platform"),
        name="status_platform_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer summarizes the platform status, EITHER by "
                "reporting specific incidents/maintenance OR by clearly "
                "stating there are none active. Both are valid outcomes. It "
                "must not report a tool error or fabricate incidents."
            ),
            min_score=0.7,
        ),
        response=response,
        name="status_platform_reported",
    )
