"""TFT analytics live eval. Asserts data-shape invariants that hold regardless
of which player is discovered or how they have been playing."""

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


@task("TFT analytics returns coherent aggregates for a discovered player")
@with_agent(TFT_TESTER)
async def test_tft_analytics_invariants(agent, session):
    # Steer explicitly to the composite analytics tool, mirroring the LoL
    # analytics eval: left unsteered, the agent tends to assemble the
    # analysis from the granular match tools and never call
    # tft_analytics_player_matches.
    response = await agent.generate_str(
        "Get the CHALLENGER TFT apex league on NA1, pick any one player, then "
        "use the analytics tool (tft_analytics_player_matches) to analyze "
        "that player's recent TFT matches on NA1 / AMERICAS. Report their "
        "average placement and top-4 rate."
    )
    await session.assert_that(
        Expect.tools.was_called("tft_analytics_player_matches"),
        name="tft_analytics_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer reports an average placement between 1 and 8 "
                "inclusive and a top-4 rate expressed as a percentage between "
                "0% and 100% inclusive. If the player has no recent matches, "
                "the zero-games case reads cleanly (e.g. 'no recent matches "
                "to analyze'), not as an error. It must not report impossible "
                "values or a tool error."
            ),
            min_score=0.7,
        ),
        response=response,
        name="tft_analytics_invariants_hold",
    )
