"""TFT league ranked-data live eval. Subject is discovered at runtime from the
CHALLENGER apex league, so nothing is hardcoded to rot."""

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


@task("TFT apex league returns a populated CHALLENGER ladder")
@with_agent(TFT_TESTER)
async def test_tft_apex_challenger(agent, session):
    response = await agent.generate_str(
        "Get the CHALLENGER TFT apex league on NA1. Report that it lists "
        "ranked players with league points."
    )
    await session.assert_that(
        Expect.tools.was_called("tft_league_apex_by_tier"),
        name="tft_apex_tool_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports a non-empty CHALLENGER TFT apex ladder and "
                "shows league point (LP) values for at least one player. It "
                "does not report an error or an empty league."
            ),
            min_score=0.7,
        ),
        response=response,
        name="tft_apex_populated",
    )
