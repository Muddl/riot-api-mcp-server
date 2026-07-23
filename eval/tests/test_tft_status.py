"""TFT platform status live eval. Non-player-keyed: keyed only by platform, so
there is no discovery step -- the tool is called directly."""

from mcp_agent.agents.agent import Agent
from mcp_eval import task, with_agent, Expect

# mcpevals 0.1.10: @with_agent is a marker that sets an attribute the @task wrapper reads at
# run time, so @task must be the OUTER decorator (above) and @with_agent the inner (below).
# Reversed, the override is set too late and the test silently falls back to default_agent
# (the LoL agent on lol_server) with no tft_* tools. This inline Agent binds it to tft_server.
TFT_TESTER = Agent(
    name="tft_tester",
    instruction=(
        "You are a precise QA agent for a Teamfight Tactics MCP server. "
        "Use the provided tools to answer. Prefer NA1 as the platform and "
        "AMERICAS as the region unless told otherwise. If a tool call returns "
        "an error, do NOT retry it — report the failure plainly and stop, "
        "rather than inventing data or calling it repeatedly."
    ),
    server_names=["tft_server"],
)


@task("TFT platform status returns a usable status for NA1")
@with_agent(TFT_TESTER)
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
