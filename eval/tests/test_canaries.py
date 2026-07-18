"""Negative / behavior-canary layer.

Tier A probes guard undocumented Riot assumptions our adapters depend on; a
failure here means "investigate a Riot behavior change", not "your code broke".
Tier B confirms our own input validation surfaces a usable error to the agent.

Do not "fix" a Tier-A failure by loosening the rubric — first confirm whether
Riot's live behavior actually changed.
"""

from mcp_eval import task, Expect

# A well-formed-looking but almost-certainly-nonexistent PUUID (78 chars).
FAKE_PUUID = "0" * 78
# A high-entropy Riot ID that should resolve to no account.
FAKE_RIOT_ID = "zzq9v7wxk2#zz999"


# --- Tier A: Riot-behavior canaries -----------------------------------------

@task("CANARY: unknown PUUID yields a clean not-found, not a crash")
async def test_unknown_puuid_summoner(agent, session):
    response = await agent.generate_str(
        f"Look up the summoner for the PUUID '{FAKE_PUUID}' on NA1. "
        "If it cannot be found, say so."
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer clearly communicates that the summoner/player could "
                "not be found or that the lookup failed as a not-found. It must "
                "not fabricate summoner data and must not surface an unhandled "
                "stack trace."
            ),
            min_score=0.7,
        ),
        response=response,
        name="unknown_puuid_not_found",
    )


@task("CANARY: nonexistent Riot ID yields a clean not-found")
async def test_unknown_riot_id_account(agent, session):
    response = await agent.generate_str(
        f"Get the Riot account for the Riot ID '{FAKE_RIOT_ID}'. "
        "If no such account exists, say so."
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer clearly communicates that no account was found for "
                "that Riot ID. It must not fabricate an account or surface an "
                "unhandled error."
            ),
            min_score=0.7,
        ),
        response=response,
        name="unknown_riot_id_not_found",
    )


@task("CANARY: spectator 404->null mapping holds (never a raw error)")
async def test_spectator_not_in_game_invariant(agent, session):
    # Use a discovered ladder player, who is very often NOT currently in a game.
    response = await agent.generate_str(
        "Get the CHALLENGER apex league for RANKED_SOLO_5x5 on NA1, pick any one "
        "player, then check whether that player is currently in a live game on "
        "NA1 using the current-game tool. Report the result."
    )
    await session.assert_that(
        Expect.tools.was_called("lol_spectator_current_game_by_player"),
        name="current_game_called",
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer states EITHER that the player is currently in a game "
                "(with some game detail) OR that they are not currently in a game. "
                "Both are valid. It must NOT surface a raw error, HTTP status, or "
                "stack trace — 'not in a game' must read as a clean, expected "
                "outcome."
            ),
            min_score=0.7,
        ),
        response=response,
        name="spectator_clean_no_game",
    )


# --- Tier B: our-side input validation --------------------------------------

@task("VALIDATION: invalid platform yields a usable error message")
async def test_invalid_platform(agent, session):
    response = await agent.generate_str(
        "Get the summoner for the player 'Faker#KR1' on the platform 'ZZ9'. "
        "If the platform is invalid, tell me."
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer communicates that the platform value is invalid / not "
                "recognized, in a way a user could act on. It must not fabricate "
                "summoner data."
            ),
            min_score=0.7,
        ),
        response=response,
        name="invalid_platform_reported",
    )


@task("VALIDATION: malformed Riot ID yields a usable error message")
async def test_malformed_riot_id(agent, session):
    response = await agent.generate_str(
        "Get the Riot account for the player 'notariotid-no-hash'. "
        "If the identifier is malformed, tell me how it should be formatted."
    )
    await session.assert_that(
        Expect.judge.llm(
            rubric=(
                "The answer communicates that the identifier is malformed and "
                "indicates the expected GameName#TAG (or PUUID) format. It must "
                "not fabricate an account."
            ),
            min_score=0.7,
        ),
        response=response,
        name="malformed_riot_id_reported",
    )
