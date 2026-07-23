"""TFT account live eval. Resolves a real, currently-existing player discovered
from the TFT ladder, rather than hardcoding a Riot ID (which would rot as
players rename), exercising the Riot ID/PUUID -> account path."""

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


@task("TFT account resolves for a discovered player and round-trips to a PUUID")
@with_agent(TFT_TESTER)
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
