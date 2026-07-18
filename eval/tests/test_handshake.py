"""Connectivity + inventory smoke test.

A successful agent turn proves the MCP handshake completed over the active
transport. Over stdio this doubles as the stdout-purity check: any stray log
line on stdout corrupts the JSON-RPC stream and the session cannot connect.
"""

from mcp_eval import task, Expect


@task("Server serves and lists its tools over the active transport")
async def test_lists_tools(agent, session):
    # Asking the agent to enumerate its tools forces a connect + tools/list.
    response = await agent.generate_str(
        "List the names of every tool you have available. "
        "Return them as a plain comma-separated list."
    )
    # Invariant: the League ranked tools are present (a representative subset;
    # do not over-fit to all seven in prose, the agent may summarize).
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The response lists MCP tool names for a League of Legends "
                "server, including at least league, summoner, and spectator "
                "related tools. It must not claim to have no tools."
            ),
            min_score=0.7,
        ),
        response=response,
        name="tools_listed",
    )
